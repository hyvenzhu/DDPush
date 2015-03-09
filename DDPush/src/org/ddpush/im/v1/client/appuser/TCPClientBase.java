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
package org.ddpush.im.v1.client.appuser;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.ddpush.im.v1.client.appuser.UDPClientBase.Worker;

public abstract class TCPClientBase implements Runnable {
	protected static int connectTimeout = 10;
	protected SocketChannel channel;
	protected long lastSent = 0;
	protected int remotePort = 9966;
	protected int appid = 1;
	protected byte[] uuid;
	protected String remoteAddress = null;
	protected ConcurrentLinkedQueue<Message> mq = new ConcurrentLinkedQueue<Message>();
	
	protected AtomicLong queueIn = new AtomicLong(0);
	protected AtomicLong queueOut = new AtomicLong(0);

	protected int bufferSize = 1024;
	protected int heartbeatInterval = 50;
	
	protected byte[] bufferArray;
	protected ByteBuffer buffer;
	protected boolean needReset = true;
	
	protected boolean started = false;
	protected boolean stoped = false;
	
	protected Thread receiverT;
	protected Worker worker;
	protected Thread workerT;
	
	private long sentPackets;
	private long receivedPackets;
	
	public TCPClientBase(byte[] uuid, int appid, String serverAddr, int serverPort, int connectTimeout) throws Exception{
		if(uuid == null || uuid.length != 16){
			throw new java.lang.IllegalArgumentException("uuid byte array must be not null and length of 16 bytes");
		}
		if(appid < 1 || appid > 255){
			throw new java.lang.IllegalArgumentException("appid must be from 1 to 255");
		}
		if(serverAddr == null || serverAddr.trim().length() == 0){
			throw new java.lang.IllegalArgumentException("server address illegal: "+serverAddr);
		}
		
		this.uuid = uuid;
		this.appid = appid;
		this.remoteAddress = serverAddr;
		this.remotePort = serverPort;
		TCPClientBase.connectTimeout = connectTimeout;
	}
	
	protected boolean enqueue(Message message){
		boolean result = mq.add(message);
		if(result == true){
			queueIn.addAndGet(1);
		}
		return result;
	}
	
	protected Message dequeue(){
		Message m = mq.poll();
		if(m != null){
			queueOut.addAndGet(1);
		}
		return m;
	}
	
	private synchronized void init(){
		bufferArray = new byte[bufferSize];
		buffer = ByteBuffer.wrap(bufferArray);
		buffer.limit(Message.SERVER_MESSAGE_MIN_LENGTH);
	}
	
	protected synchronized void reset() throws Exception{
		if(needReset == false){
			return;
		}

		if(channel != null){
			try{channel.socket().close();}catch(Exception e){}
			try{channel.close();}catch(Exception e){}
		}
		if(hasNetworkConnection() == true){
			channel = SocketChannel.open();
			channel.configureBlocking(true);
			channel.socket().connect(new InetSocketAddress(remoteAddress, remotePort), 1000*connectTimeout);
			channel.socket().setSoTimeout(1000*5);
			needReset = false;
		}else{
			try{Thread.sleep(1000);}catch(Exception e){}
		}
	}
	
	public synchronized void start() throws Exception{
		if(this.started == true){
			return;
		}
		this.init();
		
		receiverT = new Thread(this,"DDPUSH-TCP-CLIENT-RECEIVER");
		receiverT.setDaemon(true);
		synchronized(receiverT){
			receiverT.start();
			receiverT.wait();
		}
		
		worker = new Worker();
		workerT = new Thread(worker,"DDPUSH-TCP-CLIENT-WORKER");
		workerT.setDaemon(true);
		synchronized(workerT){
			workerT.start();
			workerT.wait();
		}
		
		this.started = true;
	}
	
	public synchronized void stop(){
		stoped = true;
		
		if(channel != null){
			try{channel.socket().close();}catch(Exception e){};
			try{channel.close();}catch(Exception e){};
		}
		channel = null;
		if(receiverT != null){
			try{receiverT.interrupt();}catch(Exception e){}
		}

		if(workerT != null){
			try{workerT.interrupt();}catch(Exception e){}
		}
	}
	
	public void run(){
		
		synchronized(receiverT){
			receiverT.notifyAll();
		}
		
		while(stoped == false){
			try{
				if(hasNetworkConnection() == false){
					try{
						trySystemSleep();
						Thread.sleep(1000);
					}catch(Exception e){}
					continue;
				}
				reset();
				receiveData();
			}catch(java.net.SocketTimeoutException e){
				
			}catch(java.nio.channels.ClosedChannelException e){
				this.needReset = true;
			}catch(Exception e){
				e.printStackTrace();
				this.needReset = true;
			}catch(Throwable t){
				t.printStackTrace();
				this.needReset = true;
			}finally{
				if(needReset == true){
					try{
						trySystemSleep();
						Thread.sleep(1000);
					}catch(Exception e){}
				}
				if(mq.isEmpty() == true || hasNetworkConnection() == false){
					try{
						trySystemSleep();
						Thread.sleep(1000);
					}catch(Exception e){}
				}
			}
		}
		if(this.channel != null){
			try{channel.socket().close();}catch(Exception e){}
			try{channel.close();}catch(Exception e){}
			channel = null;
		}
	}

	private void heartbeat() throws Exception{
		if(System.currentTimeMillis() - lastSent < heartbeatInterval * 1000){
			return;
		}

		byte[] buffer = new byte[Message.CLIENT_MESSAGE_MIN_LENGTH];
		ByteBuffer.wrap(buffer).put((byte)Message.version).put((byte)appid).put((byte)Message.CMD_0x00).put(uuid).putChar((char)0);
		send(buffer);
	}
	
	private void receiveData() throws Exception{
		while(hasPacket() == false){
			int read = channel.read(buffer);
			if(read < 0){
				throw new Exception("end of stream");
			}
			if(hasPacket() == true){
				break;
			}
			if(mq.isEmpty() == true || hasNetworkConnection() == false){
				try{
					trySystemSleep();
					Thread.sleep(1000);
				}catch(Exception e){}
			}
		}
		
		byte[] data = new byte[buffer.position()];
		System.arraycopy(bufferArray, 0, data, 0, buffer.position());
		Message m = new Message(channel.socket().getRemoteSocketAddress(), data);
		buffer.clear();
		buffer.limit(Message.SERVER_MESSAGE_MIN_LENGTH);
		if(m.checkFormat() == false){
			return;
		}
		this.receivedPackets++;
		this.ackServer(m);
		if(m.getCmd() == Message.CMD_0x00){
			return;
		}
		this.enqueue(m);
		worker.wakeup();
	}
	
	private boolean hasPacket(){
		if(buffer.limit() == Message.SERVER_MESSAGE_MIN_LENGTH){
			if(buffer.hasRemaining() == true){
				return false;
			}else{
				int dataLen = (int)ByteBuffer.wrap(bufferArray, Message.SERVER_MESSAGE_MIN_LENGTH-2, 2).getChar();
				if(dataLen == 0){
					return true;
				}else{
					buffer.limit(Message.SERVER_MESSAGE_MIN_LENGTH+dataLen);
					return false;
				}
			}
		}else{
			if(buffer.hasRemaining() == true){
				return false;
			}else{
				return true;
			}
		}
	}
	
	private void ackServer(Message m) throws Exception{
		if(m.getCmd() == Message.CMD_0x10){
			byte[] buffer = new byte[Message.CLIENT_MESSAGE_MIN_LENGTH];
			ByteBuffer.wrap(buffer).put((byte)Message.version).put((byte)appid).put((byte)Message.CMD_0x10).put(uuid).putChar((char)0);
			send(buffer);
		}
		if(m.getCmd() == Message.CMD_0x11){
			byte[] buffer = new byte[Message.CLIENT_MESSAGE_MIN_LENGTH + 8];
			byte[] data = m.getData();
			ByteBuffer.wrap(buffer).put((byte)Message.version).put((byte)appid).put((byte)Message.CMD_0x11).put(uuid).putChar((char)8).put(data, Message.SERVER_MESSAGE_MIN_LENGTH, 8);
			send(buffer);
		}
		if(m.getCmd() == Message.CMD_0x20){
			byte[] buffer = new byte[Message.CLIENT_MESSAGE_MIN_LENGTH];
			ByteBuffer.wrap(buffer).put((byte)Message.version).put((byte)appid).put((byte)Message.CMD_0x20).put(uuid).putChar((char)0);
			send(buffer);
		}
	}
	
	private void send(byte[] data) throws Exception{
		if(data == null){
			return;
		}
		if(channel == null || channel.isOpen() == false || channel.isConnected() == false){
			return;
		}
		ByteBuffer bb = ByteBuffer.wrap(data);
		while(bb.hasRemaining()){
			channel.write(bb);
		}
		channel.socket().getOutputStream().flush();
		lastSent = System.currentTimeMillis();
		this.sentPackets++;
	}
	
	public long getSentPackets(){
		return this.sentPackets;
	}
	
	public long getReceivedPackets(){
		return this.receivedPackets;
	}
	
	public long getLastHeartbeatTime(){
		return lastSent;
	}
	
	/*
	 * send heart beat every given seconds
	 */
	public void setHeartbeatInterval(int second){
		if(second <= 0){
			return;
		}
		this.heartbeatInterval = second;
	}
	
	public int getHeartbeatInterval(){
		return this.heartbeatInterval;
	}
	
	public abstract boolean hasNetworkConnection();
	public abstract void trySystemSleep();
	public abstract void onPushMessage(Message message);
	
	class Worker implements Runnable{
		public void run(){
			synchronized(workerT){
				workerT.notifyAll();
			}
			while(stoped == false){
				try{
					heartbeat();
					handleEvent();
				}catch(Exception e){
					e.printStackTrace();
				}finally{
					waitMsg();
				}
			}
		}
		
		private void waitMsg(){
			synchronized(this){
				try{
					this.wait(1000);
				}catch(java.lang.InterruptedException e){
					
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		
		private void wakeup(){
			synchronized(this){
				this.notifyAll();
			}
		}
		
		private void handleEvent() throws Exception{
			Message m = null;
			while(true){
				m = dequeue();
				if(m == null){
					return;
				}
				if(m.checkFormat() == false){
					continue;
				}

				//real work here
				onPushMessage(m);
			}
			//finish work here, such as release wake lock
		}
		
	}

}
	
	
	

