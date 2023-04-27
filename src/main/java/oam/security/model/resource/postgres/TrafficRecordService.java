package oam.security.model.resource.postgres;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import oam.security.util.DateTimeUtil;

@Service
@Slf4j
public class TrafficRecordService {
	
	@Autowired
	private TrafficRecordRepository trafficRecordRepository;
	@Autowired
	private ObjectMapper objectMapper;
	
	public void saveToPostgres(TrafficRecord traffic) {
		log.info("Save TrafficRecord to postgres:" + traffic.toString());
		trafficRecordRepository.save(traffic);
	}
	
	public TrafficRecord trafficDataFilter(String list, String sourceIP, String destIP) throws JsonMappingException, JsonProcessingException {
		TrafficRecord t = new TrafficRecord();
		String traffic = "";
		JsonNode iperf3Data = objectMapper.readTree(list);
//		for(int i = 0; i < list.size(); i++) {
//			String [] lineData = list.get(i).split(",");
//			if(lineData[lineData.length-1].equals("sender")) {  //找結尾是sender的那一行
//				for(int index = 0; index < lineData.length; index++) {
//					if(lineData[index].contains("/sec")) { //有一秒傳多少的那一個 再加上前面那一個數字 就是流量
//						traffic = lineData[index - 1] + " " + lineData[index];
//						break;
//					}
//				}
//			}
//		}
		log.info("iperf3Data:" + iperf3Data.toPrettyString());
		traffic = iperf3Data.path("end").path("sum_received").path("bits_per_second").asText();
		ZonedDateTime zonedDateTime = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC"));
    	String now = DateTimeUtil.castMillsToDateString(zonedDateTime.toInstant().toEpochMilli());
    	
    	t.setRecord_time(now);
    	t.setTraffic(traffic);
    	t.setRecord_hour(zonedDateTime.getHour());
    	t.setRecord_day(zonedDateTime.getDayOfMonth());
    	t.setRecord_month(zonedDateTime.getMonthValue());
    	t.setSourceIp(sourceIP);
    	t.setDestIp(destIP);
    	
    	return t;
	}
	
	public double getAvgTrafficByHour(int hour) {
		//用小時當條件來取得資料
		List<TrafficRecord> recordList = trafficRecordRepository.findByHour(hour);
		double allTraffic = 0.0;
		int countNoTrafficData = 0;
		//計算平均
		for(int i = 0; i < recordList.size(); i++) {
			TrafficRecord record = recordList.get(i);
			double speedRate = turnTrafficData(record.getTraffic());
			System.out.println("speedRate:" + speedRate);
			if(speedRate == -1) {
				countNoTrafficData++;
			}else {
				allTraffic += speedRate;
			}
			System.out.println("allTraffic:" + allTraffic);
		}
		log.info("recordList.size():" + recordList.size() + " countNoTrafficData:" + countNoTrafficData);
		return allTraffic / (recordList.size() - countNoTrafficData);
	}
	
	public double turnTrafficData(String traffic) {//傳入資料庫的traffic那一欄
		String[] speed = traffic.split(" ");
		String unit = speed[speed.length - 1]; //抓最後一個 反正不是單位就是原來的數值
		unit = unit.replace("bits/sec", "").replace("bps", "");  //前面的for iperf3 後面的for 網站註冊API
		double speedRate = 0.0;
		switch(unit) {
			case "G":
			case "g":
				speedRate = Double.parseDouble(speed[0]) * 1000000000;
				break;
			case "M":
			case "m":
				speedRate = Double.parseDouble(speed[0]) * 1000000;
				break;
			case "K":
			case "k":
				speedRate = Double.parseDouble(speed[0]) * 1000;
				break;
			case "":
				return -1.0;//空資料不計算平均 等等要扣掉
			default:
				speedRate = Double.parseDouble(speed[0]) * 1;
				break;
		}
		return speedRate;
	}

}
