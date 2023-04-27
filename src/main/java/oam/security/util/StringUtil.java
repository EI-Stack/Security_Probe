package oam.security.util;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
	
	public static int countAppear(String source, String findText) {
		int count = 0;
		Pattern p = Pattern.compile(findText);
		Matcher m = p.matcher(source);
		while(m.find()) {
			count++;
		}
		return count;
	}
	
	public static String findPodName(ArrayList<String> allPodsInf, String ... keyword) {
		int namePosition = 0;
		for(int i = 0; i < allPodsInf.size(); i++) {
			String title = allPodsInf.get(0);
			if(i == 0) {//拿title找 name在哪裡
				String[] titleCol = title.split(",");
				for(int a = 0; a < titleCol.length; a++) {
					if(titleCol[a].toUpperCase().equals("NAME")) {
						namePosition = a;
						break;
					}
				}
			}else {
				String podName = allPodsInf.get(i).split(",")[namePosition];
				System.out.println("podName:" + podName);
				int countmatch = 0;
				for(int a = 0; a < keyword.length; a++) {
					if(podName.contains(keyword[a])) {
						countmatch++;
//						System.out.println("podName:" + podName + " has " + countmatch);
					}else {
						continue;//有一個keyword不包含 就跳出不用比下面的keyword
					}
					if(countmatch == keyword.length) {
						System.out.println("GOT!!!!!!!!!!!!!");
						return podName;
					}
				}
			}
		}
		return "";
	}
	
	public static String findPodIp(ArrayList<String> allPodsInf, String ... keyword) {
		int namePosition = 0;
		int ipPosition = 0;
		for(int i = 0; i < allPodsInf.size(); i++) {
			String title = allPodsInf.get(0);
			if(i == 0) {//拿title找 name在哪裡
				String[] titleCol = title.split(",");
				for(int a = 0; a < titleCol.length; a++) {
					if(titleCol[a].toUpperCase().equals("NAME")) {
						namePosition = a;
					}else if(titleCol[a].toUpperCase().equals("IP")){
						ipPosition = a;
					}
				}
			}else {
				String podName = allPodsInf.get(i).split(",")[namePosition];
				String ip = allPodsInf.get(i).split(",")[ipPosition];
				System.out.println("podName:" + podName + " ip:" + ip);
				int countmatch = 0;
				for(int a = 0; a < keyword.length; a++) {
					if(podName.contains(keyword[a])) {
						countmatch++;
//						System.out.println("podName:" + podName + " has " + countmatch);
					}else {
						continue;//有一個keyword不包含 就跳出不用比下面的keyword
					}
					if(countmatch == keyword.length) {
						System.out.println("GOT!!!!!!!!!!!!!");
						return ip;
					}
				}
			}
		}
		return "";
	}
	
	public static boolean stringHasKeyWord(String source, String ... keyword) {
		for(int i = 0; i < keyword.length; i++) {
			if(!source.contains(keyword[i])) {
				return false;
			}
		}
		System.out.println("source:" + source);
		return true;
	}
	
	public static void showStringList(ArrayList<String> list) {
		for(int i = 0; i < list.size(); i++) {
			System.out.println(list.get(i));
		}
	}
	
	public static String stringListToString(ArrayList<String> list) {
		String all = "";
		for(int i = 0; i < list.size(); i++) {
			all += list.get(i) + "\n";
		}
		return all;
	}

}
