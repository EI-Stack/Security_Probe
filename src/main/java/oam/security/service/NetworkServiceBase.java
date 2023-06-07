package oam.security.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import oam.security.exception.base.ExceptionBase;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.*;


@Slf4j
@Service
public class NetworkServiceBase
{
	@Autowired
	public RestTemplate		restTemplate;
	@Autowired
	private ObjectMapper	objectMapper;

	public HttpHeaders		httpHeaders		= null;
	public HttpHeaders		textHttpHeaders	= null;
	public String			networkUrl		= null;
	@Value("${solaris.free5gc-version}")
	private String free5gcVersion;
	
    private final String IMAGE = "image";
    private final String CONTAINER = "container";
    private final String NAMESPACE = "namespace";
    
    private final String query_cpuNowRate = "CPU_NOW_RATE";
    private final String query_cpuAllRate = "CPU_ALL_RATE";
    private final String query_memoryNowRate = "MEMORY_NOW_RATE";
    private final String query_memoryAllRate = "MEMORY_ALL_RATE";

	public RestTemplate getRestTemplate()
	{
		return this.restTemplate;
	}

	public ObjectNode getJsonNode(final String path) throws ExceptionBase
	{
		log.debug("\t GET URL=[{}]", path);
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders = new HttpHeaders();
			this.httpHeaders.add("token", "admin");
		}
		final HttpEntity<ObjectNode> requestEntity = new HttpEntity<>(this.httpHeaders);
		ResponseEntity<JsonNode> response = null;
		JsonNode responseJson = null;
		try
		{
			response = this.restTemplate.exchange(path, HttpMethod.GET, requestEntity, JsonNode.class);
			log.debug("\t GET StatusCode={}", response.getStatusCode());
			if (!(response.getStatusCode().equals(HttpStatus.OK))) throw new ExceptionBase(400, "Fetching resource is failed.");

			final JsonNode bodyJson = response.getBody();
			final String errorCode = bodyJson.path("error_code").asText();
			final String errorMessage = bodyJson.path("error_string").asText();
			if (!errorCode.equalsIgnoreCase("00000") || !errorMessage.equalsIgnoreCase("success")) throw new ExceptionBase(Integer.parseInt(errorCode), errorMessage);

			responseJson = bodyJson.path("content");
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}

		return (ObjectNode) responseJson;
	}
	
	public JsonNode getJsonInformation(String path) throws ExceptionBase {
		log.debug("\t GET URL=[{}]", path);
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders = new HttpHeaders();
			this.httpHeaders.add("token", "admin");
		}
		final HttpEntity<ObjectNode> requestEntity = new HttpEntity<>(this.httpHeaders);
		ResponseEntity<JsonNode> response = null;
		JsonNode responseJson = null;
		try
		{
			response = this.restTemplate.exchange(path, HttpMethod.GET, requestEntity, JsonNode.class);
			log.debug("\t GET StatusCode={}", response.getStatusCode());
			if (!(response.getStatusCode().equals(HttpStatus.OK))) throw new ExceptionBase(400, "Fetching resource is failed.");

			final JsonNode bodyJson = response.getBody();

			responseJson = bodyJson;
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}

		return (JsonNode) responseJson;
	}

	public JsonNode postJsonNode(final String path, final JsonNode requestNode) throws ExceptionBase
	{
		log.debug("\t POST URL=[{}]", path);
		this.httpHeaders = new HttpHeaders();
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders.add("token", "admin");
		}else {
			this.httpHeaders.add("Content-Type", "application/json");
		}
		final HttpEntity<JsonNode> requestEntity = new HttpEntity<>(requestNode, this.httpHeaders);
		JsonNode node = null;
		try
		{
			final ResponseEntity<JsonNode> response = this.restTemplate.exchange(path, HttpMethod.POST, requestEntity, JsonNode.class);
			// log.debug("\t[ODL] [getOnlineDeviceNames] code={}", response.getStatusCode());
			// if (!(response.getStatusCode().equals(HttpStatus.CREATED) || response.getStatusCode().equals(HttpStatus.NO_CONTENT))) throw new ExceptionBase(400, "Creating resource is failed.");
			node = response.getBody();
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}

		return node;
	}

	public JsonNode create(final String path, final JsonNode requestNode) throws ExceptionBase, UnsupportedEncodingException
	{
		log.debug("\t POST URL=[{}]", path);
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders = new HttpHeaders();
			this.httpHeaders.add("token", "admin");
		}
		final HttpEntity<JsonNode> requestEntity = new HttpEntity<>(requestNode, this.httpHeaders);
		JsonNode responseJson = null;
		try
		{
			final ResponseEntity<JsonNode> response = this.restTemplate.exchange(path, HttpMethod.POST, requestEntity, JsonNode.class);
			log.debug("\t POST StatusCode={}", response.getStatusCode());
			if (!(response.getStatusCode().equals(HttpStatus.OK))) throw new ExceptionBase(400, "Creating resource is failed.");

			final JsonNode bodyJson = response.getBody();
			final String errorCode = bodyJson.path("error_code").asText();
			final String errorMessage = bodyJson.path("error_string").asText();
			if (!errorCode.equalsIgnoreCase("00000") || !errorMessage.equalsIgnoreCase("success")) throw new ExceptionBase(Integer.parseInt(errorCode), errorMessage);

			responseJson = bodyJson.path("content");
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}

		return responseJson;
	}

	public void modify(final String path, final JsonNode requestNode) throws ExceptionBase
	{
		final String url = this.networkUrl + path;
		log.debug("\t PUT URL=[{}]", url);
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders = new HttpHeaders();
			this.httpHeaders.add("token", "admin");
		}
		final HttpEntity<JsonNode> requestEntity = new HttpEntity<>(requestNode, this.httpHeaders);
		try
		{
			final ResponseEntity<JsonNode> response = this.restTemplate.exchange(url, HttpMethod.PUT, requestEntity, JsonNode.class);
			log.debug("PUT StatusCode =[{}]", response.getStatusCode().toString());
			if (!response.getStatusCode().equals(HttpStatus.NO_CONTENT)) throw new ExceptionBase(400, "Modifing resource is failed.");

			final JsonNode bodyJson = response.getBody();
			final String errorCode = bodyJson.path("error_code").asText();
			final String errorMessage = bodyJson.path("error_string").asText();
			if (!errorCode.equalsIgnoreCase("00000") || !errorMessage.equalsIgnoreCase("success")) throw new ExceptionBase(Integer.parseInt(errorCode), errorMessage);
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}
	}

	public void delete(final String path) throws ExceptionBase
	{
		final String url = path;
		log.debug("\t DELETE URL=[{}]", url);
		if(free5gcVersion.equals("Business")) {
			this.httpHeaders = new HttpHeaders();
			this.httpHeaders.add("token", "admin");
		}
		final HttpEntity<JsonNode> requestEntity = new HttpEntity<>(this.httpHeaders);
		try
		{
			final ResponseEntity<JsonNode> response = this.restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, JsonNode.class);
			log.debug("\t DELETE StatusCode={}", response.getStatusCode());
			if (!(response.getStatusCode().equals(HttpStatus.NO_CONTENT))) throw new ExceptionBase(400, "Delete resource is failed.");
		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}
	}

	protected void handleErrorMessage(final HttpClientErrorException e) throws ExceptionBase
	{
		e.printStackTrace();

		final String errorMessage = MessageFormat.format("Connecting to outer server is failed. Message：{0}", e.getMessage());
		log.error("\t[ErrorHandler] Status Code=[{}]", e.getStatusCode());
		log.error("\t[ErrorHandler] response.getBody() ={}", e.getResponseBodyAsString());
		log.error("\t[ErrorHandler] Error Message: {}", errorMessage);

		JsonNode emNode = null;
		try
		{
			emNode = this.objectMapper.readTree(e.getResponseBodyAsString()).path("message");
		} catch (final Exception e2)
		{
			throw new ExceptionBase(400, errorMessage);
		}

		throw new ExceptionBase(e.getStatusCode().value(), emNode.asText());
	}
	
	public JsonNode getJsonNode_ForNormal(final String path) throws ExceptionBase
	{
		log.debug("\t GET URL=[{}]", path);
		final HttpEntity<ObjectNode> requestEntity = new HttpEntity<>(this.httpHeaders);
		ResponseEntity<JsonNode> response = null;
		JsonNode responseJson = null;
		try
		{
			response = this.restTemplate.exchange(path, HttpMethod.GET, requestEntity, JsonNode.class);
			log.debug("\t GET StatusCode={}", response.getStatusCode());
			responseJson = response.getBody();

		} catch (final HttpClientErrorException ex)
		{
			handleErrorMessage(ex);
		}
		return responseJson;
	}
	
	public String httpRequest(String url , HttpMethod method, Map<String,String> header, Object map) throws Exception {
		log.info("HttpRequest url:" + url);
        HttpURLConnection con = getConnection(url, header);

        if ("POST".equals(method) || "PUT".equals(method)) {
            con.setRequestMethod(method.toString());
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(map.toString());

            wr.flush();
            wr.close();
        }

        if ("GET".equals(method) || "DELETE".equals(method)){
            con.setRequestMethod(method.toString());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null){
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }	
	
	public HttpURLConnection getConnection(String url , Map<String,String> header) throws IOException {

        URL obj = new URL(url);
        HttpURLConnection headers  = (HttpURLConnection)obj.openConnection();
        if(header != null) {
        	for (Map.Entry<String,String> map : header.entrySet()){
                headers.setRequestProperty(map.getKey() , map.getValue());
            }
        }
        return headers;
    }
	
	public String getPrometheusFilter(String type, Map<String, String> filter) {
		log.info("filter:" + filter);
        List<String> whereList2 = new ArrayList<>();
        for (Map.Entry<String, String> entry : filter.entrySet()) {
            String key = entry.getKey();
            String value = "\"" + entry.getValue() + "\"";
            
            if (key.contains(IMAGE)){
                String image = "image!=" + value;
                whereList2.add(image);
            }else if ((key.contains(CONTAINER))){
                String container = "container!=" + value;
                whereList2.add(container);
            }else if ((key.contains(NAMESPACE))){
                String namespace = "namespace=" + value;
                whereList2.add(namespace);
            }
        }
        
        log.info("whereList2:" + whereList2);

        String response = "";
        switch(type) {
	        case query_cpuNowRate:
	        	response = "sum(container_memory_usage_bytes{" + StringUtils.join(whereList2, ",") + "})";
	        	break;
	        case query_cpuAllRate:
	        	response = "sum(node_memory_MemTotal_bytes)-sum(container_memory_usage_bytes{" + StringUtils.join(whereList2, ",") + "})";
	        	break;
	        case query_memoryNowRate:
	        	response = "sum(rate(container_cpu_usage_seconds_total{" + StringUtils.join(whereList2, ",") + "}[5m]))";
	        	break;
	        case query_memoryAllRate:
	        	response = "sum(machine_cpu_cores)-sum(rate(container_cpu_usage_seconds_total{" + StringUtils.join(whereList2, ",") +"}[5m]))";
	        	break;
        }
        
        log.info("response:" + response);
        return response;
    }
	
	/**
	 * 用來呼叫探針用的方法
	 * @param url
	 * @param body
	 * @return
	 */
	public JsonNode postJsonInformation(String url, JsonNode body){
		final HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, getHeaders());
		log.info("url:" + url);
		try {
			final ResponseEntity<JsonNode> responseEntity = this.restTemplate.exchange(url, HttpMethod.POST,
                    requestEntity, JsonNode.class);
			return responseEntity.getBody();
        } catch (final HttpStatusCodeException e) {
            e.printStackTrace();
        }
		return null;
	}
	/**
	 * 探針的header加入Bearer token
	 * @return
	 */
	private HttpHeaders getHeaders() {//加入Bearer token
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJ1c2VybmFtZSI6IlVTRVJfQURNSU4iLCJyb2xlIjoiUk9MRV9BRE1JTiIsImlhdCI6MTU1ODg4NjQwMCwiZXhwIjoxODc0MjQ2NDAwfQ.vbkgcdaGE61jJL-Jrf4hMAek4lDyGKtRkmDw0gjuvTvYEPK1rvfZiU0xn5pTYvlCrxPKBAnU2GTmo1EO0tIZiA");
        return httpHeaders;
    }
}