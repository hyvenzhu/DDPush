package org.ddpush.im.v1.node.udpconnector;

import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import org.ddpush.im.util.PropertyUtil;
import org.ddpush.im.v1.node.ClientMessage;
import org.ddpush.im.v1.node.ServerMessage;
/**
 * UDPServer接受终端的连接, 并接受和发送消息
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public class UdpConnector {
	
	protected DatagramChannel antenna;//天线
	
	protected Receiver receiver;
	protected Sender sender;
	
	protected Thread receiverThread;
	protected Thread senderThread;
	
	boolean started = false;
	boolean stoped = false;
	
	protected int port = PropertyUtil.getPropertyInt("CLIENT_UDP_PORT");
	
	public void setPort(int port){
		this.port = port;
	}
	
	public int getPort(){
		return this.port;
	}
	
	public void init(){
		
	}
	
	public void start() throws Exception{
		if(antenna != null){
			throw new Exception("antenna is not null, may have run before");
		}
		antenna = DatagramChannel.open();
		antenna.socket().bind(new InetSocketAddress(port));
		System.out.println("udp connector port:"+port);
		//non-blocking
		antenna.configureBlocking(false);
		antenna.socket().setReceiveBufferSize(1024*1024*PropertyUtil.getPropertyInt("CLIENT_UDP_BUFFER_RECEIVE"));
		antenna.socket().setSendBufferSize(1024*1024*PropertyUtil.getPropertyInt("CLIENT_UDP_BUFFER_SEND"));
		System.out.println("udp connector recv buffer size:"+antenna.socket().getReceiveBufferSize());
		System.out.println("udp connector send buffer size:"+antenna.socket().getSendBufferSize());
		
		
		this.receiver = new Receiver(antenna);
		this.receiver.init();
		this.sender = new Sender(antenna);
		this.sender.init();
		
		this.senderThread = new Thread(sender,"AsynUdpConnector-sender");
		this.receiverThread = new Thread(receiver,"AsynUdpConnector-receiver");
		this.receiverThread.start();
		this.senderThread.start();
	}
	public void stop() throws Exception{
		receiver.stop();
		sender.stop();
		try{
			receiverThread.join();
		}catch(Exception e){
			e.printStackTrace();
		}
		try{
			senderThread.join();
		}catch(Exception e){
			e.printStackTrace();
		}
		try{antenna.socket().close();}catch(Exception e){}
		try{antenna.close();}catch(Exception e){}
	}
	
	public long getInqueueIn(){
		return this.receiver.queueIn.longValue();
	}
	
	public long getInqueueOut(){
		return this.receiver.queueOut.longValue();
	}
	
	public long getOutqueueIn(){
		return this.sender.queueIn.longValue();
	}
	
	public long getOutqueueOut(){
		return this.sender.queueOut.longValue();
	}


	public ClientMessage receive() throws Exception {
		return receiver.receive();
	}


	public boolean send(ServerMessage message) throws Exception {
		return sender.send(message);
		
	}
	
}
