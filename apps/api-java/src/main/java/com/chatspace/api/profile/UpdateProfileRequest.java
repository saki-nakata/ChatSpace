package com.chatspace.api.profile;

/**
 * プロフィール更新リクエスト。全フィールド任意で、指定されなかった(null)フィールドは更新しない
 * (S-14はアバター変更を選択と同時に即時保存し、表示名・ステータスは「保存」ボタン押下時にまとめて 保存する2系統の更新フローを持つため、部分更新にしている)。
 */
public record UpdateProfileRequest(String displayName, String status, String avatarUrl) {}
