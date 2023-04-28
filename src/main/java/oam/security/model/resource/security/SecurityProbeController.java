package oam.security.model.resource.security;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RequestMapping("/v1/security")
@RestController
@Slf4j
public class SecurityProbeController {
	
	@Autowired
	private SecurityProbeService securityProbeService;
	
	@GetMapping("/testSocketBindInterface")
	@ResponseStatus(HttpStatus.OK)
	private void testSocketBindInterface() throws Exception {
		log.info("Test Socket client");
		 // 綁定到網卡上的IP地址
		String ueIp = securityProbeService.getUeransimProbeIp();
        InetAddress inetAddress = InetAddress.getByName(ueIp);
        int port = 8888;
        Socket socket = new Socket(inetAddress, port);
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

}
