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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.ddpush.im.util.PropertyUtil;


public class IMServerConsole implements Runnable {
	
	public static final String CMD_SHUTDOWN = "shutdown";
	public static final String CMD_STOP = "stop";
	public static final String CMD_STATUS = "status";
	public static final String CMD_GC = "gc";
	
	private int port = PropertyUtil.getPropertyInt("CONSOLE_TCP_PORT");
	
	private ServerSocket serverSocket = null;

	@Override
	public void run() {
		
		try {
            serverSocket =
                new ServerSocket(port, 1,
                                 InetAddress.getByName("0.0.0.0"));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("console listening port:"+port);
        while(true){
        	try{
        		processCommand();
        	}catch(Exception e){
        		e.printStackTrace();
        	}catch(Throwable t){
        		t.printStackTrace();
        	}
        }

	}
	
	public void processCommand() throws Exception{
		Socket socket = null;
    	socket = serverSocket.accept();
        socket.setSoTimeout(10 * 1000);  // 10 seconds
        InputStream ins;
        OutputStream ops;
        
        ins = socket.getInputStream();
        ops = socket.getOutputStream();

        StringBuffer command = new StringBuffer();
        int maxLen = 1024; //max length
        
        while (maxLen > 0) {
            int ch = -1;
            try {
                ch = ins.read();
            } catch (IOException e) {
                e.printStackTrace();
                ch = -1;
            }
            if (ch < 32)  // Control character or EOF terminates loop
                break;
            command.append((char) ch);
            maxLen--;
        }
        String cmd = command.toString().trim();
        
        if(cmd.equals(CMD_SHUTDOWN)){
        	onShutdown(ops);
        }else if(cmd.equalsIgnoreCase(CMD_STOP)){
        	onStop(ops);
        }else if(cmd.equalsIgnoreCase(CMD_STATUS)){
        	onStatus(ops);
        }else if(cmd.equalsIgnoreCase(CMD_GC)){
        	onGC(ops);
        }else if(cmd.startsWith("uuid-")){
        	onUuid(cmd, ops);
        }else if(cmd.startsWith("clean")){
        	onClean(cmd, ops);
        }else{
        	
        }
        
        try {
            socket.close();
        } catch (IOException e) {
            ;
        }
	}
	
	private void onShutdown(OutputStream response)throws Exception{
		IMServer.server.stop();
		response.write("shutdown cmd committed".getBytes());
		response.flush();
	}
	
	private void onStop(OutputStream response)throws Exception{
		IMServer.server.stop();
		response.write("shutdown cmd committed".getBytes());
		response.flush();
	}
	
	private void onStatus(OutputStream response)throws Exception{
		String status = IMServer.server.getStatusString();
		response.write(status.getBytes());
		response.flush();
	}
	
	private void onGC(OutputStream response)throws Exception{
		System.gc();
		response.write("gc committed".getBytes());
		response.flush();
	}
	
	private void onUuid(String cmd, OutputStream response)throws Exception{
		String uuid = null;
		try{
			uuid = cmd.substring(cmd.indexOf('-')+1).trim();
			if(uuid == null || uuid.length() != 32){
				throw new java.lang.IllegalArgumentException();
			}

		}catch(Exception e){
			response.write(("uuid format error: "+cmd).getBytes());
			response.flush();
			return;
		}

		String status = IMServer.server.getUuidStatString(uuid);
		if(status == null){
			status = "uuid "+uuid +" not exist";
		}
		response.write(status.getBytes());
		response.flush();
	}
	
	private void onClean(String cmd, OutputStream response)throws Exception{
		String hours = null;
		int expiredHours = 48;
		try{
			hours = cmd.substring(cmd.indexOf('-')+1).trim();
			expiredHours = Integer.parseInt(hours);
		}catch(Exception e){
			response.write(("clean cmd format error: "+cmd+", should be like: clean-48, which means to clean stat machines who's last active time is more than 48 hours before now").getBytes());
			response.flush();
			return;
		}

		IMServer.server.cleanExpiredMachines(expiredHours);

		response.write(("clean task of "+expiredHours+" exipred hours committed").getBytes());
		response.flush();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if(args == null || args.length == 0 || args[0]== null || args[0].trim().length() == 0){
			System.out.println("no cmd! cmd: stop | status | gc | uuid-hexuuid | clean-hours");
			return;
		}
		if(!"stop".equals(args[0]) && !"status".equals(args[0]) && !"gc".equals(args[0]) && !args[0].startsWith("uuid-") && !args[0].startsWith("clean-")){
			System.out.println("cmd: "+args[0]+" not found. should be: stop | status | gc | uuid-hexuuid | clean-hours");
			return;
		}
		String cmd = args[0].trim();
		Socket s = null;
		try{
			s = new Socket("localhost",9900);
			s.setSoTimeout(1000*10);
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			//DataInputStream din = new DataInputStream(in);
			//DataOutputStream dout = new DataOutputStream(out);
			
			out.write(cmd.getBytes());
			out.write(3);
			out.flush();
			//out.close();
			
			StringBuffer sb = new StringBuffer();
			int ch = -1;
			while(true){
				try{
					ch = in.read();
				}catch(Exception e){
					ch = -1;
				}
				if(ch < 0){
					break;
				}
				sb.append((char) ch);
			}
			System.out.println(sb.toString());
			
		}catch(java.net.ConnectException ce){
			System.out.println("can not connect to server, is server up?");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(s != null){
				try{s.close();}catch(Exception e){}
			}
		}

	}

}
