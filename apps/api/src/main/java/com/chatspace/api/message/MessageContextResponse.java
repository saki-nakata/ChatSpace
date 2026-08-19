package com.chatspace.api.message;

import com.chatspace.api.common.Cursor;
import java.util.List;
import java.util.UUID;

/**
 * 検索ジャンプ用のコンテキスト(around)取得レスポンス(メッセージング機能定義書§3.6)。
 *
 * <p>{@code targetMessageId} は要求された {@code messageId} そのもの。対象がスレッド返信だった場合、{@code messages}
 * は親メッセージを軸にウィンドウを組み、{@code replyMessageId} に元の返信IDを設定してクライアントがスレッドパネルを開けるようにする
 * (通常のトップレベルメッセージが対象の場合は {@code replyMessageId} はnull)。
 */
public record MessageContextResponse(
    List<MessageResponse> messages,
    UUID targetMessageId,
    UUID replyMessageId,
    Cursor olderCursor,
    Cursor newerCursor,
    boolean hasOlder,
    boolean hasNewer) {}
