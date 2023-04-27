package oam.security.model.resource.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import oam.security.service.JSchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CommandService {
	
	@Autowired
	public JSchService jschService;
	@Value("${solaris.session.sudo-password}")
	private String session_pwd;
	
	
	public ArrayList<String> execCommandAndGetResult(Session s, String command, boolean isOriginalOutput){
		if(s == null) {
			s = jschService.getJschSession();
		}
		if(!session_pwd.trim().equals("") || session_pwd != null) {
			command = "echo " + session_pwd + " | sudo -S  "+ command;  
		}
		log.info("Commands is : " + command);
		ArrayList<String> result = execCommand(s, command, isOriginalOutput);
		return result;
	}
	
	public ArrayList<String> execCommand(Session session, String command, boolean isOriginalOutput){
		ArrayList<String> data = new ArrayList<>();
		String lineData;
		try {
			ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
	        channelExec.setCommand(command);
	        channelExec.setInputStream(null);
	        channelExec.setErrStream(System.err);
	        channelExec.getErrStream();
	        channelExec.connect();
	        InputStream in = channelExec.getInputStream();

	        BufferedReader br = new BufferedReader(new InputStreamReader(in));
	        
	        while((lineData = br.readLine()) != null) {
	        	if(!isOriginalOutput) {
	        		lineData = lineData.replaceAll("\\s+", ",");	
	        	}
	            data.add(lineData);
	        }
	        in.close();
	        channelExec.disconnect();
		}catch(Exception e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public void executeUeYaml(Session s, String fileBase ,String gnbFileName, boolean isOriginalOutput) {
		String command = " kubectl create -f " + fileBase +gnbFileName;
		execCommandAndGetResult(s, command, isOriginalOutput);
		System.out.println("executeUeYaml 執行K8S:" + command + " is ok");
	}
	
	public void executeGnbYaml(Session s, String fileBase, String gnbFileName, boolean isOriginalOutput) {
		String command = " kubectl create -f " + fileBase + gnbFileName;
		execCommandAndGetResult(s, command, isOriginalOutput);
		System.out.println("executeGnbYaml 執行K8S:" + command + " is ok");
	}
	
	public ArrayList<String> readFile(Session session, String filePath){
		ChannelSftp sftp = null;
		InputStream stream = null;
		ArrayList<String> content = new ArrayList<>();
		try {
			sftp = (ChannelSftp) session.openChannel("sftp");
			sftp.connect();//記得要加connect
			stream = sftp.get(filePath);
		    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
		    String line;
		    while ((line = br.readLine()) != null) {
//		        System.out.println(line);
		        content.add(line);
		    }
		    stream.close();
		    sftp.disconnect();
		    Thread.sleep(1000);
		}catch(Exception e) {
			e.printStackTrace();
		}
		return content;
	}
	
	public void generateYamlFile(Session s, ArrayList<String> exampleContent, String filePath, String fileName) {
		ChannelSftp sftp = null;
		OutputStream stream = null;        
		try {
			sftp = (ChannelSftp) s.openChannel("sftp");
			sftp.connect();//記得要加connect
			stream = sftp.put(filePath + fileName, ChannelSftp.OVERWRITE);
			BufferedWriter bw = new BufferedWriter(new PrintWriter(stream));
		    for(int i = 0; i < exampleContent.size(); i++) {
//		    	System.out.println(exampleContent.get(i));
		    	bw.write(exampleContent.get(i) + "\n");
		    }
		    bw.close();
		    stream.close();
		    sftp.disconnect();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void changeNameSpace(Session s, String namespace, boolean isOriginalOutput) {
		String command = " kubens " + namespace;
		execCommandAndGetResult(s, command, isOriginalOutput);
		System.out.println("切換namespace 執行K8S:" + command + " is ok");
	}

}
