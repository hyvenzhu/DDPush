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
package org.ddpush.im.v1.node.tcpconnector;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import org.ddpush.im.util.PropertyUtil;
import org.ddpush.im.v1.node.ClientMessage;
import org.ddpush.im.v1.node.ClientStatMachine;
import org.ddpush.im.v1.node.Constant;
import org.ddpush.im.v1.node.NodeStatus;
import org.ddpush.im.v1.node.ServerMessage;

public class MessengerTask implements Runnable {
	
	private NIOTcpConnector listener;
	private SocketChannel channel;
	private SelectionKey key;
	private long lastActive;
	private boolean isCancel =false;
	
	private boolean writePending = false;

	private byte[] bufferArray;
	private ByteBuffer buffer;
	
	private java.util.LinkedList<ServerMessage> pendingEvents = null;
	
	public MessengerTask(NIOTcpConnector listener, SocketChannel channel){
		this.listener = listener;
		this.channel = channel;
		bufferArray = new byte[Constant.SERVER_MESSAGE_MIN_LENGTH+PropertyUtil.getPropertyInt("PUSH_MSG_MAX_CONTENT_LEN")];
		buffer = ByteBuffer.wrap(bufferArray);
		buffer.limit(Constant.CLIENT_MESSAGE_MIN_LENGTH);
		lastActive = System.currentTimeMillis();
	}
	
	public void setKey(SelectionKey key){
		this.key = key;
	}
	
	private void cancelKey(final SelectionKey key) {
		if(this.isCancel ==true){
			return;
		}
        Runnable r = new Runnable() {
            public void run() {
            	listener.cancelKey(key);
            }
        };
        listener.addEvent(r);this.isCancel =true;
    }
	
	private void registerForWrite(final SelectionKey key, final boolean needWrite) {
		if(key == null || key.isValid() == false){
			return;
		}
		
		if(needWrite == true){
			if((key.interestOps() & SelectionKey.OP_WRITE) > 0){
				return;
			}
		}else{
			if((key.interestOps() & SelectionKey.OP_WRITE) == 0){
				return;
			}
		}
		
		
		Runnable r = new Runnable() {
            public void run() {
            	if(key == null || !key.isValid()){
            		return;
            	}
            	key.selector().wakeup();
            	if(needWrite == true){
            		key.interestOps(key.interestOps()  & (~SelectionKey.OP_READ) | SelectionKey.OP_WRITE);
            	}else{
            		key.interestOps(key.interestOps() & (~SelectionKey.OP_WRITE) | SelectionKey.OP_READ);
            	}
            }
        };
        listener.addEvent(r);
        try{
        	key.selector().wakeup();
        }catch(Exception e){
        	e.printStackTrace();
        }
	}

	@Override
	public synchronized void run() {
		if(listener == null || channel == null){
			return;
		}

		if(key == null){
			return;
		}
		if(this.isCancel == true){//vic
			return;
		}
		try{
			if(writePending == false && buffer.position() == 0 && pendingEvents != null){
				ServerMessage sm = pendingEvents.poll();
				if(sm == null){
					registerForWrite(key, false);
					if(key.isReadable()){
						//read pkg
						readReq();
					}else{
						// do nothing
					}
					return;
				}
				buffer.clear();
				buffer.put(sm.getData());
				buffer.flip();
				this.writePending = true;
				registerForWrite(key, true);
				return;
			}else if(writePending == false){
				if(key.isReadable()){
					//read pkg
					readReq();
				}else{
					// do nothing
				}
			}else{//has package

				//if(key.isWritable()){
					writeRes();
				//}
			}
		}catch(Exception e){
			cancelKey(key);
		}catch(Throwable t){
			cancelKey(key);
		}
		
		//key = null;

	}
	
	private void readReq() throws Exception{
		if(this.writePending == true){
			return;
		}
		
		if(channel.read(buffer) < 0){
			throw new Exception("end of stream");
		}
		if(this.calcWritePending() == false){
			return;
		}else{
			processReq();
		}

		lastActive = System.currentTimeMillis();
	}
	
	private void writeRes() throws Exception{
		if(buffer.hasRemaining()){
			channel.write(buffer);
		}else{
			buffer.clear();
			buffer.limit(Constant.CLIENT_MESSAGE_MIN_LENGTH);
			this.writePending = false;
		}
		lastActive = System.currentTimeMillis();
	}
	
	public long getLastActive(){
		return lastActive;
	}
	
	public boolean isWritePending(){
		return writePending;
	}
	
	private synchronized boolean calcWritePending() throws Exception{
		if(this.writePending == false){
			if(buffer.position() < Constant.CLIENT_MESSAGE_MIN_LENGTH){
				this.writePending = false;
			}else{
				int bodyLen = (int)ByteBuffer.wrap(bufferArray, Constant.CLIENT_MESSAGE_MIN_LENGTH - 2, 2).getChar();
				if(bodyLen > Constant.CLIENT_MESSAGE_MAX_LENGTH-Constant.CLIENT_MESSAGE_MIN_LENGTH){
					throw new java.lang.IllegalArgumentException("content length is "+bodyLen+", larger than the max of "+(Constant.CLIENT_MESSAGE_MAX_LENGTH-Constant.CLIENT_MESSAGE_MIN_LENGTH));
				}
				if(bodyLen == 0){
					this.writePending = true;
				}else{
					if(buffer.limit() != Constant.CLIENT_MESSAGE_MIN_LENGTH + bodyLen){
						buffer.limit(Constant.CLIENT_MESSAGE_MIN_LENGTH + bodyLen);
					}else{
						if(buffer.position() == Constant.CLIENT_MESSAGE_MIN_LENGTH + bodyLen){
							this.writePending = true;
						}
					}
				}
			}
		}else{//this.writePending == true
			if(buffer.hasRemaining()){
				this.writePending = true;
			}else{
				this.writePending = false;
			}
		}

		return this.writePending;
	}
	
	private void processReq() throws Exception{
		//check and put data into nodeStat
		//buffer.flip();
		byte[] data = new byte[buffer.limit()];
		System.arraycopy(bufferArray, 0, data, 0, buffer.limit());
		buffer.clear();
		this.writePending = false;//important
		ClientMessage cm = new ClientMessage(null, data);
		NodeStatus nodeStat = NodeStatus.getInstance();
		String uuid = cm.getUuidHexString(); 
		ClientStatMachine csm = nodeStat.getClientStat(uuid);
		if(csm == null){//
			csm = ClientStatMachine.newByClientTick(cm);
			if(csm == null){
				return;
			}
			nodeStat.putClientStat(uuid, csm);
		}
		csm.setMessengerTask(this);
		ArrayList<ServerMessage> smList = csm.onClientMessage(cm);
		if(smList == null || smList.size() == 0){
			return;
		}
		
		for(int i = 0; i < smList.size(); i++){
			ServerMessage sm = smList.get(i);
			this.pushInstanceMessage(sm);
		}

	}
	
	public void pushInstanceMessage(ServerMessage sm){
		if(sm == null || sm.getData() == null || sm.getData().length == 0){
			return;
		}
		if(this.channel == null || this.channel.isConnected()==false || this.channel.isRegistered() == false){
			return;
		}
		if(this.isCancel == true){
			return;
		}
		if(this.pendingEvents == null){
			this.pendingEvents = new java.util.LinkedList<ServerMessage>();
		}
		this.pendingEvents.add(sm);
		if(key != null){
			this.registerForWrite(key, true);
			key.selector().wakeup();
		}
	}
	
//	private ServerMessage pollInstanceMessage(){
//		if(this.pendingEvents == null){
//			return null;
//		}
//		return this.pendingEvents.poll();
//	}

}
