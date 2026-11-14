package com.quickcare.backend.config;

import com.quickcare.backend.jwt.AutenticacaoPorToken;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final AutenticacaoPorToken autenticacaoPorToken;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();

        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeat);
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                StompCommand comando = accessor.getCommand();

                if (comando == StompCommand.CONNECT) {
                    Long usuarioId = autenticacaoPorToken
                            .autenticarCabecalho(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
                            .map(AutenticacaoPorToken.Sessao::usuarioId)
                            .orElseThrow(() -> new MessageDeliveryException("Sessão expirada. Entre novamente."));
                    accessor.setUser(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));
                }
                if (comando == StompCommand.SEND) {
                    throw new MessageDeliveryException("Envio de mensagens não permitido.");
                }
                if (comando == StompCommand.SUBSCRIBE) {
                    String destino = accessor.getDestination();
                    if (accessor.getUser() == null || destino == null || !destino.startsWith("/user/queue/")) {
                        throw new MessageDeliveryException("Assinatura não permitida.");
                    }
                }
                return message;
            }
        });
    }

}
