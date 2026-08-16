package com.chatspace.api.auth;

import com.chatspace.api.user.User;

/** signup/login成功時にController層へ渡す、ユーザーエンティティと発行済みJWTの組。 */
public record AuthResult(User user, String token) {}
