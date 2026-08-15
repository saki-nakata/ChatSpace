package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * メッセージング機能定義書§9・テスト設計書§6.1(AUTH-P01・AUTH-P02)に対応する。プロトタイプ {@code test/authorization.test.ts}
 * の「クロスチャンネルのメッセージID漏洩防止」テストの移植(フェーズ3分)。
 */
class MessageScopeAuthorizationTest extends AbstractIntegrationTest {

  @Autowired private MessageRepository messageRepository;

  /**
   * AUTH-P01: 自分が参加している別チャンネルのURLに、非参加のプライベートチャンネルの{@code messageId}を組み合わせて
   * 渡しても、context/replies/編集/削除/リアクションの全エンドポイントで404になること(confused-deputy対策)。
   */
  @Test
  void crossChannelMessageId_allEndpoints_return404() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User attacker = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, attacker, WorkspaceRole.MEMBER);

    Channel ownChannel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, attacker);
    Channel otherChannel =
        fixtures.createChannel(workspace, ChannelType.PRIVATE, owner); // attackerは非参加

    Message secretMessage =
        messageRepository.save(
            new Message(otherChannel.getId(), null, null, owner.getId(), "secret"));

    String base = "/workspaces/{workspaceId}/channels/{channelId}/messages/{messageId}";
    Object[] path = {workspace.getId(), ownChannel.getId(), secretMessage.getId()};

    mockMvc
        .perform(get(base + "/context", path).cookie(fixtures.authCookie(attacker)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get(base + "/replies", path).cookie(fixtures.authCookie(attacker)))
        .andExpect(status().isNotFound());

    String editBody = objectMapper.writeValueAsString(Map.of("body", "hacked"));
    mockMvc
        .perform(
            patch(base, path)
                .cookie(fixtures.authCookie(attacker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(delete(base, path).cookie(fixtures.authCookie(attacker)))
        .andExpect(status().isNotFound());

    String reactionBody = objectMapper.writeValueAsString(Map.of("emoji", "👍"));
    mockMvc
        .perform(
            post(base + "/reactions", path)
                .cookie(fixtures.authCookie(attacker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionBody))
        .andExpect(status().isNotFound());
  }

  /** AUTH-P02: 正当なスコープ経由であれば同じ操作が成功すること(AUTH-P01の過剰ブロック検証)。 */
  @Test
  void legitimateScopedMessageId_allEndpoints_succeed() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    Message message =
        messageRepository.save(new Message(channel.getId(), null, null, member.getId(), "hello"));

    String base = "/workspaces/{workspaceId}/channels/{channelId}/messages/{messageId}";
    Object[] path = {workspace.getId(), channel.getId(), message.getId()};

    mockMvc
        .perform(get(base + "/context", path).cookie(fixtures.authCookie(member)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(base + "/replies", path).cookie(fixtures.authCookie(member)))
        .andExpect(status().isOk());

    String reactionBody = objectMapper.writeValueAsString(Map.of("emoji", "👍"));
    mockMvc
        .perform(
            post(base + "/reactions", path)
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactionBody))
        .andExpect(status().isOk());

    String editBody = objectMapper.writeValueAsString(Map.of("body", "hello, edited"));
    mockMvc
        .perform(
            patch(base, path)
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete(base, path).cookie(fixtures.authCookie(member)))
        .andExpect(status().isNoContent());
  }
}
