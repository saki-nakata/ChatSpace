package com.chatspace.api.common;

import java.time.Duration;

/**
 * レート制限超過(HTTP 429)。{@code Retry-After}ヘッダに載せる待機時間を保持する。
 *
 * <p>認証機能定義書§7のブルートフォース対策(フェーズ12で追加)で使用する。
 */
public class TooManyRequestsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyRequestsException(Duration retryAfter) {
    super("試行回数の上限を超えました。しばらく時間をおいてから再度お試しください。");
    this.retryAfter = retryAfter;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }
}
