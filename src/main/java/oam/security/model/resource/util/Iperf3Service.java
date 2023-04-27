package oam.security.model.resource.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import oam.security.model.resource.gnb.GnbService;

@Service
@Slf4j
public class Iperf3Service {
	
	@Autowired 
	private CommandService commandService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	@Autowired
	private GnbService gnbService;
	@Value("${solaris.session.oam_namespace}")
	private String oam_namespace;
	@Autowired
	private ObjectMapper objectMapper;
	
	public JsonNode createIperf3Service(Session s, String fileFolder) throws InterruptedException {
		//寫yaml並建立
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        ArrayList<String> exampleContent = commandService.readFile(s, fileBasePath + "iperf3.yaml"); 
        String iperf3FileName = "iperf3_" + timestamp + ".yaml";
		writeIperf3ServierYaml(s, exampleContent, timestamp, fileFolder, iperf3FileName);
		
		//先處理iperf3
		//看IP好了沒		
		boolean checkPodStatus = false;
        int ipPosition = 0;
		String lineData = "";
        String title = "";
        String ip = "";
        String command = " kubectl get pods -o wide ";
        if(!oam_namespace.trim().equals("")) {
        	command += " -n " + oam_namespace;
        }
        command += " | grep -E \'IP|" + timestamp+ "\'";
		while(!checkPodStatus) {//檢查pod的狀態 只要不是ContainerCreating 就OK
			checkPodStatus = gnbService.checkPodStatus(s, timestamp);
			Thread.sleep(5000);//給K8S時間建立
		}
		Thread.sleep(10000);//給K8S時間建立
		ArrayList<String> result = commandService.execCommandAndGetResult(s, command, false);
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
		log.info("UeranService=====Now iperf3 pod ip is:" + ip);
		
		JsonNode iperf3Data = objectMapper.createObjectNode();
		((ObjectNode)iperf3Data).put("timestamp", timestamp);
		((ObjectNode)iperf3Data).put("ip", ip);
		
		return iperf3Data;
	}
	
	public void writeIperf3ServierYaml(Session s, ArrayList<String> exampleContent, 
			String timestamp, String filePath, String fileName) {
		log.info("Write iperf3 server yaml");
		
		exampleContent.add(3, exampleContent.get(3) + "-" + timestamp);
		exampleContent.remove(4);
		exampleContent.add(6, exampleContent.get(6) + "-" + timestamp);
		exampleContent.remove(7);
		exampleContent.add(11, exampleContent.get(11) + "-" + timestamp);
		exampleContent.remove(12);
		exampleContent.add(15, exampleContent.get(15) + "-" + timestamp);
		exampleContent.remove(16);
		exampleContent.add(24, exampleContent.get(24) + "-" + timestamp);
		exampleContent.remove(25);
		exampleContent.add(41, exampleContent.get(41) + "-" + timestamp);
		exampleContent.remove(42);
		exampleContent.add(45, exampleContent.get(45) + "-" + timestamp);
		exampleContent.remove(46);
		
		commandService.generateYamlFile(s, exampleContent, filePath, fileName);
		commandService.executeUeYaml(s, filePath, fileName, false);
		log.info("exampleContent:" + exampleContent);
		log.info("filePath:" + filePath);
		log.info("fileName:" + fileName);
		log.info("Iperf3Service=====iperf3 server OK");
	}

}
