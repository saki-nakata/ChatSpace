package com.chatspace.api.auth;

import com.chatspace.api.common.CurrentUser;
import com.chatspace.api.user.UserResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 認証機能定義書§4の使用APIに対応する。 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final String COOKIE_NAME = "chatspace_token";
  private static final Duration COOKIE_MAX_AGE = Duration.ofDays(7);

  private final AuthService authService;
  private final boolean cookieSecure;

  public AuthController(
      AuthService authService, @Value("${chatspace.cookie-secure}") boolean cookieSecure) {
    this.authService = authService;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/signup")
  public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    AuthResult result = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, buildCookie(result.token()).toString())
        .body(UserResponse.from(result.user()));
  }

  @PostMapping("/login")
  public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResult result = authService.login(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, buildCookie(result.token()).toString())
        .body(UserResponse.from(result.user()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, buildExpiredCookie().toString())
        .build();
  }

  @GetMapping("/me")
  public UserResponse me(@CurrentUser UUID userId) {
    return UserResponse.from(authService.currentUser(userId));
  }

  private ResponseCookie buildCookie(String token) {
    return ResponseCookie.from(COOKIE_NAME, token)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(COOKIE_MAX_AGE)
        .build();
  }

  private ResponseCookie buildExpiredCookie() {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }
}
