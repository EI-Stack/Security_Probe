package oam.security.model.resource.ueran;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import oam.security.exception.base.ExceptionBase;
import oam.security.service.JSchService;
import oam.security.service.NetworkServiceBase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;

@RequestMapping("/v1/ueran")
@RestController
@Slf4j
public class UeranController {
	
	@Autowired
	private UeranService ueranService;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	public JSchService jschService;
	@Autowired
	private NetworkServiceBase networkService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	
	@PostMapping("/createGnbAndUe")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private void createGnbAndUe(@RequestBody JsonNode inf) throws JSchException, ExceptionBase, InterruptedException, IOException {
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/"; 
		ueranService.createGnbAndUe(inf, session, fileFolder);
		jschService.closeJschSession(session);
	}
	
	@GetMapping("/getAmfIP")
	@ResponseStatus(HttpStatus.OK)
	private String findAMFIP() {
		return ueranService.findAMFIP();
	}
	
	@GetMapping("/getAllPods")
	@ResponseStatus(HttpStatus.OK)
	private String getAllPods() {
		return ueranService.getAllPods();
	}
	
	@DeleteMapping("/deleteUeranDeploy")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private void deletedeployAndConfigmap() throws InterruptedException, ExceptionBase {
		ueranService.deleteUeransimDeployAndConfigMap();
	}
	
	@PostMapping("/throughput/{imsi}/{iperf3Ip}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private void testThroughput(@PathVariable String imsi, @PathVariable String iperf3Ip) {
		log.info("TestThroughput imsi:" + imsi + " iperf3Ip:" + iperf3Ip);
		ueranService.testThroughput(imsi, iperf3Ip);
	}
	
	@PostMapping("/maxUe")
	@ResponseStatus(HttpStatus.OK)
	private JsonNode testCoreNetworkMaxUe(@RequestBody JsonNode inf) throws ExceptionBase, JSchException, InterruptedException, IOException {
		log.info("testCoreNetworkMaxUe");
		Session session = jschService.getJschSession();
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "UeranAuto" + timestamp + "/";
		int maxUe = ueranService.testMaxUe(inf, session, fileFolder);
		jschService.closeJschSession(session);
		String jsonString = "{\"maxUe:\":" + maxUe + "}";
		return objectMapper.readTree(jsonString);
	}
	
	@PostMapping("/postTest")
	@ResponseStatus(HttpStatus.CREATED)
	private void testPost() throws JsonMappingException, JsonProcessingException, ExceptionBase {
		JsonNode t = objectMapper.readTree("{\"plmnID\":\"20893\",\"ueId\":\"imsi-208930000000007\",\"AuthenticationSubscription\":{\"authenticationManagementField\":\"8000\",\"authenticationMethod\":\"5G_AKA\",\"milenage\":{\"op\":{\"encryptionAlgorithm\":0,\"encryptionKey\":0,\"opValue\":\"8e27b6af0e692e750f32667a3b14605d\"}},\"opc\":{\"encryptionAlgorithm\":0,\"encryptionKey\":0,\"opcValue\":\"\"},\"permanentKey\":{\"encryptionAlgorithm\":0,\"encryptionKey\":0,\"permanentKeyValue\":\"8baf473f2f8fd09487cccbd7097c6862\"},\"sequenceNumber\":\"16f3b3f70fc2\"},\"AccessAndMobilitySubscriptionData\":{\"gpsis\":[\"msisdn-0900000000\"],\"nssai\":{\"defaultSingleNssais\":[{\"sst\":1,\"sd\":\"010203\",\"isDefault\":true}],\"singleNssais\":[]},\"subscribedUeAmbr\":{\"downlink\":\"2 Gbps\",\"uplink\":\"1 Gbps\"}},\"SessionManagementSubscriptionData\":[{\"singleNssai\":{\"sst\":1,\"sd\":\"010203\"},\"dnnConfigurations\":{\"internet\":{\"sscModes\":{\"defaultSscMode\":\"SSC_MODE_1\",\"allowedSscModes\":[\"SSC_MODE_2\",\"SSC_MODE_3\"]},\"pduSessionTypes\":{\"defaultSessionType\":\"IPV4\",\"allowedSessionTypes\":[\"IPV4\"]},\"sessionAmbr\":{\"uplink\":\"200 Mbps\",\"downlink\":\"100 Mbps\"},\"5gQosProfile\":{\"5qi\":9,\"arp\":{\"priorityLevel\":8},\"priorityLevel\":8}}}}],\"SmfSelectionSubscriptionData\":{\"subscribedSnssaiInfos\":{\"01010203\":{\"dnnInfos\":[{\"dnn\":\"internet\"}]}}},\"AmPolicyData\":{\"subscCats\":[\"free5gc\"]},\"SmPolicyData\":{\"smPolicySnssaiData\":{\"01010203\":{\"snssai\":{\"sst\":1,\"sd\":\"010203\"},\"smPolicyDnnData\":{\"internet\":{\"dnn\":\"internet\"}}}}},\"FlowRules\":[]}");
		String url = "http://60.251.156.214:31112/api/subscriber/imsi-208930000000007/20893";
		networkService.postJsonNode(url, t);
	}
	
	@PostMapping("/maxUpload/{imsi}/{iperf3Ip}")
	@ResponseStatus(HttpStatus.OK)
	private String testMaxUploadByImsi(@PathVariable String imsi, @PathVariable String iperf3Ip) {
		return ueranService.testMaxLoadByImsi(imsi, iperf3Ip, "Upload");
	}
	
	@PostMapping("/maxDownload/{imsi}/{iperf3Ip}")
	@ResponseStatus(HttpStatus.OK)
	private String testMaxDownloadByImsi(@PathVariable String imsi, @PathVariable String iperf3Ip) {
		return ueranService.testMaxLoadByImsi(imsi, iperf3Ip, "Download");
	}
	
	
}
