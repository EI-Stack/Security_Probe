package oam.security.model.resource.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SecurityProbeService {

	//取得探針這台機器的IP
	public String getUeransimProbeIp() throws Exception{
		String targetHost = "uesimtun0";
		String targetIp = "";
		NetworkInterface ni = NetworkInterface.getByName(targetHost);
		if (ni != null) {
		    for (InterfaceAddress address : ni.getInterfaceAddresses()) {
		        InetAddress ip = address.getAddress();
		        if (ip != null && ip instanceof Inet4Address) {
		            log.info("Interface: " + ni.getName() + " - IP: " + ip.getHostAddress());
		            targetIp = ip.getHostAddress();
		        }
		    }
		}
//		targetIp = "192.168.50.209";
		return targetIp;
	}

}
