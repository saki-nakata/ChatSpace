package com.chatspace.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ワークスペース機能定義書§5のバリデーション仕様に対応する。 */
public record CreateWorkspaceRequest(
    @NotBlank @Size(min = 1, max = 80, message = "ワークスペース名は1〜80文字で入力してください。") String name) {}
