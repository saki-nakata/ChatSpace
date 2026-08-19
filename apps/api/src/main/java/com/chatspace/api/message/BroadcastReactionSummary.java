package com.chatspace.api.message;

import java.util.List;
import java.util.UUID;

/**
 * STOMPブロードキャスト用のリアクション集計(レビュー指摘対応)。
 *
 * <p>{@link ReactionSummary#reactedByMe()}は「REST応答を要求した本人が押したか」を表す値であり、STOMPの1つの
 * 配信は購読者全員に同一ペイロードが届くため、そのまま流用すると操作した本人の視点が全員に配信されてしまう (Bさん・Cさんの画面にも、実際には押していないのに{@code
 * reactedByMe: true}が届く不具合)。配信用ペイロードでは この値を持たせず、クライアント側が{@code userIds}に自分のIDが含まれるかで判定する設計に変更する。
 */
public record BroadcastReactionSummary(String emoji, long count, List<UUID> userIds) {

  static BroadcastReactionSummary from(ReactionSummary summary) {
    return new BroadcastReactionSummary(summary.emoji(), summary.count(), summary.userIds());
  }
}
