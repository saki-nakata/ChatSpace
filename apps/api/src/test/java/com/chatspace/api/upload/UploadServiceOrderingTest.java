package com.chatspace.api.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatspace.api.audit.AuditLogger;
import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.message.Attachment;
import com.chatspace.api.message.AttachmentKind;
import com.chatspace.api.message.AttachmentRepository;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code UploadService.serve()}が「存在確認→DB検索→ライブ権限再チェック→(成功時のみ)本文取得」の順序を守ることを検証する単体テスト。
 *
 * <p>認可拒否されるリクエストのたびに{@code AttachmentStorage.load()}(オブジェクトストレージ実装では実転送を伴う)が
 * 呼ばれないことをピンポイントで検証する(フェーズ13レビュー指摘対応のリグレッションテスト)。
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceOrderingTest {

  @Mock private AttachmentStorage storage;
  @Mock private AttachmentRepository attachmentRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChannelAuthorizationService channelAuthorizationService;
  @Mock private DmAuthorizationService dmAuthorizationService;
  @Mock private AuditLogger auditLogger;
  @Mock private PlatformTransactionManager transactionManager;

  private UploadService uploadService;

  // コンストラクタに long(プリミティブ)が混在すると Mockito の @InjectMocks が
  // コンストラクタ注入に失敗するため、明示的に組み立てる。
  @BeforeEach
  void setUp() {
    uploadService =
        new UploadService(
            storage,
            0L,
            attachmentRepository,
            messageRepository,
            userRepository,
            channelAuthorizationService,
            dmAuthorizationService,
            auditLogger,
            transactionManager);
  }

  @Test
  void serve_deniedByLiveAuthorization_neverLoadsStorageBody() {
    String storageKey = "abc123.png";
    UUID uploader = UUID.randomUUID();
    UUID caller = UUID.randomUUID(); // uploader本人ではない
    Attachment attachment =
        new Attachment(uploader, storageKey, "photo.png", "image/png", 3, AttachmentKind.IMAGE);

    when(storage.exists(storageKey)).thenReturn(true);
    when(attachmentRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(attachment));
    when(userRepository.existsByAvatarUrl("/uploads/" + storageKey)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> uploadService.serve(storageKey, caller));

    verify(storage, never()).load(any());
    verify(auditLogger).attachmentAccessDenied(eq(caller), eq(storageKey), any());
  }

  @Test
  void serve_allowed_loadsStorageBodyOnlyAfterAuthorizationSucceeds() {
    String storageKey = "abc123.png";
    UUID uploader = UUID.randomUUID();
    Attachment attachment =
        new Attachment(uploader, storageKey, "photo.png", "image/png", 3, AttachmentKind.IMAGE);
    Resource resource = new ByteArrayResource(new byte[] {1});

    when(storage.exists(storageKey)).thenReturn(true);
    when(attachmentRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(attachment));
    when(storage.load(storageKey)).thenReturn(resource);

    UploadedFile result = uploadService.serve(storageKey, uploader); // アップロード本人によるアクセス

    assertEquals(resource, result.resource());
    verify(storage).load(storageKey);
  }

  @Test
  void serve_missingInStorage_neverQueriesAttachmentRepository() {
    String storageKey = "missing123.png";
    when(storage.exists(storageKey)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> uploadService.serve(storageKey, UUID.randomUUID()));

    verify(attachmentRepository, never()).findByStorageKey(any());
    verify(storage, never()).load(any());
  }
}
