package oam.security.model.resource.postgres;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import com.vladmihalcea.hibernate.type.json.JsonType;

import lombok.Data;

@Entity
@Data
@Table(name = "TrafficRecord", schema = "public")  //直接把schema寫在這邊 也會自己生成
//@Value("${spring.jpa.properties.hibernate.default_schema}")
@TypeDefs({@TypeDef(name = "json", typeClass = JsonType.class), @TypeDef(name = "json-node", typeClass = JsonNodeBinaryType.class)})
public class TrafficRecord {
	
	@Id
	@Column(columnDefinition = "text")
	private String record_time;//事件時間
	
	@Column(columnDefinition = "bigint")
	private int record_month;//月
	
	@Column(columnDefinition = "bigint")
	private int record_day;//日
	
	@Column(columnDefinition = "bigint")
	private int record_hour;//時
	
	@Column(columnDefinition = "text")
	private String traffic;//流量
	
	@Column(columnDefinition = "text")  //來源IP
	private String sourceIp;//sourceIP
	
	@Column(columnDefinition = "text")  //目的地IP
	private String destIp;//destIP
	
	public TrafficRecord() {
		
	}
	
	public TrafficRecord(String record_time, int months, int days, int hours, String traffic) {
		this.record_time = record_time;
		this.record_month = months;
		this.record_day = days;
		this.record_hour = hours;
		this.traffic = traffic;
	}

	@Override
	public String toString() {
		return "TrafficRecord [record_time=" + record_time + ", record_month=" + record_month + ", record_day="
				+ record_day + ", record_hour=" + record_hour + ", traffic=" + traffic + ", sourceIP=" + sourceIp
				+ ", destIp=" + destIp + "]";
	}
	
	public JsonNode toJson() {
		ObjectMapper om = new ObjectMapper();
		ObjectNode n = om.createObjectNode();
		n.put("record_time", this.record_time);
		n.put("months", this.record_month);
		n.put("days", this.record_day);
		n.put("hours", this.record_hour);
		n.put("traffic", this.traffic);
		return n;
	}

	
}
