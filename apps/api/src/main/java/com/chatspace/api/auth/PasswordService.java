package com.chatspace.api.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * bcrypt(コストファクタ12)によるパスワードハッシュ化・照合を担う(認証機能定義書§6)。
 *
 * <p>{@link #matchAgainstDummyHash(String)} はユーザー不存在時にも呼び出すことで、存在有無による応答時間差を無くし
 * ユーザーID列挙を防ぐ(タイミング攻撃対策)。
 */
@Service
public class PasswordService {

  private static final int BCRYPT_COST = 12;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_COST);

  // 起動時に1回だけ生成する固定のダミーハッシュ。実在しないパスワードに対応するため、
  // どのユーザーの実パスワードとも一致しない。
  private final String dummyPasswordHash =
      encoder.encode("dummy-password-for-timing-attack-mitigation");

  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String passwordHash) {
    return encoder.matches(rawPassword, passwordHash);
  }

  public void matchAgainstDummyHash(String rawPassword) {
    encoder.matches(rawPassword, dummyPasswordHash);
  }
}
