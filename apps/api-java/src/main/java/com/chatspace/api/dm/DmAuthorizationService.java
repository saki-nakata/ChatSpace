package com.chatspace.api.dm;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * DMアクセスの認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>フェーズ1時点ではメソッドシグネチャのみ(骨格)。本体実装はフェーズ2で完成させる。
 */
@Service
public class DmAuthorizationService {

  /**
   * 呼び出し元ユーザーがDM参加者(userAId/userBId)であることに加え、現在有効な {@code WorkspaceMember}
   * であることも必須条件として検証する(ワークスペースキック後もDM参加者情報自体は消えないため、参加者チェックのみでは不十分。 DB設計書§3.6設計上の注意点)。いずれか欠けても
   * {@code NotFoundException}(404)。{@code workspaceIdOrNull}
   * が指定された場合はconfused-deputy対策としてDMスレッドの実所属ワークスペースとの一致も確認する。
   */
  public void requireDmAccess(UUID dmId, UUID userId, UUID workspaceIdOrNull) {
    throw new UnsupportedOperationException("フェーズ2で実装");
  }
}
