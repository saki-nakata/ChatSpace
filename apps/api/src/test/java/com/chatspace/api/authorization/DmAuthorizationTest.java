package com.chatspace.api.authorization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.dm.DmThread;
import com.chatspace.api.dm.DmThreadRepository;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * DM機能定義書§9・テスト設計書§6.1/§6.2(AUTH-P10・AUTH-N22)に対応する。
 *
 * <p>AUTH-N22は「ワークスペースからキックされたユーザーは、既知のdmIdさえあればDMの読み書きを継続できてしまう」という
 * プロトタイプの実在するギャップに対する必須修正の検証(DM機能定義書§6参照)。
 */
class DmAuthorizationTest extends AbstractIntegrationTest {

  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

  @Autowired private DmThreadRepository dmThreadRepository;

  @Autowired private DmAuthorizationService dmAuthorizationService;

  /** AUTH-P10: DMハンドル解決。ログインハンドルを指定してDMを開始でき、内部UUIDが返る。 */
  @Test
  void createDm_byHandle_resolvesToInternalUserId() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User other = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, other, WorkspaceRole.MEMBER);

    String body = objectMapper.writeValueAsString(Map.of("userId", other.getUserId()));

    MvcResult result =
        mockMvc
            .perform(
                post("/workspaces/{workspaceId}/dms", workspace.getId())
                    .cookie(fixtures.authCookie(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertEquals(other.getId().toString(), json.get("otherUser").get("id").asText());
  }

  /**
   * AUTH-N22: {@code DmAuthorizationService.requireDmAccess()} の単体/統合テスト。ワークスペースキック後、DM参加者 チェックは通るが
   * WorkspaceMember 再確認で失敗し、404相当の例外を投げること。
   */
  @Test
  @Transactional
  void requireDmAccess_afterWorkspaceMembershipRemoved_throwsNotFound() {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);

    UUID userAId = owner.getId().compareTo(member.getId()) < 0 ? owner.getId() : member.getId();
    UUID userBId = owner.getId().compareTo(member.getId()) < 0 ? member.getId() : owner.getId();
    DmThread dm = dmThreadRepository.save(new DmThread(workspace.getId(), userAId, userBId));

    // キック前は成功する(DM参加者かつワークスペースメンバー)
    assertDoesNotThrow(
        () ->
            dmAuthorizationService.requireDmAccess(dm.getId(), member.getId(), workspace.getId()));

    // ワークスペースキックを模擬(WorkspaceMemberのみ削除。DmThreadのuserAId/userBIdは消えない)
    workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspace.getId(), member.getId());

    // DM参加者チェックは依然として通るが、WorkspaceMember再確認で失敗し404相当の例外になること
    assertThrows(
        NotFoundException.class,
        () ->
            dmAuthorizationService.requireDmAccess(dm.getId(), member.getId(), workspace.getId()));
  }
}
