package oam.security.model.resource.gnb;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.jcraft.jsch.JSchException;

import lombok.extern.slf4j.Slf4j;

@RequestMapping("/v1/gnb")
@RestController
@Slf4j
public class GnbController {
	
	@Autowired
	private GnbService gnbService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	
	@GetMapping("/gnbStatus")
	@ResponseStatus(HttpStatus.OK)
	private void getGnbStatus(){
		gnbService.getGnbStatus();
	}
	
	@GetMapping("/testLs")
	@ResponseStatus(HttpStatus.OK)
	private String ls(){
		return gnbService.testLs();
	}
	
	@PostMapping("/createGnb")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private String createGnb(@RequestBody JsonNode inf) throws JSchException {
		//傳入mcc(String) mnc(String) slice(sst(int).sd(String))
		//自動找出 amf ip.gnb自己的ip
		//回傳自己的IP
//		String fileBasePath = "/home/ubuntu/UeranAuto/";
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "/" + "UeranAuto" + timestamp; 
		return gnbService.createAnGnb(null, inf, fileFolder, "", "").toPrettyString();
	}
	

}
