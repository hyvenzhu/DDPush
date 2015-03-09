package org.ddpush.im.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;



public class DateTimeUtil {
	
	public static String DEFAULTFORMAT = "yyyy-MM-dd HH:mm:ss";
	
	public static String getCurDateTime() {
		return getCurDateTime(DEFAULTFORMAT);
	} 
    
	/**
	 * 当前时间
	 * @param pattern
	 * @return
	 */
	public static String getCurDateTime(String pattern) {
		return formatCalendar(Calendar.getInstance(),pattern);
	}
	
	public static String formatCalendar(Calendar calendar){
		return formatCalendar(calendar,DEFAULTFORMAT);
	}
	
	public static String formatCalendar(Calendar calendar,String pattern){
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		//sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
		return sdf.format(calendar.getTime());
	}
	
	public static Date parseDate(String date) throws ParseException{
		if("".equals(StringUtil.checkBlankString(date)))
		return null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		return  sdf.parse(date);
	}
	
	public static Date parseDate(String date, String pattern) throws ParseException{
		if("".equals(StringUtil.checkBlankString(date)))
		return null;
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		return  sdf.parse(date);
	}
	
	public static String formatDate(Date date, String pattern){
		if(date==null)
		return "";
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return formatCalendar(calendar,pattern);
	}
	
	public static String formatDate(Date date){
		return formatDate(date,DEFAULTFORMAT);
	}
	
	public static Calendar parseString(String dateStr, String pattern) throws ParseException{
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		Date date = sdf.parse(dateStr);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar;
	}
	public static String convertDateTimeStrFormat(String dateStr, String pattern, String newPattern) throws ParseException{
		return DateTimeUtil.formatCalendar(DateTimeUtil.parseString(dateStr,pattern),newPattern);
	}
	
	public static Calendar parseString(String dateStr)throws ParseException{
		return parseString(dateStr,DEFAULTFORMAT);
	}
	
	 
}
