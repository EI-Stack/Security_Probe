package oam.security.model.resource.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;
import oam.security.exception.base.ExceptionBase;
import oam.security.model.resource.gnb.GnbService;
import oam.security.model.resource.postgres.TrafficRecord;
import oam.security.model.resource.postgres.TrafficRecordService;
import oam.security.model.resource.ue.UeService;
//import oam.security.model.resource.postgres.TrafficRecord;
//import oam.security.model.resource.postgres.TrafficRecordService;
import oam.security.model.resource.ueran.UeranService;
import oam.security.model.resource.util.CommandService;
import oam.security.model.resource.util.Iperf3Service;
import oam.security.model.resource.util.ProbeCorrespondService;
import oam.security.service.JSchService;
import oam.security.util.DateTimeUtil;
import oam.security.util.StringUtil;

@RequestMapping("/v1/security")
@RestController
@Slf4j
public class SecurityController {
	
	@Autowired
	private SecurityService securityService;
	@Autowired
	private TrafficRecordService trafficRecordService;
	@Autowired
	public JSchService jschService;
	@Autowired
	private UeranService ueranService;
	@Autowired
	private Iperf3Service iperf3Service;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	public UeService ueService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	@Autowired
	private ProbeCorrespondService probeCorrespondService;
	
	
	private double abNormalRate = 0.75;
	
	
	@PostMapping("/record")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private void recordTraffic(@RequestBody JsonNode content) throws Exception {
		/**傳入
		 * {
		    "amfIP" : "10.244.0.171"
		}
		 */
		//先產生要傳給方法的JsonNode
		ObjectNode inf = objectMapper.createObjectNode();
		String amfIP = content.get("amfIP").asText();
		inf.put("amfIP", amfIP);
		inf.put("numOfGnb", 1);
		inf.put("gnbIP", "");
		inf.put("numOfUe", 1);//這邊都預設一個
		//產生要儲存的對應的jsonNode
		ObjectNode corre = objectMapper.createObjectNode();
		
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/"; 
		ueranService.mkdir(session, fileFolder);
		//找iperf3 Server 的IP
		JsonNode iperf3Data = iperf3Service.createIperf3Service(session, fileFolder);
		corre.set("iperf3", iperf3Data);
		String iperf3ServerIP = iperf3Data.get("ip").asText();
		//找ue pod的timestamp
		JsonNode gnbAndUeData = ueranService.createGnbAndUe(inf, session, fileFolder);
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		String uePodTimestamp = gnbAndUeData.get("ue").get("timestamp").asText();
		ArrayList<String> allPosName = ueranService.getAllPodsName(session);
		String targetPodName = StringUtil.findPodName(allPosName, "ueransim-ue", uePodTimestamp);
		//更改權限
		ueService.changeNrBinderPermission(session, targetPodName);
		//這裡一定只有一個UE
		ArrayList<String> equip_list = ueranService.getAllGnbAndUeInPod(session, uePodTimestamp);
		//這裡一定只有一個UE 拿第0個
		TrafficRecord t = securityService.getTraffic(session, equip_list.get(0), iperf3ServerIP, false);
//		jschService.closeJschSession(session);
		//儲存到程式的對應
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		probeCorrespondService.addCorrespond(timestamp, corre);
		//儲存到資料庫
		trafficRecordService.saveToPostgres(t);
		log.info("對應表:" + probeCorrespondService.toString());
		
		//準備deregister (待測試)
		log.info("Ready to deregister");
		Thread.sleep(10000);//給核網10秒
		
		allPosName = ueranService.getAllPodsName(session);
		String gnbTimestamp = corre.get("gnb").get("timestamp").asText();
		String ueTimestamp = corre.get("ue").get("timestamp").asText();
		String uetargetPodName = StringUtil.findPodName(allPosName, "ueransim", ueTimestamp);
		ueService.deregisterFromCoreByPodName(session, uetargetPodName);//ue 才需要deregister
		//刪除deployment和configmap
		ueranService.deleteDeployAndConfigmapContains(session, gnbTimestamp);
		ueranService.deleteDeployAndConfigmapContains(session, ueTimestamp);
		//close connection
		jschService.closeJschSession(session);
	}
	
	@PostMapping("/record/test")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private void testDB() {
		TrafficRecord r = new TrafficRecord("2023-03-14T12:09:00Z", 3, 14, 12, "1K/s");
		trafficRecordService.saveToPostgres(r);
	}
	
	@GetMapping("/dosReview")
	@ResponseStatus(HttpStatus.OK)
	private boolean dosReview(@RequestBody JsonNode content) throws Exception {
		/**傳入
		 * {
		    "amfIP" : "10.244.0.171"
		}
		 */
		//檢測UE是否為最大上限
		//先產生要傳給方法的JsonNode
		boolean isUeMax = false;
		
		ObjectNode inf = objectMapper.createObjectNode();
		String amfIP = content.get("amfIP").asText();
		inf.put("amfIP", amfIP);
		inf.put("numOfGnb", 1);
		inf.put("gnbIP", "");
		inf.put("numOfUe", 1);//這邊都預設一個
		//產生要儲存的對應的jsonNode
		ObjectNode corre = objectMapper.createObjectNode();
		
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/"; 
		ueranService.mkdir(session, fileFolder);

		//生成gnb和ue
		JsonNode gnbAndUeData = ueranService.createGnbAndUe(inf, session, fileFolder);
		String gnbIp = gnbAndUeData.get("gnb").get("ip").asText();
		if(gnbIp.equals("") || gnbIp.equals("Overload")) {//如果ip裡面沒東西或是Overload 就表示無法負荷了
			isUeMax = true;
			return isUeMax;
		}
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		log.info("The correspond is:" + corre.toPrettyString());
		//儲存到程式的對應
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		//不用加到對應表 因為用完就deregister了
//		probeCorrespondService.addCorrespond(timestamp, corre);
		log.info("對應表:" + probeCorrespondService.toString());
		
		//準備deregister
		log.info("Ready to deregister");
		Thread.sleep(10000);//給核網10秒
		
		ArrayList<String> allPosName = ueranService.getAllPodsName(session);
		String gnbTimestamp = corre.get("gnb").get("timestamp").asText();
		String ueTimestamp = corre.get("ue").get("timestamp").asText();
		String uetargetPodName = StringUtil.findPodName(allPosName, "ueransim", ueTimestamp);
		ueService.deregisterFromCoreByPodName(session, uetargetPodName);//ue 才需要deregister
		//刪除deployment和configmap
		ueranService.deleteDeployAndConfigmapContains(session, gnbTimestamp);
		ueranService.deleteDeployAndConfigmapContains(session, ueTimestamp);
		//close connection
		jschService.closeJschSession(session);
		
		//檢測CPU使用率
		boolean isCpuLimit = securityService.checkCPU();
		//檢測memory
		boolean isMemoryLimit = securityService.checkMemory();
		
		return isUeMax && isCpuLimit && isMemoryLimit;
	}
	
	@GetMapping("/highThroughput")
	@ResponseStatus(HttpStatus.OK)
	private boolean isAbnormalHighThroughput(@RequestBody JsonNode content) throws Exception {
		/**傳入
		 * {
		    "amfIP" : "10.244.0.171"
		}
		 */
		boolean isAbnormal = false;
		//TODO 要測試能否達到最低頻寬
		
		//從資料庫取得現在時間(小時)平均歷史流量
		ZonedDateTime zonedDateTime = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC"));
		double avgSpeed = trafficRecordService.getAvgTrafficByHour(zonedDateTime.getHour());
		log.info("avgSpeed : " + avgSpeed + "bits/sec");
		
		//先產生要傳給方法的JsonNode
		ObjectNode inf = objectMapper.createObjectNode();
		String amfIP = content.get("amfIP").asText();
		inf.put("amfIP", amfIP);
		inf.put("numOfGnb", 1);
		inf.put("gnbIP", "");
		inf.put("numOfUe", 1);//這邊都預設一個
		//產生要儲存的對應的jsonNode
		ObjectNode corre = objectMapper.createObjectNode();
		
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/"; 
		ueranService.mkdir(session, fileFolder);
		//找iperf3 Server 的IP
		JsonNode iperf3Data = iperf3Service.createIperf3Service(session, fileFolder);
		corre.set("iperf3", iperf3Data);
		String iperf3ServerIP = iperf3Data.get("ip").asText();
		//取得ue跟gnb的資訊
		JsonNode gnbAndUeData = ueranService.createGnbAndUe(inf, session, fileFolder);
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		String uePodTimestamp = gnbAndUeData.get("ue").get("timestamp").asText();
		ArrayList<String> allPosName = ueranService.getAllPodsName(session);
		String targetPodName = StringUtil.findPodName(allPosName, "ueransim-ue", uePodTimestamp);
		//更改權限
		ueService.changeNrBinderPermission(session, targetPodName);
		//這裡一定只有一個UE
		ArrayList<String> equip_list = ueranService.getAllGnbAndUeInPod(session, uePodTimestamp);
		//這裡一定只有一個UE 拿第0個
		TrafficRecord t = securityService.getTraffic(session, equip_list.get(0), iperf3ServerIP, false);
		
		//儲存到程式的對應
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		//這個不用儲存到對應表 用完就刪(deregister)了
//		probeCorrespondService.addCorrespond(timestamp, corre);
		//這裡的流量不用儲存 以常駐在系統的資安探針為準
//		trafficRecordService.saveToPostgres(t);
		double trafficSpeed = trafficRecordService.turnTrafficData(t.getTraffic());
		log.info("TrafficData:" + t.toString());
		log.info("Test traffic:" + String.valueOf(trafficSpeed));
		if(avgSpeed * abNormalRate > trafficSpeed) {
			log.info("Traffic is abNormal!!!");
			isAbnormal = true; //異常流量
		}else {
			isAbnormal = false; //正常流量
			log.info("Traffic is Normal.");
		}
		log.info("AvgSpeed:" + avgSpeed + " rate:" + abNormalRate);
		
		
		//準備deregister
		log.info("Ready to deregister");
		Thread.sleep(10000);//給核網10秒
		
		allPosName = ueranService.getAllPodsName(session);
		String gnbTimestamp = corre.get("gnb").get("timestamp").asText();
		String ueTimestamp = corre.get("ue").get("timestamp").asText();
		String uetargetPodName = StringUtil.findPodName(allPosName, "ueransim", ueTimestamp);
		ueService.deregisterFromCoreByPodName(session, uetargetPodName);//ue 才需要deregister
		//刪除deployment和configmap
		ueranService.deleteDeployAndConfigmapContains(session, gnbTimestamp);
		ueranService.deleteDeployAndConfigmapContains(session, ueTimestamp);
		//close connection
		jschService.closeJschSession(session);
		
		return isAbnormal;  //正常流量
	}
	
	@GetMapping("/networkConfigCheck")
	@ResponseStatus(HttpStatus.OK)
	private boolean checkNetworkConfiguration(@RequestBody JsonNode content) throws Exception {
		/**傳入
		 * {
		    "amfIP" : "10.244.0.171"
		}
		 */
		boolean hasBeenChanged = false;
		//先產生要傳給方法的JsonNode
		ObjectNode inf = objectMapper.createObjectNode();
		String amfIP = content.get("amfIP").asText();
		inf.put("amfIP", amfIP);
		inf.put("numOfGnb", 1);
		inf.put("gnbIP", "");
		inf.put("numOfUe", 1);//這邊都預設一個
		//產生要儲存的對應的jsonNode
		ObjectNode corre = objectMapper.createObjectNode();		
		
		//設定iperf3
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/"; 
		ueranService.mkdir(session, fileFolder);
		//找iperf3 Server 的IP
		JsonNode iperf3Data = iperf3Service.createIperf3Service(session, fileFolder);
		corre.set("iperf3", iperf3Data);
		String iperf3ServerIP = iperf3Data.get("ip").asText();
		
		//檢測最大上行
		
		//取得ue跟gnb的資訊
		JsonNode gnbAndUeData = ueranService.createGnbAndUe(inf, session, fileFolder);
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		String uePodTimestamp = gnbAndUeData.get("ue").get("timestamp").asText();
		ArrayList<String> allPosName = ueranService.getAllPodsName(session);
		String targetPodName = StringUtil.findPodName(allPosName, "ueransim-ue", uePodTimestamp);
		//更改權限
		ueService.changeNrBinderPermission(session, targetPodName);
		//這裡一定只有一個UE
		ArrayList<String> equip_list = ueranService.getAllGnbAndUeInPod(session, uePodTimestamp);
		String imsi = equip_list.get(0);
		//這裡一定只有一個UE 拿第0個
		//這裡是up link
		TrafficRecord t_up = securityService.getTraffic(session, imsi, iperf3ServerIP, false);
		double up_speedRate = trafficRecordService.turnTrafficData(t_up.getTraffic());
		
		//這裡是 down link
		TrafficRecord t_down = securityService.getTraffic(session, imsi, iperf3ServerIP, false);
		double down_speedRate = trafficRecordService.turnTrafficData(t_down.getTraffic());
		
		//儲存到程式的對應
		corre.set("gnb", gnbAndUeData.get("gnb"));
		corre.set("ue", gnbAndUeData.get("ue"));
		
		//要拿到ue的最大上行/下行流量
		String ueUplinkAmbr = corre.get("ue").get("subscribedUeAmbr").get("Uplink_ambr").asText();
		double uplinkAmbr = trafficRecordService.turnTrafficData(ueUplinkAmbr);
		log.info("UE now uplink:" + up_speedRate);
		log.info("UE setting uplinkAmbr:" + uplinkAmbr);
		String ueDownUplinkAmbr = corre.get("ue").get("subscribedUeAmbr").get("Downlink_ambr").asText();
		double downlinkAmbr = trafficRecordService.turnTrafficData(ueDownUplinkAmbr);
		log.info("UE now downlink:" + down_speedRate);
		log.info("UE setting downlinkAmbr:" + downlinkAmbr);
		
		if(down_speedRate >= downlinkAmbr * 0.75 || down_speedRate <= downlinkAmbr * 1.25) {
			log.info("Uplink check fail");
			hasBeenChanged = true;//表示被修改了
		}
		
		if(up_speedRate >= uplinkAmbr * 0.75 || up_speedRate <= uplinkAmbr * 1.25) {
			log.info("Downlink check fail");
			hasBeenChanged = true;//表示被修改了
		}
		
		//先用剛剛那一組衝流量 這裡要用執行緒
		
		
		//再用一組看有沒有最小上行
		
		//準備deregister
		log.info("Ready to deregister");
		Thread.sleep(10000);//給核網10秒
		
		allPosName = ueranService.getAllPodsName(session);
		String gnbTimestamp = corre.get("gnb").get("timestamp").asText();
		String ueTimestamp = corre.get("ue").get("timestamp").asText();
		String uetargetPodName = StringUtil.findPodName(allPosName, "ueransim", ueTimestamp);
		ueService.deregisterFromCoreByPodName(session, uetargetPodName);//ue 才需要deregister
		//刪除deployment和configmap
		ueranService.deleteDeployAndConfigmapContains(session, gnbTimestamp);
		ueranService.deleteDeployAndConfigmapContains(session, ueTimestamp);
		//close connection
		jschService.closeJschSession(session);

		return hasBeenChanged;
	}
	
	@GetMapping("/testSocket")
	@ResponseStatus(HttpStatus.OK)
	private void testSocket() throws URISyntaxException {
		log.info("Test Socket client");
		securityService.testSocketClient(new URI("ws://localhost:8080/ws/90"));
	}


}

