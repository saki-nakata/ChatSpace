package com.chatspace.api.common;

/** リソースの存在は開示してよいが、操作権限が無い場合(オーナー限定操作等)にスローする。 */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
