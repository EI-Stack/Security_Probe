package oam.security.model.resource.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExampleContextService {
	
	@Autowired
	private ObjectMapper om;
	
	public JsonNode getUeExampleForWebContext_community() {
		try {
			return om.readTree(ExampleContextString.CREATE_UE_JSON_STRING_FOR_WEB_COMMUNITY);
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public JsonNode getUeExampleForWebContext_business() {
		try {
			return om.readTree(ExampleContextString.CREATE_UE_JSON_STRING_FOR_WEB_BUSINESS);
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public JsonNode getGnbExampleForApi() {
		try {
			return om.readTree(ExampleContextString.CREATE_GNB_FOR_API);
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public JsonNode getUeExampleForApi() {
		try {
			return om.readTree(ExampleContextString.CREATE_UE_FOR_API);
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}
	

}
