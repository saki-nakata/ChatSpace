package com.chatspace.api.workspace;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * ワークスペースメンバーシップ・オーナー権限の認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>フェーズ1時点ではメソッドシグネチャのみ(骨格)。本体実装はフェーズ2で完成させる。
 */
@Service
public class WorkspaceAuthorizationService {

  /** 呼び出し元ユーザーが対象ワークスペースの現在有効なメンバーであることを検証する。非メンバーは {@code NotFoundException}(404)。 */
  public void requireMember(UUID workspaceId, UUID userId) {
    throw new UnsupportedOperationException("フェーズ2で実装");
  }

  /** 呼び出し元ユーザーが対象ワークスペースのオーナーであることを検証する。オーナー以外は {@code ForbiddenException}(403)。 */
  public void requireOwner(UUID workspaceId, UUID userId) {
    throw new UnsupportedOperationException("フェーズ2で実装");
  }
}
