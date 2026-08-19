package com.chatspace.api.dm;

import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * DMアクセスの認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>DM機能定義書§6.2の通り、DM参加者チェックに加えて**呼び出し時点で有効なWorkspaceMemberであること**をANDで検証する
 * (ワークスペースキック後もDM参加者情報自体は消えないため、参加者チェックのみでは不十分な、実在するギャップへの対応)。
 */
@Service
public class DmAuthorizationService {

  private final DmThreadRepository dmThreadRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;

  public DmAuthorizationService(
      DmThreadRepository dmThreadRepository, WorkspaceMemberRepository workspaceMemberRepository) {
    this.dmThreadRepository = dmThreadRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
  }

  /**
   * DM参加者チェックと現在有効なWorkspaceMemberチェックの両方を満たさない限り成功させない。いずれか一方でも欠ける場合は
   * 同一の404(DM機能定義書§6.2、ワークスペースメンバーシップ切れであることを403で区別しない)。
   */
  public DmThread requireDmAccess(UUID dmId, UUID userId, UUID workspaceIdOrNull) {
    DmThread dm = dmThreadRepository.findById(dmId).orElseThrow(this::notFound);
    if (workspaceIdOrNull != null && !dm.getWorkspaceId().equals(workspaceIdOrNull)) {
      throw notFound();
    }
    if (!dm.getUserAId().equals(userId) && !dm.getUserBId().equals(userId)) {
      throw notFound();
    }
    if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(dm.getWorkspaceId(), userId)) {
      throw notFound();
    }
    return dm;
  }

  private NotFoundException notFound() {
    return new NotFoundException("DMが見つかりません。");
  }
}
