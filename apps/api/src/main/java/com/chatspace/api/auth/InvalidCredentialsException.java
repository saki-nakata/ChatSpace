package com.chatspace.api.auth;

import com.chatspace.api.common.BadRequestException;

/** ユーザー不存在・パスワード不一致のいずれの場合も同一メッセージで投げる(ユーザーID列挙防止、認証機能定義書§3.2)。 */
public class InvalidCredentialsException extends BadRequestException {

  public InvalidCredentialsException() {
    super("ユーザーIDまたはパスワードが正しくありません。");
  }
}
