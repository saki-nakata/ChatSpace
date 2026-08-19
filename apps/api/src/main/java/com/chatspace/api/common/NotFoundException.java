package com.chatspace.api.common;

/** 対象リソースが存在しない、または権限外で存在を秘匿すべき場合(404-not-403方針)にスローする。 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
