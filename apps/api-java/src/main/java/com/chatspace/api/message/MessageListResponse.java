package com.chatspace.api.message;

import java.util.List;

/** メッセージ一覧・スレッド返信一覧共通のカーソルページングレスポンス(メッセージング機能定義書§6.6)。 */
public record MessageListResponse(List<MessageResponse> messages, Cursor nextCursor) {}
