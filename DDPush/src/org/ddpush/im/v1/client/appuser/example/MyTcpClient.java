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
package org.ddpush.im.v1.client.appuser.example;

import java.util.ArrayList;

import org.ddpush.im.util.StringUtil;
import org.ddpush.im.v1.client.appuser.Message;
import org.ddpush.im.v1.client.appuser.TCPClientBase;


public class MyTcpClient extends TCPClientBase {

	public MyTcpClient(byte[] uuid, int appid, String serverAddr,
			int serverPort, int connectTimeout) throws Exception {
		super(uuid, appid, serverAddr, serverPort, connectTimeout);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean hasNetworkConnection() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void onPushMessage(Message msg) {
		if(msg == null){
			System.out.println("msg is null");
		}
		if(msg.getData() == null || msg.getData().length == 0){
			System.out.println("msg has no data");
		}
		System.out.println(StringUtil.convert(this.uuid)+"---"+StringUtil.convert(msg.getData()));
	}

	@Override
	public void trySystemSleep() {
		//System.out.println("try sleep");

	}
	
	public static void main(String[] args){
		try{
			ArrayList list = new ArrayList();
			for(int i = 0; i < 1; i++){
				byte[] uuid = StringUtil.md5Byte(""+i);
				//System.out.println("uuid is: "+StringUtil.convert(uuid));
				MyTcpClient myTcpClient = new MyTcpClient(uuid, 1, "192.168.2.111", 9966, 5);
				myTcpClient.setHeartbeatInterval(50);
				myTcpClient.start();
				//Thread.sleep(1000);
				//System.out.println(myTcpClient.channel.socket().getLocalAddress().toString());
				//System.out.println("client started...");
//				synchronized(myTcpClient){
//					myTcpClient.wait();
//				}
				list.add(myTcpClient);
				
			}
			synchronized(list){
				list.wait();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
