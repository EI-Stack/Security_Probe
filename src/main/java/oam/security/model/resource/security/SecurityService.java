package oam.security.model.resource.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;
import oam.security.exception.base.ExceptionBase;
import oam.security.model.resource.gnb.GnbService;
import oam.security.model.resource.postgres.TrafficRecord;
import oam.security.model.resource.postgres.TrafficRecordService;
import oam.security.model.resource.ue.UeService;
import oam.security.model.resource.ueran.UeranService;
import oam.security.model.resource.util.CommandService;
import oam.security.model.webSocket.client.WebSocketClientConfig;
import oam.security.service.JSchService;
import oam.security.service.NetworkServiceBase;
import oam.security.util.StringUtil;

@Service
@Slf4j
public class SecurityService {
	
	@Value("${solaris.server.free5gc.http.url}")
	private String url;
	@Value("${solaris.free5gc-version}")
	private String free5gcVersion;
	@Autowired
	public NetworkServiceBase networkService;
	@Autowired
	private JSchService jschService;
	@Autowired
	private UeranService ueranService;
	@Autowired
	private CommandService commandService;
	@Autowired
	private TrafficRecordService trafficRecordService;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private GnbService gnbService;
	@Autowired
	private UeService ueService;
	@Value("${solaris.prometheus.url}")
	private String prometheus_url;
	@Value("${solaris.prometheus.namespace}")
	private String prometheus_namespace;
		
	public boolean checkCPU() throws Exception {
		String query_cpuNowRate = "sum(container_memory_usage_bytes{image!=\"\", container!=\"POD\", namespace=\"" + prometheus_namespace + "\"})";
		String query_cpuAllRate = "sum(node_memory_MemTotal_bytes) - sum(container_memory_usage_bytes{image!=\"\", container!=\"POD\", namespace=\"" + prometheus_namespace + "\"})";
		
		Map<String, String> filter = new HashMap<>();
		filter.put("image", "");
		filter.put("container", "POD");
		filter.put("namespace", prometheus_namespace);
		String where = networkService.getPrometheusFilter("CPU_NOW_RATE", filter);
		String prome_url = prometheus_url + "/api/v1/query?query=" + where;
		
		String response = networkService.httpRequest(prome_url, HttpMethod.GET, null, null);
		log.info(response);
		double cpuNowRate = Double.parseDouble(objectMapper.readTree(response).get("data").get("result").get(0).get("value").get(1).asText());
		
		where = networkService.getPrometheusFilter("CPU_ALL_RATE", filter);
		prome_url = prometheus_url + "/api/v1/query?query=" + where;
		
		response = networkService.httpRequest(prome_url, HttpMethod.GET, null, null);
		log.info(response);
		double cpuAllRate = Double.parseDouble(objectMapper.readTree(response).get("data").get("result").get(0).get("value").get(1).asText());
		
		double rate = cpuNowRate / cpuAllRate;
		log.info("目前使用的CPU:" + cpuNowRate + " 全部CPU:" + cpuAllRate +" CPU使用率:" + rate);
		if(rate > 0.75) {
			return true;
		}
		return false;
	}
	
	public boolean checkMemory() throws Exception {
		JsonNode prometheousNode = null;
		String query_memoryNowRate = "sum(rate(container_cpu_usage_seconds_total{image!=\"\", container!=\"POD\", namespace=\"" + prometheus_namespace + "\"}[5m]))";
		String query_memoryAllRate = "sum (machine_cpu_cores) - sum(rate(container_cpu_usage_seconds_total{image!=\"\", container!=\"POD\", namespace=\"" + prometheus_namespace + "\"}[5m]))";
		
		Map<String, String> filter = new HashMap<>();
		filter.put("image", "");
		filter.put("container", "POD");
		filter.put("namespace", prometheus_namespace);
		String where = networkService.getPrometheusFilter("MEMORY_NOW_RATE", filter);
		String prome_url = prometheus_url + "/api/v1/query?query=" + where;
		
		String response = networkService.httpRequest(prome_url, HttpMethod.GET, null, null);
		log.info(response);
		double memoryNowRate = Double.parseDouble(objectMapper.readTree(response).get("data").get("result").get(0).get("value").get(1).asText());
		
		where = networkService.getPrometheusFilter("MEMORY_ALL_RATE", filter);
		prome_url = prometheus_url + "/api/v1/query?query=" + where;
		
		response = networkService.httpRequest(prome_url, HttpMethod.GET, null, null);
		log.info(response);
		double memoryAllRate = Double.parseDouble(objectMapper.readTree(response).get("data").get("result").get(0).get("value").get(1).asText());
		
		
		double rate = memoryNowRate / memoryAllRate;
		log.info("目前使用的Memory:" + memoryNowRate + " 全部Memory:" + memoryAllRate +" Memory使用率:" + rate );
		if(rate > 0.75) {
			return true;
		}
		return false;
	}
	
	public TrafficRecord getTraffic(Session s, String imsi, String iperf3Ip, boolean reverse) throws JsonMappingException, JsonProcessingException {
		log.info("Start record network traffic");
		//先進行一次流量測試
		String podName = ueranService.findImsiInWhichPod(s, imsi);
		String imsiIp = ueranService.findImsiIp(s, imsi, podName);
		if(imsiIp.equals("") || imsiIp.equals("null")) {
			log.error("Cannot find imsi ip, please check setting!!");
			return null;
		}
		String iperf3Commands = "./build/nr-binder " + imsiIp + " iperf3 -c " + iperf3Ip + " -i 1 -t 10 -J";
		if(reverse) {  //決定方向
			iperf3Commands = iperf3Commands + " -R";
		}
		String commands = " kubectl exec -i " + podName + " -- " + iperf3Commands;
		ArrayList<String> list = commandService.execCommandAndGetResult(s, commands, true);
		log.info("End record network traffic");
		StringUtil.showStringList(list);
		//過濾出流量
		TrafficRecord t = null;
		if(reverse) {
			t = trafficRecordService.trafficDataFilter(StringUtil.stringListToString(list), iperf3Ip, imsiIp);
		}else {
			t = trafficRecordService.trafficDataFilter(StringUtil.stringListToString(list), imsiIp, iperf3Ip);
		}
		System.out.println("TrafficRecord t:" + t.toString());
		return t;
	}
	
	public TrafficRecord getTraffic(Session s, String imsi, String iperf3Ip, boolean reverse, int time) throws JsonMappingException, JsonProcessingException {
		log.info("Start record network traffic");
		//先進行一次流量測試
		String podName = ueranService.findImsiInWhichPod(s, imsi);
		String imsiIp = ueranService.findImsiIp(s, imsi, podName);
		if(imsiIp.equals("") || imsiIp.equals("null")) {
			log.error("Cannot find imsi ip, please check setting!!");
			return null;
		}
		String iperf3Commands = "./build/nr-binder " + imsiIp + " iperf3 -c " + iperf3Ip + " -i 1 -t " + String.valueOf(time) + " -J";
		if(reverse) {  //決定方向
			iperf3Commands = iperf3Commands + " -R";
		}
		String commands = " kubectl exec -i " + podName + " -- " + iperf3Commands;
		ArrayList<String> list = commandService.execCommandAndGetResult(s, commands, true);
		log.info("End record network traffic");
		StringUtil.showStringList(list);
		//過濾出流量
		TrafficRecord t = null;
		if(reverse) {
			t = trafficRecordService.trafficDataFilter(StringUtil.stringListToString(list), iperf3Ip, imsiIp);
		}else {
			t = trafficRecordService.trafficDataFilter(StringUtil.stringListToString(list), imsiIp, iperf3Ip);
		}
		System.out.println("TrafficRecord t:" + t.toString());
		return t;
	}
	
	public void testSocketClient(URI uri) throws URISyntaxException {
		WebSocketClientConfig client = new WebSocketClientConfig(uri);
		client.sendMessage("hihihii");
	}
}

