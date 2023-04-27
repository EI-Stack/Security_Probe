package oam.security.model.resource.ueran;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import oam.security.exception.base.ExceptionBase;
import oam.security.model.resource.gnb.GnbService;
import oam.security.model.resource.ue.UeService;
import oam.security.model.resource.util.CommandService;
import oam.security.model.resource.util.Iperf3Service;
import oam.security.service.JSchService;
import oam.security.util.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import antlr.StringUtils;
import lombok.extern.slf4j.Slf4j;
import oam.security.model.resource.example.ExampleContextService;

@Service
@Slf4j
public class UeranService {
	
	@Value("${solaris.server.free5gc.http.url}")
	private String url;
	@Autowired
	private GnbService gnbservice;
	@Autowired
	private UeService ueService;
	@Autowired
	private CommandService commandService;
	@Autowired
	private ExampleContextService example;
	@Autowired
	private JSchService jschService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	@Value("${solaris.session.oam_namespace}")
	private String oam_namespace;
	@Value("${solaris.free5gc-version}")
	private String free5gcVersion;	
	@Autowired
	private ObjectMapper objectMapper;
	
	//回傳ue的timestamp 後面的就知道要找哪一個pod
	public JsonNode createGnbAndUe(JsonNode inf, Session session, String fileFolder) throws ExceptionBase, JSchException, InterruptedException, IOException{
		int numOfUe = inf.path("numOfUe").asInt();
		int numOfGnb = inf.path("numOfGnb").asInt();
		if(numOfGnb == 0) {  //進到這個方法就是至少要一個
			numOfGnb = 1;
		}
		String assignAmfIp = inf.path("amfIP").asText();
		String assignGnbip = inf.path("gnbIP").asText();
				
//		String fileBasePath = "/home/ubuntu/UeranAuto/";
		JsonNode gnbAndUeData = objectMapper.createObjectNode();
		JsonNode gnbData = objectMapper.createObjectNode();
		JsonNode ueData = objectMapper.createObjectNode();
		ArrayList<String> gnbIP = new ArrayList<>();
		//切namespace
		commandService.changeNameSpace(session, oam_namespace, false);
		
		///////////////////////////////////
        //都要猜IP https://ithelp.ithome.com.tw/articles/10300248
		log.info("處理gnb");
        for(int i = 1; i <= numOfGnb; i++) {
        	try {
        		//撰寫yaml file + //驗證IP
            	log.info("GnbService=====Count gnb : " + i);
            	//gnbIP如果沒有指定就用自動猜的
            	JsonNode content = gnbservice.createAnGnb(session, example.getGnbExampleForApi(), fileFolder,
            			assignAmfIp, assignGnbip);
            	gnbIP.add(content.get("gnbIp").asText());
            	Thread.sleep(10000);
            	//檢查gnb的log是否OK
            	if(!gnbservice.checkGnbLogIsOk(session, content.get("timestamp").asText())) {
            		//如果log不正常就return 
            		log.error("Gnb cannot register to 5G core net");
            		((ObjectNode)gnbData).put("timestamp", content.get("timestamp").asText());
            		((ObjectNode)gnbData).put("ip", "Overload");
            		((ObjectNode)gnbAndUeData).set("gnb", gnbData);
            		return gnbAndUeData;
            	}
            	//如果log OK就裝資料
            	log.info("Gnb log is OK");
            	((ObjectNode)gnbData).put("timestamp", content.get("timestamp").asText());
        		((ObjectNode)gnbData).put("ip", content.get("gnbIp").asText());
        		((ObjectNode)gnbAndUeData).set("gnb", gnbData);
        	}catch(ArrayIndexOutOfBoundsException e) {
        		log.info("Cannot find AMF IP, please check commands or enviroments...");
        		jschService.closeJschSession(session);
        		((ObjectNode)gnbData).put("timestamp", "");
        		((ObjectNode)gnbData).put("ip", "");
        		((ObjectNode)gnbAndUeData).set("gnb", gnbData);
        		return gnbAndUeData;
        	}
        }
        Thread.sleep(3000);//讓gnb先執行
        System.out.println("處理ue");
        System.out.println("gnbip:");

        System.out.println("gnbip : " + gnbIP);    //顯示所有的gnbIP
        
        long maxSubscriberNum = ueService.getSubscriprionMaxImsi();
        JsonNode ue_example_web = null;
        for(int i = 1; i <= numOfUe; i++) {  //網站註冊
        	if(free5gcVersion.equals("Community")) {
        		ue_example_web = example.getUeExampleForWebContext_community();
        	}else if(free5gcVersion.equals("Business")) {
        		ue_example_web = example.getUeExampleForWebContext_business();
        	}else {
        		ue_example_web = example.getUeExampleForWebContext_community();
        	}
        	
        	String imsi = "imsi-" + formatLongNumber(maxSubscriberNum + i, 15);//imsi後面要15位的數字
        	//網站註冊subscriber  //需要ueId資訊
        	((ObjectNode)ue_example_web).put("ueId", imsi);
        	log.info("Web site Ue " + imsi + " subscriber:" + ue_example_web);
        	ueService.registerSubscriber(ue_example_web);
        }
    	//撰寫yaml  //需要supi跟gnbIp 其餘預設
    	JsonNode ue_api = example.getUeExampleForApi();
    	String imsi = "imsi-" + formatLongNumber(maxSubscriberNum + 1, 15);  //imsi後面要15位的數字
    	((ObjectNode)ue_api).put("supi", imsi);
    	if(!assignGnbip.equals("")) {
    		((ObjectNode)ue_api).put("gnbIp", assignGnbip);
    	}else {
    		((ObjectNode)ue_api).put("gnbIp", ueService.findSuitableGnb(session, gnbIP));	
    	}
    	
    	String uePodTimestsmp = ueService.createMultipleUeWithOnePod(session, ue_api, fileFolder, numOfUe);
    	Thread.sleep(15000);//用好一個UE 休息15秒
    	//組成json
    	//雖然可能會有好幾個ue(imsi) 但是pod一定只會有一個 所以只有一個IP
    	String command = " kubectl get pods  -o wide";
    	if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
		ArrayList<String> result = commandService.execCommandAndGetResult(session, command, false);
    	String ueIP = StringUtil.findPodIp(result, uePodTimestsmp);
    	ObjectNode subscribedUeAmbr = objectMapper.createObjectNode();
    	subscribedUeAmbr.put("Uplink_ambr", ue_example_web.get("AccessAndMobilitySubscriptionData").get("subscribedUeAmbr").get("uplink").asText());
    	subscribedUeAmbr.put("Downlink_ambr", ue_example_web.get("AccessAndMobilitySubscriptionData").get("subscribedUeAmbr").get("downlink").asText());
    	((ObjectNode)ueData).put("timestamp", uePodTimestsmp);
		((ObjectNode)ueData).put("ip", ueIP);
		((ObjectNode)ueData).set("subscribedUeAmbr", subscribedUeAmbr);
		((ObjectNode)gnbAndUeData).set("ue", ueData);
        
		///////////////////////////////////
		log.info("gnbAndUeData:" + gnbAndUeData);
		return gnbAndUeData;
	}
	
	
	public void mkdir(Session s, String path) throws JSchException, InterruptedException, IOException {
		ChannelExec channel = (ChannelExec) s.openChannel("exec");
		// NOTE: the provided paths are expected to require no escaping
		channel.setCommand("mkdir -p " + path);
		channel.connect();
		while (!channel.isClosed()) {
		    // dir creation is usually fast, so only wait for a short time
		    Thread.sleep(10);
		}
		channel.disconnect();
		if (channel.getExitStatus() != 0) {
		    throw new IOException("Creating directory failed: "  + path);
		}
	}
	
	public String findAMFIP() {
		Session s = jschService.getJschSession(); 
		String ip = gnbservice.findAmfPodIp(s);
		jschService	.closeJschSession(s);
		return ip;
	}
	
	public String getAllPods() {
		Session s = jschService.getJschSession(); 
		String allpods = findAllPods(s);
		jschService	.closeJschSession(s);
		return allpods;
	}
	
	
	public String findAllPods(Session s) {
		String command = " kubectl get pods -A " ;
		String all = "";
		ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
		for(int i = 0; i < result.size(); i++) {
			all += result.get(i) + "\n";
		}
		return all;
	}
	
	public ArrayList<String> getAllPodsName(Session s){
		ArrayList<String> allPodsName = new ArrayList<>();
		String command = " kubectl get pods ";
		int namePosition = 0;
		if(!oam_namespace.trim().equals("")) {
			command += " -n " + oam_namespace;
		}
		ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
		
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
				allPodsName.add(podName);
			}
		}
		return allPodsName;
	}
	
	public ArrayList<String> getAllGnbAndUeInPod(Session s, String podKeyWord){
		ArrayList<String> allPodsName = getAllPodsName(s);
		String targetPodName = "";
		for(int i = 0; i < allPodsName.size(); i++) {
			if(allPodsName.get(i).contains(podKeyWord)) {
				targetPodName = allPodsName.get(i);
				break;
			}
		}
		String findImsiCommands = " kubectl exec -i " + targetPodName + " -- ./build/nr-cli -d";
		ArrayList<String> equip_list = commandService.execCommandAndGetResult(s, findImsiCommands, false);
		return equip_list;
	}
	
	public void deleteUeransimDeployAndConfigMap() throws InterruptedException, ExceptionBase {
		Session s = jschService.getJschSession();
		//Deregister all ueransim ue and delete subscription
		ArrayList<String> allPosName = getAllPodsName(s);
		String podName = "";
		for(int i = 0; i < allPosName.size(); i++) {
			if(allPosName.get(i).contains("ueransim-ue")) {
				String findImsiCommands = " kubectl exec -i " + allPosName.get(i) + " -- ./build/nr-cli -d";
				ArrayList<String> allGnbAndUe = commandService.execCommandAndGetResult(s, findImsiCommands, false);
				log.info("Pod " + allPosName.get(i) + " AllGnbAndUe: " + allGnbAndUe);
				for(int j = 0; j < allGnbAndUe.size(); j++) {
					String deregisterCommands = "kubectl exec -ti " + allPosName.get(i) 
					+ " -- ./build/nr-cli " + allGnbAndUe.get(j) 
					+ " --exec \"deregister remove-sim\"";
					commandService.execCommandAndGetResult(s, deregisterCommands, false);
					log.info("Deregister imsi:" + allGnbAndUe.get(j));
					ueService.deleteSubscriber(allGnbAndUe.get(j));
				}
			}
		}
		Thread.sleep(2000);
		//Delete ueransim comfigmap
		String command = " kubectl get configmap ";
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
				String deployname = result.get(i).split(",")[namePosition];
				if(deployname.contains("ueransim")) {
					String commandsDeleteConfig = "kubectl delete configmap " + deployname;
					if(!oam_namespace.trim().equals("")) {
						commandsDeleteConfig += " -n " + oam_namespace;
					}
					commandService.execCommandAndGetResult(s, commandsDeleteConfig, false);
				}
			}
		}
		//Delete ueransim deployment
		command = " kubectl get deployment ";
		if(!oam_namespace.trim().equals("")) {
			command += " -n " + oam_namespace;
		}
		result = commandService.execCommandAndGetResult(s, command, false);
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
				String configname = result.get(i).split(",")[namePosition];
				if(configname.contains("ueransim")) {
					String commandsDeleteDeploy = "kubectl delete deployment " + configname;
					if(!oam_namespace.trim().equals("")) {
						commandsDeleteDeploy += " -n " + oam_namespace;
					}
					commandService.execCommandAndGetResult(s, commandsDeleteDeploy, false);
				}
			}
		}
		jschService.closeJschSession(s);
	}
	
	public void deleteDeployAndConfigmapContains(Session s, String timestamp){
		//Delete ueransim comfigmap
		String command = " kubectl get configmap ";
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
				String deployname = result.get(i).split(",")[namePosition];
				if(deployname.contains(timestamp)) {
					String commandsDeleteConfig = "kubectl delete configmap " + deployname;
					if(!oam_namespace.trim().equals("")) {
						commandsDeleteConfig += " -n " + oam_namespace;
					}
					commandService.execCommandAndGetResult(s, commandsDeleteConfig, false);
				}
			}
		}
		//Delete ueransim deployment
		command = " kubectl get deployment ";
		if(!oam_namespace.trim().equals("")) {
			command += " -n " + oam_namespace;
		}
		result = commandService.execCommandAndGetResult(s, command, false);
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
				String configname = result.get(i).split(",")[namePosition];
				if(configname.contains(timestamp)) {
					String commandsDeleteDeploy = "kubectl delete deployment " + configname;
					if(!oam_namespace.trim().equals("")) {
						commandsDeleteDeploy += " -n " + oam_namespace;
					}
					commandService.execCommandAndGetResult(s, commandsDeleteDeploy, false);
				}
			}
		}
	}
	
	public String formatLongNumber(long number, int bitNumber) {
		String numberString = String.valueOf(number);
		while(numberString.length() < bitNumber) {
			numberString = "0" + numberString;
		}
		return numberString;
	}
	
	public void testThroughput(String imsi, String iperf3Server) {
		Session session = jschService.getJschSession();
		String podName = findImsiInWhichPod(session, imsi);
		String imsiIp = findImsiIp(session, imsi, podName);
		String iperf3Commands = "./build/nr-binder " + imsiIp + " iperf3 -c " + iperf3Server + " -i 1 -t 10 -b 1G >> allCommandText.txt";
		String commands = " kubectl exec -i " + podName + " -- " + iperf3Commands;
		commandService.execCommandAndGetResult(session, commands, false);
		jschService.closeJschSession(session);
	}
	
	public String findImsiInWhichPod(Session s, String imsi) {
		ArrayList<String> allPosName = getAllPodsName(s);
		String podName = "";
		for(int i = 0; i < allPosName.size(); i++) {
			if(allPosName.get(i).contains("ueransim-ue-")) {
				String findImsiCommands = " kubectl exec -i " + allPosName.get(i) + " -- ./build/nr-cli -d";
				ArrayList<String> allGnbAndUe = commandService.execCommandAndGetResult(s, findImsiCommands, false);
				log.info("Pod " + allPosName.get(i) + " AllGnbAndUe: " + allGnbAndUe);
				for(int j = 0; j < allGnbAndUe.size(); j++) {
					if(allGnbAndUe.get(j).equals(imsi)) {
						System.out.println("Get pod name : " + allPosName.get(i) + " " + imsi);
						podName = allPosName.get(i);
						break;
					}
				}
			}
		}
		return podName;
	}
	
	public String findImsiIp(Session s, String imsi, String podName) {
		String ip = "";
		String commands = " kubectl exec -i " + podName + " -- ./build/nr-cli " + imsi + " --exec \"ps-list\"";
		ArrayList<String> ps_list = commandService.execCommandAndGetResult(s, commands, false);
		for(int i = 0; i < ps_list.size(); i++) {
			String line = ps_list.get(i);
			line = line.replace(",", "");
			System.out.println(line);
			if(line.startsWith("address")) {
				System.out.println("address line:" + line);
				String [] lineArray = line.split(":");
				ip = lineArray[1];
				break;
			}
		}
		System.out.println("Imsi:" + imsi + " ip:" + ip);
		return ip;
	}
	
	public int testMaxUe(JsonNode inf, Session session, String fileFolder) throws ExceptionBase, JSchException, InterruptedException, IOException {
		
		//現在都是一個pod裡面用複數個UE 這裡要計算幾個ue成功(算uesimtun網卡的數量)
    	//找出剛剛的ue pod
		int numOfUe = inf.get("numOfUe").asInt();
		boolean coreNetworkCanAccept = true;
		int allSuccessUe = 0;
		
		while(coreNetworkCanAccept) {
			//先創立
			JsonNode gnbAndUeData = createGnbAndUe(inf, session, fileFolder);
			String ue_timestamp = gnbAndUeData.get("ue").get("timestamp").asText();
			log.info("ue_timestamp : " + ue_timestamp);
			if(ue_timestamp.equals("Overload")) {  //連GNB都無法註冊 表示overload
				coreNetworkCanAccept = false;
				log.info("Core network can not afford!!!!!!!!!!!!!!!!! Gnb register error. Ue is:" + allSuccessUe);
				break;
			}
			//再找有幾個成功
	    	ArrayList<String> allPosName = getAllPodsName(session);
	    	//找出哪一個pod是ue 而且符合ue的timestamp的 就是目標pod
			String targetPodName = StringUtil.findPodName(allPosName, "ueransim-ue", ue_timestamp);
			log.info("targetPodName:" + targetPodName);
			String findImsiCommands = " kubectl exec -i " + targetPodName + " -- ip addr";
			ArrayList<String> allGnbAndUe = 
					commandService.execCommandAndGetResult(session, findImsiCommands, false);
			int appear = 0;
			for(int size = 0; size < allGnbAndUe.size(); size++) {
				//看出現幾張網卡
				if(StringUtil.stringHasKeyWord(allGnbAndUe.get(size).replace("MTU", "mtu"), "uesimtun", "mtu", "group")) {
					appear += 1;
				}
				//不能用uesimtun 出現的次數 會有兩個
//				appear += StringUtil.countAppear(allGnbAndUe.get(size), "uesimtun");
			}
			allSuccessUe += appear;
			log.info("appear: " + String.valueOf(appear));
			log.info("allSuccessUe: " + String.valueOf(allSuccessUe));
			log.info("targetPodName : " + targetPodName + " has " + String.valueOf(appear) + " ue");
			if(appear < numOfUe) {
				//這裡代表已經不能承受了
				log.info("Core network can not afford!!!!!!!!!!!!!!!!! allSuccessUe is " + allSuccessUe);
				coreNetworkCanAccept = false;
			}
		}
		
		return allSuccessUe;
	}
	
	public String testMaxLoadByImsi(String imsi, String iperf3ServerIp, String type) {
		String fileName = "";
		String iperf3Commands = "";
		Session session = jschService.getJschSession();
		String podName = findImsiInWhichPod(session, imsi);
		String imsiIp = findImsiIp(session, imsi, podName);
		if(type.equals("Upload")) {
			fileName = "TestMaxUpLoad.txt";
			iperf3Commands = "./build/nr-binder " + imsiIp + " iperf3 -c " + iperf3ServerIp + " -i 1 -t 10 >> " + fileName;
		}else if(type.equals("Download")) {
			fileName = "TestMaxDownLoad.txt";
			iperf3Commands = "./build/nr-binder " + imsiIp + " iperf3 -c " + iperf3ServerIp + " -i 1 -t 10 -R >> " + fileName;
		}
		String commands = " kubectl exec -i " + podName + " -- " + iperf3Commands;
		commandService.execCommandAndGetResult(session, commands, false);
		String output = readFile(session, fileName);
		jschService.closeJschSession(session);
		return output;
	}
	
	public String readFile(Session s, String fileName) {
		String commands = "cat " + fileName;
		ArrayList<String> fileContent = commandService.execCommandAndGetResult(s, commands, false);
		String fileString = "";
		for(int i = 0; i < fileContent.size(); i++) {
			fileString += fileContent.get(i).replace(",", " ") + "\n";
		}
		return fileString;
	}
	
		
}
