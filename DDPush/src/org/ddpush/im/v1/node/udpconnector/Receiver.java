package org.ddpush.im.v1.node.udpconnector;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;


import org.ddpush.im.util.DateTimeUtil;
import org.ddpush.im.util.StringUtil;
import org.ddpush.im.v1.node.ClientMessage;
/**
 * UDP模式接受终端消息
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public class Receiver implements Runnable{
	
	protected DatagramChannel channel;
	
	protected int bufferSize = 1024;
	
	protected boolean stoped = false;
	protected ByteBuffer buffer;
	private SocketAddress address;

	protected AtomicLong queueIn = new AtomicLong(0);
	protected AtomicLong queueOut = new AtomicLong(0);
	protected ConcurrentLinkedQueue<ClientMessage> mq = new ConcurrentLinkedQueue<ClientMessage>();
	
	public Receiver(DatagramChannel channel){
		this.channel = channel;
	}
	
	public void init(){
		buffer = ByteBuffer.allocate(this.bufferSize);
	}
	
	public void stop(){
		this.stoped = true;
	}
	
	public void run(){
		while(!this.stoped){
			try{
				//synchronized(enQueSignal){
					processMessage();
				//	if(mq.isEmpty() == true){
				//		enQueSignal.wait();
				//	}
				//}
			}catch(Exception e){
				e.printStackTrace();
			}catch(Throwable t){
				t.printStackTrace();
			}
		}
	}
	
	protected void processMessage() throws Exception{
		address = null;
		buffer.clear();
		try{
			address = this.channel.receive(buffer);
		}catch(SocketTimeoutException timeout){
			
		}
		if(address == null){
			try{
				Thread.sleep(1);
			}catch(Exception e){
				
			}
			return;
		}
		
		buffer.flip();
		byte[] swap = new byte[buffer.limit() - buffer.position()];
		System.arraycopy(buffer.array(), buffer.position(), swap, 0, swap.length);

		ClientMessage m = new ClientMessage(address,swap);
		
		enqueue(m);
		//System.out.println(DateTimeUtil.getCurDateTime()+" r:"+StringUtil.convert(m.getData())+" from:"+m.getSocketAddress().toString());

	}
	
	protected boolean enqueue(ClientMessage message){
		boolean result = mq.add(message);
		if(result == true){
			queueIn.addAndGet(1);
		}
		return result;
	}
	
	protected ClientMessage dequeue(){
		ClientMessage m = mq.poll();
		if(m != null){
			queueOut.addAndGet(1);
		}
		return m;
	}
	
	public ClientMessage receive() {

		ClientMessage m = null;
		while(true){
			m = dequeue();
			if(m == null){
				return null;
			}
			if(m.checkFormat() == true){//检查包格式是否合法，为了网络快速响应，在这里检查，不在接收线程检查
				return m;
			}
		}
	}
}
