package com.chatspace.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ログイン時は存在有無を漏らさないため、登録時と同じ形式チェック(文字種・最小長)は行わない(認証機能定義書§5)。
 *
 * <p>ただし<b>最大長だけは制限する</b>(レビュー指摘対応)。形式の合否ではなく単なる長さの上限であり、
 * 登録可能なユーザーIDの上限(20文字)より十分大きい値にしてあるため、これによってアカウントの存在有無が
 * 漏れることはない。無制限だと、巨大文字列を送りつけて監査ログを肥大化させたりbcryptの照合対象を 膨らませたりできてしまうため、入口で弾く。
 */
public record LoginRequest(
    @NotBlank @Size(max = 100, message = "ユーザーIDまたはパスワードが正しくありません。") String userId,
    @NotBlank @MaxUtf8Bytes(72) String password) {}
