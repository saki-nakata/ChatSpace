package com.chatspace.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 認証機能定義書§5のバリデーション仕様に対応する。 */
public record SignupRequest(
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,20}$", message = "ユーザーIDの形式が不正です。")
        String userId,
    @NotBlank @Size(min = 8, message = "パスワードは8文字以上で入力してください。") @MaxUtf8Bytes(72) String password,
    @NotBlank @Size(min = 1, max = 50, message = "表示名は1〜50文字で入力してください。") String displayName) {}
