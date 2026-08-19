package com.chatspace.api.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Payload;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;

/** 認証機能定義書§9テストケース(パスワードのUTF-8バイト長バリデーション単体テスト)に対応する。 */
class MaxUtf8BytesValidatorTest {

  private final MaxUtf8Bytes.Validator validator = validatorFor(72);

  @Test
  void acceptsAsciiPasswordExactlyAtByteLimit() {
    assertTrue(validator.isValid("a".repeat(72), null));
  }

  @Test
  void rejectsAsciiPasswordExceedingByteLimit() {
    assertFalse(validator.isValid("a".repeat(73), null));
  }

  @Test
  void rejectsMultibytePasswordWithinCharCountButOverByteLimit() {
    // 日本語1文字はUTF-8で3バイト消費するため、25文字(75バイト)は72バイト制限を超える
    assertFalse(validator.isValid("あ".repeat(25), null));
  }

  @Test
  void acceptsMultibytePasswordExactlyAtByteLimit() {
    // 24文字 x 3バイト = 72バイトちょうど
    assertTrue(validator.isValid("あ".repeat(24), null));
  }

  @Test
  void treatsNullAsValid() {
    // 必須チェックは @NotBlank 側の責務であり、このバリデータは対象外として扱う
    assertTrue(validator.isValid(null, null));
  }

  private static MaxUtf8Bytes.Validator validatorFor(int max) {
    MaxUtf8Bytes.Validator validator = new MaxUtf8Bytes.Validator();
    validator.initialize(annotation(max));
    return validator;
  }

  private static MaxUtf8Bytes annotation(int max) {
    return new MaxUtf8Bytes() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return MaxUtf8Bytes.class;
      }

      @Override
      public int value() {
        return max;
      }

      @Override
      public String message() {
        return "";
      }

      @Override
      public Class<?>[] groups() {
        return new Class<?>[0];
      }

      @Override
      public Class<? extends Payload>[] payload() {
        @SuppressWarnings("unchecked")
        Class<? extends Payload>[] empty = new Class[0];
        return empty;
      }
    };
  }
}
