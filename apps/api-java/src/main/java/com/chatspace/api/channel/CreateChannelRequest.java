package com.chatspace.api.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** チャンネル機能定義書§5のバリデーション仕様に対応する。{@code memberUserIds} はPRIVATE作成時のみ意味を持つ(任意)。 */
public record CreateChannelRequest(
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,50}$", message = "チャンネル名の形式が不正です。") String name,
    @NotNull ChannelType type,
    List<String> memberUserIds) {}
