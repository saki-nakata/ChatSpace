package com.chatspace.api.upload;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link AttachmentStorage}のCloudflare R2(S3互換)実装。添付ファイルは25MB上限(application.yml
 * chatspace.max-attachment-size-bytes)のため、{@link #load(String)}は全量メモリ展開する({@link #exists(String)}で
 * 本文転送しない存在確認を分離しているため、認可拒否されるリクエストでは本文取得自体が発生しない、インフラ構成書§7.1)。
 */
@Component
@ConditionalOnProperty(prefix = "chatspace.storage", name = "type", havingValue = "s3")
public class S3AttachmentStorage implements AttachmentStorage {

  private final S3Client s3Client;
  private final String bucket;

  public S3AttachmentStorage(S3Client s3Client, S3StorageProperties properties) {
    this.s3Client = s3Client;
    this.bucket = properties.getBucket();
  }

  @Override
  public void put(String storageKey, byte[] content) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(storageKey).build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public boolean exists(String storageKey) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public Resource load(String storageKey) {
    ResponseBytes<GetObjectResponse> object =
        s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
    return new ByteArrayResource(object.asByteArray());
  }

  @Override
  public void delete(String storageKey) {
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    } catch (RuntimeException ignored) {
      // ベストエフォート。孤児オブジェクトが残っても認可・DoSには影響しない(添付ファイル機能定義書§8)
    }
  }
}
