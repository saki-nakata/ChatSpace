package com.chatspace.api.upload;

/** 添付ファイル機能定義書§4のアップロードレスポンス({@code { "attachment": {...} } }形式)。 */
public record UploadResponse(AttachmentResponse attachment) {}
