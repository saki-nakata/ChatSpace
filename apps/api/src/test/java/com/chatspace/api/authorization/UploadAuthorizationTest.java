package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceRole;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 添付ファイル機能定義書§9・テスト設計書§6.2(AUTH-N07・AUTH-N25)に対応する。
 *
 * <p>取得の都度のライブ権限再チェックは、プロトタイプの実装コード自体には存在するが既存テストで検証されていなかった (新規の受け入れテストとして追加する対象)。
 */
class UploadAuthorizationTest extends AbstractIntegrationTest {

  private static final byte[] PNG_BYTES = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4
  };

  private record Uploaded(UUID attachmentId, String storageKey) {}

  /** AUTH-N07: アップロード後にチャンネルからキックされたユーザーが、以後添付ファイルを取得できなくなること。 */
  @Test
  void serve_afterChannelKick_returnsNotFound() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    Channel channel = fixtures.createChannel(workspace, ChannelType.PUBLIC, owner, member);

    Uploaded uploaded = upload(member);
    postChannelMessageWithAttachment(workspace, channel, member, uploaded.attachmentId());

    // キック前は取得できる
    mockMvc
        .perform(
            get("/uploads/{storageKey}", uploaded.storageKey()).cookie(fixtures.authCookie(member)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete(
                    "/workspaces/{workspaceId}/channels/{channelId}/members/{targetUserId}",
                    workspace.getId(),
                    channel.getId(),
                    member.getId())
                .cookie(fixtures.authCookie(owner)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/uploads/{storageKey}", uploaded.storageKey()).cookie(fixtures.authCookie(member)))
        .andExpect(status().isNotFound());
  }

  /** AUTH-N25: ワークスペースキック後、DM添付ファイルを取得できないこと。 */
  @Test
  void serve_afterWorkspaceKick_dmAttachmentReturnsNotFound() throws Exception {
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

    Uploaded uploaded = upload(member);
    mockMvc
        .perform(
            post("/workspaces/{workspaceId}/dms/{dmId}/messages", workspace.getId(), dmId)
                .cookie(fixtures.authCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "body", "画像を送ります", "attachmentIds", List.of(uploaded.attachmentId())))))
        .andExpect(status().isCreated());

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
            get("/uploads/{storageKey}", uploaded.storageKey()).cookie(fixtures.authCookie(member)))
        .andExpect(status().isNotFound());
  }

  /** 存在しない(未アップロードの)storageKeyは404で、パス構造の不正も同様に404で拒否されること。 */
  @Test
  void serve_unknownOrMalformedStorageKey_returnsNotFound() throws Exception {
    User user = fixtures.createUser();

    mockMvc
        .perform(
            get("/uploads/{storageKey}", "00000000-0000-0000-0000-000000000000.png")
                .cookie(fixtures.authCookie(user)))
        .andExpect(status().isNotFound());

    // エンコードされたパストラバーサル文字列はSpringのルーティング層で400として拒否される(自前のstorageKey検証に
    // 到達する前の防御)。到達したとしても正規表現・パス正規化チェック(UploadService#serve)で404になる設計だが、
    // どちらの層で拒否されても「読み出しに成功しない」ことが重要なため4xxで検証する。
    mockMvc
        .perform(
            get("/uploads/{storageKey}", "..%2f..%2fsecret.png").cookie(fixtures.authCookie(user)))
        .andExpect(status().is4xxClientError());
  }

  /** マジックバイト検証: 拡張子を偽装したファイル(実データがPNG等のシグネチャと一致しない)は400で拒否されること。 */
  @Test
  void upload_extensionSpoofedFile_returns400() throws Exception {
    User user = fixtures.createUser();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "innocent.png",
            MediaType.IMAGE_PNG_VALUE,
            "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/uploads").file(file).cookie(fixtures.authCookie(user)))
        .andExpect(status().isBadRequest());
  }

  /** アップロードサイズ上限の多層防御・二段目(サービス層でのMultipartFile#getSize()再チェック)。 */
  @Test
  void upload_exceedsSizeLimit_returns400() throws Exception {
    User user = fixtures.createUser();
    byte[] oversized =
        new byte[26 * 1024 * 1024 + 1]; // chatspace.max-attachment-size-bytes(25MB)超過
    System.arraycopy(PNG_BYTES, 0, oversized, 0, PNG_BYTES.length);
    MockMultipartFile file =
        new MockMultipartFile("file", "big.png", MediaType.IMAGE_PNG_VALUE, oversized);

    mockMvc
        .perform(multipart("/uploads").file(file).cookie(fixtures.authCookie(user)))
        .andExpect(status().isBadRequest());
  }

  /** 正当な範囲(アップロード直後、投稿前プレビュー段階)ではアップロード本人が取得できること(過剰ブロック検証)。 */
  @Test
  void serve_unattachedFile_ownerCanAccessButOthersCannot() throws Exception {
    User uploader = fixtures.createUser();
    User other = fixtures.createUser();
    Uploaded uploaded = upload(uploader);

    mockMvc
        .perform(
            get("/uploads/{storageKey}", uploaded.storageKey())
                .cookie(fixtures.authCookie(uploader)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/uploads/{storageKey}", uploaded.storageKey()).cookie(fixtures.authCookie(other)))
        .andExpect(status().isNotFound());
  }

  private void postChannelMessageWithAttachment(
      Workspace workspace, Channel channel, User author, UUID attachmentId) throws Exception {
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
                        Map.of("body", "画像を送ります", "attachmentIds", List.of(attachmentId)))))
        .andExpect(status().isCreated());
  }

  private Uploaded upload(User uploader) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES);
    MvcResult result =
        mockMvc
            .perform(multipart("/uploads").file(file).cookie(fixtures.authCookie(uploader)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode attachment =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("attachment");
    UUID attachmentId = UUID.fromString(attachment.get("id").asText());
    String url = attachment.get("url").asText(); // "/uploads/{storageKey}"
    String storageKey = url.substring("/uploads/".length());
    return new Uploaded(attachmentId, storageKey);
  }
}
