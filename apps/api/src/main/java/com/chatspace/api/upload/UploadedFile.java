package com.chatspace.api.upload;

import org.springframework.core.io.Resource;

/** 配信(取得)エンドポイントのレスポンス組み立て用。{@code mimeType}はサーバーが検出した値を使う(添付ファイル機能定義書§3.2)。 */
public record UploadedFile(Resource resource, String mimeType) {}
