package com.chatspace.api.dm;

import jakarta.validation.constraints.NotBlank;

/** DM相手のユーザーID(ハンドル)。自分自身の指定は400(DM機能定義書§5)。 */
public record CreateDmRequest(@NotBlank String userId) {}
