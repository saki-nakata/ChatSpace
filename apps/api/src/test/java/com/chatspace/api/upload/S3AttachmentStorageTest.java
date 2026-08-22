package com.chatspace.api.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** {@link S3AttachmentStorage}の単体テスト。実際のR2疎通は行わず、{@code S3Client}呼び出しの組み立てのみ検証する。 */
@ExtendWith(MockitoExtension.class)
class S3AttachmentStorageTest {

  private static final String BUCKET = "chatspace-attachments";

  @Mock private S3Client s3Client;

  private S3AttachmentStorage storage;

  @BeforeEach
  void setUp() {
    S3StorageProperties properties = new S3StorageProperties();
    properties.setBucket(BUCKET);
    storage = new S3AttachmentStorage(s3Client, properties);
  }

  @Test
  void put_sendsCorrectBucketAndKey() {
    storage.put("abc123.png", new byte[] {1, 2, 3});

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
    assertEquals(BUCKET, captor.getValue().bucket());
    assertEquals("abc123.png", captor.getValue().key());
  }

  @Test
  void exists_returnsFalse_whenNoSuchKeyException() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().build());

    assertFalse(storage.exists("missing.png"));
  }

  @Test
  void exists_returnsTrue_whenHeadObjectSucceeds() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().build());

    assertTrue(storage.exists("abc123.png"));
  }

  @Test
  void load_returnsResourceWithObjectBytes() throws IOException {
    byte[] content = {9, 8, 7};
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

    Resource resource = storage.load("abc123.png");

    assertArrayEquals(content, resource.getInputStream().readAllBytes());
  }

  @Test
  void delete_sendsCorrectBucketAndKey() {
    storage.delete("abc123.png");

    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(captor.capture());
    assertEquals(BUCKET, captor.getValue().bucket());
    assertEquals("abc123.png", captor.getValue().key());
  }
}
