package com.chatspace.api.upload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** {@link AttachmentStorage}のローカルディスク実装。単一インスタンス構成(フェーズ0-12)・開発/テスト環境向け(既定実装)。 */
@Component
@ConditionalOnProperty(
    prefix = "chatspace.storage",
    name = "type",
    havingValue = "local",
    matchIfMissing = true)
public class LocalDiskAttachmentStorage implements AttachmentStorage {

  private final Path uploadDir;

  public LocalDiskAttachmentStorage(@Value("${chatspace.upload-dir}") String uploadDir) {
    this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.uploadDir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void put(String storageKey, byte[] content) {
    try {
      Files.write(uploadDir.resolve(storageKey), content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** パストラバーサル対策(添付ファイル機能定義書§3.2): 正規化後のパスが{@code uploadDir}配下であることを再確認する。 */
  @Override
  public boolean exists(String storageKey) {
    return resolveWithinUploadDir(storageKey).map(Files::isRegularFile).orElse(false);
  }

  /**
   * 呼び出し側は{@link #exists(String)}が{@code true}を返した後にのみ呼ぶ契約だが、将来の呼び出し順序の実装ミスに
   * 備えてここでも同じパストラバーサル検証を再確認する(二重防御、レビュー指摘対応)。
   */
  @Override
  public Resource load(String storageKey) {
    Path target =
        resolveWithinUploadDir(storageKey)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "exists()での検証を経ずにload()が呼ばれた不正なstorageKeyです: " + storageKey));
    return new FileSystemResource(target);
  }

  private Optional<Path> resolveWithinUploadDir(String storageKey) {
    Path target = uploadDir.resolve(storageKey).normalize();
    return target.startsWith(uploadDir) ? Optional.of(target) : Optional.empty();
  }

  /** {@link #exists(String)}・{@link #load(String)}と同じ検証を再利用し、パストラバーサルキーでの削除を防ぐ(レビュー指摘対応)。 */
  @Override
  public void delete(String storageKey) {
    resolveWithinUploadDir(storageKey)
        .ifPresent(
            target -> {
              try {
                Files.deleteIfExists(target);
              } catch (IOException ignored) {
                // ベストエフォート。孤児ファイルが残っても認可・DoSには影響しない(添付ファイル機能定義書§8)
              }
            });
  }
}
