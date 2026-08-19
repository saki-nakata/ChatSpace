package com.chatspace.api.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
    @NotBlank @Size(min = 1, max = 4000, message = "本文は1〜4000文字で入力してください。") String body) {}
