import type { MessageAttachmentResponse } from "../../api/types";

/**
 * チャットUI内部で使う正規化済みメッセージ型。REST初回取得(`MessageResponse`)とSTOMP配信
 * (`BroadcastMessageResponse`、reactedByMeを持たない)のどちらから来ても同じ形に揃える。
 * 「自分が押したか」は{@link DisplayReaction.userIds}に自分のIDが含まれるかで判定する
 * (レビュー指摘対応: サーバーの操作者視点reactedByMeをそのまま信用しない設計)。
 */
export interface DisplayReaction {
  emoji: string;
  count: number;
  userIds: string[];
}

export interface DisplayMessage {
  id: string;
  channelId?: string | null;
  dmId?: string | null;
  parentId?: string | null;
  authorId: string;
  body: string;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
  editedAt?: string | null;
  replyCount: number;
  reactions: DisplayReaction[];
  attachments: MessageAttachmentResponse[];
}
