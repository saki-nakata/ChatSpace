package com.chatspace.api.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT(HS256)の発行・検証を担う。認証機能定義書§6の通り、Spring SecurityのOAuth2 Resource Server自動構成は使わず、 Nimbus
 * JOSE+JWTを直接使用する(本アプリは自前でトークンを発行するため)。
 */
@Service
public class JwtService {

  private static final Duration TOKEN_TTL = Duration.ofDays(7);

  private final JWSSigner signer;
  private final JWSVerifier verifier;

  public JwtService(@Value("${chatspace.jwt-secret}") String jwtSecret) {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException("JWT_SECRET が設定されていません。");
    }
    byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    try {
      this.signer = new MACSigner(secretBytes);
      this.verifier = new MACVerifier(secretBytes);
    } catch (JOSEException e) {
      throw new IllegalStateException("JWT_SECRET の長さが不正です(HS256には最低256bit/32バイト必要)。", e);
    }
  }

  /** 内部ユーザーID(User.id)をsub claimとするJWTを発行する(認証機能定義書§6)。 */
  public String issue(UUID userId) {
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(TOKEN_TTL)))
            .build();
    SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      signedJwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("JWTの署名に失敗しました。", e);
    }
    return signedJwt.serialize();
  }

  /** 署名・有効期限を検証し、有効な場合のみ内部ユーザーIDを返す。不正・期限切れは例外を投げず空を返す(認証機能定義書§3.5)。 */
  public Optional<UUID> verify(String token) {
    try {
      SignedJWT signedJwt = SignedJWT.parse(token);
      if (!signedJwt.verify(verifier)) {
        return Optional.empty();
      }
      JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
      if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
        return Optional.empty();
      }
      return Optional.of(UUID.fromString(claims.getSubject()));
    } catch (ParseException | JOSEException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
