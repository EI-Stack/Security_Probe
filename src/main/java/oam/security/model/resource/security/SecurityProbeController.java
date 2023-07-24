package oam.security.model.resource.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@RequestMapping("/v1/security")
@RestController
@Slf4j
public class SecurityProbeController {
	
	@Autowired
	private SecurityProbeService securityProbeService;
	
	@Value("${solaris.session.dn-service}")
	private String dn_service;
	@Value("${solaris.session.dn-socketPort}")
	private int dn_socketPort;
	@Value("${solaris.session.manage-service}")
	private String manage_service;
	@Autowired
	private ObjectMapper objectMapper;
	
	private String ansFolder = "ans/";
	
	private byte[] head = { (byte) 0xAA, (byte) 0xAB, (byte) 0xAC, (byte) 0xAD };
	
	@GetMapping("/testSocketBindInterface")
	@ResponseStatus(HttpStatus.OK)
	private void testSocketBindInterface() throws Exception {
		log.info("Test Socket client");
		 // 綁定到網卡上的IP地址
		String ueIp = securityProbeService.getUeransimProbeIp();
        InetAddress inetAddress = InetAddress.getByName(ueIp);

        String serverAddress = "60.251.156.214"; // 伺服器地址
        int serverPort = 131; // 伺服器端口號
        Socket socket = new Socket(serverAddress, serverPort, inetAddress, 0);
        System.out.println("Connected to server on " + socket.getRemoteSocketAddress());

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        out.println("Hello, server!");

        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("Received from server: " + line);
            out.println("Client: " + line);
            if(line.startsWith("The end")) {
            	log.info("Socket end!!!");
            	out.println("The end");
            	break;
            }
        }

        socket.close();
        System.out.println("Connection closed");
	}
	
	@PostMapping("/transferImage")
	@ResponseStatus(HttpStatus.OK)
	private JsonNode transferImage(@RequestBody JsonNode iperf) throws Exception {
		//與DN端建立socket
		//先找出自己的網卡IP
		String ueIp = securityProbeService.getUeransimProbeIp();//(上214及正式環境用)
        InetAddress inetAddress = InetAddress.getByName(ueIp);
        int socketPort = iperf.get("socketPort").asInt();
        log.info("DN socket nodeport:" + String.valueOf(socketPort));
        String probe_id = iperf.get("probe_id").asText();
		
        //找出server
//        String iperfTimeStamp = content.get("iperf3").get("timestamp").asText();
//        String serverAddress = dn_service + "-" + iperfTimeStamp; // DN的server(正式用)
        String serverAddress = dn_service; // DN的server(單純上214測試用)
//        String serverAddress = "localhost"; // DN的server(本機測試用)
        
        //進行socket連線
        //serverAddress 是由外部指定，socketNodePort是管理程式傳過來的(因為是管理程式決定nodePort的)
        log.info("serverAddress:" + serverAddress + "  socketPort:" + String.valueOf(socketPort));
        log.info("inetAddress:" + inetAddress.toString());
        Socket socket = new Socket(serverAddress, socketPort, inetAddress, 0);//(單純上214測試用)
//        Socket socket = new Socket(serverAddress, 8888);//(本機測試用)
        System.out.println("Connected to server on " + socket.getRemoteSocketAddress());
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("Role", "Probe");//先確定腳色
		ObjectNode receiveData = objectMapper.createObjectNode();//存放 接收圖片的log
		ObjectNode sendData = objectMapper.createObjectNode();//存放 傳送圖片的log
        
        //建立輸出串流，用於讀取圖片檔案
        ArrayNode send = securityProbeService.sendPicture(socket);
        sendData.set("send_log", send);
        
        // 建立輸入串流，用於讀取圖片檔案
        ArrayNode receive = securityProbeService.reveicePicture(socket);
        receiveData.set("receive_log", receive);
        
        //檢查圖片
        JsonNode compareResult = securityProbeService.checkImage();
        ((ObjectNode)compareResult).put("probe_id", probe_id);
        receiveData.set("receive_compare", compareResult);
        //通知管理程式檢查圖片的結果
        securityProbeService.notifyManagerImageComapreResult(compareResult);
        
        result.set("receive", receiveData);
        result.set("send", sendData);
        
        return result;
	}
	
	

	private byte chooseLastHead(String fileName) {
		byte lastHead = (byte)0xAD;
		switch(fileName) {
			case "Account.jpg":
				lastHead = (byte)0x98;
				break;
			case "Chest X-Ray.jpg":
				lastHead = (byte)0x97;
				break;
			case "covid19-Positive.jpg":
				lastHead = (byte)0x96;
				break;
			case "Hands X ray.jpg":
				lastHead = (byte)0x95;
				break;
			case "Identity Card.jpg":
				lastHead = (byte)0x94;
				break;
			case "Insurance.jpg":
				lastHead = (byte)0x93;
				break;
			case "missile.jpg":
				lastHead = (byte)0x92;
				break;
			case "Modem.jpg":
				lastHead = (byte)0x91;
				break;
			case "Panasonic.jpg":
				lastHead = (byte)0x90;
				break;
			case "Taiwan.jpg":
				lastHead = (byte)0x89;
				break;
		}
		return lastHead;
	}
	

}
