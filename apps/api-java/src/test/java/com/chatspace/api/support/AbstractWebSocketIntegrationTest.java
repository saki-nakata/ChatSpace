package com.chatspace.api.support;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * STOMP over WebSocketの統合テスト基盤(実サーバー・実DB・実WebSocket接続)。
 *
 * <p>{@link AbstractIntegrationTest}(MockMvc、{@code WebEnvironment.MOCK})とは異なり、実際の
 * WebSocketハンドシェイクが必要なため{@code WebEnvironment.RANDOM_PORT}で起動する。Postgresコンテナは
 * Testcontainers公式のsingleton containerパターン(手動起動、{@code @Container}は使わない)を、 {@link
 * AbstractIntegrationTest}と同じ理由で踏襲する。
 *
 * <p>STOMPメッセージコンバータは{@link PermissiveStringMessageConverter}(自前実装)を使う。Spring標準の {@code
 * StringMessageConverter}はcontent-typeが明示されている場合({@code text/plain}以外、例えば サーバーが送る{@code
 * application/json})は{@code setStrictContentTypeMatch(false)}にしても拒否してしまう
 * (content-typeが未指定の場合のみ緩和される仕様のため)ため、content-typeを一切見ずに常にbyte[]→UTF-8文字列変換する
 * コンバータを自前で用意した。JSONペイロードは各テストが個別に{@link #objectMapper}(Jackson 3、{@code
 * tools.jackson.databind})で組み立てる(Spring Boot 4.1のデフォルトJSON実装がJackson 3系のため、Jackson 2ベースの{@code
 * MappingJackson2MessageConverter}は使えない)。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractWebSocketIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @LocalServerPort protected int port;

  @Autowired protected AuthorizationTestFixtures fixtures;

  @Autowired protected ObjectMapper objectMapper;

  protected WebSocketStompClient stompClient;

  protected void setUpStompClient() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new PermissiveStringMessageConverter());
  }

  protected String wsUrl() {
    return "ws://localhost:" + port + "/ws";
  }

  protected void disconnectQuietly(StompSession session) {
    if (session != null && session.isConnected()) {
      session.disconnect();
    }
  }

  /** content-typeを一切見ず、常にbyte[]/StringをUTF-8文字列として送受信するテスト専用コンバータ。 */
  private static final class PermissiveStringMessageConverter implements MessageConverter {

    @Override
    public Object fromMessage(Message<?> message, Class<?> targetClass) {
      Object payload = message.getPayload();
      if (payload instanceof byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
      }
      return payload.toString();
    }

    @Override
    public Message<?> toMessage(Object payload, MessageHeaders headers) {
      // MessageBuilder.withPayload(...).copyHeaders(...)は元のStompHeaderAccessorとの紐付けを失ってしまい
      // 「No StompHeaderAccessor available」エラーになるため、既存のMessageHeadersインスタンスをそのまま使う
      byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
      return MessageBuilder.createMessage(bytes, headers);
    }
  }
}
