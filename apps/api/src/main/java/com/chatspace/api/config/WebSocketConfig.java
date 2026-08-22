package com.chatspace.api.config;

import com.chatspace.api.realtime.RealtimeWebSocketHandlerDecoratorFactory;
import com.chatspace.api.realtime.StompChannelInterceptor;
import com.chatspace.api.realtime.StompDestinations;
import com.chatspace.api.realtime.UserIdHandshakeHandler;
import com.chatspace.api.realtime.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over WebSocket基盤の設定(リアルタイム通信機能定義書§3)。
 *
 * <p>{@code /ws}エンドポイント(SockJSフォールバックなし)、Spring標準シンプルブローカー(単一インスタンス前提。
 * RabbitMQ外部ブローカーリレーへの切替は水平スケール対応を実施しないことが確定しているため行わない)、 ハンドシェイク時JWT検証、SUBSCRIBE/SEND認可、キック時強制切断用の
 * セッション追跡を配線する。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  /** サーバー→クライアント/クライアント→サーバーとも10秒間隔(Renderのアイドルタイムアウト非固定という運用実態を踏まえた既定値)。 */
  private static final long HEARTBEAT_INTERVAL_MS = 10_000;

  /** STOMPフレームの最大サイズ(タイピング等の小さなペイロード想定、§9のサイズ上限)。 */
  private static final int MESSAGE_SIZE_LIMIT_BYTES = 8 * 1024;

  private final WebSocketAuthInterceptor webSocketAuthInterceptor;
  private final StompChannelInterceptor stompChannelInterceptor;
  private final RealtimeWebSocketHandlerDecoratorFactory decoratorFactory;
  private final String webOrigin;

  public WebSocketConfig(
      WebSocketAuthInterceptor webSocketAuthInterceptor,
      StompChannelInterceptor stompChannelInterceptor,
      RealtimeWebSocketHandlerDecoratorFactory decoratorFactory,
      @Value("${chatspace.web-origin}") String webOrigin) {
    this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    this.stompChannelInterceptor = stompChannelInterceptor;
    this.decoratorFactory = decoratorFactory;
    this.webOrigin = webOrigin;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint(StompDestinations.WEBSOCKET_ENDPOINT)
        .setHandshakeHandler(new UserIdHandshakeHandler())
        .addInterceptors(webSocketAuthInterceptor)
        .setAllowedOriginPatterns(webOrigin);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
        .setTaskScheduler(heartbeatTaskScheduler());
    registry.setApplicationDestinationPrefixes(StompDestinations.APP_PREFIX);
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompChannelInterceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.addDecoratorFactory(decoratorFactory);
    registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
  }

  @Bean
  public TaskScheduler heartbeatTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("stomp-heartbeat-");
    scheduler.initialize();
    return scheduler;
  }
}
