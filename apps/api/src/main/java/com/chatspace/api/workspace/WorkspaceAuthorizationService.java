package com.chatspace.api.workspace;

import com.chatspace.api.common.ForbiddenException;
import com.chatspace.api.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * ワークスペースメンバーシップ・オーナー権限の認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>ワークスペース機能定義書§6を正とする。
 */
@Service
public class WorkspaceAuthorizationService {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  public WorkspaceAuthorizationService(WorkspaceMemberRepository workspaceMemberRepository) {
    this.workspaceMemberRepository = workspaceMemberRepository;
  }

  /** 呼び出し元ユーザーが対象ワークスペースの現在有効なメンバーであることを検証する。非メンバーは404(存在自体を秘匿)。 */
  public WorkspaceMember requireMember(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository
        .findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow(() -> new NotFoundException("ワークスペースが見つかりません。"));
  }

  /**
   * 呼び出し元ユーザーが対象ワークスペースのオーナーであることを検証する。非メンバーは404(メンバーチェックが先に走るため、
   * オーナー限定操作の失敗理由から「メンバーかどうか」が漏れない)。メンバーだがオーナーでない場合は403。
   */
  public WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
    WorkspaceMember member = requireMember(workspaceId, userId);
    if (member.getRole() != WorkspaceRole.OWNER) {
      throw new ForbiddenException("この操作にはオーナー権限が必要です。");
    }
    return member;
  }
}
