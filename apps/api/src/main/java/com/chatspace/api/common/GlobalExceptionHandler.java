package com.chatspace.api.common;

import com.chatspace.api.audit.AuditLogger;
import com.chatspace.api.audit.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 共通例外階層をHTTPステータス・エラーボディへ変換する。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final long maxAttachmentSizeBytes;
  private final AuditLogger auditLogger;
  private final CurrentUserProvider currentUserProvider;

  public GlobalExceptionHandler(
      @Value("${chatspace.max-attachment-size-bytes}") long maxAttachmentSizeBytes,
      AuditLogger auditLogger,
      CurrentUserProvider currentUserProvider) {
    this.maxAttachmentSizeBytes = maxAttachmentSizeBytes;
    this.auditLogger = auditLogger;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * 404。本アプリでは「存在しない」だけでなく<b>認可による非可視化</b>(プライベートチャンネル・DMの
   * 存在秘匿、confused-deputy対策)にも404を使うため、監査ログの対象に含める(ログ運用設計書§1.3)。
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      NotFoundException ex, HttpServletRequest request) {
    auditAuthorizationDenied(ex, request);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  /** 403(オーナー限定操作の拒否等)。同じく監査ログの対象(ログ運用設計書§1.3)。 */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {
    auditAuthorizationDenied(ex, request);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
  }

  /** 例外メッセージは監査ログへ渡さない(リソース名など、拒否された相手に見せていない情報が ログ経由で残るのを避けるため。ログ運用設計書§1.4)。渡すのは例外クラス名のみ。 */
  private void auditAuthorizationDenied(RuntimeException ex, HttpServletRequest request) {
    auditLogger.authorizationDenied(
        currentUserProvider.currentUserIdOrNull(),
        request.getMethod(),
        request.getRequestURI(),
        ex.getClass().getSimpleName());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("入力内容を確認してください。");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
  }

  /**
   * multipart解析エンジンのレベルでの上限超過(添付ファイル機能定義書§3.3、多層防御の一段目)。メッセージは{@code
   * chatspace.max-attachment-size-bytes}から算出し、{@link com.chatspace.api.upload.UploadService}側の
   * ハードコード文言と二重管理にならないようにする(レビュー指摘対応)。
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(maxAttachmentSizeMessage()));
  }

  private String maxAttachmentSizeMessage() {
    long maxMegabytes = maxAttachmentSizeBytes / (1024 * 1024);
    return "ファイルサイズは" + maxMegabytes + "MB以下にしてください。";
  }

  /** レート制限超過(認証機能定義書§7、フェーズ12)。{@code Retry-After}(秒)を添えて、クライアントが いつ再試行してよいかを機械的に判断できるようにする。 */
  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
    long retryAfterSeconds = Math.max(1, ex.getRetryAfter().toSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
        .body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleUnauthenticated(
      AuthenticationCredentialsNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("認証が必要です。"));
  }

  /** 壊れたJSONボディ・空ボディ等(レビュー指摘により追加。既定だとErrorResponse形状にならず400のみ返る)。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("リクエストボディの形式が不正です。"));
  }

  /** クエリパラメータ・パス変数の型変換失敗(例: cursorIdにUUID以外の文字列。レビュー指摘により追加)。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("パラメータ「" + ex.getName() + "」の形式が不正です。"));
  }

  /**
   * 想定外の実行時例外のフォールバック(レビュー指摘により追加)。スタックトレースはサーバー側のログにのみ出力し、 クライアントへは汎用メッセージのみを返す(内部実装の詳細を漏らさないため)。
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("想定外のエラーが発生しました。", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("サーバーエラーが発生しました。"));
  }
}
