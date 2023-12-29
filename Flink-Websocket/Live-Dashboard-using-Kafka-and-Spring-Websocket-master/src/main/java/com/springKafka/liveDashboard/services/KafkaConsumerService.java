package com.springKafka.liveDashboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@Service
public class KafkaConsumerService{
	
	@Autowired
	SimpMessagingTemplate template;
	
	@KafkaListener(topics="${kafka.topic}")
	public void consume(@Payload String message) {
		if(isNumeric(message)) {
			template.convertAndSend("/topic/temperature", message);
		}
		else {



			JSONObject jsonObject = JSON.parseObject(message);
			String msgkey = jsonObject.getString("key");
			if (msgkey.equals("device_area"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-deviceArea", msg_dtl);



			}
			else if (msgkey.equals("device_count"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-deviceCount", msg_dtl);



			}

			else if (msgkey.equals("user_count"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-userCount", msg_dtl);



			}

			else if (msgkey.equals("order_window_count"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-orderWindowCount", msg_dtl);



			}

			else if (msgkey.equals("order_window_ttl_count"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-orderTtlWindowCount", msg_dtl);



			}

			else if (msgkey.equals("device_city"))
			{
				String msg_dtl  = jsonObject.getString("value");
				template.convertAndSend("/topic/live-msg-deviceCityCount", msg_dtl);



			}

			System.out.println(message);
		}
		
	}
	public  boolean isNumeric(String str)  
	{  
	  try  
	  {  
	    @SuppressWarnings("unused")
		double d = Double.parseDouble(str);  
	  }  
	  catch(NumberFormatException nfe)  
	  {  
	    return false;  
	  }  
	  return true;  
	}
	
}
