package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * チャンネル機能定義書§9・テスト設計書§6.1(AUTH-P03・AUTH-P04)に対応する。プロトタイプ {@code test/authorization.test.ts}
 * の「プライベートチャンネルの非可視性」テストの移植(フェーズ3分)。
 */
class ChannelVisibilityAuthorizationTest extends AbstractIntegrationTest {

  /** AUTH-P03: 非参加者のチャンネル一覧にプライベートチャンネルが含まれないこと。 */
  @Test
  void privateChannel_excludedFromNonMemberChannelList() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User nonMember = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, nonMember, WorkspaceRole.MEMBER);
    Channel privateChannel = fixtures.createChannel(workspace, ChannelType.PRIVATE, owner);

    MvcResult result =
        mockMvc
            .perform(
                get("/workspaces/{workspaceId}/channels", workspace.getId())
                    .cookie(fixtures.authCookie(nonMember)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode channels = objectMapper.readTree(result.getResponse().getContentAsString());
    boolean containsPrivateChannel =
        StreamSupport.stream(channels.spliterator(), false)
            .anyMatch(node -> node.get("id").asText().equals(privateChannel.getId().toString()));
    assertFalse(containsPrivateChannel);
  }

  /** AUTH-P04: 非参加者が直接IDを指定してもメッセージ一覧を取得できない(404)。 */
  @Test
  void privateChannel_directMessageAccessByNonMember_returns404() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User nonMember = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, nonMember, WorkspaceRole.MEMBER);
    Channel privateChannel = fixtures.createChannel(workspace, ChannelType.PRIVATE, owner);

    mockMvc
        .perform(
            get(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages",
                    workspace.getId(),
                    privateChannel.getId())
                .cookie(fixtures.authCookie(nonMember)))
        .andExpect(status().isNotFound());
  }
}
