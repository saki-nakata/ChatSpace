package com.chatspace.api.auth;

import com.chatspace.api.audit.AuditLogger;
import com.chatspace.api.common.CurrentUser;
import com.chatspace.api.common.TooManyRequestsException;
import com.chatspace.api.ratelimit.AuthRateLimiter;
import com.chatspace.api.ratelimit.ClientAddress;
import com.chatspace.api.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
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
  private final AuthRateLimiter authRateLimiter;
  private final AuditLogger auditLogger;
  private final boolean cookieSecure;

  public AuthController(
      AuthService authService,
      AuthRateLimiter authRateLimiter,
      AuditLogger auditLogger,
      @Value("${chatspace.cookie-secure}") boolean cookieSecure) {
    this.authService = authService;
    this.authRateLimiter = authRateLimiter;
    this.auditLogger = auditLogger;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/signup")
  public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    AuthResult result = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, buildCookie(result.token()).toString())
        .body(UserResponse.from(result.user()));
  }

  /**
   * ログイン。ブルートフォース対策として、同一「ユーザーID + クライアントIP」の試行にレート制限を掛ける (認証機能定義書§7、フェーズ12)。
   *
   * <p>試行枠の確保({@code acquireAttempt})はパスワード照合の<b>前</b>に原子的に行う。以前は「確認」と
   * 「失敗の記録」が別操作だったため、同時リクエストが上限をすり抜けられた(レビュー指摘対応)。
   */
  @PostMapping("/login")
  public ResponseEntity<UserResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    String remoteAddress = ClientAddress.of(httpRequest);
    String rateLimitKey = ClientAddress.loginKey(request.userId(), remoteAddress);
    try {
      authRateLimiter.acquireAttempt(rateLimitKey);
    } catch (TooManyRequestsException ex) {
      auditLogger.loginRateLimited(request.userId(), remoteAddress);
      throw ex;
    }
    try {
      AuthResult result = authService.login(request);
      authRateLimiter.reset(rateLimitKey);
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, buildCookie(result.token()).toString())
          .body(UserResponse.from(result.user()));
    } catch (InvalidCredentialsException ex) {
      // 「ユーザー不存在」と「パスワード不一致」はAuthService側で同一例外に寄せてある(§3.2)ため、
      // 監査ログでも区別しない(区別するとログ経由でユーザーIDの存在有無が漏れる)
      auditLogger.loginFailed(request.userId(), remoteAddress);
      throw ex;
    }
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
