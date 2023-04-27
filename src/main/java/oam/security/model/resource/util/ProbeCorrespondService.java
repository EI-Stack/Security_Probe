package oam.security.model.resource.util;

import java.util.HashMap;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProbeCorrespondService {
	
	private HashMap<String, JsonNode> correspond =  new HashMap<>();
	
	public void addCorrespond(String timestamp, JsonNode corre) {
		correspond.put(timestamp, corre);
	}
	
	@Override
	public String toString() {
		return correspond.toString();
	}

}
/**
 * 用資料夾的timestamp做對應
 * {
 *   iperf3:{
 *     timestamp:20230330-092900,
 *     ip:192.168.1.1
 *   },
 *   gnb:{
 *     timestamp
 *     ip
 *   },
 *   ue:{
 *     timestamp
 *     ip
 *   }
 * }  
 * 
 */
