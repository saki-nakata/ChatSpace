package com.chatspace.api.ratelimit;

import com.chatspace.api.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 認証系エンドポイントのレート制限(認証機能定義書§7、フェーズ12)。プロトタイプから継続していた 「ログイン試行が無制限」という既知ギャップを塞ぐ。
 *
 * <p><b>方式</b>: 固定ウィンドウ方式のインメモリカウンタ。フェーズ0-12は単一インスタンス前提
 * (インフラ構成書§1)のため共有ストア(Redis等)は用いない。水平スケール時はインスタンスごとに独立した
 * カウンタになり実効上限がインスタンス数倍になるため、フェーズ13で共有ストアへの移行を検討する。
 *
 * <p><b>「確認してから記録する」形にしない</b>(レビュー指摘対応): 以前は「ブロック中か確認」→ パスワード照合
 * →「失敗を記録」という3操作に分かれていたため、同一キーの多数のリクエストを同時に 開始すると<b>全てがカウンタ更新前の確認をすり抜け</b>、上限を超えてbcrypt照合まで到達できた。
 * 現在は{@link #acquireAttempt(String)}が{@code ConcurrentHashMap#compute}の中で「上限確認」と
 * 「試行回数の加算」を原子的に行うため、同時実行でも上限を超えられない。
 *
 * <p><b>失敗回数ではなく試行回数を数える</b>: 上記の原子性を成立させるには照合結果を待てないため、 照合前に試行枠を確保する方式にした。認証に成功した場合は{@link
 * #reset(String)}でカウンタを破棄するため、 正当な利用者から見た挙動は従来と変わらない(打ち間違い後に成功すればカウンタは残らない)。
 *
 * <p><b>キー設計</b>: 呼び出し側が「試行対象のユーザーID + クライアントIP」を組み合わせたキーを渡す。
 * IP単独だと同一NAT配下の無関係な利用者を巻き込み、ユーザーID単独だと攻撃者が任意のアカウントを ロックアウトできてしまう(DoS)ため、両方を含めることでどちらの副作用も避ける。
 *
 * <p><b>メモリ枯渇への耐性</b>(レビュー指摘対応): 未認証の攻撃者はユーザーIDとIPの組を変えることで 任意個のキーを生成できる。{@link
 * #MAX_TRACKED_KEYS}を厳密な上限とし、超過時は古いエントリから 追い出す。全件走査は{@link
 * #MIN_PURGE_INTERVAL}間隔に制限し、リクエストごとの反復走査を防ぐ。
 */
@Component
public class AuthRateLimiter {

  /**
   * 追跡するキー数の厳密な上限。超過時は古いエントリを追い出してでもこの数を維持する (メモリ枯渇を防ぐことを、個々のブロック状態を保持し続けることより優先する)。
   *
   * <p>1エントリは数十バイト程度のため、5万件でも数MB規模に収まる。
   */
  static final int MAX_TRACKED_KEYS = 50_000;

  /** 全件走査の最小間隔。上限に達した状態でリクエストが殺到しても、走査がCPUを食い潰さないようにする。 */
  private static final Duration MIN_PURGE_INTERVAL = Duration.ofSeconds(10);

  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
  private final AtomicReference<Instant> lastPurgeAt = new AtomicReference<>(Instant.EPOCH);
  private final Clock clock;
  private final int maxAttempts;
  private final Duration window;
  private final Duration blockDuration;

  public AuthRateLimiter(
      Clock clock,
      @Value("${chatspace.auth-rate-limit.max-attempts}") int maxAttempts,
      @Value("${chatspace.auth-rate-limit.window}") Duration window,
      @Value("${chatspace.auth-rate-limit.block-duration}") Duration blockDuration) {
    this.clock = clock;
    this.maxAttempts = maxAttempts;
    this.window = window;
    this.blockDuration = blockDuration;
  }

  /**
   * 1回分の試行枠を原子的に確保する。認証処理を実行する<b>前</b>に呼ぶこと。
   *
   * <p>ウィンドウ内の試行回数が上限を超えた場合、またはブロック期間中の場合は {@link
   * TooManyRequestsException}を投げる(パスワード照合自体を実行させない)。ブロック中の追加試行は
   * ブロックを延長する(ウィンドウ経過でカウンタが振り出しに戻ると、解除直後に再び上限回数まで試せてしまい 総当たりの実効速度が上がるため)。
   */
  public void acquireAttempt(String key) {
    Instant now = clock.instant();
    enforceCapacity(now);
    Attempt result =
        attempts.compute(
            key,
            (ignored, current) -> {
              if (current == null || current.isWindowExpired(now, window)) {
                return Attempt.firstAttempt(now);
              }
              return current.withAdditionalAttempt(now, maxAttempts, blockDuration);
            });
    Instant blockedUntil = result.blockedUntil();
    if (blockedUntil != null && now.isBefore(blockedUntil)) {
      throw new TooManyRequestsException(Duration.between(now, blockedUntil));
    }
  }

  /** 認証成功時に呼び、試行カウンタを破棄する。 */
  public void reset(String key) {
    attempts.remove(key);
  }

  /** テスト・監視用に、現在追跡しているキー数を返す。 */
  public int trackedKeyCount() {
    return attempts.size();
  }

  /**
   * キー数を{@link #MAX_TRACKED_KEYS}以下に保つ。
   *
   * <p>まず期限切れ(ウィンドウもブロックも過ぎた)エントリを回収し、それでも上限に達している場合は
   * 走査順に追い出す。ブロック中のエントリも追い出し対象に含める点はトレードオフで、「攻撃者が大量の
   * キーを作ってブロック状態を早期に解除させうる」ことより「メモリ枯渇でサービス全体が停止する」ことを 重く見た判断(単一インスタンス前提のため後者の影響が大きい)。
   */
  private void enforceCapacity(Instant now) {
    if (attempts.size() < MAX_TRACKED_KEYS) {
      return;
    }
    Instant lastPurge = lastPurgeAt.get();
    boolean intervalElapsed = !now.isBefore(lastPurge.plus(MIN_PURGE_INTERVAL));
    if (intervalElapsed && lastPurgeAt.compareAndSet(lastPurge, now)) {
      attempts.values().removeIf(attempt -> attempt.isFullyExpired(now, window));
    }
    // 期限切れ回収後もなお上限に達している場合は、1回の走査で上限未満まで追い出す
    if (attempts.size() >= MAX_TRACKED_KEYS) {
      Iterator<Map.Entry<String, Attempt>> iterator = attempts.entrySet().iterator();
      while (iterator.hasNext() && attempts.size() >= MAX_TRACKED_KEYS) {
        iterator.next();
        iterator.remove();
      }
    }
  }

  /**
   * 1キー分の試行状況。
   *
   * @param windowStart 現在のウィンドウの開始時刻
   * @param attemptCount ウィンドウ内の試行回数(失敗回数ではない。クラスコメント参照)
   * @param blockedUntil ブロック解除時刻(ブロックされていなければ{@code null})
   */
  private record Attempt(Instant windowStart, int attemptCount, Instant blockedUntil) {

    static Attempt firstAttempt(Instant now) {
      return new Attempt(now, 1, null);
    }

    Attempt withAdditionalAttempt(Instant now, int maxAttempts, Duration blockDuration) {
      int nextCount = attemptCount + 1;
      // 上限「回」までは試行を通し、それを超えた時点でブロックする
      Instant nextBlockedUntil = nextCount > maxAttempts ? now.plus(blockDuration) : blockedUntil;
      return new Attempt(windowStart, nextCount, nextBlockedUntil);
    }

    boolean isWindowExpired(Instant now, Duration window) {
      // ブロック中はウィンドウ経過でリセットしない(ブロック解除直後に再び上限回数まで試せてしまうため)
      if (blockedUntil != null && now.isBefore(blockedUntil)) {
        return false;
      }
      return !now.isBefore(windowStart.plus(window));
    }

    boolean isFullyExpired(Instant now, Duration window) {
      boolean windowDone = !now.isBefore(windowStart.plus(window));
      boolean blockDone = blockedUntil == null || !now.isBefore(blockedUntil);
      return windowDone && blockDone;
    }
  }
}
