package com.chatspace.api.message;

import com.chatspace.api.common.NotFoundException;
import java.util.UUID;

/**
 * confused-deputy対策(メッセージング機能定義書§6.1、最重要)。
 *
 * <p>{@code ChannelAuthorizationService.requireChannelMember}/{@code
 * DmAuthorizationService.requireDmAccess} は「呼び出し元がURLの channelId/dmId のメンバーか」だけを検証するため、パスに含まれる
 * {@code messageId} が実際にそのスコープに属しているかは別途保証しなければならない。{@code messageId} を受け取る全エンドポイント
 * (context取得・replies取得・編集・削除・リアクション切り替え・返信投稿時のparentId検証)の先頭で必ず呼ぶこと。
 *
 * <p>{@code MessageService} のミューテーション処理と同じパッケージに置くことで、認可チェックの取りこぼしを防ぐ (プロトタイプの {@code
 * messages-service.ts} の設計を踏襲、計画書§3)。
 */
final class MessageScopeGuard {

  private MessageScopeGuard() {}

  static void assertInScope(Message message, UUID channelId, UUID dmId) {
    boolean matches =
        (channelId != null && channelId.equals(message.getChannelId()))
            || (dmId != null && dmId.equals(message.getDmId()));
    if (!matches) {
      throw new NotFoundException("メッセージが見つかりません。");
    }
  }
}
