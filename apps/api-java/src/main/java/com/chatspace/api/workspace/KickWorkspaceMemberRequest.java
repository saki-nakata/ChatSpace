package com.chatspace.api.workspace;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** キック対象は内部ユーザーID(ワークスペース機能定義書§5)。 */
public record KickWorkspaceMemberRequest(@NotNull UUID userId) {}
