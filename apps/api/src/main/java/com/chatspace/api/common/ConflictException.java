package com.chatspace.api.common;

/** 既存リソースとの衝突(ユーザーID重複、チャンネル名重複等)の場合にスローする。 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
