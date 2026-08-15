package com.chatspace.api.channel;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * チャンネルメンバーシップの認可チェックを担う(計画書§3、404-not-403方針)。
 *
 * <p>フェーズ1時点ではメソッドシグネチャのみ(骨格)。本体実装はフェーズ2で完成させる。
 */
@Service
public class ChannelAuthorizationService {

  /**
   * 呼び出し元ユーザーが対象チャンネルの現在有効なメンバーであることを検証する。非メンバー・非公開チャンネルへの非参加は {@code
   * NotFoundException}(404)。{@code workspaceIdOrNull} が指定された場合はconfused-deputy対策として
   * チャンネルの実所属ワークスペースとの一致も確認する。
   */
  public void requireChannelMember(UUID channelId, UUID userId, UUID workspaceIdOrNull) {
    throw new UnsupportedOperationException("フェーズ2で実装");
  }
}
