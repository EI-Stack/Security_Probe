package oam.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JSchService {
	
	@Value("${solaris.session.host}")
	private String host;
	@Value("${solaris.session.username}")
	private String username;
	@Value("${solaris.session.port}")
	private int port;
	@Value("${solaris.session.identity}")
	private String identityFile;
	@Value("${solaris.session.login-password}")
	private String pwd;

	private JSch jsch;
	private Session session;
	
	public Session getJschSession() {
		if(jsch != null && session != null) {
			return session;
		}
		try {
			if(jsch == null) {
				jsch = new JSch(); 
			}
			if(!identityFile.trim().equals("")) {  //看是否有key檔案
				log.info("identityFile:" + identityFile);
				jsch.addIdentity(identityFile);	
			}
			
			log.info("username:" + username + " host:" + host + " port:" + port);			
			session = jsch.getSession(username, host, port);
			if(!pwd.trim().equals("")) {  //密碼
				log.info("pwd:" + pwd);
				session.setPassword(pwd);
			}
			System.out.println("Session created");
			java.util.Properties config = new java.util.Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();
			System.out.println("Session connected....");
		}catch(Exception e) {
			e.printStackTrace();			
		}
		return session;
	}
	
	public void closeJschSession(Session session) {
		if(session.isConnected()) {
			session.disconnect();
		}
		jsch = null;
		session = null;
		System.out.println("Session has disConnected");
	}

}
