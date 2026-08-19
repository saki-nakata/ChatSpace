package com.chatspace.api.common;

/** リクエスト内容自体が不正な場合(認証情報不一致等、Bean Validationでは表現できないもの)にスローする。 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
