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
 * 検索機能定義書§9・テスト設計書§6.2(AUTH-N08・AUTH-N09)に対応する。
 *
 * <p>CLAUDE.mdが名指しで警告する「メッセージ検索は権限外のチャンネル/DMの内容を含めやすい」箇所であり最重要。
 */
class SearchAuthorizationTest extends AbstractIntegrationTest {

  /** AUTH-N08: 非所属チャンネルのメッセージが検索結果に含まれないこと。 */
  @Test
  void search_excludesMessagesFromNonMemberChannel() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User outsider = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);

    Channel channel =
        fixtures.createChannel(workspace, ChannelType.PUBLIC, owner); // outsiderは非メンバー
    postMessage(workspace, channel, owner, "会議の議事録を共有します");

    JsonNode results = search(workspace, outsider, "議事録");
    assertTrue(
        StreamSupport.stream(results.get("messages").spliterator(), false).findAny().isEmpty(),
        "非所属チャンネルのメッセージは検索結果に含まれないはず");
  }

  /** channelId指定時、非所属チャンネルなら403/404ではなく結果なしを返すこと(検索機能定義書§3.1)。 */
  @Test
  void search_withNonMemberChannelIdFilter_returnsEmptyNotError() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User outsider = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);
    postMessage(workspace, channel, owner, "非公開の会議メモ");

    MvcResult result =
        mockMvc
            .perform(
                get("/workspaces/{workspaceId}/search", workspace.getId())
                    .param("q", "会議")
                    .param("channelId", channel.getId().toString())
                    .cookie(fixtures.authCookie(outsider)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertTrue(
        StreamSupport.stream(body.get("messages").spliterator(), false).findAny().isEmpty(),
        "非所属チャンネル指定は403/404ではなく空の結果を返すはず");
  }

  /** AUTH-N09: ソフトデリート済みメッセージが検索結果に含まれないこと。 */
  @Test
  void search_excludesSoftDeletedMessages() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);
    UUID messageId = postMessage(workspace, channel, owner, "削除される予定のメッセージ本文");

    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/messages/{messageId}",
                    workspace.getId(),
                    channel.getId(),
                    messageId)
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());

    JsonNode results = search(workspace, owner, "削除される予定");
    assertTrue(
        StreamSupport.stream(results.get("messages").spliterator(), false).findAny().isEmpty(),
        "削除済みメッセージは検索結果に含まれないはず");
  }

  /** ワイルドカードインジェクション対策: `%`/`_`を含む検索語が文字どおりの一致として扱われること。 */
  @Test
  void search_wildcardCharactersAreEscaped() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner);
    postMessage(workspace, channel, owner, "進捗は50%_完了です");
    postMessage(workspace, channel, owner, "進捗はXX完了です"); // "%"/"_"をワイルドカードとして誤解釈すると誤ってヒットする

    JsonNode results = search(workspace, owner, "50%_完了");
    long matchCount =
        StreamSupport.stream(results.get("messages").spliterator(), false)
            .filter(n -> n.get("body").asText().contains("50%_完了"))
            .count();
    assertEquals(1, matchCount, "エスケープされたリテラル文字列にのみマッチするはず");
    boolean matchedWrongMessage =
        StreamSupport.stream(results.get("messages").spliterator(), false)
            .anyMatch(n -> n.get("body").asText().equals("進捗はXX完了です"));
    assertFalse(matchedWrongMessage, "ワイルドカードとして誤動作し無関係なメッセージにマッチしてはいけない");
  }

  /** ワークスペースキック後、DM検索対象からの除外(検索エンドポイント自体がワークスペーススコープのため404で担保される)。 */
  @Test
  void search_afterWorkspaceKick_returnsNotFound() throws Exception {
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
                .content(objectMapper.writeValueAsString(Map.of("body", "DM内の秘密の合言葉です"))))
        .andExpect(status().isCreated());

    // キック前は検索できる
    JsonNode beforeKick = search(workspace, member, "秘密の合言葉");
    assertTrue(
        StreamSupport.stream(beforeKick.get("messages").spliterator(), false).findAny().isPresent(),
        "キック前は参加中のDMメッセージが検索できるはず");

    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/kick", workspace.getId())
                .cookie(fixtures.authCookie(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("userId", member.getId().toString()))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/workspaces/{workspaceId}/search", workspace.getId())
                .param("q", "秘密の合言葉")
                .cookie(fixtures.authCookie(member)))
        .andExpect(status().isNotFound());
  }

  private UUID postMessage(Workspace workspace, Channel channel, User author, String body)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(
                        "/workspaces/{workspaceId}/channels/{channelId}/messages",
                        workspace.getId(),
                        channel.getId())
                    .cookie(fixtures.authCookie(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("body", body))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private JsonNode search(Workspace workspace, User caller, String query) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/workspaces/{workspaceId}/search", workspace.getId())
                    .param("q", query)
                    .cookie(fixtures.authCookie(caller)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
