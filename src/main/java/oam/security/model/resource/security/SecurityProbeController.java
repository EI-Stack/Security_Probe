package oam.security.model.resource.security;

import java.io.BufferedReader;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

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
	private void transferImage(/*content 附帶標註*/) throws Exception {
		//與DN端建立socket
		//先找出自己的網卡IP
//		String ueIp = securityProbeService.getUeransimProbeIp();//(上214及正式環境用)
//		String ueIp = "192.168.50.209";//(本機測試用)
//        InetAddress inetAddress = InetAddress.getByName(ueIp);
		
        //找出server
//        String iperfTimeStamp = content.get("iperf3").get("timestamp").asText();
//        String serverAddress = dn_service + "-" + iperfTimeStamp; // DN的server(正式用)
//        String serverAddress = dn_service; // DN的server(單純上214測試用)
        String serverAddress = "localhost"; // DN的server(本機測試用)
        //進行socket連線
//        Socket socket = new Socket(serverAddress, dn_socketPort, inetAddress, 0);//(單純上214測試用)
        Socket socket = new Socket(serverAddress, dn_socketPort);//(本機測試用)
        System.out.println("Connected to server on " + socket.getRemoteSocketAddress());
        
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        
//        out.println("Hello!");
        
        // 建立輸入串流，用於讀取圖片檔案
        InputStream inputStream = new FileInputStream("image.jpg");
        
        // 建立緩衝區
        byte[] buffer = new byte[4096];
        int bytesRead;
        // 建立輸出串流，用於發送圖片
        OutputStream imageOutputStream = socket.getOutputStream();
        
        // 發送圖片
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            imageOutputStream.write(buffer, 0, bytesRead);
        }
        imageOutputStream.flush();
        System.out.println("圖片發送完成");
        
        // 關閉串流和Socket連接
        imageOutputStream.close();
        inputStream.close();
        out.close();
        socket.close();
        
        
	}

}
