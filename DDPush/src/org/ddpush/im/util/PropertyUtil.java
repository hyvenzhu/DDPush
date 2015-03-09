package org.ddpush.im.util;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.ResourceBundle;

public class PropertyUtil {
	
	public static final String DEFAULTSET = "ddpush";
	
	protected static HashMap<String,Properties> propertiesSets = new HashMap<String, Properties>();

	private PropertyUtil() {
		
	}
	
	protected static void init() {
		init(DEFAULTSET);
	}
	
	protected static void init(String setName) {

		ResourceBundle rb = ResourceBundle.getBundle(setName);
		Properties properties = new Properties();
		Enumeration<String> eu = rb.getKeys();
		while(eu.hasMoreElements()){
			String key = eu.nextElement().trim();
			String value = rb.getString(key).trim();
			try{
				value = new String(value.getBytes("ISO8859-1"),"UTF-8");
			}catch(Exception e){
				e.printStackTrace();
			}
			properties.put(key.toUpperCase(), value);
		}
		
		propertiesSets.put(setName, properties);
		
	}
	
	public static String getProperty(String key){
		if(propertiesSets.get(DEFAULTSET) == null){
			init();
		}
		return propertiesSets.get(DEFAULTSET).getProperty(key.toUpperCase());
	}
	
	public static Integer getPropertyInt(String key){
		int value = 0;
		try{
			value = Integer.parseInt(getProperty(key));
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		return value;
	}
	
	public static Float getPropertyFloat(String key){
		float value = 0;
		try{
			value = Float.parseFloat(getProperty(key));
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		return value;
	}
	
	public static String getProperty(String setName, String key){
		if(propertiesSets.get(setName) == null){
			init(setName);
		}
		String value = propertiesSets.get(setName).getProperty(key.toUpperCase());
		if(value == null){
			return "";
		}
		return value;
	}

}
