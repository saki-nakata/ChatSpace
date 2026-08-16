package com.chatspace.api.realtime;

import java.util.UUID;

/**
 * ワークスペース/チャンネルからのキック(オーナーによる他者の強制退出。自主退出では発行しない)を示すイベント (リアルタイム通信機能定義書§10.3)。
 *
 * <p>{@code WorkspaceService}/{@code ChannelService}はメンバーシップ削除のトランザクション内でこのイベントを {@code
 * ApplicationEventPublisher}経由で発行するだけに留め、実際のセッション強制切断は{@link
 * MemberKickedEventListener}が{@code @TransactionalEventListener(phase = AFTER_COMMIT)}で行う。
 *
 * <p>キックは接続単位(購読単位ではない)で処理するため、ワークスペースキック・チャンネルキックのいずれも同じ
 * イベントで表現する(対象ユーザーの全WebSocket接続を切断し、以後の再購読は削除済みメンバーシップにより自然に拒否される)。
 */
public record MemberKickedEvent(UUID userId) {}
