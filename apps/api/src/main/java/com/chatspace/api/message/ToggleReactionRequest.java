package com.chatspace.api.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** リアクション機能定義書§5のバリデーション仕様に対応する。 */
public record ToggleReactionRequest(
    @NotBlank @Size(min = 1, max = 8, message = "絵文字は1〜8文字で指定してください。") String emoji) {}
