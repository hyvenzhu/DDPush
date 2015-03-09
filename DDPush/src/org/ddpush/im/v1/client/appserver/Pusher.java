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
package org.ddpush.im.v1.client.appserver;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.ddpush.im.util.StringUtil;

// AppServer推送消息
public class Pusher {
	
	private int version = 1; 
	private int appId = 1;
	private int timeout;
	
	private String host;
	private int port ;
	private Socket socket;
	private InputStream in ;
	private OutputStream out ;
	
	public Pusher(String host, int port, int timeoutMillis, int version, int appId) throws Exception{
		this.setVersion(version);
		this.setAppId(appId);
		this.host = host;
		this.port = port;
		this.timeout = timeoutMillis;
		initSocket();
	}
	
	public Pusher(String host, int port, int timeoutMillis) throws Exception{
		this(host,port,timeoutMillis,1,1);
	}
	
	public Pusher(Socket socket)throws Exception{
		this.socket = socket;
		in = socket.getInputStream();
		out = socket.getOutputStream();
	}
	
	private void initSocket()throws Exception{
		socket = new Socket(this.host, this.port);
		socket.setSoTimeout(timeout);
		in = socket.getInputStream();
		out = socket.getOutputStream();
	}
	
	public void close() throws Exception{
		if(socket == null){
			return;
		}
		socket.close();
	}
	
	public void setVersion(int version) throws java.lang.IllegalArgumentException{
		if(version < 1 || version > 255){
			throw new java.lang.IllegalArgumentException("version must be 1 to 255");
		}
		this.version = version;
	}
	
	public int getVersion(){
		return this.version;
	}
	
	public void setAppId(int appId) throws IllegalArgumentException{
		if(appId < 1 || appId > 255){
			throw new java.lang.IllegalArgumentException("appId must be 1 to 255");
		}
		this.appId = appId;
	}
	
	public int getAppId(){
		return this.appId;
	}
	
	private boolean checkUuidArray(byte[] uuid) throws IllegalArgumentException{
		if(uuid == null || uuid.length != 16){
			throw new IllegalArgumentException("uuid byte array must be not null and length of 16");
		}
		return true;
	}
	
	private boolean checkLongArray(byte[] longArray) throws IllegalArgumentException{
		if(longArray == null || longArray.length != 8){
			throw new IllegalArgumentException("array must be not null and length of 8");
		}
		return true;
	}
	
	public boolean push0x10Message(byte[] uuid) throws Exception{
		checkUuidArray(uuid);
		out.write(version);
		out.write(appId);
		out.write(16);
		out.write(uuid);
		out.write(0);
		out.write(0);
		out.flush();
		
		byte[] b = new byte[1];
		in.read(b);
		if((int)b[0] == 0){
			return true;
		}else{
			return false;
		}
	}
	
	public boolean push0x11Message(byte[] uuid, long data) throws Exception{
		byte[] tmp = new byte[8];
		ByteBuffer.wrap(tmp).putLong(data);
		return this.push0x11Message(uuid, tmp);
	}
	
	public boolean push0x11Message(byte[] uuid, byte[] data) throws Exception{
		this.checkLongArray(data);
		out.write(version);
		out.write(appId);
		out.write(17);
		out.write(uuid);
		out.write(0);
		out.write(8);
		out.write(data);
		out.flush();
		
		byte[] b = new byte[1];
		in.read(b);
		if((int)b[0] == 0){
			return true;
		}else{
			return false;
		}
	}
	
	public boolean push0x20Message(byte[] uuid, byte[] data) throws Exception{
		this.checkUuidArray(uuid);
		if(data == null){
			throw new NullPointerException("data array is null");
		}
		if(data.length == 0 || data.length > 500){
			throw new IllegalArgumentException("data array length illegal, min 1, max 500");
		}
		byte[] dataLen = new byte[2];
		ByteBuffer.wrap(dataLen).putChar((char)data.length);
		out.write(version);
		out.write(appId);
		out.write(32);
		out.write(uuid);
		out.write(dataLen);
		
		out.write(data);
		out.flush();
		
		byte[] b = new byte[1];
		in.read(b);
		if((int)b[0] == 0){
			return true;
		}else{
			return false;
		}
		
	}
	
	public static void main(String[] args){
		Pusher pusher = null;
		try{
			boolean result;
			pusher = new Pusher("192.168.1.104",9999, 5000);
			result = pusher.push0x20Message(StringUtil.hexStringToByteArray("2cb1abca847b4491bc2b206b592b64fe"), "cmd=ntfurl|title=通知标题|content=通知内容|tt=提示标题|url=/m/admin/eml/inbox/list".getBytes("UTF-8"));
			//result = pusher.push0x10Message(StringUtil.hexStringToByteArray("2cb1abca847b4491bc2b206b592b64fd"));
			//result = pusher.push0x11Message(StringUtil.hexStringToByteArray("2cb1abca847b4491bc2b206b592b64fd"),128);
			
			System.out.println(result);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(pusher != null){
				try{pusher.close();}catch(Exception e){};
			}
		}
	}

}
