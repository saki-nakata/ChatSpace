package com.chatspace.api.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

/** {@link LocalDiskAttachmentStorage}の単体テスト。特にパストラバーサル対策が{@code load()}側にもあることを検証する(レビュー指摘対応)。 */
class LocalDiskAttachmentStorageTest {

  @TempDir private Path tempDir;

  @Test
  void put_load_delete_roundTrip() throws IOException {
    LocalDiskAttachmentStorage storage = new LocalDiskAttachmentStorage(tempDir.toString());
    byte[] content = {1, 2, 3};

    storage.put("abc123.png", content);

    assertTrue(storage.exists("abc123.png"));
    Resource resource = storage.load("abc123.png");
    assertArrayEquals(content, resource.getInputStream().readAllBytes());

    storage.delete("abc123.png");
    assertFalse(storage.exists("abc123.png"));
  }

  @Test
  void exists_returnsFalse_forPathTraversalKey() throws IOException {
    // uploadDirの外に実ファイルを置き、正規化後にそこへ抜けようとするstorageKeyを模擬する
    Path outside = tempDir.getParent().resolve("outside-secret.png");
    try {
      java.nio.file.Files.write(outside, new byte[] {9});
      LocalDiskAttachmentStorage storage = new LocalDiskAttachmentStorage(tempDir.toString());

      assertFalse(storage.exists("../outside-secret.png"));
    } finally {
      java.nio.file.Files.deleteIfExists(outside);
    }
  }

  @Test
  void load_throwsIllegalState_whenCalledWithPathTraversalKeyWithoutExistsCheck() {
    LocalDiskAttachmentStorage storage = new LocalDiskAttachmentStorage(tempDir.toString());

    // exists()を経由しない不正な呼び出し順序を模擬しても、load()自体がuploadDir配下チェックで弾くこと
    assertThrows(IllegalStateException.class, () -> storage.load("../outside-secret.png"));
  }
}
