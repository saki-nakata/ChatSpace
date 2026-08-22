package com.chatspace.api.upload;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

/** {@code S3Client}に明示的なAPI呼び出しタイムアウトが設定されていることを検証する(レビュー指摘対応)。 */
class S3StorageConfigTest {

  @Test
  void s3Client_hasExplicitApiCallTimeouts() {
    S3StorageProperties properties = new S3StorageProperties();
    properties.setBucket("chatspace-attachments");
    properties.setEndpoint("https://example.r2.cloudflarestorage.com");
    properties.setRegion("auto");
    properties.setAccessKeyId("test-access-key");
    properties.setSecretAccessKey("test-secret-key");

    S3Client s3Client = new S3StorageConfig().s3Client(properties);

    ClientOverrideConfiguration override =
        s3Client.serviceClientConfiguration().overrideConfiguration();
    assertTrue(override.apiCallTimeout().isPresent());
    assertTrue(override.apiCallAttemptTimeout().isPresent());
    assertTrue(override.apiCallTimeout().get().compareTo(Duration.ZERO) > 0);
    assertTrue(override.apiCallAttemptTimeout().get().compareTo(Duration.ZERO) > 0);
  }
}
