package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 通知機能定義書§9・テスト設計書§6.2(AUTH-N03〜N06・AUTH-N26)に対応する。
 *
 * <p>通知のスコープ再チェック(AND結合)は、プロトタイプに実在するギャップの修正であり最重要。
 */
class NotificationScopeAuthorizationTest extends AbstractIntegrationTest {

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
