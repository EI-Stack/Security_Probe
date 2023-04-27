package oam.security.model.resource.ue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import oam.security.exception.base.ExceptionBase;
import oam.security.service.JSchService;
import oam.security.service.NetworkServiceBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;
import oam.security.model.resource.util.CommandService;

@Service
@Slf4j
public class UeService {
	
	
	@Value("${solaris.server.free5gc.http.url}")
	private String url;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	@Value("${solaris.session.oam_namespace}")
	private String oam_namespace;
	@Value("${solaris.free5gc-version}")
	private String free5gcVersion;
	@Autowired
	public NetworkServiceBase networkService;
	@Autowired
	public JSchService jschService;
	@Autowired
	private CommandService commandService;
	
	@Value("${solaris.ue.mcc}")
	private String mcc;
	@Value("${solaris.ue.mnc}")
	private String mnc;
	@Value("${solaris.ue.key}")
	private String key;
	@Value("${solaris.ue.opCode}")
	private String opCode;
	@Value("${solaris.ue.opType}")
	private String opType;
	@Value("${solaris.ue.amf}")
	private String amf;
	@Value("${solaris.ue.slice.sst}")
	private int sst;
	@Value("${solaris.ue.slice.sd}")
	private String sd;
	
	public void getUeStatus() {
		log.info("UeService getUeStatus");
	}
	
	public void registerSubscriber(JsonNode ueContent) throws ExceptionBase {
		log.info("UeService=====registerSubscriber");
		String imsi = ueContent.get("ueId").asText();
		String plmnID = ueContent.get("plmnID").asText();
		String url = "";
		if(free5gcVersion.equals("Community")) {
			url = this.url + "/api/subscriber/"+ imsi + "/" + plmnID;
		}else if(free5gcVersion.equals("Business")){
			url = this.url + "/ncms-oam/v1/subscriber/"+ imsi + "/" + plmnID;
		}
		log.info("UeService=====Web registerSubscriber " + imsi);
		networkService.postJsonNode(url, ueContent);
	}
	
	public void deleteSubscriber(String imsi) throws ExceptionBase {
		log.info("UeService=====deleteSubscribe " + imsi);
		String plmnID = imsi.substring(5, 10);
		String url = "";
		if(free5gcVersion.equals("Community")) {
			url = this.url + "/api/subscriber/"+ imsi + "/" + plmnID;
		}else if(free5gcVersion.equals("Business")){
			url = this.url + "/ncms-oam/v1/subscriber/"+ imsi + "/" + plmnID;
		}
		log.info("UeService=====Web registerSubscriber " + imsi);
		networkService.delete(url);
	}
	
	public boolean createUe(Session s, JsonNode inf, String fileFolder) {		
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        ArrayList<String> exampleContent = commandService.readFile(s, fileBasePath + "ue_example.yaml"); 
        log.info("UeService=====UeContext supi:" + inf.get("supi").asText() +
        		", gnbIp:" + inf.get("gnbIp").asText());
        String ueFileName = "ue_" + timestamp + ".yaml";

		writeUeYamlFile(s, inf, exampleContent, timestamp, fileFolder, ueFileName);
		commandService.executeUeYaml(s, fileFolder, ueFileName, false);
		return true;
	}
	
	public String createMultipleUeWithOnePod(Session s, JsonNode inf, String fileFolder, int numOfUe) {
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        ArrayList<String> exampleContent = commandService.readFile(s, fileBasePath + "ue_example.yaml"); 
        log.info("UeService=====UeContext supi:" + inf.get("supi").asText() +
        		", gnbIp:" + inf.get("gnbIp").asText());
        String ueFileName = "ue_" + timestamp + ".yaml";

        ObjectMapper o = new ObjectMapper();
		((ObjectNode)inf).put("mcc", mcc);
		((ObjectNode)inf).put("mnc", mnc);
		((ObjectNode)inf).put("key", key);
		((ObjectNode)inf).put("opCode", opCode);
		((ObjectNode)inf).put("opType", opType);
		((ObjectNode)inf).put("amf", amf);
		ObjectNode slice = o.createObjectNode();
		slice.put("sst", sst);
		slice.put("sd", sd);
		((ObjectNode)inf).set("slice", slice);
		
        writeMultipleUeYamlFile(s, inf, exampleContent, timestamp, fileFolder, ueFileName, numOfUe);
		commandService.executeUeYaml(s, fileFolder, ueFileName, false);
		return timestamp;
	}
	
	
	public String findSuitableGnb(Session s, ArrayList<String> gnbIpList) {
		if(gnbIpList.size() > 0) {
			//取亂數
			int i = (int)(Math.random() * gnbIpList.size());
			log.info("UeService=====Gnb ip: gnbIpList.get(" + i + ") " + gnbIpList.get(i));
			return gnbIpList.get(i);
		}else {
			log.info("UeService=====Gnb list is empty, find suitable gnb pod");
			String ip = findRandomGnbPodIp(s); 
			return ip;
		}
	}
	
	public String findRandomGnbPodIp(Session s) {
        String lineData = "";
        String title = "";
        String ip = "";
        int ipPosition = 0;
        String command = " kubectl get pods  -o wide ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'IP|gnb\'";
		try {
			ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
			title = result.get(0);
			String[] titleCol = title.split(",");
			for(int i = 0; i < titleCol.length; i++) {
				if(titleCol[i].toUpperCase().contains("IP")) {
					ipPosition = i;
					break;
				}
			}
			int i = (int)(Math.random() * result.size()-1) + 1;
			lineData = result.get(i);  //亂數找一個gnb
			lineData = lineData.replaceAll("\\s+", ",");
			ip = lineData.split(",")[ipPosition];
		}catch(Exception e) {
			e.printStackTrace();
		}
		log.info("UeService=====find suitable IP:" + ip);
		return ip;
	}
	
	
	
	public long getSubscriprionMaxImsi() throws ExceptionBase {
		String url = "";
		if(free5gcVersion.equals("Community")) {
			url = this.url + "/api/subscriber";
		}else if(free5gcVersion.equals("Business")) {
			url = this.url + "/ncms-oam/v1/subscriber/" ;
		}
		JsonNode subscribers = networkService.getJsonInformation(url);
		long max = 0L;
		if(subscribers.isArray()) {
			for(JsonNode subscriber : subscribers) {
				String ueId = subscriber.get("ueId").asText();
				long number = Long.parseLong(ueId.split("imsi-")[1]);
				if(number > max) {
					max = number;
				}
			}
		}
		if(max == 0L) {
			max = 208930000000003L;
		}
		log.info("UeService=====Web max subscriber is:" + max);
		return max;
	}
	
	public void removeFromWebsite(String imsi) throws ExceptionBase {
		String url = "";
		String plmnID = imsi.substring(5, 10);
		if(free5gcVersion.equals("Community")) {
			url = this.url + "/api/subscriber/" + imsi + "/" + plmnID;
		}else if(free5gcVersion.equals("Business")) {
			url = this.url + "/ncms-oam/v1/subscriber/" + imsi + "/" + plmnID;
		}
		networkService.delete(url);
	}
	
	
	public void deregisterFromCoreByPodName(Session s, String podName) throws Exception {
		log.info("DeregisterFromCoreByPodName:" + podName);
		String findUeCommand = " kubectl exec -ti " + podName; 
		if(!oam_namespace.trim().equals("")) {
			findUeCommand += " -n " + oam_namespace;
		}
		findUeCommand = findUeCommand + "  -- ./build/nr-cli -d ";
		ArrayList<String> equip_list = commandService.execCommandAndGetResult(s, findUeCommand, false);
		for(int i = 0; i < equip_list.size(); i++) {
			String deregisterCommands = "kubectl exec -ti " + podName
			+ " -- ./build/nr-cli " + equip_list.get(i) 
			+ " --exec \"deregister remove-sim\"";
			commandService.execCommandAndGetResult(s, deregisterCommands, false);
			deleteSubscriber(equip_list.get(i));
			log.info("Deregister imsi:" + equip_list.get(i));
		}
//		commandService.execShellCommand_deregister(s, "ls -l ", "echo P@ssw0rd | sudo -S kubectl get pods ");
	}
	
	public String findUeransimUePodName(Session s, String imsi) {
		String command = " kubectl get pods ";
		if(!oam_namespace.trim().equals("")) {
			command += " -n " + oam_namespace;
		}
		ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
		int namePosition = 0;
		for(int i = 0; i < result.size(); i++) {
			String title = result.get(0);
			if(i == 0) {//拿title找 name在哪裡
				String[] titleCol = title.split(",");
				for(int a = 0; a < titleCol.length; a++) {
					if(titleCol[a].toUpperCase().equals("NAME")) {
						namePosition = a;
						break;
					}
				}
			}else {
				String podName = result.get(i).split(",")[namePosition];
				if(podName.contains("ueransim")) { //找這個pod裡面的UE&gnb
					String findUeCommand = " kubectl exec -ti " + podName; 
					if(!oam_namespace.trim().equals("")) {
						findUeCommand += " -n " + oam_namespace;
					}
					findUeCommand = findUeCommand + "  -- ./build/nr-cli -d ";
					ArrayList<String> equip_list = commandService.execCommandAndGetResult(s, findUeCommand, false);
					if(equip_list.contains(imsi)) {
						return podName;
					}
				}
			}
		}
		return "";
	}
	
	public void changeNrBinderPermission(Session s, String targetPodName) {
		String commands = "kubectl exec -ti " + targetPodName + " -- " + " chmod 777 ./build/nr-binder";
		commandService.execCommandAndGetResult(s, commands, false);
	}
	
	public void writeUeYamlFile(Session s, JsonNode inf, ArrayList<String> exampleContent,
			String timestamp, String filePath, String fileName) {
		//ConfigMap名稱
		exampleContent.add(3, exampleContent.get(3) + "-" + timestamp);
		exampleContent.remove(4);
		exampleContent.add(106, exampleContent.get(106) + "-" + timestamp);
		exampleContent.remove(107);
		//deploy metadata name
		exampleContent.add(73, exampleContent.get(73) + "-" + timestamp);
		exampleContent.remove(74);
		
		//app
		exampleContent.add(76, exampleContent.get(76) + "-" + timestamp);
		exampleContent.remove(77);
		exampleContent.add(81, exampleContent.get(81) + "-" + timestamp);
		exampleContent.remove(82);
		exampleContent.add(85, exampleContent.get(85) + "-" + timestamp);
		exampleContent.remove(86);
		exampleContent.add(88, exampleContent.get(88) + "-" + timestamp);
		exampleContent.remove(89);
		
		//volumeMounts name
		exampleContent.add(96, exampleContent.get(96) + "-" + timestamp);
		exampleContent.remove(97);
		exampleContent.add(101, exampleContent.get(101) + "-" + timestamp);
		exampleContent.remove(102);
		
		exampleContent.add(97, exampleContent.get(97) + "-" + timestamp);
		exampleContent.remove(98);
		exampleContent.add(104, exampleContent.get(104) + "-" + timestamp);
		exampleContent.remove(105);
		
		//supi
		exampleContent.add(8, exampleContent.get(8).split(":")[0] + ": "  + "\'" + inf.get("supi").asText()+"\'");
		exampleContent.remove(9);
		//mcc
		exampleContent.add(10, exampleContent.get(10).split(":")[0] + ": "  + "\'" + inf.get("mcc").asText() + "\'");
		exampleContent.remove(11);
		//mnc
		exampleContent.add(12, exampleContent.get(12).split(":")[0]  + ": " + "\'" + inf.get("mnc").asText()  + "\'");
		exampleContent.remove(13);
		//key
		exampleContent.add(14, exampleContent.get(14).split(":")[0]  + ": " + "\'" + inf.get("key").asText() +"\'");
		exampleContent.remove(15);
		
		//op code
		exampleContent.add(16, exampleContent.get(16).split(":")[0]  + ": " + "\'" + inf.get("opCode").asText() +"\'");
		exampleContent.remove(17);
		
		//op type
		exampleContent.add(18, exampleContent.get(18).split(":")[0]  + ": " + "\'" + inf.get("opType").asText() +"\'");
		exampleContent.remove(19);
		
		//amf
		exampleContent.add(20, exampleContent.get(20).split(":")[0]  + ": " + "\'" + inf.get("amf").asText() +"\'");
		exampleContent.remove(21);
		
		//gnbIp
		exampleContent.add(27, exampleContent.get(27).split("-")[0]  + "- " + inf.get("gnbIp").asText());
		exampleContent.remove(28);
		
		//slice
		exampleContent.add(45, exampleContent.get(45).split(":")[0] + ": " + "0x" + String.format("%02d", inf.get("slice").get("sst").asInt()));
		exampleContent.remove(46);
		exampleContent.add(46, exampleContent.get(46).split(":")[0] + ": " + "0x" +String.valueOf(inf.get("slice").get("sd").asText()));
		exampleContent.remove(47);
		
		//slice
		exampleContent.add(49, exampleContent.get(49).split(":")[0] + ": " + "0x" + String.valueOf(inf.get("slice").get("sst").asInt()));
		exampleContent.remove(50);
		exampleContent.add(50, exampleContent.get(50).split(":")[0] + ": " + "0x" +String.valueOf(inf.get("slice").get("sd").asText()));
		exampleContent.remove(51);
		
		//command
//		exampleContent.add(93, exampleContent.get(93).split(":")[0] + ": " + "[\"sleep infinity && ./build/nr-ue -c ./config/free5gc-ue.yaml " +" \"]");
		exampleContent.add(93, exampleContent.get(93).split(":")[0] + ": " + "[\"./build/nr-ue -c ./config/free5gc-ue.yaml " +" \"]");
		exampleContent.remove(94);
		
		
		commandService.generateYamlFile(s, exampleContent, filePath, fileName);
		log.info("UeService=====Write ue yaml file OK");
	}
	
	public void writeMultipleUeYamlFile(Session s, JsonNode inf, ArrayList<String> exampleContent,
			String timestamp, String filePath, String fileName, int numOfUe) {
		log.info("ue inf:" + inf);
		//ConfigMap名稱
		exampleContent.add(3, exampleContent.get(3) + "-" + timestamp);
		exampleContent.remove(4);
		exampleContent.add(106, exampleContent.get(106) + "-" + timestamp);
		exampleContent.remove(107);
		//deploy metadata name
		exampleContent.add(73, exampleContent.get(73) + "-" + timestamp);
		exampleContent.remove(74);
		
		//app
		exampleContent.add(76, exampleContent.get(76) + "-" + timestamp);
		exampleContent.remove(77);
		exampleContent.add(81, exampleContent.get(81) + "-" + timestamp);
		exampleContent.remove(82);
		exampleContent.add(85, exampleContent.get(85) + "-" + timestamp);
		exampleContent.remove(86);
		exampleContent.add(88, exampleContent.get(88) + "-" + timestamp);
		exampleContent.remove(89);
		
		//volumeMounts name
		exampleContent.add(96, exampleContent.get(96) + "-" + timestamp);
		exampleContent.remove(97);
		exampleContent.add(101, exampleContent.get(101) + "-" + timestamp);
		exampleContent.remove(102);
		
		exampleContent.add(97, exampleContent.get(97) + "-" + timestamp);
		exampleContent.remove(98);
		exampleContent.add(104, exampleContent.get(104) + "-" + timestamp);
		exampleContent.remove(105);
		
		//supi
		exampleContent.add(8, exampleContent.get(8).split(":")[0] + ": "  + "\'" + inf.get("supi").asText()+"\'");
		exampleContent.remove(9);
		//mcc
		exampleContent.add(10, exampleContent.get(10).split(":")[0] + ": "  + "\'" + inf.get("mcc").asText() + "\'");
		exampleContent.remove(11);
		//mnc
		exampleContent.add(12, exampleContent.get(12).split(":")[0]  + ": " + "\'" + inf.get("mnc").asText()  + "\'");
		exampleContent.remove(13);
		//key
		exampleContent.add(14, exampleContent.get(14).split(":")[0]  + ": " + "\'" + inf.get("key").asText() +"\'");
		exampleContent.remove(15);
		
		//op code
		exampleContent.add(16, exampleContent.get(16).split(":")[0]  + ": " + "\'" + inf.get("opCode").asText() +"\'");
		exampleContent.remove(17);
		
		//op type
		exampleContent.add(18, exampleContent.get(18).split(":")[0]  + ": " + "\'" + inf.get("opType").asText() +"\'");
		exampleContent.remove(19);
		
		//amf
		exampleContent.add(20, exampleContent.get(20).split(":")[0]  + ": " + "\'" + inf.get("amf").asText() +"\'");
		exampleContent.remove(21);
		
		//gnbIp
		exampleContent.add(27, exampleContent.get(27).split("-")[0]  + "- " + inf.get("gnbIp").asText());
		exampleContent.remove(28);
		
		//slice
		exampleContent.add(45, exampleContent.get(45).split(":")[0] + ": " + "0x" + String.format("%02d", inf.get("slice").get("sst").asInt()));
		exampleContent.remove(46);
		exampleContent.add(46, exampleContent.get(46).split(":")[0] + ": " + "0x" +String.valueOf(inf.get("slice").get("sd").asText()));
		exampleContent.remove(47);
		
		//slice
		exampleContent.add(49, exampleContent.get(49).split(":")[0] + ": " + "0x" + String.valueOf(inf.get("slice").get("sst").asInt()));
		exampleContent.remove(50);
		exampleContent.add(50, exampleContent.get(50).split(":")[0] + ": " + "0x" +String.valueOf(inf.get("slice").get("sd").asText()));
		exampleContent.remove(51);
		
		//command
		exampleContent.add(93, exampleContent.get(93).split(":")[0] + ": " + "[\"./build/nr-ue -c ./config/free5gc-ue.yaml -n " + numOfUe + "  >> ue_log.txt \"]");
		exampleContent.remove(94);
		
		commandService.generateYamlFile(s, exampleContent, filePath, fileName);
		log.info("UeService=====Write ue with multiple yaml file OK");
	}

}
