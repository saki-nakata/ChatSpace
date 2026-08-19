package com.chatspace.api.auth;

import jakarta.validation.constraints.NotBlank;

/** ログイン時は存在有無を漏らさないため、登録時と同じ形式チェックは行わない(認証機能定義書§5)。 */
public record LoginRequest(@NotBlank String userId, @NotBlank String password) {}
