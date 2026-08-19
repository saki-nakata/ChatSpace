package com.chatspace.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 認証機能定義書§9テストケース1(JWT発行・検証の単体テスト)に対応する。 */
class JwtServiceTest {

  private static final String SECRET = "unit-test-secret-must-be-at-least-32-bytes-long";

  private final JwtService jwtService = new JwtService(SECRET);

  @Test
  void issuesTokenWithCorrectSubjectAndVerifiesIt() {
    UUID userId = UUID.randomUUID();

    String token = jwtService.issue(userId);
    Optional<UUID> verified = jwtService.verify(token);

    assertTrue(verified.isPresent());
    assertEquals(userId, verified.get());
  }

  @Test
  void rejectsTamperedToken() {
    String token = jwtService.issue(UUID.randomUUID());
    String tampered = token.substring(0, token.length() - 4) + "abcd";

    assertTrue(jwtService.verify(tampered).isEmpty());
  }

  @Test
  void rejectsExpiredToken() throws JOSEException {
    Instant past = Instant.now().minusSeconds(60);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .issueTime(Date.from(past.minusSeconds(60)))
            .expirationTime(Date.from(past))
            .build();
    SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    signedJwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

    assertTrue(jwtService.verify(signedJwt.serialize()).isEmpty());
  }

  @Test
  void rejectsMalformedToken() {
    assertTrue(jwtService.verify("not-a-jwt").isEmpty());
  }

  @Test
  void rejectsTokenSignedWithDifferentSecret() throws JOSEException {
    JwtService otherIssuer = new JwtService("a-completely-different-32-byte-plus-secret-value");
    String token = otherIssuer.issue(UUID.randomUUID());

    assertTrue(jwtService.verify(token).isEmpty());
  }
}
