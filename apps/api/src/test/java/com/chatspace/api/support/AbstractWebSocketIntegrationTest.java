package com.chatspace.api.support;

import com.chatspace.api.user.User;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.WebSocketHttpHeaders;
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

  /** {@code application-test.yml}の{@code chatspace.web-origin}と一致させること(許可Origin)。 */
  protected static final String ALLOWED_ORIGIN = "http://localhost:5173";

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

  /** 認証Cookie・許可Origin付きでハンドシェイクし、STOMPセッションを確立する。 */
  protected CompletableFuture<StompSession> connect(User user, StompSessionHandler handler) {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookieHeader(user));
    headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
    return stompClient.connectAsync(wsUrl(), headers, handler);
  }

  protected String cookieHeader(User user) {
    Cookie cookie = fixtures.authCookie(user);
    return cookie.getName() + "=" + cookie.getValue();
  }

  protected StompFrameHandler noOpFrameHandler() {
    return collectingFrameHandler(new LinkedBlockingQueue<>());
  }

  /** 受信フレームを文字列のまま{@code sink}へ積むだけのフレームハンドラ。 */
  protected StompFrameHandler collectingFrameHandler(BlockingQueue<String> sink) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return String.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        sink.add((String) payload);
      }
    };
  }

  /**
   * {@code handleException}/{@code handleTransportError}を捕捉して{@link BlockingQueue}へ積むだけの記録用ハンドラ。
   */
  public static class RecordingHandler extends StompSessionHandlerAdapter {
    public final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

    public RecordingHandler() {}

    @Override
    public void handleException(
        StompSession session,
        StompCommand command,
        StompHeaders headers,
        byte[] payload,
        Throwable exception) {
      errors.add(exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      errors.add(exception);
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
