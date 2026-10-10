package com.quickcare.backend.config;

import com.quickcare.backend.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    
    public WebSocketConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user"); 
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketAuthChannelInterceptor());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/websocket")
                    .setAllowedOrigins("http://localhost:4200", "https://quickcare-frontend.vercel.app")                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        
                        if (request instanceof ServletServerHttpRequest servletRequest) {
                            HttpServletRequest req = servletRequest.getServletRequest();
                            if (req.getCookies() != null) {
                                Arrays.stream(req.getCookies())
                                        .filter(c -> "access_token".equals(c.getName()))
                                        .findFirst()
                                        .ifPresent(cookie -> {
                                            try {
                                                
                                                String userId = jwtService.validarAccessToken(cookie.getValue()).getSubject();
                                                attributes.put("usuarioId", userId);
                                            } catch (Exception ignored) { }
                                        });
                            }
                        }
                        return true;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
                })
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        
                        String usuarioId = (String) attributes.get("usuarioId");
                        if (usuarioId != null) {
                            return () -> usuarioId; 
                        }
                        return null;
                    }
                })
                .withSockJS();
    }
}