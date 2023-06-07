package oam.security.model.resource.security;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import oam.security.service.CoreNetworkService;
import oam.security.service.NetworkServiceBase;

@Service
@Slf4j
public class SecurityProbeService {

	private String receiveFolder = "receive/";
	private String ansFolder = "ans/";
	private byte[] head = { (byte) 0xAA, (byte) 0xAB, (byte) 0xAC, (byte) 0xAD };
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private NetworkServiceBase networkService;
	@Value("${solaris.session.manage-service}")
	private String manage_service;
	
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
	
	public ArrayNode sendPicture(Socket socket) throws Exception{
		log.info("準備傳送!!!");
		ArrayNode send = objectMapper.createArrayNode();  //存放 傳送圖片的log
        String imagesDirectory = ansFolder; // 要傳送的圖片目錄
        File imagesFolder = new File(imagesDirectory);
        File[] imageFiles = imagesFolder.listFiles();
		// 建立輸出串流，用於發送圖片到DN
        OutputStream imageOutputStream = socket.getOutputStream();
        
        for (File imageFile : imageFiles) {
        	ObjectNode sendLog = objectMapper.createObjectNode();
        	System.out.println("\n傳送" + imagesDirectory + imageFile.getName());
        	sendLog.put("FileName", imageFile.getName());
        	//這是每一張圖片的串流
        	InputStream inputStreamImage = new FileInputStream(imagesDirectory + imageFile.getName());
        	 // 建立緩衝區
            byte[] buffer = new byte[1];
            int bytesRead;
            //發送標頭檔
		    for(int i = 0; i < head.length; i++) {
		    	if(i == head.length - 1) {
		    		//最後一個標頭檔依據檔名決定
		    		System.out.println("head[i]:" + chooseLastHead(imageFile.getName()));
		    		imageOutputStream.write(chooseLastHead(imageFile.getName()));
		    	}else {
		    		System.out.println("head[i]:" + head[i]);
		    		imageOutputStream.write(head[i]);
		    	}
		    }
		    sendLog.put("FileSize", imageFile.length() + " Bytes");
		    sendLog.put("StartTime", getNowTime());
            // 發送圖片
            while ((bytesRead = inputStreamImage.read(buffer)) != -1) {
//            	System.out.print(bytesRead + ", ");
                imageOutputStream.write(buffer, 0, bytesRead);
            }
            //剛剛沒傳送完的傳送出去
            imageOutputStream.flush();
            sendLog.put("EndTime", getNowTime());
            //關閉要讀出單張圖片的串流
            inputStreamImage.close();
            send.add(sendLog);
        }
        
        imageOutputStream.write(head[0]);
        imageOutputStream.write(head[1]);
        imageOutputStream.write(head[2]);
        imageOutputStream.write((byte)0x11);
        imageOutputStream.flush();
        
        System.out.println("圖片發送完成");
        // 關閉輸出串流和Socket連接
//        imageOutputStream.close();  //這裡關了會有問題
        return send;
	}
	
	public ArrayNode reveicePicture(Socket socket) throws Exception {
		InputStream inputStream = socket.getInputStream();
		//先建立資料夾 如果資料夾存在就清空receive資料夾
		File receivedirectory = new File(receiveFolder);
		if (!receivedirectory.exists()) {
            // 資料夾不存在，建立資料夾
            boolean created = receivedirectory.mkdirs();
            if (created) {
                System.out.println("資料夾已成功建立");
            } else {
                System.out.println("無法建立資料夾");
            }
        } else {
            System.out.println("資料夾已存在");
        }
		FileUtils.cleanDirectory(receivedirectory);
		ArrayNode receive = objectMapper.createArrayNode();  //存放 接收圖片的log
		
		//計算現在是哪一張圖片
        int countPicture = 0;
         // 接收圖片的緩衝區
        byte []buffer = new byte[1];
        byte []imageTmp = new byte[4];
        //計算現在是要檢查head的哪一個
        int countHead = 0;
        //是否為圖片資料
        boolean isImageData = false;
        // 建立輸出串流，儲存客戶端傳來的圖片 這是每一張圖片的串流
        OutputStream outputStream = null;
        String fileName = "";
//        outputStream = new FileOutputStream("image" + String.valueOf(countPicture) + ".jpg");
        // 接收圖片數據
        int bytesRead;
        ObjectNode fileLog = null;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
//        	System.out.print(bytesRead+",");        	
        	if(countHead == 3) { //準備比序號3 (第四個)
        		if(compareLastHead(buffer[0])) { //有比對成功(10張圖片的標頭)
        			System.out.println("header比對完畢 開啟下一張圖片串流");
        			if(outputStream != null && fileLog != null) {
        				//關閉剛剛的圖片串流
        				outputStream.close();
        				fileLog.put("EndTime", getNowTime());  //紀錄傳送結束時間
        				receive.add(fileLog);
        				fileLog = objectMapper.createObjectNode();//用一個新的
        			}
        			fileName = getFileNameByHead(buffer[0]);
        			//標頭檔Index歸零
    				countHead = 0;
    				//暫存歸零
    				imageTmp = new byte[4];
    				//圖片Index +1
    				countPicture++;
    				//開啟新圖片的串流
    				outputStream = new FileOutputStream(receiveFolder + fileName);
    				System.out.println("開啟" + receiveFolder + fileName + " 圖片串流");
    				if(fileLog == null) {
    					fileLog = objectMapper.createObjectNode();
    				}
    				fileLog.put("FileName", fileName);
    				fileLog.put("StartTime", getNowTime());//紀錄傳送開始時間
    				isImageData = true;
    				continue;  //跳出去 不要往下比 因為這個還是標頭不能寫入圖片檔案
        		}else if(buffer[0] == (byte)0x11){  //如果前三個都對 最後一個是0x11就代表全部結束
        			System.out.println("Client...收到結束標記");
        			isImageData = false;
        			//關閉串流
        	        inputStream.close();
        	        fileLog.put("EndTime", getNowTime());  //紀錄傳送結束時間
    				receive.add(fileLog);
        	        socket.close();
        			break;
        		}
        	}else if(buffer[0] == head[countHead]) {  //直接寫死第0個 因為也只會有一個
//        		System.out.println("bytesRead:" + bytesRead+" 比對到header" + String.valueOf(countHead));
        		if(countHead == 0) {
//        			System.out.println("用新的imageTmp");
        			imageTmp = new byte[4];
        		}
        		imageTmp[countHead] = buffer[0];
        		isImageData = false;
        		countHead++;//已經比對完成幾個
        	}else {
	    		//歸零 下次從第一個開始看 (表示圖片本來就是這個資料)
	    		if(countHead != 0) {
	    			countHead = 0;
		    		isImageData = true;
		    		for(int i = 0; i < imageTmp.length; i++) {
		    			if(imageTmp[i] != 0) {  //剛剛懷疑為標頭檔的資料要寫回來(因為知道標頭檔是甚麼 可以直接寫 !=0)
//		    				System.out.print("寫回標頭檔資料"+imageTmp[i]+",");
		    				outputStream.write(imageTmp[i]);//如果這裡出現null就是串流最一開始的標頭檔案有問題
		    			}
		    		}
		    		imageTmp = new byte[4];
	    		}
        	}
        	
			if(isImageData) {
				//寫入圖片
//				System.out.print(bytesRead+",");
	    		outputStream.write(buffer, 0, bytesRead);
			}
//        	outputStream.write(bytesRead);//這行是for沒有標頭檔時測試用
//        	outputStream.write(buffer, 0, bytesRead);//這行是for沒有標頭檔時測試用
        }
        outputStream.close();
        System.out.println("圖片接收完成");
        return receive;
	}
	
	public JsonNode checkImage() throws Exception {
		String ansDirectory = ansFolder; // 正確答案圖片目錄
        File ansFolder = new File(ansDirectory);
        File[] ansFiles = ansFolder.listFiles();
        String receiveDirectory = receiveFolder; // 接收到的圖片目錄
        File receiveFolder = new File(receiveDirectory);
        File[] receiveFiles = receiveFolder.listFiles();
        boolean []haveCheckAns = new boolean[10];  //這裡要寫10張圖片 免得有圖片沒傳來也被說OK
        ObjectNode check = objectMapper.createObjectNode();
        ArrayNode result = objectMapper.createArrayNode();
        //先預設都沒有查過
        System.out.println("預設為全部false");
        for(int i = 0; i < haveCheckAns.length; i++) {
        	haveCheckAns[i] = false;
        	System.out.print(haveCheckAns[i] + ", ");
        }
        
        for (File receiveFile : receiveFiles) {  //拿收到的去跟正確答案比較
        	String receiveFileName = receiveDirectory + receiveFile.getName();
        	BufferedImage receiveImage = ImageIO.read(new File(receiveFileName));
        	for(File ansFile : ansFiles) {
        		if(ansFile.getName().equals(receiveFile.getName())) {//在and和receive資料夾 找相同檔名的進行比較
        			String ansFileName = ansDirectory + ansFile.getName();
        			BufferedImage ansImage = ImageIO.read(new File(ansFileName));
            		boolean isEqual = true;
            		//進行比較
            		for (int x = 0; x < ansImage.getWidth(); x++) {
            		    for (int y = 0; y < receiveImage.getHeight(); y++) {
            		        if (ansImage.getRGB(x, y) != receiveImage.getRGB(x, y)) {
            		        	//有一個點不一樣 就表示不是這張圖
            		        	isEqual = false;
            		            break;
            		        }
            		    }
            		    if(isEqual) {
            		    	int index = getImageFileNameIndex(receiveFile.getName());
            		    	haveCheckAns[index] = true;
            		    }else {
            		    	break;
            		    }
            		}
        		}
        		
        	}
        }
        //先預設都沒有查過
        System.out.println("\n比對結果");
        for(int i = 0; i < haveCheckAns.length; i++) {
        	ObjectNode compareResult = objectMapper.createObjectNode();
        	compareResult.put("compare", haveCheckAns[i]);
        	compareResult.put("FileName", getImageFileNameIndex(i));
        	result.add(compareResult);  //把檢測結果加入array
        	System.out.print(haveCheckAns[i] + ", ");
        }
        check.put("Role", "Probe");
        check.set("content", result);
        return check;
	}
	
	public void notifyManagerImageComapreResult(JsonNode result) {
		String url = manage_service + "/v1/security/receiveImageCompareResult";
		networkService.postJsonInformation(url, result);
	}
	
	public String getNowTime() {
		ZonedDateTime zonedDateTime = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC"));
//        System.out.println(zonedDateTime.toLocalDateTime());
        return zonedDateTime.toLocalDateTime().toString();
	}
	
	public boolean compareLastHead(byte lastHead) {
		if(lastHead == (byte)0x99) {
			return true;
		}else if(lastHead == (byte)0x98) {
			return true;
		}else if(lastHead == (byte)0x97) {
			return true;
		}else if(lastHead == (byte)0x96) {
			return true;
		}else if(lastHead == (byte)0x95) {
			return true;
		}else if(lastHead == (byte)0x94) {
			return true;
		}else if(lastHead == (byte)0x93) {
			return true;
		}else if(lastHead == (byte)0x92) {
			return true;
		}else if(lastHead == (byte)0x91) {
			return true;
		}else if(lastHead == (byte)0x90) {
			return true;
		}else if(lastHead == (byte)0x89) {
			return true;
		}
		return false;
	}
	
	public String getFileNameByHead(byte lastHead) {
		if(lastHead == (byte)0x98) {
			return "Account.jpg";
		}else if(lastHead == (byte)0x97) {
			return "ChestXRay.jpg";
		}else if(lastHead == (byte)0x96) {
			return "covid19Positive.jpg";
		}else if(lastHead == (byte)0x95) {
			return "HandsXray.jpg";
		}else if(lastHead == (byte)0x94) {
			return "IdentityCard.jpg";
		}else if(lastHead == (byte)0x93) {
			return "Insurance.jpg";
		}else if(lastHead == (byte)0x92) {
			return "missile.jpg";
		}else if(lastHead == (byte)0x91) {
			return "Modem.jpg";
		}else if(lastHead == (byte)0x90) {
			return "Panasonic.jpg";
		}else if(lastHead == (byte)0x89) {
			return "Taiwan.jpg";
		}
		return "";
	}
	
	private int getImageFileNameIndex(String fileName) {//用在確認是否相同的布林陣列用
		int index = -1;
		switch(fileName) {//index是0開始
			case "Account.jpg":
				index = 0;
				break;
			case "ChestXRay.jpg":
				index = 1;
				break;
			case "covid19Positive.jpg":
				index = 2;
				break;
			case "HandsXray.jpg":
				index = 3;
				break;
			case "IdentityCard.jpg":
				index = 4;
				break;
			case "Insurance.jpg":
				index = 5;
				break;
			case "missile.jpg":
				index = 6;
				break;
			case "Modem.jpg":
				index = 7;
				break;
			case "Panasonic.jpg":
				index = 8;
				break;
			case "Taiwan.jpg":
				index = 9;
				break;
		}
		return index;
	}
	
	private String getImageFileNameIndex(int index) {
		String fileName = "";
		switch(index) {//index是0開始
			case 0:
				fileName = "Account.jpg";
				break;
			case 1:
				fileName = "ChestXRay.jpg";
				break;
			case 2:
				fileName = "covid19Positive.jpg";
				break;
			case 3:
				fileName = "HandsXray.jpg";
				break;
			case 4:
				fileName = "IdentityCard.jpg";
				break;
			case 5:
				fileName = "Insurance.jpg";
				break;
			case 6:
				fileName = "missile.jpg";
				break;
			case 7:
				fileName = "Modem.jpg";
				break;
			case 8:
				fileName = "Panasonic.jpg";
				break;
			case 9:
				fileName = "Taiwan.jpg";
				break;
		}
		return fileName;
	}
	
	private byte chooseLastHead(String fileName) {
		byte lastHead = (byte)0xAD;
		switch(fileName) {
			case "Account.jpg":
				lastHead = (byte)0x98;
				break;
			case "ChestXRay.jpg":
				lastHead = (byte)0x97;
				break;
			case "covid19Positive.jpg":
				lastHead = (byte)0x96;
				break;
			case "HandsXray.jpg":
				lastHead = (byte)0x95;
				break;
			case "IdentityCard.jpg":
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
