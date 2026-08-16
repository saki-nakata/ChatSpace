package com.chatspace.api.upload;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.message.Attachment;
import com.chatspace.api.message.AttachmentRepository;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 添付ファイル機能定義書§3の業務ロジック。アップロード時はマジックバイト判定・保存先決定を、配信時はパストラバーサル対策と
 * ライブ権限再チェック(最重要)を担う。単一インスタンス前提のローカルディスク保存(計画書§6)。
 */
@Service
public class UploadService {

  /** {@code storageKey}の固定形式。アップロード時に自ら生成する形式と一致し、これ以外は404で存在を秘匿する(添付ファイル機能定義書§3.2)。 */
  private static final Pattern STORAGE_KEY_PATTERN =
      Pattern.compile("^[A-Za-z0-9_-]+\\.[a-z0-9]+$");

  /** マジックバイト判定に必要な最大バイト数(WEBP判定に12バイト必要、余裕を見て16バイト読む)。 */
  private static final int SNIFF_HEADER_BYTES = 16;

  private final Path uploadDir;
  private final long maxAttachmentSizeBytes;
  private final AttachmentRepository attachmentRepository;
  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;

  public UploadService(
      @Value("${chatspace.upload-dir}") String uploadDir,
      @Value("${chatspace.max-attachment-size-bytes}") long maxAttachmentSizeBytes,
      AttachmentRepository attachmentRepository,
      MessageRepository messageRepository,
      UserRepository userRepository,
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService) {
    this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    this.maxAttachmentSizeBytes = maxAttachmentSizeBytes;
    this.attachmentRepository = attachmentRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
    try {
      Files.createDirectories(this.uploadDir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
      throw new BadRequestException("ファイルサイズは25MB以下にしてください。");
    }

    byte[] header = Arrays.copyOf(content, Math.min(SNIFF_HEADER_BYTES, content.length));
    MimeSniffer.Detection detection =
        MimeSniffer.detect(header).orElseThrow(() -> new BadRequestException("対応していないファイル形式です。"));

    String storageKey = UUID.randomUUID() + "." + detection.extension();
    saveToDisk(content, storageKey);

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

  @Transactional(readOnly = true)
  public UploadedFile serve(String storageKey, UUID callerId) {
    if (!STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
      throw notFound();
    }
    Path target = uploadDir.resolve(storageKey).normalize();
    if (!target.startsWith(uploadDir) || !Files.isRegularFile(target)) {
      throw notFound();
    }
    Attachment attachment =
        attachmentRepository.findByStorageKey(storageKey).orElseThrow(this::notFound);
    authorizeServe(attachment, callerId);
    return new UploadedFile(new FileSystemResource(target), attachment.getMimeType());
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

  private void saveToDisk(byte[] content, String storageKey) {
    try {
      Files.write(uploadDir.resolve(storageKey), content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private NotFoundException notFound() {
    return new NotFoundException("ファイルが見つかりません。");
  }
}
