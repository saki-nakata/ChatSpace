package com.chatspace.api.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** メッセージング機能定義書§5のバリデーション仕様に対応する。 */
public record CreateMessageRequest(
    @NotBlank @Size(min = 1, max = 4000, message = "本文は1〜4000文字で入力してください。") String body,
    UUID parentId,
    @Size(max = 10, message = "添付ファイルは最大10件までです。") List<UUID> attachmentIds) {}
