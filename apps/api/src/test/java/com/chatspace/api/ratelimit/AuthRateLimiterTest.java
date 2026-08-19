package com.chatspace.api.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatspace.api.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * レート制限の単体テスト(認証機能定義書§7、フェーズ12)。
 *
 * <p>時間経過は{@link MutableClock}で仮想的に進める(実時間の{@code Thread.sleep}に依存させない)。
 */
class AuthRateLimiterTest {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration BLOCK = Duration.ofMinutes(15);
  private static final String KEY = "login|alice|198.51.100.10";

  /** 上限回数までの試行は通ること(正当な打ち間違いを妨げない)。 */
  @Test
  void allowsUpToMaxAttempts() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      assertDoesNotThrow(() -> limiter.acquireAttempt(KEY), "上限回数までは試行できるはず");
    }
  }

  /** 上限回数を超えた時点でブロックされ、429相当の例外になること。 */
  @Test
  void blocksAfterExceedingMaxAttempts() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      limiter.acquireAttempt(KEY);
    }

    TooManyRequestsException ex =
        assertThrows(TooManyRequestsException.class, () -> limiter.acquireAttempt(KEY));
    assertTrue(
        ex.getRetryAfter().toSeconds() > 0 && ex.getRetryAfter().compareTo(BLOCK) <= 0,
        "Retry-Afterはブロック残り時間(0超〜block-duration以内)になるはず");
  }

  /** ブロック期間が明ければ再び試行できること(恒久ロックアウトにしない)。 */
  @Test
  void allowsAgainAfterBlockDurationElapses() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS + 1; i++) {
      try {
        limiter.acquireAttempt(KEY);
      } catch (TooManyRequestsException expected) {
        // 上限超過時の例外はここでは検証対象外
      }
    }
    assertThrows(TooManyRequestsException.class, () -> limiter.acquireAttempt(KEY));

    clock.advance(BLOCK.plusSeconds(1));
    assertDoesNotThrow(() -> limiter.acquireAttempt(KEY), "ブロック期間経過後は再び試行できるはず");
  }

  /** ウィンドウをまたいだ散発的な失敗ではブロックされないこと(カウンタが持ち越されない)。 */
  @Test
  void failuresSpreadAcrossWindowsDoNotAccumulate() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS * 3; i++) {
      assertDoesNotThrow(() -> limiter.acquireAttempt(KEY), "ウィンドウを跨いだ試行は累積しないはず");
      clock.advance(WINDOW.plusSeconds(1));
    }
  }

  /** ログイン成功でカウンタが破棄されること(打ち間違い後に成功した利用者を巻き込まない)。 */
  @Test
  void resetClearsAccumulatedFailures() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      limiter.acquireAttempt(KEY);
    }
    limiter.reset(KEY);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      assertDoesNotThrow(() -> limiter.acquireAttempt(KEY), "リセット後は試行回数が0から数え直されるはず");
    }
  }

  /** キーが異なれば互いに影響しないこと(他ユーザー・他IPを巻き込まない)。 */
  @Test
  void keysAreIsolatedFromEachOther() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      limiter.acquireAttempt(KEY);
    }
    assertThrows(TooManyRequestsException.class, () -> limiter.acquireAttempt(KEY));

    String otherUserSameIp = ClientAddress.loginKey("bob", "198.51.100.10");
    String sameUserOtherIp = ClientAddress.loginKey("alice", "203.0.113.7");
    assertDoesNotThrow(() -> limiter.acquireAttempt(otherUserSameIp), "別ユーザーは巻き込まれないはず");
    assertDoesNotThrow(() -> limiter.acquireAttempt(sameUserOtherIp), "別IPは巻き込まれないはず");
  }

  /**
   * ブロック中にさらに試行を重ねた場合、カウンタが振り出しに戻らずブロックが延長されること。
   *
   * <p>ウィンドウ経過でカウンタをリセットしてしまうと、ブロック中に叩き続けた攻撃者がブロック解除直後に 再び上限回数まで試行できてしまい、総当たりの実効速度が上がる。{@code
   * Attempt#isWindowExpired}が ブロック中はウィンドウ経過を無視する実装になっていることの回帰テスト。
   */
  @Test
  void attemptsDuringBlockExtendItInsteadOfResettingTheCounter() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      limiter.acquireAttempt(KEY);
    }
    assertThrows(TooManyRequestsException.class, () -> limiter.acquireAttempt(KEY));

    // 当初のブロック解除予定時刻の直前まで進め、そこで追加試行する
    clock.advance(BLOCK.minusMinutes(1));
    assertThrows(TooManyRequestsException.class, () -> limiter.acquireAttempt(KEY));

    // 当初の解除予定時刻を過ぎてもなお、延長されたブロックが継続していること
    clock.advance(Duration.ofMinutes(2));
    assertThrows(
        TooManyRequestsException.class,
        () -> limiter.acquireAttempt(KEY),
        "ブロック中の追加試行によりブロックが延長されるはず");
  }

  /**
   * <b>同時リクエストで上限を突破できないこと</b>(レビュー指摘の回帰テスト)。
   *
   * <p>以前は「ブロック確認」と「失敗の記録」が別操作だったため、同一キーの多数のリクエストを同時に 開始すると全てが確認をすり抜け、上限を超えてパスワード照合まで到達できた。多数のスレッドを
   * {@link CountDownLatch}で同時に走らせ、実際に通過した試行が上限回数ちょうどであることを検証する。
   */
  @Test
  void concurrentAttemptsCannotExceedTheLimit() throws Exception {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    int threadCount = 64;
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(threadCount);
    AtomicInteger allowed = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        pool.submit(
            () -> {
              try {
                startSignal.await();
                limiter.acquireAttempt(KEY);
                allowed.incrementAndGet();
              } catch (TooManyRequestsException e) {
                rejected.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                finished.countDown();
              }
            });
      }
      startSignal.countDown();
      assertTrue(finished.await(30, TimeUnit.SECONDS), "全スレッドが終了するはず");
    }

    assertEquals(
        MAX_ATTEMPTS, allowed.get(), "同時実行でも、通過できる試行は設定値ちょうどに限られるはず(超過するとbcrypt照合まで到達できてしまう)");
    assertEquals(threadCount - MAX_ATTEMPTS, rejected.get(), "残りは全て429として拒否されるはず");
  }

  /** キー数が上限に達しても、際限なく増え続けないこと(未認証の攻撃者によるメモリ枯渇の防止)。 */
  @Test
  void trackedKeysAreBoundedEvenWhenAttackerGeneratesManyKeys() {
    MutableClock clock = new MutableClock();
    AuthRateLimiter limiter = newLimiter(clock);

    // 上限を超える数のユニークなキー(攻撃者はユーザーIDとIPの組を自由に変えられる)
    int attackKeyCount = AuthRateLimiter.MAX_TRACKED_KEYS + 5_000;
    for (int i = 0; i < attackKeyCount; i++) {
      limiter.acquireAttempt(ClientAddress.loginKey("user" + i, "203.0.113." + (i % 256)));
    }

    assertTrue(
        limiter.trackedKeyCount() <= AuthRateLimiter.MAX_TRACKED_KEYS,
        "追跡キー数は上限以下に保たれるはず(実際: " + limiter.trackedKeyCount() + ")");
  }

  /** レート制限キーがユーザーIDとIPの両方で分かれること。 */
  @Test
  void loginKeyDistinguishesUserIdAndAddress() {
    assertEquals(
        ClientAddress.loginKey("alice", "198.51.100.10"),
        ClientAddress.loginKey("alice", "198.51.100.10"));
    assertFalse(
        ClientAddress.loginKey("alice", "198.51.100.10")
            .equals(ClientAddress.loginKey("alice", "198.51.100.11")));
    assertFalse(
        ClientAddress.loginKey("alice", "198.51.100.10")
            .equals(ClientAddress.loginKey("alicE", "198.51.100.10")));
  }

  private AuthRateLimiter newLimiter(Clock clock) {
    return new AuthRateLimiter(clock, MAX_ATTEMPTS, WINDOW, BLOCK);
  }

  /** テストから任意に時間を進められる{@link Clock}。 */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-08-20T00:00:00Z");

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }
}
