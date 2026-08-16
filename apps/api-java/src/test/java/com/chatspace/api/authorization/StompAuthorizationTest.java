package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.message.CreateMessageRequest;
import com.chatspace.api.message.MessageService;
import com.chatspace.api.support.AbstractWebSocketIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;

/**
 * リアルタイム通信機能定義書§7・§8・§10・テスト設計書§6.2(AUTH-N10・N14・N15・N16・N27・N28)に対応する。
 *
 * <p>ハンドシェイク時認証(§14)を厳格に行う設計上、未認証状態でのCONNECT自体が拒否されるため、AUTH-N28
 * (「/user/queue/eventsの未認証拒否」)の意図は「Cookie無しでのハンドシェイク拒否」として検証する。
 */
class StompAuthorizationTest extends AbstractWebSocketIntegrationTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

  @Autowired private MessageService messageService;

  private StompSession session;
  private StompSession otherSession;

  @BeforeEach
  void setUp() {
    setUpStompClient();
  }

  @AfterEach
  void tearDown() {
    disconnectQuietly(session);
    disconnectQuietly(otherSession);
  }

  /** AUTH-N28の意図: 未認証(Cookie無し)でのハンドシェイクが拒否されること。 */
  @Test
  void handshake_withoutCookie_isRejected() {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
    CompletableFuture<StompSession> future =
        stompClient.connectAsync(wsUrl(), headers, new StompSessionHandlerAdapter() {});
    assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
  }

  /** AUTH-N14: 許可外Originからのハンドシェイクが拒否されること。 */
  @Test
  void handshake_withDisallowedOrigin_isRejected() {
    User user = fixtures.createUser();
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookieHeader(user));
    headers.add(HttpHeaders.ORIGIN, "http://evil.example.com");
    CompletableFuture<StompSession> future =
        stompClient.connectAsync(wsUrl(), headers, new StompSessionHandlerAdapter() {});
    assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
  }

  /** AUTH-N10: 非メンバーによるチャンネルトピックへのSUBSCRIBEが拒否されること。 */
  @Test
  void subscribeChannelTopic_nonMember_isRejected() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    User outsider = fixtures.createUser(); // ワークスペース非所属

    RecordingHandler handler = new RecordingHandler();
    session = connect(outsider, handler).get(5, TimeUnit.SECONDS);
    session.subscribe("/topic/channels." + channel.getId(), noOpFrameHandler());

    Throwable error = handler.errors.poll(5, TimeUnit.SECONDS);
    assertNotNull(error, "非メンバーによる購読はエラーになるはず");
  }

  /** AUTH-N16(過剰ブロック検証): 正当なメンバーはチャンネルトピックを購読でき、投稿されたメッセージを受信できること。 */
  @Test
  void subscribeChannelTopic_member_receivesBroadcastMessage() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    RecordingHandler handler = new RecordingHandler();
    session = connect(owner, handler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe("/topic/channels." + channel.getId(), collectingFrameHandler(frames));

    // 購読が確立するまで少し待ってからメッセージを投稿する
    Thread.sleep(300);
    messageService.create(
        workspace.getId(),
        channel.getId(),
        null,
        owner.getId(),
        new CreateMessageRequest("hello via stomp test", null, null));

    String frame = frames.poll(5, TimeUnit.SECONDS);
    assertNotNull(frame, "チャンネルメンバーはブロードキャストを受信できるはず");
    assertTrue(frame.contains("MESSAGE_CREATED"));
    assertNull(handler.errors.poll(), "正当な購読はエラーにならないはず");
  }

  /** AUTH-N15: クライアントが/topic/**へ直接SENDしても拒否され、偽装イベントが配信されないこと。 */
  @Test
  void directSendToTopic_isRejectedAndNotDelivered() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);

    // 正当な購読者
    RecordingHandler victimHandler = new RecordingHandler();
    session = connect(owner, victimHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe("/topic/channels." + channel.getId(), collectingFrameHandler(frames));
    Thread.sleep(300);

    // 攻撃者が直接SEND
    RecordingHandler attackerHandler = new RecordingHandler();
    otherSession = connect(member, attackerHandler).get(5, TimeUnit.SECONDS);
    otherSession.send(
        "/topic/channels." + channel.getId(),
        "{\"type\":\"MESSAGE_CREATED\",\"payload\":\"forged\"}");

    Throwable error = attackerHandler.errors.poll(5, TimeUnit.SECONDS);
    assertNotNull(error, "/topic/**への直接SENDは拒否されるはず");

    String leaked = frames.poll(2, TimeUnit.SECONDS);
    assertNull(leaked, "偽装イベントは正当な購読者へ配信されないはず");
  }

  /** AUTH-N27: 認証済みユーザーが自分の/user/queue/eventsを購読でき、拒否されないこと。 */
  @Test
  void subscribeOwnUserQueueEvents_isAllowed() throws Exception {
    User user = fixtures.createUser();
    RecordingHandler handler = new RecordingHandler();
    session = connect(user, handler).get(5, TimeUnit.SECONDS);
    session.subscribe("/user/queue/events", noOpFrameHandler());

    Throwable error = handler.errors.poll(2, TimeUnit.SECONDS);
    assertNull(error, "自分のuser queueへの購読はエラーにならないはず");
  }

  private CompletableFuture<StompSession> connect(User user, RecordingHandler handler) {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookieHeader(user));
    headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
    return stompClient.connectAsync(wsUrl(), headers, handler);
  }

  private String cookieHeader(User user) {
    Cookie cookie = fixtures.authCookie(user);
    return cookie.getName() + "=" + cookie.getValue();
  }

  private StompFrameHandler noOpFrameHandler() {
    return collectingFrameHandler(new LinkedBlockingQueue<>());
  }

  private StompFrameHandler collectingFrameHandler(BlockingQueue<String> sink) {
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

  /** {@code handleException}を捕捉して{@link BlockingQueue}へ積むだけの記録用ハンドラ。 */
  private static class RecordingHandler extends StompSessionHandlerAdapter {
    final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

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
}
