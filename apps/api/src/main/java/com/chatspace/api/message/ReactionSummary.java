package com.chatspace.api.message;

import java.util.List;
import java.util.UUID;

/** リアクション機能定義書§3.2のレスポンス仕様に対応する。 */
public record ReactionSummary(String emoji, long count, boolean reactedByMe, List<UUID> userIds) {}
