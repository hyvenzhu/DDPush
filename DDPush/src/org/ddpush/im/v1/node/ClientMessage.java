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

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.ddpush.im.util.StringUtil;
import org.ddpush.im.v1.node.ClientStatMachine;
/**
 * 终端发过来的信息（心跳包、应答信息）
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public final class ClientMessage{
	
	protected SocketAddress address; // sender的地址信息
	protected byte[] data; // sender发送的的具体数据
	
	public ClientMessage(SocketAddress address, byte[] data) throws Exception{
		this.address = address;
		this.data = data;
	}
	
//	public static org.ddpush.im.node.Message getNewInstance(){
//		return null;
//	}
	
	public void setData(byte[] data){
		this.data = data;
	}
	
	public byte[] getData(){
		return this.data;
	}
	
	public SocketAddress getSocketAddress(){
		return this.address;
	}
	
	public void setSocketAddress(SocketAddress addr){
		this.address = addr;
	}
	
	public int getVersionNum(){
		byte b = data[0];
		return b & 0xff;
	}
	
	public int getCmd(){
		byte b = data[2];
		return b & 0xff;
	}
	
	public int getDataLength(){
		return (int)ByteBuffer.wrap(data, 19, 2).getChar();
	}
	
	public String getUuidHexString(){
		return StringUtil.convert(data, 3, 16);
	}
	
	public boolean checkFormat(){
		if(this.data == null){
			return false;
		}
		if(data.length < Constant.CLIENT_MESSAGE_MIN_LENGTH){
			return false;
		}
		if(getVersionNum() != Constant.VERSION_NUM){
			return false;
		}

		int cmd = getCmd();
		if(cmd != ClientStatMachine.CMD_0x00
				//&& cmd != ClientStatMachine.CMD_0x01
				&& cmd != ClientStatMachine.CMD_0x10
				&& cmd != ClientStatMachine.CMD_0x11
				&& cmd != ClientStatMachine.CMD_0x20
				&& cmd != ClientStatMachine.CMD_0xff){
			return false;
		}
		int dataLen = getDataLength();
		if(data.length != dataLen + Constant.CLIENT_MESSAGE_MIN_LENGTH){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x00 && dataLen != 0){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x10 && dataLen != 0){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x11 && dataLen != 8){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x20 && dataLen != 0){
			return false;
		}
		
		return true;
	}
	
//	public byte[] getUUID(){
//		return 
//	}

}
