package com.chatspace.api.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** メッセージング機能定義書§9(新規受け入れテスト)に対応する: 2階層目スレッド返信の拒否、ソフトデリートのtombstone表示。 */
class MessageCrudIntegrationTest extends AbstractIntegrationTest {

  /** §3.1手順1・§7: 既に返信であるメッセージにさらに返信しようとすると400になること。 */
  @Test
  void replyToAReply_isRejectedWith400() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    String topLevelId = createMessage(workspace, channel, owner, "top-level", null);
    String replyId = createMessage(workspace, channel, owner, "first-level reply", topLevelId);

    String secondLevelBody =
        objectMapper.writeValueAsString(Map.of("body", "second-level reply", "parentId", replyId));
    mockMvc
        .perform(
            post(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages",
                    workspace.getId(),
                    channel.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondLevelBody))
        .andExpect(status().isBadRequest());
  }

  /** §3.3・§6.3: 削除済みメッセージは一覧から除外されず、tombstone(本文を伏せた行)として残ること。 */
  @Test
  void deletedMessage_remainsInListAsTombstone() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);

    String messageId = createMessage(workspace, channel, owner, "will be deleted", null);

    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages/{messageId}",
                    workspace.getId(),
                    channel.getId(),
                    messageId)
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());

    MvcResult listResult =
        mockMvc
            .perform(
                get(
                        "/workspaces/{workspaceId}/channels/{channelId}/messages",
                        workspace.getId(),
                        channel.getId())
                    .cookie(fixtures.authCookie(owner)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode messages =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).get("messages");
    JsonNode tombstone =
        StreamSupport.stream(messages.spliterator(), false)
            .filter(node -> node.get("id").asText().equals(messageId))
            .findFirst()
            .orElseThrow();
    assertTrue(tombstone.get("deleted").asBoolean());
    assertEquals("", tombstone.get("body").asText());
  }

  private String createMessage(
      Workspace workspace, Channel channel, User author, String body, String parentId)
      throws Exception {
    String requestBody =
        parentId == null
            ? objectMapper.writeValueAsString(Map.of("body", body))
            : objectMapper.writeValueAsString(Map.of("body", body, "parentId", parentId));
    MvcResult result =
        mockMvc
            .perform(
                post(
                        "/workspaces/{workspaceId}/channels/{channelId}/messages",
                        workspace.getId(),
                        channel.getId())
                    .cookie(fixtures.authCookie(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
