package com.chatspace.api.message;

import com.chatspace.api.common.Cursor;
import java.time.Instant;
import java.util.List;

/**
 * メッセージ一覧・スレッド返信一覧共通のカーソルページングレスポンス(メッセージング機能定義書§6.6)。
 *
 * <p>{@code callerLastReadAt} は呼び出し元(自分)のスコープ内既読位置。値が入るのは{@code list()}の初回ページ
 * (カーソル無し)呼び出し時のみで、ページング継続(2ページ目以降)・スレッド返信一覧(list Replies)では常に{@code null}
 * (未読区切り線の基準に使うフロントの初回読み込みでしか参照しないため)。
 */
public record MessageListResponse(
    List<MessageResponse> messages, Cursor nextCursor, Instant callerLastReadAt) {}
