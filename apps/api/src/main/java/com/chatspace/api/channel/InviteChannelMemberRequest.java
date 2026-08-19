package com.chatspace.api.channel;

import jakarta.validation.constraints.NotBlank;

public record InviteChannelMemberRequest(@NotBlank String userId) {}
