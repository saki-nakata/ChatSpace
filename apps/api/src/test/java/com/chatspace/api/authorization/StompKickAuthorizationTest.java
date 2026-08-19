package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.dm.DmService;
import com.chatspace.api.dm.DmThreadResponse;
import com.chatspace.api.message.CreateMessageRequest;
import com.chatspace.api.message.MessageService;
import com.chatspace.api.realtime.StompDestinations;
import com.chatspace.api.support.AbstractWebSocketIntegrationTest;
import com.chatspace.api.support.KickRollbackTestHelper;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import com.chatspace.api.workspace.WorkspaceService;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * リアルタイム通信機能定義書§10・テスト設計書§6.2(AUTH-N11・N12・N13・N24)に対応する。
 *
 * <p>フェーズ4では「{@code AFTER_COMMIT}イベントリスナー・{@code DmAuthorizationService}のライブ再検証という
 * 設計・実装レベルでの担保」に留めていた4項目を、実STOMPクライアントによる回帰テストとして固定する (フェーズ11の機能同等性チェックリスト対応)。
 */
class StompKickAuthorizationTest extends AbstractWebSocketIntegrationTest {

  @Autowired private MessageService messageService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private DmService dmService;
  @Autowired private KickRollbackTestHelper kickRollbackTestHelper;

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

  /**
   * AUTH-N11: キック前に購読済みだったユーザーが、キック後に投稿された新規メッセージを受信しないこと。
   *
   * <p>キック前に同じ購読で1件受信できることを先に確認しておくことで、「そもそも配信されていなかっただけ」という 偽陰性を排除する。
   */
  @Test
  void kickedMember_stopsReceivingChannelMessages() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    RecordingHandler memberHandler = new RecordingHandler();
    session = connect(member, memberHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe(
        StompDestinations.channelTopic(channel.getId()), collectingFrameHandler(frames));
    Thread.sleep(300);

    // キック前は受信できる(過剰ブロックでないことの確認)
    postMessage(workspace, channel, owner, "before kick");
    assertNotNull(frames.poll(5, TimeUnit.SECONDS), "キック前は購読者がメッセージを受信できるはず");

    workspaceService.kick(workspace.getId(), owner.getId(), member.getId());
    // AFTER_COMMITでの強制切断が完了するまで待つ
    Thread.sleep(1000);

    postMessage(workspace, channel, owner, "after kick");
    assertNull(frames.poll(3, TimeUnit.SECONDS), "キック後に投稿されたメッセージを受信できてはいけない");
  }

  /**
   * AUTH-N12: キック処理がロールバックした場合(後続処理の失敗など)、強制切断が発生せず、購読も生き続けること。
   *
   * <p>{@code MemberKickedEventListener}が{@code @TransactionalEventListener(AFTER_COMMIT)}であることの担保。
   * これが素の{@code @EventListener}だった場合、ロールバックしたのに切断されてしまい本テストが落ちる。
   */
  @Test
  void rolledBackKick_doesNotDisconnectMember() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    RecordingHandler memberHandler = new RecordingHandler();
    session = connect(member, memberHandler).get(5, TimeUnit.SECONDS);
    BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    session.subscribe(
        StompDestinations.channelTopic(channel.getId()), collectingFrameHandler(frames));
    Thread.sleep(300);

    assertThrows(
        KickRollbackTestHelper.IntentionalRollbackException.class,
        () ->
            kickRollbackTestHelper.kickThenRollback(
                workspace.getId(), owner.getId(), member.getId()));
    Thread.sleep(1000);

    assertTrue(session.isConnected(), "ロールバックしたキックでは強制切断されないはず");
    postMessage(workspace, channel, owner, "after rolled back kick");
    assertNotNull(frames.poll(5, TimeUnit.SECONDS), "ロールバック後もメンバーシップは有効なのでメッセージを受信できるはず");
  }

  /** AUTH-N13: キック確定後に即座に再接続しても、削除済みメンバーシップを参照するため対象チャンネルへ再購読できないこと。 */
  @Test
  void kickedMember_cannotResubscribeChannelAfterReconnect() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    workspaceService.kick(workspace.getId(), owner.getId(), member.getId());

    RecordingHandler handler = new RecordingHandler();
    session = connect(member, handler).get(5, TimeUnit.SECONDS);
    session.subscribe(StompDestinations.channelTopic(channel.getId()), noOpFrameHandler());

    assertNotNull(handler.errors.poll(5, TimeUnit.SECONDS), "キック後の再購読は拒否されるはず");
  }

  /**
   * AUTH-N24: ワークスペースからキックされた後、DMトピックへの再購読が拒否されること。
   *
   * <p>{@code DmThread}の参加者情報自体はキック後も残るため、参加者チェックだけでは通ってしまう。{@code
   * DmAuthorizationService}がワークスペースメンバーシップをライブに再検証していることの担保。
   */
  @Test
  void kickedMember_cannotResubscribeDmTopicAfterReconnect() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    DmThreadResponse dm =
        dmService.getOrCreate(workspace.getId(), owner.getId(), member.getUserId());
    UUID dmId = dm.id();

    // キック前はDMトピックを購読できる(過剰ブロックでないことの確認)
    RecordingHandler beforeHandler = new RecordingHandler();
    session = connect(member, beforeHandler).get(5, TimeUnit.SECONDS);
    session.subscribe(StompDestinations.dmTopic(dmId), noOpFrameHandler());
    assertNull(beforeHandler.errors.poll(2, TimeUnit.SECONDS), "キック前のDMトピック購読は許可されるはず");

    workspaceService.kick(workspace.getId(), owner.getId(), member.getId());
    Thread.sleep(1000);

    RecordingHandler afterHandler = new RecordingHandler();
    otherSession = connect(member, afterHandler).get(5, TimeUnit.SECONDS);
    otherSession.subscribe(StompDestinations.dmTopic(dmId), noOpFrameHandler());

    assertNotNull(afterHandler.errors.poll(5, TimeUnit.SECONDS), "ワークスペースキック後のDMトピック再購読は拒否されるはず");
  }

  private void postMessage(Workspace workspace, Channel channel, User author, String body) {
    messageService.create(
        workspace.getId(),
        channel.getId(),
        null,
        author.getId(),
        new CreateMessageRequest(body, null, null));
  }
}
