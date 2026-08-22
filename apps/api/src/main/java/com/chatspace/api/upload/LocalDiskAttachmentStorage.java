package com.chatspace.api.upload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    Path target = uploadDir.resolve(storageKey).normalize();
    return target.startsWith(uploadDir) && Files.isRegularFile(target);
  }

  @Override
  public Resource load(String storageKey) {
    return new FileSystemResource(uploadDir.resolve(storageKey).normalize());
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(uploadDir.resolve(storageKey));
    } catch (IOException ignored) {
      // ベストエフォート。孤児ファイルが残っても認可・DoSには影響しない(添付ファイル機能定義書§8)
    }
  }
}
