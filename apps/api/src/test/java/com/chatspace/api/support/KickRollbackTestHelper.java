package com.chatspace.api.support;

import com.chatspace.api.workspace.WorkspaceService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-N12(キック確定の AFTER_COMMIT 保証)専用のテスト補助コンポーネント。
 *
 * <p>{@code WorkspaceService#kick}は{@code @Transactional(REQUIRED)}のため、外側のトランザクションから呼ぶと
 * それに参加する。このヘルパーは呼び出し直後に例外を投げて外側ごとロールバックさせることで、「後続処理の失敗により キックがロールバックした」状況を再現する。{@code
 * MemberKickedEventListener}は{@code AFTER_COMMIT}で 動作するため、この場合は強制切断が一切発生しないことが期待値となる。
 *
 * <p>本番コードに「意図的に失敗する経路」を持ち込まないよう、テストソースセット側にのみ置く({@code @SpringBootTest}のコンポーネントスキャンは{@code
 * com.chatspace.api}配下のテストクラスも拾うため、 {@link AuthorizationTestFixtures}と同じくDIで注入できる)。
 */
@Component
public class KickRollbackTestHelper {

  /** ロールバックのトリガーとして投げる例外。テスト側で捕捉して意図した経路であることを確認する。 */
  public static class IntentionalRollbackException extends RuntimeException {
    public IntentionalRollbackException() {
      super("AUTH-N12: テスト用の意図的なロールバック");
    }
  }

  private final WorkspaceService workspaceService;

  public KickRollbackTestHelper(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  /** キックを実行した直後に例外を投げ、同一トランザクションごとロールバックさせる。 */
  @Transactional
  public void kickThenRollback(UUID workspaceId, UUID callerId, UUID targetUserId) {
    workspaceService.kick(workspaceId, callerId, targetUserId);
    throw new IntentionalRollbackException();
  }
}
