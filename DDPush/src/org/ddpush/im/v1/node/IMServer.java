/*
 *Copyright 2014 DDPush
 *Author: AndyKwok(in English) GuoZhengzhu(in Chinese)
 *Email: ddpush@126.com
 *

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package org.ddpush.im.v1.node;

import java.util.ArrayList;
import java.util.Date;

import org.ddpush.im.util.DateTimeUtil;
import org.ddpush.im.util.PropertyUtil;
import org.ddpush.im.v1.node.pushlistener.NIOPushListener;
import org.ddpush.im.v1.node.tcpconnector.NIOTcpConnector;
import org.ddpush.im.v1.node.udpconnector.UdpConnector;
/**
 * DDPush服务器
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public class IMServer {
	
	public static IMServer server;

	private boolean stoped = false;
	
	int workerNum = PropertyUtil.getPropertyInt("CLIENT_UDP_WORKER_THREAD");//fixed work threads
	
	private UdpConnector udpConnector;
	
	private Thread tcpConnThread;
	private NIOTcpConnector tcpConnector;
	
	private NodeStatus nodeStatus = NodeStatus.getInstance();
	
	private ArrayList<Messenger> workerList = new ArrayList<Messenger>();
	
	private Thread clearnThread = null;
	private ClientStatMachineCleaner cleaner = null;
	
	private Thread cmdThread = null;
	private IMServerConsole console = null;
	
	private Thread pushThread = null;
	private NIOPushListener pushListener = null;
	
	private long startTime;
	
	private IMServer(){

	}
	
	public static IMServer getInstance(){
		if(server == null){
			synchronized(IMServer.class){
				if(server == null){
					server = new IMServer();
				}
			}
		}
		return server;
	}
	
	public void init() throws Exception{
		initPushListener();
		initConsole();
		initUdpConnector();
		initTcpConnector();
		initWorkers();
		initCleaner();
		
	}
	
	public void initConsole() throws Exception{
		console = new IMServerConsole();
		cmdThread = new Thread(console,"IMServer-console");
		cmdThread.setDaemon(true);
		cmdThread.start();
	}
	
	public void initUdpConnector() throws Exception{
		System.out.println("start connector...");
		udpConnector = new UdpConnector();
		udpConnector.start();
	}
	
	public void initTcpConnector() throws Exception{
		if(!"YES".equalsIgnoreCase(PropertyUtil.getProperty("TCP_CONNECTOR_ENABLE"))){
			return;
		}
		tcpConnector = new NIOTcpConnector();
		tcpConnThread = new Thread(tcpConnector,"IMServer-NIOTcpConnector");
		synchronized(tcpConnector){
			tcpConnThread.start();
			tcpConnector.wait();
		}
	}
	
	public void initWorkers(){
		System.out.println("start "+workerNum+" workers...");
		for(int i = 0; i < workerNum; i++){
			Messenger worker = new Messenger(udpConnector, nodeStatus);
			workerList.add(worker);
			Thread t = new Thread(worker, "IMServer-worker-"+i);
			worker.setHostThread(t);
			t.setDaemon(true);
			t.start();
		}
	}
	
	public void initCleaner() throws Exception{
		cleaner = new ClientStatMachineCleaner();
		clearnThread = new Thread(cleaner,"IMServer-cleaner");
		clearnThread.start();
	}
	
	public void initPushListener() throws Exception{
		pushListener = new NIOPushListener();
		pushThread =  new Thread(pushListener,"IMServer-push-listener");
		pushThread.start();
	}
	
	public void start() throws Exception{
		System.out.println("working dir: "+System.getProperty("user.dir"));
		init();
		
		final Thread mainT = Thread.currentThread();
		
		// 监控Server关闭
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run(){
				stoped = true;
				System.out.println("shut down server... ");
				try{
					mainT.join();
					System.out.println("server is down, bye ");
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		});
		this.startTime = System.currentTimeMillis();
		System.out.println("server is up ");
		while(stoped == false){ // 自动清理失效的状态机和gc内存
			try{
				synchronized(this){
					this.wait(1000*60);
					if(stoped == false){
						autoClean();
					}
				}
			}catch(Exception e){}
		}
		this.quit();
		
	}
	
	private void autoClean(){
		float percent = PropertyUtil.getPropertyFloat("CLEANER_AUTO_RUN_MEM_PERCENT");
		if(percent >=1 || percent <=0){
			return;
		}
		Runtime rt = Runtime.getRuntime();
		if((rt.totalMemory()-rt.freeMemory())/(double)rt.maxMemory() > percent){
			System.out.println("run auto clean...");
			cleaner.wakeup();
		}
	}
	
	public void stop() {
		this.stoped = true;
		synchronized(this){
			this.notifyAll();
		}
		
	}
	
	/**
	 * 停止DDPush
	 * @throws Exception
	 */
	protected void quit() throws Exception{
		try{
			stopWorkers();
			stopUdpConnector();
			stopTcpConnector();
			stopCleaner();
			stopPushListener();
		}catch(Throwable t){
			t.printStackTrace();
		}
		// 保存本地关闭前内存中的状态机
		saveStatus();
	}
	
	public void stopWorkers() throws Exception{
		for(int i = 0; i < workerList.size(); i++){
			try{
				workerList.get(i).stop();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public void stopUdpConnector()  throws Exception{
		if(udpConnector == null){
			return;
		}
		udpConnector.stop();
	}
	
	public void stopTcpConnector()  throws Exception{
		if(tcpConnector == null || tcpConnThread == null){
			return;
		}
		tcpConnector.stop();
		tcpConnThread.join();
	}
	
	public void stopCleaner() throws Exception{
		cleaner.stop();
		try{
			clearnThread.interrupt();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void stopPushListener() throws Exception{
		pushListener.stop();
		pushThread.join();
	}
	
	public void saveStatus() throws Exception{
		nodeStatus.saveToFile();
	}
	
	public String getStatusString(){
		StringBuffer sb = new StringBuffer();
		
		String end = "\r\n";
		
		sb.append("server start up at: ").append(DateTimeUtil.formatDate(new Date(this.startTime))).append(end);
		long runtime = System.currentTimeMillis()-this.startTime;
		sb.append("up time: ").append(runtime/(1000*3600*24)).append(" day ").append(runtime/(1000*3600)).append(" hour ").append(runtime/(1000*60)).append(" minute").append(end);
		sb.append("messagers: ").append(this.workerList.size()).append(end);
		sb.append("current stat machines: ").append(nodeStatus.size()).append(end);
		sb.append("udp recieve packages: ").append(this.udpConnector.getInqueueIn()).append(end);
		sb.append("udp recieve packages pending: ").append(this.udpConnector.getInqueueIn()-this.udpConnector.getInqueueOut()).append(end);
		sb.append("udp send packages: ").append(this.udpConnector.getOutqueueIn()).append(end);
		sb.append("udp send packages pending: ").append(this.udpConnector.getOutqueueIn()-this.udpConnector.getOutqueueOut()).append(end);
		sb.append("jvm  max  mem: ").append(Runtime.getRuntime().maxMemory()).append(end);
		sb.append("jvm total mem: ").append(Runtime.getRuntime().totalMemory()).append(end);
		sb.append("jvm  free mem: ").append(Runtime.getRuntime().freeMemory()).append(end);
		sb.append("last clean time: ").append(DateTimeUtil.formatDate(new Date(this.cleaner.getLastCleanTime()))).append(end);
		sb.append("messengers threads:----------------------").append(end);
		for(int i = 0; i < workerList.size(); i++){
			Thread t = workerList.get(i).getHostThread();
			sb.append(t.getName()+" stat: "+ t.getState().toString()).append(end);
		}
		return sb.toString();
	}
	
	public String getUuidStatString(String uuid){
		ClientStatMachine csm = this.nodeStatus.getClientStat(uuid);
		if(csm == null){
			return null;
		}
		StringBuffer sb = new StringBuffer();
		String end = "\r\n";
		
		sb.append("stat of   uuid: "+uuid).append(end);
		sb.append("last tick time: "+DateTimeUtil.formatDate(new Date(csm.getLastTick()))).append(end);
		sb.append("last ip addres: "+csm.getLastAddr()).append(end);
		sb.append("last tcp  time: "+DateTimeUtil.formatDate(new Date(csm.getMessengerTask()==null?0:csm.getMessengerTask().getLastActive()))).append(end);
		sb.append("0x10   message: "+csm.has0x10Message()).append(end);
		sb.append("last 0x10 time: "+DateTimeUtil.formatDate(new Date(csm.getLast0x10Time()))).append(end);
		sb.append("0x11   message: "+csm.get0x11Message()).append(end);
		sb.append("last 0x11 time: "+DateTimeUtil.formatDate(new Date(csm.getLast0x11Time()))).append(end);
		sb.append("0x20   message: "+csm.has0x20Message()).append(end);
		sb.append("last 0x20 time: "+DateTimeUtil.formatDate(new Date(csm.getLast0x20Time()))).append(end);
		sb.append("0x20 arry  len: "+csm.getMessage0x20Len()).append(end);
		
		return sb.toString();
	}
	
	public void cleanExpiredMachines(int hours){
		cleaner.setExpiredHours(hours);
		cleaner.wakeup();
	}
	
	public void pushInstanceMessage(ServerMessage sm) throws Exception{
		if(sm == null || sm.getData() == null || sm.getSocketAddress() == null){
			return;
		}
		if(udpConnector != null){
			udpConnector.send(sm);
		}
	}
	
	
	public static void main(String[] args){
		IMServer server = IMServer.getInstance();
		try{
		server.start();
		
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}catch(Throwable t){
			t.printStackTrace();
			System.exit(1);
		}
	}
}
