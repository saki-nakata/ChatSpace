package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelService;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.message.CreateMessageRequest;
import com.chatspace.api.message.MessageService;
import com.chatspace.api.realtime.RealtimeEvent;
import com.chatspace.api.realtime.StompDestinations;
import com.chatspace.api.support.AbstractWebSocketIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

  @Autowired private MessageService messageService;
  @Autowired private ChannelService channelService;
  @Autowired private SimpMessagingTemplate messagingTemplate;

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

  /**
   * レビュー指摘対応: プライベートチャンネル作成時、ワークスペーストピックへブロードキャストされない(チャンネル非メンバーの
   * ワークスペースメンバーに、チャンネル名・存在が漏洩しないこと)。以前は{@code type}を問わず無条件で {@code
   * /topic/workspaces.{id}}へ送っていたため、購読しているだけの非メンバー全員にプライベートチャンネルの 作成イベント(チャンネル名を含む)が届いてしまっていた。
   */
  @Test
  void createPrivateChannel_doesNotBroadcastToWorkspaceTopic() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User outsider = fixtures.createUser(); // ワークスペースメンバーだがチャンネル非メンバー
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);

    RecordingHandler outsiderHandler = new RecordingHandler();
    session = connect(outsider, outsiderHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> workspaceFrames = new LinkedBlockingQueue<>();
    session.subscribe(
        "/topic/workspaces." + workspace.getId(), collectingFrameHandler(workspaceFrames));
    Thread.sleep(300);

    channelService.create(
        workspace.getId(), owner.getId(), "secret-room", ChannelType.PRIVATE, List.of());

    String leaked = workspaceFrames.poll(2, TimeUnit.SECONDS);
    assertNull(leaked, "プライベートチャンネルの作成はワークスペーストピックへブロードキャストされないはず");
  }

  /** レビュー指摘対応(過剰ブロック検証): プライベートチャンネルの実メンバーは、個人キュー経由でCHANNEL_CREATEDを受信できること。 */
  @Test
  void createPrivateChannel_notifiesMemberViaPersonalQueue() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);

    RecordingHandler handler = new RecordingHandler();
    session = connect(owner, handler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> personalFrames = new LinkedBlockingQueue<>();
    session.subscribe("/user/queue/events", collectingFrameHandler(personalFrames));
    Thread.sleep(300);

    channelService.create(
        workspace.getId(), owner.getId(), "secret-room-2", ChannelType.PRIVATE, List.of());

    String frame = personalFrames.poll(5, TimeUnit.SECONDS);
    assertNotNull(frame, "プライベートチャンネルの作成者は個人キュー経由でCHANNEL_CREATEDを受信できるはず");
    assertTrue(frame.contains("CHANNEL_CREATED"));
  }

  /** レビュー指摘対応: プライベートチャンネルからのキックも同様にワークスペーストピックへブロードキャストされないこと。 */
  @Test
  void kickFromPrivateChannel_doesNotBroadcastToWorkspaceTopic() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    User outsider = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PRIVATE, owner, member);

    RecordingHandler outsiderHandler = new RecordingHandler();
    session = connect(outsider, outsiderHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> workspaceFrames = new LinkedBlockingQueue<>();
    session.subscribe(
        "/topic/workspaces." + workspace.getId(), collectingFrameHandler(workspaceFrames));
    Thread.sleep(300);

    channelService.removeMember(workspace.getId(), channel.getId(), owner.getId(), member.getId());

    String leaked = workspaceFrames.poll(2, TimeUnit.SECONDS);
    assertNull(leaked, "プライベートチャンネルからのキックはワークスペーストピックへブロードキャストされないはず");
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

  /**
   * AUTH-N17(1): {@code @MessageMapping}ハンドラの宛先変数が不正な形式(UUIDでない)の場合、ハンドラが実行されず
   * TYPING_UPDATEがブロードキャストされないこと。
   *
   * <p>「何も受信しなかった」ことだけを見ると、タイピング配信機能そのものが壊れていてもテストが通ってしまう。
   * そのため、先に**正常なタイピングイベントが実際に配信されること**を確認して配信経路が生きていることを 示したうえで、不正な宛先では配信されないことを検証する(レビュー指摘対応)。
   */
  @Test
  void typingSend_withMalformedChannelId_isNotBroadcast() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    RecordingHandler subscriberHandler = new RecordingHandler();
    session = connect(owner, subscriberHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe(
        StompDestinations.channelTopic(channel.getId()), collectingFrameHandler(frames));
    Thread.sleep(300);

    // 正常系: 配信経路が生きていることを先に確認する(この検証が無いと過剰ブロックを見逃す)
    session.send("/app/channels." + channel.getId() + ".typing", "{}");
    String validFrame = frames.poll(5, TimeUnit.SECONDS);
    assertNotNull(validFrame, "正当なタイピングイベントはトピックへブロードキャストされるはず");
    assertTrue(validFrame.contains("TYPING_UPDATE"));

    // 異常系: 宛先のchannelId部分がUUIDでない(SEND自体は/app/配下のためインターセプタは通過する)
    session.send("/app/channels.not-a-uuid.typing", "{}");

    assertNull(frames.poll(2, TimeUnit.SECONDS), "不正な形式のchannelIdではTYPING_UPDATEがブロードキャストされないはず");
  }

  /**
   * AUTH-N17(2): STOMPフレームのサイズ上限({@code WebSocketConfig#MESSAGE_SIZE_LIMIT_BYTES} = 8KiB)を超える
   * ペイロードが処理されず、TYPING_UPDATEがブロードキャストされないこと。加えて、送信側セッションが エラー通知または切断によって拒否を認識できること。
   *
   * <p>(1)と同様に、まず送信側セッションから正常なタイピングイベントが配信されることを確認してから サイズ超過を送る(レビュー指摘対応)。
   */
  @Test
  void typingSend_withOversizedPayload_isRejected() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    RecordingHandler subscriberHandler = new RecordingHandler();
    session = connect(owner, subscriberHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe(
        StompDestinations.channelTopic(channel.getId()), collectingFrameHandler(frames));
    Thread.sleep(300);

    RecordingHandler senderHandler = new RecordingHandler();
    otherSession = connect(owner, senderHandler).get(5, TimeUnit.SECONDS);

    // 正常系: この送信側セッションからのタイピングイベントは配信される
    otherSession.send("/app/channels." + channel.getId() + ".typing", "{}");
    String validFrame = frames.poll(5, TimeUnit.SECONDS);
    assertNotNull(validFrame, "正当なタイピングイベントはトピックへブロードキャストされるはず");
    assertTrue(validFrame.contains("TYPING_UPDATE"));

    // 異常系: サイズ上限を超えるフレーム
    String oversized = "{\"padding\":\"" + "x".repeat(16 * 1024) + "\"}";
    otherSession.send("/app/channels." + channel.getId() + ".typing", oversized);

    assertNull(frames.poll(3, TimeUnit.SECONDS), "サイズ上限超過のフレームは処理されずブロードキャストされないはず");
    boolean rejectedOnSenderSide =
        senderHandler.errors.poll(5, TimeUnit.SECONDS) != null || !otherSession.isConnected();
    assertTrue(rejectedOnSenderSide, "サイズ上限超過は送信側にエラー通知または切断として現れるはず");
  }

  /**
   * AUTH-N29: 他人の個人キュー({@code /user/{他人のuserId}/queue/events})を明示的に指定して購読しても、他人宛の通知を 盗み見できないこと。
   *
   * <p>本実装では{@code StompChannelInterceptor}のcatch-all default-denyが先に評価されるため、この宛先は
   * SUBSCRIBEの時点で拒否される(テスト設計書が想定していた「{@code UserDestinationResolver}が自分のキューへ
   * 書き換えるため盗み見できない」よりも早い段階での遮断)。いずれの経路であっても「他人宛イベントを受信しない」 ことが守るべき性質のため、ここではその性質自体を検証する。
   */
  @Test
  void subscribeOtherUsersQueue_doesNotReceiveTheirEvents() throws Exception {
    User attacker = fixtures.createUser();
    User victim = fixtures.createUser();

    RecordingHandler attackerHandler = new RecordingHandler();
    session = connect(attacker, attackerHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> stolenFrames = new LinkedBlockingQueue<>();
    session.subscribe(
        "/user/" + victim.getId() + "/queue/events", collectingFrameHandler(stolenFrames));
    Thread.sleep(300);

    messagingTemplate.convertAndSendToUser(
        victim.getId().toString(),
        StompDestinations.USER_EVENTS_DESTINATION,
        new RealtimeEvent("SECRET_FOR_VICTIM", Map.of("secret", "must-not-leak")));

    assertNull(stolenFrames.poll(3, TimeUnit.SECONDS), "他人の個人キュー宛のイベントを受信できてはいけない");
  }
}
