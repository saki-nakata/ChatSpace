package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.dm.DmThread;
import com.chatspace.api.dm.DmThreadRepository;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.support.AbstractIntegrationTest;
import com.chatspace.api.user.User;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * DM機能定義書§9・テスト設計書§6.2(AUTH-N23)に対応する。ワークスペースキック後、DMメッセージ取得の実エンドポイントが
 * 404になることをHTTP経由で確認する(AUTH-N22はフェーズ2でService単体テスト済み、本テストはHTTPエンドポイント版)。
 */
class DmMessageKickAuthorizationTest extends AbstractIntegrationTest {

  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

  @Autowired private DmThreadRepository dmThreadRepository;

  @Autowired private MessageRepository messageRepository;

  @Test
  @Transactional
  void dmMessageList_afterWorkspaceKick_returns404() throws Exception {
    User owner = fixtures.createUser();
    Workspace workspace = fixtures.createWorkspaceWithOwner(owner);
    User member = fixtures.createUser();
    fixtures.addWorkspaceMember(workspace, member, WorkspaceRole.MEMBER);

    UUID userAId = owner.getId().compareTo(member.getId()) < 0 ? owner.getId() : member.getId();
    UUID userBId = owner.getId().compareTo(member.getId()) < 0 ? member.getId() : owner.getId();
    DmThread dm = dmThreadRepository.save(new DmThread(workspace.getId(), userAId, userBId));
    messageRepository.save(new Message(null, dm.getId(), null, owner.getId(), "hi"));

    // ワークスペースキックを模擬(WorkspaceMemberのみ削除。DmThreadは残る)
    workspaceMemberRepository.deleteByWorkspaceIdAndUserId(workspace.getId(), member.getId());

    mockMvc
        .perform(
            get("/workspaces/{workspaceId}/dms/{dmId}/messages", workspace.getId(), dm.getId())
                .cookie(fixtures.authCookie(member)))
        .andExpect(status().isNotFound());
  }
}
