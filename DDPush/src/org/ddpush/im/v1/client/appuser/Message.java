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

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * 消息对象
 * 协议格式:[1字节版本号][1字节appid][1字节命令码][16字节UUID][2字节包内容长度][包内容]
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public final class Message {
	
	public static int version = 1;
	public static final int SERVER_MESSAGE_MIN_LENGTH = 5;
	public static final int CLIENT_MESSAGE_MIN_LENGTH = 21;
	public static final int CMD_0x00 = 0;//心跳包
	public static final int CMD_0x10 = 16;//通用信息
	public static final int CMD_0x11 = 17;//分类信息
	public static final int CMD_0x20 = 32;//自定义信息
	
	protected SocketAddress address;
	protected byte[] data;
	
	public Message(SocketAddress address, byte[] data){
		this.address = address;
		this.data = data;
	}
	
	/**
	 * @return 消息"内容"长度
	 */
	public int getContentLength(){
		return (int)ByteBuffer.wrap(data, SERVER_MESSAGE_MIN_LENGTH - 2, 2).getChar();
	}
	
	/**
	 * @return 命令码
	 */
	public int getCmd(){
		byte b = data[2];
		return b & 0xff; // 转为10进制
	}
	
	/**
	 * 检查消息的正确性
	 * @return true or false
	 */
	public boolean checkFormat(){
		if(address == null || data == null || data.length < Message.SERVER_MESSAGE_MIN_LENGTH){ // 服务器最短消息为通用消息, 长度为5
			return false;
		}
		int cmd = getCmd();
		if(cmd != CMD_0x00
				&& cmd != CMD_0x10
				&& cmd != CMD_0x11
				&& cmd != CMD_0x20){
			return false;
		}
		int dataLen = getContentLength();
		if(data.length != dataLen + SERVER_MESSAGE_MIN_LENGTH){ // 检测数据包的长度是否正确
			return false;
		}
		if(cmd ==  CMD_0x10 && dataLen != 0){ // 通用消息长度为0
			return false;
		}
		
		if(cmd ==  CMD_0x11 && dataLen != 8){ // 分类消息长度为8
			return false;
		}
		
		if(cmd ==  CMD_0x20 && dataLen < 1){//must has content
			return false;
		}
		return true;
	}
	
	public void setData(byte[] data){
		this.data = data;
	}
	
	public byte[] getData(){
		return this.data;
	}
	
	public void setSocketAddress(SocketAddress address){
		this.address = address;
	}
	
	public SocketAddress getSocketAddress(){
		return this.address;
	}
	
	public static void setVersion(int v){
		if(v < 1 || v > 255){
			return;
		}
		version = v;
	}
	
	public static int getVersion(){
		return version;
	}

}
