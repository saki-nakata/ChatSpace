package com.chatspace.api.profile;

import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.message.Attachment;
import com.chatspace.api.message.AttachmentRepository;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.user.UserResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S-14プロフィール編集モーダルの業務ロジック。 */
@Service
public class UserProfileService {

  private static final int DISPLAY_NAME_MAX = 50;
  private static final int STATUS_MAX = 100;
  private static final String UPLOAD_URL_PREFIX = "/uploads/";

  private final UserRepository userRepository;
  private final AttachmentRepository attachmentRepository;

  public UserProfileService(
      UserRepository userRepository, AttachmentRepository attachmentRepository) {
    this.userRepository = userRepository;
    this.attachmentRepository = attachmentRepository;
  }

  @Transactional
  public UserResponse updateProfile(UUID callerId, UpdateProfileRequest request) {
    validateDisplayName(request.displayName());
    validateStatus(request.status());
    String avatarUrl = validateAndResolveAvatarUrl(request.avatarUrl(), callerId);

    User user =
        userRepository.findById(callerId).orElseThrow(() -> new NotFoundException("ユーザーが見つかりません。"));
    user.updateProfile(request.displayName(), request.status(), avatarUrl);
    userRepository.save(user);
    return UserResponse.from(user);
  }

  private void validateDisplayName(String displayName) {
    if (displayName == null) return;
    if (displayName.isBlank() || displayName.length() > DISPLAY_NAME_MAX) {
      throw new BadRequestException("表示名は1〜" + DISPLAY_NAME_MAX + "文字で入力してください。");
    }
  }

  private void validateStatus(String status) {
    if (status != null && status.length() > STATUS_MAX) {
      throw new BadRequestException("ステータスは" + STATUS_MAX + "文字以内で入力してください。");
    }
  }

  /**
   * アバター画像URLは、呼び出し元自身がアップロードした添付ファイルの{@code storageKey}に対応するものだけを許可する (添付ファイル機能定義書§6:
   * 投稿前プレビュー段階のファイルはアップロード本人のみ閲覧可能だが、いずれかの ユーザーの現在のアバターとして使われているファイルは認証済み全ユーザーに公開される設計のため、所有権を
   * 確認せずに他人のアップロード済みファイルを自分のアバターとして指定できてしまうと、本来非公開のはずの プレビュー画像を誰でも閲覧できる状態にしてしまう権限昇格になる)。
   */
  private String validateAndResolveAvatarUrl(String avatarUrl, UUID callerId) {
    if (avatarUrl == null) return null;
    if (!avatarUrl.startsWith(UPLOAD_URL_PREFIX)) {
      throw new BadRequestException("アバター画像のURLが不正です。");
    }
    String storageKey = avatarUrl.substring(UPLOAD_URL_PREFIX.length());
    Attachment attachment =
        attachmentRepository
            .findByStorageKey(storageKey)
            .orElseThrow(() -> new BadRequestException("アバター画像のURLが不正です。"));
    if (!attachment.getUploaderId().equals(callerId)) {
      throw new BadRequestException("アバター画像のURLが不正です。");
    }
    return avatarUrl;
  }
}
