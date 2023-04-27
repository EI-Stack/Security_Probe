package oam.security.model.resource.gnb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Vector;

import oam.security.service.JSchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;
import oam.security.model.resource.util.CommandService;

@Service
@Slf4j
public class GnbService {
	
	@Autowired
	public JSchService jschService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	@Autowired
	private CommandService commandService;
	@Value("${solaris.session.oam_namespace}")
	private String oam_namespace;
	
	@Value("${solaris.gnb.mcc}")
	private String mcc;
	@Value("${solaris.gnb.mnc}")
	private String mnc;
	@Value("${solaris.gnb.slice.sst}")
	private int sst;
	@Value("${solaris.gnb.slice.sd}")
	private String sd;
	
	public void getGnbStatus() {
		log.info("GnbService getGnbStatus");
		log.info("mcc:" + mcc);
		log.info("sd:" + sd);
	}
	
	public JsonNode createAnGnb(Session s, JsonNode inf, String fileFolder,
			String AssignAmfIp, String assignGnbIp) throws JSchException {
		ObjectMapper o = new ObjectMapper();
		((ObjectNode)inf).put("mcc", mcc);
		((ObjectNode)inf).put("mnc", mnc);
		ObjectNode slice = o.createObjectNode();
		slice.put("sst", sst);
		slice.put("sd", sd);
		((ObjectNode)inf).set("slice", slice);
		
		String amfPodIp = "";
		if(AssignAmfIp.equals("")) {
			amfPodIp = findAmfPodIp(s);
			log.info("Auto Search Amf ip : " + amfPodIp);
		}else {
			amfPodIp = AssignAmfIp;
			log.info("Assign Amf ip : " + amfPodIp);
		}
		
		//Guess gnbip
		String gnbIP = "";
		if(assignGnbIp.equals("")) {
			gnbIP = guessGnbIP(s, null);
			log.info("Auto Search gnbIP ip : " + gnbIP);
		}else {
			gnbIP = assignGnbIp;
			log.info("Assign gnbIP ip : " + gnbIP);
		}
		System.out.println("gnbIP : " + gnbIP);
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		ArrayList<String> exampleContent = readFile(s, fileBasePath + "gnb_example.yaml");
		String gnbFileName = "gnb_" + timestamp + ".yaml";
		writeGnbFile(s, fileFolder, gnbFileName, timestamp, 
				amfPodIp, gnbIP, exampleContent, inf);
		
		log.info("Write yaml file OK, create yaml file and check IP");
		String nowgnbIP = "";
		if(assignGnbIp.equals("")) {
			nowgnbIP = checkGnbIP(s, fileFolder, gnbFileName, gnbIP);
		}else {
			nowgnbIP = execFileWithGnbIp(s, fileFolder, gnbFileName, gnbIP);
		}
		ObjectNode content = o.createObjectNode();
		content.put("gnbIp", nowgnbIP);
		content.put("timestamp", timestamp);
		return content;
	}
	
	public String checkGnbIP(Session session, String fileBasePath ,String gnbFileName, String guessIP) {
		//執行K8S
		log.info("GnbService=====Execute yaml file " + fileBasePath + gnbFileName);
		commandService.executeGnbYaml(session, fileBasePath, gnbFileName, false);
		log.info("GnbService=====K8S execute OK, Guess ip:" + guessIP);
		String lineData = "";
        String title = "";
        String ip = "";
        //其實就是取得timestamp
        String timestamp = gnbFileName.replace(".yaml", "").replace("gnb_", "");
        String command = " kubectl get pods -o wide ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'IP|" + timestamp+ "\'";
//        System.out.println("找POD的COMMAND : " + command);
        int ipPosition = 0;
        boolean checkPodStatus = false;
		try {
			Thread.sleep(10000);//給K8S時間建立
			while(!checkPodStatus) {//檢查pod的狀態 只要不是ContainerCreating 就OK
				checkPodStatus = checkPodStatus(session, timestamp);
				Thread.sleep(3000);//給K8S時間建立
			}
			ArrayList<String> result = commandService.execCommandAndGetResult(session, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("IP")) {
					ipPosition = i;
					break;
				}
			}
			System.out.println(result.get(0));
			System.out.println(result.get(1));
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			ip = lineData.split(",")[ipPosition];//找出IP
			log.info("GnbService=====Now Gnb pod ip is:" + ip);
			//檢測猜的IP有沒有準確
			if(ip.equals(guessIP)) {//猜中
				log.info("GnbService=====Got ip !! IP is:" + ip);
				return ip;
			}else {
				log.info("GnbService=====Miss ip, try it again");
				//刪除之前的configmap跟deployment
				delConfigAndDeploy(session, timestamp, fileBasePath, gnbFileName);
				Thread.sleep(3000);
				log.info("GnbService=====Delete configmap and deployments");
				//重新寫檔案 
				ArrayList<String> exampleContent = readFile(session, fileBasePath + gnbFileName);
				String gnewIP = guessGnbIP(session, ip);
				log.info("GnbService=====Guess again, guess ip:" + gnewIP);
				editGnbIPForYamlFile(session, fileBasePath + gnbFileName, 
									gnewIP, exampleContent);
				log.info("GnbService=====Rewrite file OK, sleep 30s");
				Thread.sleep(30000);
//				重新檢測
				ip = checkGnbIP(session, fileBasePath, gnbFileName, gnewIP);
			}
					
		}catch(Exception e) {
			e.printStackTrace();
		}
		return ip;
	}
	
	public String execFileWithGnbIp(Session session, String fileBasePath ,String gnbFileName, String assignGnbIp) {
		//執行K8S
		log.info("GnbService=====Execute yaml file " + fileBasePath + gnbFileName);
		commandService.executeGnbYaml(session, fileBasePath, gnbFileName, false);
		log.info("GnbService=====K8S execute OK, AssignGnb ip:" + assignGnbIp);
		String lineData = "";
        String title = "";
        String ip = "";
        //其實就是取得timestamp
        String timestamp = gnbFileName.replace(".yaml", "").replace("gnb_", "");
        String command = " kubectl get pods -o wide ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'IP|" + timestamp+ "\'";
//		        System.out.println("找POD的COMMAND : " + command);
        int ipPosition = 0;
        boolean checkPodStatus = false;
		try {
			Thread.sleep(10000);//給K8S時間建立
			while(!checkPodStatus) {//檢查pod的狀態 只要不是ContainerCreating 就OK
				checkPodStatus = checkPodStatus(session, timestamp);
				Thread.sleep(3000);//給K8S時間建立
			}
			ArrayList<String> result = commandService.execCommandAndGetResult(session, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("IP")) {
					ipPosition = i;
					break;
				}
			}
			System.out.println(result.get(0));
			System.out.println(result.get(1));
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			ip = lineData.split(",")[ipPosition];//找出IP 顯示出來供參考
			log.info("GnbService=====Now Gnb pod ip is:" + ip);
		}catch(Exception e) {
			log.info("execFileWithGnbIp error");
			e.printStackTrace();
		}
		return assignGnbIp;
	}
	
	public boolean checkPodStatus(Session s, String podNameKeyWord) {
		String lineData = "";
        String title = "";
        String podStatus = "";
        int statusPosition = 0;
        String command = " kubectl get pods -A | grep -E \'NAME|" + podNameKeyWord + "\'"; 
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("STATUS")) {
					statusPosition = i;
					break;
				}
			}
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			podStatus = lineData.split(",")[statusPosition];
			//只要不是CONTAINERCREATING 就算建立成功
			if(!podStatus.toUpperCase().equals("CONTAINERCREATING")) {
				return true;
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void delConfigAndDeploy(Session s, String timestamp, String filePath, String fileName) {
		String command = " kubectl delete configmap "  + getConfigMapName(s, timestamp);
		if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
		commandService.execCommandAndGetResult(s, command, false);
		command = " kubectl delete deployment "  + getDeployName(s, timestamp);
		if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
		commandService.execCommandAndGetResult(s, command, false);
//		command = "echo P@ssw0rd | sudo -S rm "  + filePath +fileName;
//		execCommandAndGetResult(s, command);
	}
	
	public void editGnbIPForYamlFile(Session s, String fileName, String guessIP, ArrayList<String> fileContent) {
		//僅針對gnbIP進行修改
		fileContent.add(12, fileContent.get(12).split(":")[0] + ": " + guessIP);
		fileContent.remove(13);
		fileContent.add(13, fileContent.get(13).split(":")[0] + ": " + guessIP);
		fileContent.remove(14);
		fileContent.add(14, fileContent.get(14).split(":")[0] + ": " + guessIP);
		fileContent.remove(15);
		
		ChannelSftp sftp = null;
		OutputStream stream = null;        
		try {
			sftp = (ChannelSftp) s.openChannel("sftp");
			sftp.connect();//記得要加connect
			stream = sftp.put(fileName, ChannelSftp.OVERWRITE);
			BufferedWriter bw = new BufferedWriter(new PrintWriter(stream));
		    for(int i = 0; i < fileContent.size(); i++) {
		    	bw.write(fileContent.get(i) + "\n");
		    }
		    bw.close();
		    stream.close();
		    sftp.disconnect();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public String findAmfPodIp(Session s) {
        String lineData = "";
        String title = "";
        String ip = "";
        int ipPosition = 0;
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, " kubectl get pods -A -o wide | grep -E \'IP|amf\'", false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("IP")) {
					ipPosition = i;
					break;
				}
			}
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			ip = lineData.split(",")[ipPosition];
		    log.info("AMF IP is:" + ip);
		}catch(Exception e) {
			e.printStackTrace();
		}
		return ip;
	}
	
	public String guessGnbIP(Session s, String lastIp) {
		String lineData = "";
        String title = "";
        String ip = "";
        int ipPosition = 0;
        String guess_ip = "";
        if(lastIp != null) {
        	log.info("GnbService=====LastIp:" + lastIp);
        	String []lastIPArr = lastIp.split("\\.");
        	int gue = Integer.parseInt(lastIPArr[3]) + 1;
        	if(gue == 255) {
        		gue = 1;
        	}
        	guess_ip = lastIPArr[0] + "." + lastIPArr[1] + "." +
        			   lastIPArr[2] + "." + String.valueOf(gue);
        	return guess_ip;
        }
        try {
        	String command = " kubectl get pods  -o wide";
        	if(!oam_namespace.trim().equals("")) {
            	command += " -n " + oam_namespace;
            }
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("IP")) {
					ipPosition = i;
					break;
				}
			}
			ArrayList<String> allIP = new ArrayList<>();
			int bigIP = 0;
			
			for(int i = 1; i < result.size(); i++) {
				lineData = result.get(i);
				lineData = lineData.replaceAll("\\s+", ",");
				ip = lineData.split(",")[ipPosition];
				allIP.add(ip);
				String [] ipArray = ip.split("\\.");//不能單用一個點 會出錯
				int ip_last = Integer.parseInt(ipArray[3]);
				if(ip_last > bigIP) {
					bigIP = ip_last;
					if(bigIP == 255) {
						guess_ip = ip.substring(0, ip.lastIndexOf(".")) + "." +String.valueOf(1);
						break;
					}else {
						guess_ip = ip.substring(0, ip.lastIndexOf(".")) + "." +String.valueOf(bigIP + 1);
					}
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return guess_ip;
	}
	
	public String getConfigMapName(Session s, String timestamp) {
		String lineData = "";
        String title = "";
        String configname = "";
        int namePosition = 0;
        String command = " kubectl get configmap ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'NAME|" + timestamp + "\'";
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().equals("NAME")) {
					namePosition = i;
					break;
				}
			}
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			configname = lineData.split(",")[namePosition];
		}catch(Exception e) {
			e.printStackTrace();
		}
		return configname;
	}
	
	public String getDeployName(Session s, String timestamp) {
		String lineData = "";
        String title = "";
        String deployname = "";
        int namePosition = 0;
        String command = " kubectl get deployment ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'NAME|" + timestamp + "\'";
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().equals("NAME")) {
					namePosition = i;
					break;
				}
			}
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			deployname = lineData.split(",")[namePosition];
		}catch(Exception e) {
			e.printStackTrace();
		}
		return deployname;
	}
	
	public String getPodName(Session s, String timestamp) {
		String lineData = "";
        String title = "";
        String podname = "";
        int namePosition = 0;
        String command = " kubectl get pods ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'NAME|" + timestamp + "\'";
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().equals("NAME")) {
					namePosition = i;
					break;
				}
			}
			lineData = result.get(1);
			lineData = lineData.replaceAll("\\s+", ",");
			podname = lineData.split(",")[namePosition];
		}catch(Exception e) {
			e.printStackTrace();
		}
		return podname;
	}
	
	public void writeGnbFile(Session session, 
			String filePath, String fileName,
			String timestamp, 
			String amfPodIp, String guessGnbIp,
			ArrayList<String> exampleData, JsonNode inf) {
		log.info("gnb inf:" + inf);
		//configmap 名稱
		exampleData.add(3, exampleData.get(3) + "-" + timestamp);
		exampleData.remove(4);
		exampleData.add(57, exampleData.get(57) + "-" + timestamp);
		exampleData.remove(58);
		//deployment 名稱
		exampleData.add(29, exampleData.get(29) + "-" + timestamp);
		exampleData.remove(30);
		//app連結
		exampleData.add(32, exampleData.get(32) + "-" + timestamp);
		exampleData.remove(33);
		exampleData.add(37, exampleData.get(37) + "-" + timestamp);
		exampleData.remove(38);
		exampleData.add(41, exampleData.get(41) + "-" + timestamp);
		exampleData.remove(42);
		exampleData.add(44, exampleData.get(44) + "-" + timestamp);
		exampleData.remove(45);
		//volumeMounts name
		exampleData.add(51, exampleData.get(51) + "-" + timestamp);
		exampleData.remove(52);
		exampleData.add(55, exampleData.get(55) + "-" + timestamp);
		exampleData.remove(56);
		//gnbIP
		exampleData.add(12, exampleData.get(12).split(":")[0] + ": " + guessGnbIp);
		exampleData.remove(13);
		exampleData.add(13, exampleData.get(13).split(":")[0] + ": " + guessGnbIp);
		exampleData.remove(14);
		exampleData.add(14, exampleData.get(14).split(":")[0] + ": " + guessGnbIp);
		exampleData.remove(15);
		//amfIp
		exampleData.add(17, exampleData.get(17).split(":")[0] + ": " +amfPodIp);
		exampleData.remove(18);
		//mcc
		exampleData.add(7, exampleData.get(7).split(":")[0] + ": "  + "\'" + inf.get("mcc").asText()+"\'");
		exampleData.remove(8);
		//mnc
		exampleData.add(8, exampleData.get(8).split(":")[0]  + ": " + "\'" + inf.get("mnc").asText()+"\'");
		exampleData.remove(9);
		//slice
		exampleData.add(21, exampleData.get(21).split(":")[0] + ": " + "0x" + String.format("%02d", inf.get("slice").get("sst").asInt()));
		exampleData.remove(22);
		exampleData.add(22, exampleData.get(22).split(":")[0] + ": " + "0x" + inf.get("slice").get("sd").asText());
		exampleData.remove(23);
		
		//command
		exampleData.add(49, exampleData.get(49).split(":")[0] + ": " + "[\" ./build/nr-gnb -c ./config/free5gc-gnb.yaml >> gnb_log.txt" + " \"]");
		exampleData.remove(50);
		
		commandService.generateYamlFile(session, exampleData, filePath, fileName);
	}
	
	public ArrayList<String> readFile(Session session, String filePath){
		ChannelSftp sftp = null;
		InputStream stream = null;
		ArrayList<String> content = new ArrayList<>();
		log.info("Read gnb example file:" + filePath);
		try {
			sftp = (ChannelSftp) session.openChannel("sftp");
			sftp.connect();//記得要加connect
			stream = sftp.get(filePath);
		    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
		    String line;
		    while ((line = br.readLine()) != null) {
//		        System.out.println(line);
		        content.add(line);
		    }
		    stream.close();
		    Thread.sleep(1000);
		    sftp.disconnect();
		}catch(Exception e) {
			e.printStackTrace();
		}
		return content;
	}	
	
	public String testLs() {
		try {
			JSch jsch = new JSch();
			jsch.addIdentity("C:\\id_rsa\\id_rsa_214.ppk");
			Session session = jsch.getSession("ubuntu", "60.251.156.214", 22);
			System.out.println("Session created");
			java.util.Properties config = new java.util.Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();
			System.out.println("Session connected....");
			///////////////////
			
			ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
			channelSftp.connect();
			channelSftp.cd("/");
			Vector<?> list = channelSftp.ls("*");
			list.forEach(str->{
				System.out.println(str.toString());
			});
			channelSftp.disconnect();
	        //////////////////
			
			session.disconnect();
			System.out.println("Session disconnected....");
			if(!session.isConnected()) {
				System.out.println("Session is closed");
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	
	public boolean checkGnbLogIsOk(Session s, String timestamp) {
		String podName = getPodName(s, timestamp);
		log.info("Check gnb log");
		String commands = "kubectl exec -ti " + podName + " bash -- cat gnb_log.txt";
		ArrayList<String> content = commandService.execCommandAndGetResult(s, commands, true);
		for(int i = 0; i < content.size(); i++) {
			log.info("Gnb Logs:" + content.get(i));
			if(content.get(i).contains("Cannot assign requested address")) {
				return false;
			}
		}
		return true;
	}

}
