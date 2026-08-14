/**
 * オンライン/オフライン状態をメモリ上で追跡する(プロトタイプのため単一プロセス前提)。
 * 同一ユーザーが複数タブ/デバイスで接続する場合を考慮し、接続数をカウントする。
 */
const connectionCounts = new Map<string, number>();

export function markOnline(userId: string): boolean {
  const next = (connectionCounts.get(userId) ?? 0) + 1;
  connectionCounts.set(userId, next);
  return next === 1; // 最初の接続 = オンラインになった
}

export function markOffline(userId: string): boolean {
  const next = (connectionCounts.get(userId) ?? 1) - 1;
  if (next <= 0) {
    connectionCounts.delete(userId);
    return true; // 全接続が切れた = オフラインになった
  }
  connectionCounts.set(userId, next);
  return false;
}

export function isOnline(userId: string): boolean {
  return connectionCounts.has(userId);
}

export function getOnlineUserIds(candidateIds: string[]): string[] {
  return candidateIds.filter((id) => connectionCounts.has(id));
}
