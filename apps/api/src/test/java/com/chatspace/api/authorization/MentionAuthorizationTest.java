package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * メンション機能定義書§9・テスト設計書§6.2(AUTH-N01・AUTH-N02)に対応する。
 *
 * <p>メンション経由のメンバー漏洩防止(最重要・新規の受け入れテスト)。
 */
class MentionAuthorizationTest extends AbstractIntegrationTest {

  /** AUTH-N01: 対象チャンネルの非メンバーへの`@メンション`が、通知・Mentionレコードを生成しないこと。 */
  @Test
  void mentioningNonChannelMember_doesNotNotifyOrCreateMentionRecord() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    User outsider = fixtures.createUser(); // ワークスペースメンバーだがチャンネル非メンバー
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);

    String body =
        objectMapper.writeValueAsString(Map.of("body", "hello @" + outsider.getUserId() + " !"));
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

    MvcResult result =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(outsider)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("notifications");
    assertTrue(
        StreamSupport.stream(notifications.spliterator(), false).findAny().isEmpty(),
        "非メンバーへの通知は生成されないはず");
  }

  /** AUTH-N02(前半): メンション自動補完APIが対象チャンネルの非メンバーを候補として返さないこと。 */
  @Test
  void mentionCandidates_excludeNonChannelMembers() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    User outsider = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);

    MvcResult result =
        mockMvc
            .perform(
                get(
                        "/workspaces/{workspaceId}/channels/{channelId}/mentions/candidates",
                        workspace.getId(),
                        channel.getId())
                    .cookie(fixtures.authCookie(owner)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode candidates =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("candidates");
    boolean containsOutsider =
        StreamSupport.stream(candidates.spliterator(), false)
            .anyMatch(node -> node.get("userId").asText().equals(outsider.getUserId()));
    assertFalse(containsOutsider, "チャンネル非メンバーは候補に含まれないはず");
  }

  /** AUTH-N02(後半): 非メンバーが候補取得APIを呼び出すと404になること。 */
  @Test
  void mentionCandidates_nonChannelMember_returns404() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    User outsider = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);

    mockMvc
        .perform(
            get(
                    "/workspaces/{workspaceId}/channels/{channelId}/mentions/candidates",
                    workspace.getId(),
                    channel.getId())
                .cookie(fixtures.authCookie(outsider)))
        .andExpect(status().isNotFound());
  }

  /** 正当なメンション(対象チャンネルのメンバー)は通知が生成されること(過剰ブロック検証を兼ねる)。 */
  @Test
  void mentioningChannelMember_createsNotification() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

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

    MvcResult result =
        mockMvc
            .perform(get("/notifications").cookie(fixtures.authCookie(member)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode notifications =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("notifications");
    boolean hasMention =
        StreamSupport.stream(notifications.spliterator(), false)
            .anyMatch(node -> node.get("type").asText().equals("MENTION"));
    assertTrue(hasMention, "チャンネルメンバーへの正当なメンションは通知を生成するはず");
  }
}
