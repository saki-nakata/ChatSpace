package com.chatspace.api.realtime;

/** チャンネル/DM/ワークスペーストピック・個人キューで共通のタグ付きユニオン形式(リアルタイム通信機能定義書§4.1〜§4.3)。 */
public record RealtimeEvent(String type, Object payload) {}
