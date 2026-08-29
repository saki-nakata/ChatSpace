package com.chatspace.api.upload;

import com.chatspace.api.audit.AuditLogger;
import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.message.Attachment;
import com.chatspace.api.message.AttachmentRepository;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.user.UserRepository;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 添付ファイル機能定義書§3の業務ロジック。アップロード時はマジックバイト判定・保存先決定を、配信時は存在確認と ライブ権限再チェック(最重要)を担う。実体I/Oは{@link
 * AttachmentStorage}(ローカルディスク/オブジェクトストレージ)に委譲する。
 */
@Service
public class UploadService {

  /** {@code storageKey}の固定形式。アップロード時に自ら生成する形式と一致し、これ以外は404で存在を秘匿する(添付ファイル機能定義書§3.2)。 */
  private static final Pattern STORAGE_KEY_PATTERN =
      Pattern.compile("^[A-Za-z0-9_-]+\\.[a-z0-9]+$");

  /** マジックバイト判定に必要な最大バイト数(WEBP判定に12バイト必要、余裕を見て16バイト読む)。 */
  private static final int SNIFF_HEADER_BYTES = 16;

  private final AttachmentStorage storage;
  private final AuditLogger auditLogger;
  private final long maxAttachmentSizeBytes;
  private final AttachmentRepository attachmentRepository;
  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;
  private final TransactionTemplate readOnlyTransactionTemplate;

  public UploadService(
      AttachmentStorage storage,
      @Value("${chatspace.max-attachment-size-bytes}") long maxAttachmentSizeBytes,
      AttachmentRepository attachmentRepository,
      MessageRepository messageRepository,
      UserRepository userRepository,
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService,
      AuditLogger auditLogger,
      PlatformTransactionManager transactionManager) {
    this.storage = storage;
    this.maxAttachmentSizeBytes = maxAttachmentSizeBytes;
    this.attachmentRepository = attachmentRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
    this.auditLogger = auditLogger;
    this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
    this.readOnlyTransactionTemplate.setReadOnly(true);
  }

  /**
   * {@code content}は呼び出し元(Controller)が{@code MultipartFile}から読み出し済みのバイト列を渡すこと。Service層は {@code
   * org.springframework.web..}に属する型(Servlet/MVCの概念)へ依存してはならない(ArchUnit {@code
   * servicesMustNotDependOnServletOrWebMvcTypes}、計画書§1)ため、Controllerで変換してから渡す設計とする。
   */
  @Transactional
  public AttachmentResponse upload(byte[] content, String originalFileName, UUID uploaderId) {
    if (content == null || content.length == 0) {
      throw new BadRequestException("ファイルを指定してください。");
    }
    // multipart解析レベルの上限(application.yml)に加え、サービス層でも実サイズを再確認する(多層防御の二段目、§3.3)
    if (content.length > maxAttachmentSizeBytes) {
      throw new BadRequestException(
          "ファイルサイズは" + (maxAttachmentSizeBytes / (1024 * 1024)) + "MB以下にしてください。");
    }

    byte[] header = Arrays.copyOf(content, Math.min(SNIFF_HEADER_BYTES, content.length));
    MimeSniffer.Detection detection =
        MimeSniffer.detect(header).orElseThrow(() -> new BadRequestException("対応していないファイル形式です。"));

    String storageKey = UUID.randomUUID() + "." + detection.extension();
    storage.put(storageKey, content);
    registerRollbackCleanup(storageKey);

    Attachment attachment =
        attachmentRepository.save(
            new Attachment(
                uploaderId,
                storageKey,
                originalFileName == null || originalFileName.isBlank() ? "file" : originalFileName,
                detection.mimeType(),
                content.length,
                detection.kind()));
    return AttachmentResponse.from(attachment);
  }

  /**
   * オブジェクトストレージ実装(R2)への{@code exists()}/{@code load()}呼び出しはネットワークI/Oであり、DBトランザクション内で
   * 実行するとR2応答遅延時にDBコネクションプールがTomcatスレッドプールより先に枯渇しうる(レビュー指摘対応)。そのため
   * DBアクセスが必要な区間(添付ファイル検索・ライブ権限再チェック)だけを{@link #readOnlyTransactionTemplate}で 明示的に区切り、{@code
   * exists()}/{@code load()}はトランザクションの外側で呼び出す。
   */
  public UploadedFile serve(String storageKey, UUID callerId) {
    if (!STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
      throw notFound();
    }
    if (!storage.exists(storageKey)) {
      throw notFound();
    }
    Attachment attachment =
        readOnlyTransactionTemplate.execute(status -> findAndAuthorize(storageKey, callerId));
    // 本文取得(オブジェクトストレージ実装では実際の転送が発生する)はライブ権限再チェック成功後にのみ行う
    // (認可拒否されるリクエストのたびに本文転送させないため、インフラ構成書§7.1)
    return new UploadedFile(storage.load(storageKey), attachment.getMimeType());
  }

  private Attachment findAndAuthorize(String storageKey, UUID callerId) {
    Attachment attachment =
        attachmentRepository.findByStorageKey(storageKey).orElseThrow(this::notFound);
    try {
      authorizeServe(attachment, callerId);
    } catch (RuntimeException ex) {
      // ログ運用設計書§2「添付ファイルのライブ権限再チェック拒否」。GlobalExceptionHandlerの汎用的な
      // 認可拒否ログとは別に、storageKeyを独立したフィールドとして持つ専用イベントを残す
      // (パスからの文字列抽出に頼らず検索できるようにするため)
      auditLogger.attachmentAccessDenied(callerId, storageKey, ex.getClass().getSimpleName());
      throw ex;
    }
    return attachment;
  }

  /**
   * 取得の都度、所属メッセージのチャンネル/DMへのライブなメンバーシップを再検証する(アップロード時点のみのチェックにしない、
   * 添付ファイル機能定義書§6の最重要ポイント)。DM添付ファイルは{@code DmAuthorizationService.requireDmAccess}が内部で {@code
   * DmThread}自体の{@code workspaceId}を使ってワークスペースメンバーシップも再確認するため、ワークスペースキック後は 自動的に404になる。
   */
  private void authorizeServe(Attachment attachment, UUID callerId) {
    UUID messageId = attachment.getMessageId();
    if (messageId == null) {
      authorizeUnattachedServe(attachment, callerId);
      return;
    }
    Message message = messageRepository.findById(messageId).orElseThrow(this::notFound);
    if (message.isDeleted()) {
      // メッセージが削除された時点でUI・APIからは添付を秘匿しているため、保存済みURLを知っている
      // 利用者にだけ本文を返し続けるのは削除の期待に反する(利用者からの指摘対応)
      throw notFound();
    }
    if (message.getChannelId() != null) {
      channelAuthorizationService.requireChannelMember(message.getChannelId(), callerId, null);
    } else {
      dmAuthorizationService.requireDmAccess(message.getDmId(), callerId, null);
    }
  }

  private void authorizeUnattachedServe(Attachment attachment, UUID callerId) {
    String url = "/uploads/" + attachment.getStorageKey();
    if (userRepository.existsByAvatarUrl(url)) {
      return; // 誰かの現在のアバターとして使われていれば認証済みユーザー全員に許可(§6)
    }
    if (!attachment.getUploaderId().equals(callerId)) {
      throw notFound(); // 投稿前プレビュー段階はアップロード本人のみ
    }
  }

  /**
   * ストレージへの書き込みはDBトランザクションの外側の副作用であるため、トランザクションがロールバックされた場合に
   * 孤児オブジェクトが残らないよう、コミット以外の完了(ロールバック)時に削除する後始末を登録する(レビュー指摘対応)。
   */
  private void registerRollbackCleanup(String storageKey) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != TransactionSynchronization.STATUS_COMMITTED) {
                storage.delete(storageKey);
              }
            }
          });
    }
  }

  private NotFoundException notFound() {
    return new NotFoundException("ファイルが見つかりません。");
  }
}
