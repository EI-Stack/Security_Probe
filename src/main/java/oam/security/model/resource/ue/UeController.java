package oam.security.model.resource.ue;

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

@RequestMapping("/v1/ue")
@RestController
@Slf4j
public class UeController {
	
	@Autowired
	private UeService ueService;
	@Value("${solaris.session.file_base}")
	private String fileBasePath;
	
	@GetMapping("/ueStatus")
	@ResponseStatus(HttpStatus.OK)
	private void getUeStatus(){
		ueService.getUeStatus();
	}
	
	@PostMapping("/createUe")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	private boolean createGnb(@RequestBody JsonNode inf) throws JSchException {
//		String fileBasePath = "/home/ubuntu/UeranAuto/";
		String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String fileFolder = fileBasePath + "/" + "UeranAuto" + timestamp; 
		return ueService.createUe(null, inf, fileFolder);
	}

}
