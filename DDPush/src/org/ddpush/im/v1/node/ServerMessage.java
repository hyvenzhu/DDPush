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
/**
 * DDPush推送的消息
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public final class ServerMessage{
	
	protected SocketAddress address;
	protected byte[] data;
	
	public ServerMessage(SocketAddress address, byte[] data) throws Exception{
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

}
