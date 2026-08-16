package com.chatspace.api.workspace;

import jakarta.validation.constraints.NotBlank;

/** 招待対象ユーザーID(ハンドル)。存在しないユーザーIDは404で弾かれるため形式チェックは非空のみ(ワークスペース機能定義書§5)。 */
public record InviteWorkspaceMemberRequest(@NotBlank String userId) {}
