package com.chatspace.api.message;

import com.chatspace.api.user.UserResponse;
import java.util.List;

/** メンション機能定義書§4のレスポンス形式に対応する。 */
public record MentionCandidatesResponse(List<UserResponse> candidates) {}
