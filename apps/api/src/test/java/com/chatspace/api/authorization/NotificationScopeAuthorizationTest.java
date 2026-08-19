package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.notification.Notification;
import com.chatspace.api.notification.NotificationRepository;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 通知機能定義書§9・テスト設計書§6.2(AUTH-N03〜N06・AUTH-N26)に対応する。
 *
 * <p>通知のスコープ再チェック(AND結合)は、プロトタイプに実在するギャップの修正であり最重要。
 */
class NotificationScopeAuthorizationTest extends AbstractIntegrationTest {

  @Autowired private NotificationRepository notificationRepository;

  /** AUTH-N03〜N05: あるチャンネルからキックされた後、キック前に受け取った通知(メンション)の一覧・未読件数・個別既読のいずれからも 除外されること。 */
  @Test
  void channelNotification_afterChannelKick_excludedFromListUnreadCountAndRead() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    // オーナーがmemberをメンションして通知(channelId付き)を生成する
    String body = objectMapper.writeValueAsString(Map.of("body", "hi @" + member.getUserId()));
    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages",
                    workspace.getId(),
                    channel.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    // キック前: 一覧に表示され、未読件数1件
    UUID notificationId = fetchNotificationId(member);
    assertUnreadCount(member, 1);

    // チャンネルからキック(オーナーによる強制退出)
    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/members/{targetUserId}",
                    workspace.getId(),
                    channel.getId(),
                    member.getId())
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());

    // AUTH-N03: 一覧から除外される
    MvcResult listResult =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(member)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).get("notifications");
    assertFalse(
        StreamSupport.stream(notifications.spliterator(), false)
            .anyMatch(n -> n.get("id").asText().equals(notificationId.toString())),
        "キック後は通知一覧から除外されるはず");

    // AUTH-N04: 未読件数からも除外される
    assertUnreadCount(member, 0);

    // AUTH-N05: 個別既読APIを直接叩いても404(除外された通知には触れない)
    mockMvc
        .perform(
            post("/notifications/{id}/read", notificationId).cookie(fixtures.authCookie(member)))
        .andExpect(status().isNotFound());
  }

  /**
   * AUTH-N05(read-all): キック後に不可視になった通知が {@code POST /notifications/read-all} でも既読化されないこと。
   *
   * <p>個別既読(404)だけでは「一括既読が全件を無条件にUPDATEしている」退行を検出できない。可視な通知と
   * 不可視な通知を同時に用意し、一括既読の後で「可視分だけが既読になり、不可視分は{@code readAt}がnullのまま」で あることをDBレベルで確認する({@code
   * markAllVisibleRead}に一覧・未読件数と同一のスコープ条件が 適用されていることの担保)。
   */
  @Test
  void markAllRead_doesNotTouchNotificationsHiddenByChannelKick() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel stayingChannel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);
    Channel kickedChannel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    mention(workspace, stayingChannel, owner, member);
    mention(workspace, kickedChannel, owner, member);
    assertUnreadCount(member, 2);

    UUID hiddenNotificationId = notificationIdForChannel(member, kickedChannel);
    UUID visibleNotificationId = notificationIdForChannel(member, stayingChannel);

    // 片方のチャンネルからキックし、その通知を不可視にする
    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/members/{targetUserId}",
                    workspace.getId(),
                    kickedChannel.getId(),
                    member.getId())
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());
    assertUnreadCount(member, 1);

    mockMvc
        .perform(post("/notifications/read-all").cookie(fixtures.authCookie(member)))
        .andExpect(status().isOk());

    assertNotNull(
        findNotification(visibleNotificationId).getReadAt(), "可視な通知は一括既読で既読になるはず(過剰ブロックでないことの確認)");
    assertNull(
        findNotification(hiddenNotificationId).getReadAt(),
        "キックで不可視になった通知は一括既読の対象外であるはず(readAtがnullのまま)");
    assertUnreadCount(member, 0);
  }

  /** AUTH-N26: ワークスペースキック後、過去のDM通知が一覧・未読件数に含まれなくなること。 */
  @Test
  void dmNotification_afterWorkspaceKick_excludedFromListAndUnreadCount() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);

    MvcResult dmResult =
        mockMvc
            .perform(
                post("/workspaces/{workspaceId}/dms", workspace.getId())
                    .cookie(fixtures.authCookie(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("userId", member.getUserId()))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID dmId =
        UUID.fromString(
            objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/dms/{dmId}/messages", workspace.getId(), dmId)
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", "hi via dm"))))
        .andExpect(status().isCreated());

    assertUnreadCount(member, 1);

    // ワークスペースからキック
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/kick", workspace.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("userId", member.getId().toString()))))
        .andExpect(status().isNoContent());

    MvcResult listResult =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(member)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).get("notifications");
    assertTrue(
        StreamSupport.stream(notifications.spliterator(), false).findAny().isEmpty(),
        "ワークスペースキック後はDM通知が一覧から除外されるはず");
    assertUnreadCount(member, 0);
  }

  /**
   * AUTH-N06: 通知スコープ条件の3条件(channel / dm / workspace)が{@code OR}ではなく{@code AND}で結合されて いることの回帰テスト。
   *
   * <p>{@code OR}結合だと、チャンネル通知は{@code dm_id IS NULL}分岐が常に真になるためフィルタが丸ごと無効化され、
   * キック済みチャンネルの通知まで見えてしまう(プロトタイプに実在したバグパターン)。1人のユーザーについて
   * 「見えるべき通知1件」と「3条件それぞれで弾かれるべき通知」を同時に用意し、可視なのが1件だけであることを 検証することで、どれか1条件でも{@code
   * OR}に退行した瞬間に失敗するようにしている。
   */
  @Test
  void notificationScope_conditionsAreAndedNotOred() throws Exception {
    User owner = fixtures.createUser();
    User target = fixtures.createUser();

    // ワークスペースA: targetは在籍し続ける
    Workspace workspaceA = fixtures.createWorkspaceWithOwner(owner);
    fixtures.addWorkspaceMember(workspaceA, target, WorkspaceRole.MEMBER);
    Channel visibleChannel = fixtures.createChannel(workspaceA, ChannelType.PUBLIC, owner, target);
    Channel kickedChannel = fixtures.createChannel(workspaceA, ChannelType.PUBLIC, owner, target);

    // (1) 見えるべき通知: 在籍中チャンネルでのメンション
    mention(workspaceA, visibleChannel, owner, target);
    // (2) channel条件で弾かれるべき通知: この後キックされるチャンネルでのメンション
    mention(workspaceA, kickedChannel, owner, target);

    // ワークスペースB: targetは後でキックされる
    Workspace workspaceB = fixtures.createWorkspaceWithOwner(owner);
    // (3) workspace条件で弾かれるべき通知: ワークスペースBへの招待通知(channelId/dmIdは共にnull)。
    // 招待APIを使うことでメンバーシップ追加とWORKSPACE_INVITE通知の生成を同時に行う。
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/invite", workspaceB.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", target.getUserId()))))
        .andExpect(status().isCreated());
    // (4) dm条件で弾かれるべき通知: ワークスペースBでのDM
    MvcResult dmResult =
        mockMvc
            .perform(
                post("/workspaces/{workspaceId}/dms", workspaceB.getId())
                    .cookie(fixtures.authCookie(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("userId", target.getUserId()))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID dmId =
        UUID.fromString(
            objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asText());
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/dms/{dmId}/messages", workspaceB.getId(), dmId)
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", "hi via dm"))))
        .andExpect(status().isCreated());

    assertUnreadCount(target, 4);

    // チャンネルキックとワークスペースキックを実施する
    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/members/{targetUserId}",
                    workspaceA.getId(),
                    kickedChannel.getId(),
                    target.getId())
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/kick", workspaceB.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("userId", target.getId().toString()))))
        .andExpect(status().isNoContent());

    // 3条件がANDで効いていれば、残るのは(1)の1件だけになる
    MvcResult listResult =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(target)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).get("notifications");
    assertEquals(
        1, notifications.size(), "channel/dm/workspaceの3条件がAND結合なら、可視な通知は在籍中チャンネルのメンション1件だけになるはず");
    assertEquals(
        visibleChannel.getId().toString(),
        notifications.get(0).get("channelId").asText(),
        "残る1件は在籍中チャンネルの通知であるはず");
    assertUnreadCount(target, 1);
  }

  private void mention(Workspace workspace, Channel channel, User author, User mentioned)
      throws Exception {
    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages",
                    workspace.getId(),
                    channel.getId())
                .cookie(fixtures.authCookie(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("body", "hi @" + mentioned.getUserId()))))
        .andExpect(status().isCreated());
  }

  private UUID notificationIdForChannel(User user, Channel channel) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(user)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("notifications");
    return StreamSupport.stream(notifications.spliterator(), false)
        .filter(n -> !n.get("channelId").isNull())
        .filter(n -> n.get("channelId").asText().equals(channel.getId().toString()))
        .findFirst()
        .map(n -> UUID.fromString(n.get("id").asText()))
        .orElseThrow(() -> new AssertionError("対象チャンネルの通知が見つかりません: " + channel.getId()));
  }

  private Notification findNotification(UUID notificationId) {
    return notificationRepository
        .findById(notificationId)
        .orElseThrow(() -> new AssertionError("通知が見つかりません: " + notificationId));
  }

  private UUID fetchNotificationId(User user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(user)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("notifications");
    return UUID.fromString(notifications.get(0).get("id").asText());
  }

  private void assertUnreadCount(User user, long expected) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/notifications/unread-count").cookie(fixtures.authCookie(user)))
            .andExpect(status().isOk())
            .andReturn();
    long actual =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("unreadCount")
            .asLong();
    assertEquals(expected, actual);
  }
}
