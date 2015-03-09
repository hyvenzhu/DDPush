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

import java.nio.ByteBuffer;

import org.ddpush.im.util.StringUtil;
import org.ddpush.im.v1.node.ClientStatMachine;
/**
 * AppServer推送的数据
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public final class PushMessage{
	
	protected byte[] data;
	
	public PushMessage(byte[] data) throws Exception{
		if(data == null){
			throw new NullPointerException("data array is null");
		}
		this.data = data;
		if(checkFormat() == false){
			throw new java.lang.IllegalArgumentException("data format error");
		}
	}
	
	public void setData(byte[] data){
		this.data = data;
	}
	
	public byte[] getData(){
		return this.data;
	}
	
	public int getVersionNum(){
		byte b = data[0];
		return b & 0xff;
	}
	
	public int getCmd(){
		byte b = data[2];
		return b & 0xff;
	}
	
	public int getContentLength(){
		return (int)ByteBuffer.wrap(data, 19, 2).getChar();
	}
	
	public String getUuidHexString(){
		return StringUtil.convert(data, 3, 16);
	}
	
	public boolean checkFormat(){
		if(data.length < Constant.CLIENT_MESSAGE_MIN_LENGTH){
			return false;
		}
		if(getVersionNum() != Constant.VERSION_NUM){
			return false;
		}

		int cmd = getCmd();
		if(cmd != ClientStatMachine.CMD_0x10
				&& cmd != ClientStatMachine.CMD_0x11
				&& cmd != ClientStatMachine.CMD_0x20){
			return false;
		}
		int dataLen = getContentLength();
		if(data.length != dataLen + Constant.CLIENT_MESSAGE_MIN_LENGTH){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x10 && dataLen != 0){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x11 && dataLen != 8){
			return false;
		}
		
		if(cmd ==  ClientStatMachine.CMD_0x20 && dataLen < 1){//must has content
			return false;
		}
		
		return true;
	}

}
