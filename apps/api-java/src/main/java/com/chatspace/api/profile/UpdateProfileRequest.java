package com.chatspace.api.profile;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * プロフィール更新リクエスト。全フィールド任意で、指定されなかった(null)フィールドは更新しない
 * (S-14はアバター変更を選択と同時に即時保存し、表示名・ステータスは「保存」ボタン押下時にまとめて 保存する2系統の更新フローを持つため、部分更新にしている)。
 *
 * <p>他のリクエストDTOと異なり全フィールドが任意(null=更新しない)のため{@code @NotBlank}は使えない (nullを弾いてしまう)。Bean
 * Validationはnullを妥当な値として扱う({@code @Size}/{@code @Pattern}は
 * 対象がnullの場合は検証をスキップする仕様)ため、「指定された場合のみ検証する」意図をそのまま表現できる。 {@code avatarUrl}はアップロード所有権のDB照会を伴うため、Bean
 * Validationでは表現できず引き続き {@code UserProfileService}側で検証する。
 */
public record UpdateProfileRequest(
    @Pattern(regexp = ".*\\S.*", message = "表示名は1〜50文字で入力してください。")
        @Size(max = 50, message = "表示名は1〜50文字で入力してください。")
        String displayName,
    @Size(max = 100, message = "ステータスは100文字以内で入力してください。") String status,
    String avatarUrl) {}
