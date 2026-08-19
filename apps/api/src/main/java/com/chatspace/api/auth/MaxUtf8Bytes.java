package com.chatspace.api.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8バイト列としての最大長を検証する。
 *
 * <p>bcryptの入力長制限は「72文字」ではなく「72バイト」であり、日本語等のマルチバイト文字を含む場合は文字数ベースの {@code @Size}
 * では実質的に72バイトを超える入力を許してしまう(超過分が無音で切り捨てられ、異なるパスワードが同一ハッシュになり得る)。 認証機能定義書§5参照。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {

  int value();

  String message() default "パスワードが長すぎます。";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
      this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true; // 必須チェックは @NotBlank 側の責務
      }
      return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
  }
}
