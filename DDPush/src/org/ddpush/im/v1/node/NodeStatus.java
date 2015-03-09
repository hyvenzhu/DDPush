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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.ddpush.im.util.StringUtil;
/**
 * 存储ClientStatMachine
 * @author hiphonezhu@gmail.com
 * @version [DDPush, 2015-3-8]
 */
public class NodeStatus {
	
	private static  NodeStatus global;
	private static ConcurrentHashMap<String, ClientStatMachine> nodeStat;
	
	private static final int file_min_bytes_per_object = 77;
	
	private NodeStatus(){
		nodeStat = new ConcurrentHashMap<String,ClientStatMachine>(10,0.75f,16);
	}
	
	public static NodeStatus getInstance(){
		if(global == null){
			synchronized(NodeStatus.class){
				if(global == null){//need to check again!!
					global = new NodeStatus();
					System.out.println("try load node stat file...");
					global.tryLoadFile();
				}
			}
		}
		return global;
	}
	
	private void tryLoadFile(){
		FileChannel fc = null;
		//byte[] entry = new byte[file_bytes_per_object];
		byte[] entry = new byte[1024];
		ByteBuffer bb = ByteBuffer.wrap(entry);
		int loaded = 0;
		File f = null;
		try{
			f = new File(System.getProperty("user.dir")+"/nodeStatus.dat");
			if(f.exists() == false){
				return;
			}
			fc = new FileInputStream(f).getChannel();
			
			while(true){
				bb.clear();
				bb.limit(file_min_bytes_per_object);
				while(bb.remaining() >0){
					if(fc.read(bb) < 0){
						break;
					}
				}
				if(bb.remaining() >0){
					break;
				}
				bb.flip();
				String key = new String(entry,0,32);
				for(int i = 0; i < 32; i++){
					bb.get();
				}
				long lastTick = bb.getLong();
				boolean hasMessage0x10 = (int)bb.get() == 1? true:false;
				long last0x10Time = bb.getLong();
				long message0x11 = bb.getLong();
				long last0x11Time = bb.getLong();
				
				int message0x20Len = bb.getInt();
				long last0x20Time = bb.getLong();
				byte[] data0x20 = null;
				if(bb.remaining() >0){
					break;
				}
				if(message0x20Len > 0){
					bb.clear();
					data0x20 = new byte[message0x20Len];
					bb.limit(message0x20Len);
					while(bb.remaining() >0){
						if(fc.read(bb) < 0){
							break;
						}
					}
					bb.flip();
					bb.get(data0x20);
				}
				
				ClientStatMachine csm = ClientStatMachine.newFromFile(lastTick, hasMessage0x10, last0x10Time, message0x11, last0x11Time, message0x20Len, last0x20Time, data0x20);
				nodeStat.put(key, csm);
				loaded++;
			}
			System.out.println(loaded+" stat machine loaded ");
		}catch(Exception e){
			;
		}finally{
			if(fc != null){
				try{
					fc.close();
					//f.delete();
				}catch(Exception e){
					;
				}
			}
		}
	}
	
	public ClientStatMachine getClientStat(String key){
		return nodeStat.get(key);
	}
	
	public void putClientStat(String key, ClientStatMachine value){
		nodeStat.put(key, value);
	}
	
	public ClientStatMachine getClientStat(byte[] key){
		return this.getClientStat(key, 0, key.length);
	}
	
	public ClientStatMachine getClientStat(byte[] key, int pos, int len){
		return nodeStat.get(StringUtil.convert(key, pos, len));
	}
	
	public void saveToFile() throws Exception{

		Set set = nodeStat.entrySet();
		FileChannel fc = null;
		Iterator iterator = set.iterator();
		int savedCount =0 ;
		try{
			File f = new File(System.getProperty("user.dir")+"/nodeStatus.dat");
			if(f.exists() == false){
				f.createNewFile();
			}
			fc = new FileOutputStream(f, false).getChannel();
			ByteBuffer bb = ByteBuffer.allocate(1024);
			while(iterator.hasNext()){
				Entry<String, ClientStatMachine> e = (Entry)iterator.next();
				String key = e.getKey();
				ClientStatMachine csm = e.getValue();
				bb.clear();
				bb.put(key.getBytes());
				bb.putLong(csm.getLastTick());
				
				bb.put((byte)(csm.has0x10Message()?1:0));
				bb.putLong(csm.getLast0x10Time());
				
				bb.putLong(csm.get0x11Message());
				bb.putLong(csm.getLast0x11Time());
				
				if(csm.getMessage0x20Len() >0 && csm.getMessage0x20() != null && csm.getMessage0x20Len() == csm.getMessage0x20().length){
					bb.putInt(csm.getMessage0x20Len());
					bb.putLong(csm.getLast0x20Time());
					bb.put(csm.getMessage0x20());
				}else{
					bb.putInt(0);
					bb.putLong(-1);
				}
				
				bb.flip();
				fc.write(bb);
				savedCount++;
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(fc != null){
				System.out.println("saved "+savedCount +" stat machines");
				try{
					fc.close();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}
	
	public int cleanStatus(int expiredHours) throws Exception{
		Set set = nodeStat.entrySet();
		Iterator iterator = set.iterator();
		int removed = 0;
		while(iterator.hasNext()){
			Entry<String, ClientStatMachine> e = (Entry)iterator.next();
			String key = e.getKey();
			ClientStatMachine csm = e.getValue();
			
			if(System.currentTimeMillis() - csm.getLastTick()> 1000*3600*expiredHours){
				nodeStat.remove(key);
				removed++;
			}
		}
		return removed;
	}
	
	public int size(){
		return nodeStat.size();
	}
}
