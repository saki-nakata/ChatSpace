package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * ワークスペース機能定義書§9・テスト設計書§6.1(AUTH-P05〜P09)に対応する。プロトタイプ {@code test/authorization.test.ts}
 * からの移植テスト(フェーズ2分)。
 */
class WorkspaceCrudAuthorizationTest extends AbstractIntegrationTest {

  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

  @Autowired private ChannelMemberRepository channelMemberRepository;

  /** AUTH-P05: workspaceId/channelId 不一致時の404。 */
  @Test
  void channelAccess_workspaceIdMismatch_returns404() throws Exception {
    User ownerA = fixtures.createUser();
    Workspace workspaceA = fixtures.createWorkspaceWithOwner(ownerA);
    Channel channelInA = fixtures.createChannel(workspaceA, ChannelType.PUBLIC, ownerA);

    User ownerB = fixtures.createUser();
    Workspace workspaceB = fixtures.createWorkspaceWithOwner(ownerB);

    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/read",
                    workspaceB.getId(),
                    channelInA.getId())
                .cookie(fixtures.authCookie(ownerA)))
        .andExpect(status().isNotFound());
  }

  /** AUTH-P06: 一般メンバーはチャンネルを作成できない(403)。 */
  @Test
  void createChannel_nonOwnerMember_returns403() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);

    String body = objectMapper.writeValueAsString(Map.of("name", "general", "type", "PUBLIC"));

    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/channels", workspace.getId())
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  /** AUTH-P07: 一般メンバーはチャンネルへの招待を実行できない(403)。 */
  @Test
  void inviteChannelMember_nonOwnerMember_returns403() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    User target = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, target, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PRIVATE, owner, member);

    String body = objectMapper.writeValueAsString(Map.of("userId", target.getUserId()));

    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/members",
                    workspace.getId(),
                    channel.getId())
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  /** AUTH-P08: 一般メンバーは他メンバーをキックできない(403)。 */
  @Test
  void kickWorkspaceMember_nonOwnerMember_returns403() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    User target = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, target, WorkspaceRole.MEMBER);

    String body = objectMapper.writeValueAsString(Map.of("userId", target.getId().toString()));

    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/kick", workspace.getId())
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  /** AUTH-P09: オーナーはチャンネル作成・招待・キックを実行でき、キック後はDB上のメンバーシップが実際に削除されている。 */
  @Test
  void owner_canCreateInviteAndKick_andKickDeletesMembershipsInDb() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User target = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, target, WorkspaceRole.MEMBER);

    String createBody =
        objectMapper.writeValueAsString(Map.of("name", "general", "type", "PUBLIC"));
    MvcResult createResult =
        mockMvc
            .perform(
                post("/workspaces/{workspaceId}/channels", workspace.getId())
                    .cookie(fixtures.authCookie(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode channelJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
    UUID channelId = UUID.fromString(channelJson.get("id").asText());

    String inviteBody = objectMapper.writeValueAsString(Map.of("userId", target.getUserId()));
    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/members",
                    workspace.getId(),
                    channelId)
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(inviteBody))
        .andExpect(status().isCreated());
    assertTrue(
        channelMemberRepository.findByChannelIdAndUserId(channelId, target.getId()).isPresent());

    String kickBody = objectMapper.writeValueAsString(Map.of("userId", target.getId().toString()));
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/kick", workspace.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(kickBody))
        .andExpect(status().isNoContent());

    assertTrue(
        workspaceMemberRepository
            .findByWorkspaceIdAndUserId(workspace.getId(), target.getId())
            .isEmpty());
    assertTrue(
        channelMemberRepository.findByChannelIdAndUserId(channelId, target.getId()).isEmpty());
  }
}
