package com.springKafka.liveDashboard.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {


		registry.addEndpoint("/live-temperature").setAllowedOrigins("*");
		registry.addEndpoint("/live-temperature").withSockJS();
		registry.addEndpoint("/live-msg").setAllowedOrigins("*");
		registry.addEndpoint("/live-msg").withSockJS();

	}



	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
	}

	
}
