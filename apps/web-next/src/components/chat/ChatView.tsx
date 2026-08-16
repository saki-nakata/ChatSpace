import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Virtuoso, type VirtuosoHandle } from "react-virtuoso";
import { channelApi, dmApi, messageApi } from "../../api/resources";
import type { Cursor, UserResponse } from "../../api/types";
import { channelTopic, channelTypingSend, dmTopic, dmTypingSend } from "../../realtime/destinations";
import { sendToApp, subscribeJson } from "../../realtime/stomp";
import { useAuthStore } from "../../store/authStore";
import MessageComposer from "./MessageComposer";
import MessageItem from "./MessageItem";
import ThreadPanel from "./ThreadPanel";
import { isSameCalendarDay, isSameGroup, formatDateLabel } from "../../lib/time";
import type { DisplayMessage } from "./types";

/**
 * react-virtuosoの{@code firstItemIndex}(上方向prependの度に読み込んだ件数だけ減算する仮想インデックス、
 * 公式の「Prepend Items」パターン)の初期値。0付近から始めると多数ページを遡った際に負値へ到達し得るため、
 * 現実的な履歴量に対して十分な余裕を持たせる。
 */
const FIRST_ITEM_INDEX_START = 1_000_000;

type Scope = { channelId: string; dmId?: undefined } | { dmId: string; channelId?: undefined };

interface ChatViewProps {
  workspaceId: string;
  scope: Scope;
  /** 著者ID→ユーザー情報。ChannelPage/DMPageがそれぞれのスコープに応じて解決して渡す。 */
  userMap: Record<string, UserResponse>;
  placeholder?: string;
  /** 検索結果(S-12)・通知(S-13)クリック時、`?reply=`で指定された返信の親スレッドを自動的に開く。 */
  initialOpenThreadId?: string;
  /** S-05/S-06のヘッダー(チャンネル名+メンバー/削除ボタン、またはDM相手の表示名)。ChannelPage/DMPageが渡す。 */
  header?: ReactNode;
  /** 既読化APIを呼んだ後に呼ぶ(呼び出し元がサイドバーの未読バッジを再取得する)。 */
  onRead?: () => void;
}

interface TypingPayload {
  userId: string;
  channelId?: string | null;
  dmId?: string | null;
}

const REALTIME_MESSAGE_TYPES = new Set([
  "MESSAGE_CREATED",
  "MESSAGE_UPDATED",
  "MESSAGE_DELETED",
  "REACTION_UPDATED",
]);

const TYPING_SEND_THROTTLE_MS = 2000;
const TYPING_EXPIRE_MS = 3000;

/**
 * S-05/S-06(チャンネル・DM共通のメッセージ表示エリア、フェーズ9-B)。
 *
 * <p>STOMPイベント(MESSAGE_CREATED/UPDATED/DELETED/REACTION_UPDATED)はいずれも更新後の
 * メッセージ全体を運ぶため、idで一致するメッセージを置き換える(無ければ追加する)単一の
 * upsertハンドラで4種類すべてを処理できる。TYPING_UPDATEも同じトピックに配信されるため、
 * 同一の購読で両方を処理する。
 */
export default function ChatView({
  workspaceId,
  scope,
  userMap,
  placeholder,
  initialOpenThreadId,
  header,
  onRead,
}: ChatViewProps) {
  const currentUserId = useAuthStore((s) => s.user?.id);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [typingUserIds, setTypingUserIds] = useState<Set<string>>(new Set());
  const lastTypingSentRef = useRef(0);
  const typingTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());
  const scopeKey = scope.channelId ?? scope.dmId;

  // メッセージ一覧の仮想化・上方向無限スクロール(9-B、繰り越し分)。
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const [mainNextCursor, setMainNextCursor] = useState<Cursor | null>(null);
  const mainNextCursorRef = useRef<Cursor | null>(null);
  const loadingOlderRef = useRef(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [firstItemIndex, setFirstItemIndex] = useState(FIRST_ITEM_INDEX_START);
  // 最下部付近にいる間だけ新着メッセージへ自動追従し、それ以外は「↓新着N件」ボタンで手動遷移させる。
  const atBottomRef = useRef(true);
  const [unseenCount, setUnseenCount] = useState(0);
  useEffect(() => {
    mainNextCursorRef.current = mainNextCursor;
  }, [mainNextCursor]);

  // S-07スレッドパネル(フェーズ9-C)。開いているスレッドの親メッセージID。
  const [openThreadId, setOpenThreadId] = useState<string | null>(null);
  const openThreadIdRef = useRef<string | null>(null);
  const [threadReplies, setThreadReplies] = useState<DisplayMessage[]>([]);
  const [threadLoading, setThreadLoading] = useState(false);
  const [threadLoadingMore, setThreadLoadingMore] = useState(false);
  const [threadNextCursor, setThreadNextCursor] = useState<Cursor | null>(null);
  const openThreadMessage = openThreadId ? messages.find((m) => m.id === openThreadId) ?? null : null;

  const upsertMessage = useCallback((incoming: DisplayMessage) => {
    setMessages((prev) => {
      const index = prev.findIndex((m) => m.id === incoming.id);
      if (index === -1) return [...prev, incoming];
      const next = [...prev];
      next[index] = incoming;
      return next;
    });
  }, []);

  const upsertReply = useCallback((incoming: DisplayMessage) => {
    setThreadReplies((prev) => {
      const index = prev.findIndex((m) => m.id === incoming.id);
      if (index === -1) return [...prev, incoming];
      const next = [...prev];
      next[index] = incoming;
      return next;
    });
  }, []);

  /**
   * 既読化(旧`apps/web`のChatView相当、フェーズ9-Bで移植漏れとなっていた回帰の修正)。
   * このスコープを開いた直後と、自分以外の投稿によるMESSAGE_CREATED受信のたびに呼ぶ。
   * 失敗しても致命的ではない(次の既読化タイミングで再試行される)ため例外は握りつぶす。
   */
  async function markScopeRead() {
    try {
      if (scope.channelId) await channelApi.markRead(workspaceId, scope.channelId);
      else await dmApi.markRead(workspaceId, scope.dmId as string);
      onRead?.();
    } catch {
      // 既読化の失敗はUI上致命的ではないため無視する
    }
  }

  useEffect(() => {
    let cancelled = false;
    const timers = typingTimersRef.current;
    setLoading(true);
    setTypingUserIds(new Set());
    setOpenThreadId(null);
    setFirstItemIndex(FIRST_ITEM_INDEX_START);
    setUnseenCount(0);
    atBottomRef.current = true;
    messageApi.list(workspaceId, scope).then((res) => {
      if (cancelled) return;
      // バックエンドの初回ページ取得は無限スクロール(上方向)を見越して新しい順(DESC)で返るため、
      // 表示は時系列順(古い順)に反転する。
      setMessages((res.messages as DisplayMessage[]).slice().reverse());
      setMainNextCursor(res.nextCursor ?? null);
      setLoading(false);
      markScopeRead();
    });

    const destination = scope.channelId
      ? channelTopic(scope.channelId)
      : dmTopic(scope.dmId as string);
    const unsubscribe = subscribeJson<DisplayMessage | TypingPayload>(destination, (event) => {
      if (REALTIME_MESSAGE_TYPES.has(event.type)) {
        const payload = event.payload as DisplayMessage;
        if (payload.parentId) {
          // スレッド返信はメイン一覧には表示しない(親メッセージ自体は別途MESSAGE_UPDATEDで
          // replyCountが更新されるため、開いていないスレッドの返信はここでは無視してよい)。
          if (payload.parentId === openThreadIdRef.current) {
            upsertReply(payload);
          }
        } else {
          upsertMessage(payload);
          // 最下部から離れて履歴を読んでいる間は自動スクロールさせず、未読件数だけ積み上げる
          // (followOutputは`atBottom`時のみ追従するため、ここでは非表示中の新着カウントのみ管理する)。
          if (event.type === "MESSAGE_CREATED" && !atBottomRef.current) {
            setUnseenCount((c) => c + 1);
          }
        }
        // このスコープを開いている間に届いた他人発のイベント(新規投稿・スレッド返信含む)は
        // 即座に既読扱いにする(Slack同様、開いている間は常時既読)。
        if (event.type === "MESSAGE_CREATED" && payload.authorId !== currentUserId) {
          markScopeRead();
        }
        return;
      }
      if (event.type === "TYPING_UPDATE") {
        const payload = event.payload as TypingPayload;
        if (payload.userId === currentUserId) return;
        setTypingUserIds((prev) => new Set(prev).add(payload.userId));
        const existing = timers.get(payload.userId);
        if (existing) clearTimeout(existing);
        timers.set(
          payload.userId,
          setTimeout(() => {
            setTypingUserIds((prev) => {
              const next = new Set(prev);
              next.delete(payload.userId);
              return next;
            });
            timers.delete(payload.userId);
          }, TYPING_EXPIRE_MS),
        );
      }
    });

    return () => {
      cancelled = true;
      unsubscribe();
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId, scopeKey]);

  // 検索結果・通知クリック(`?reply=`)からの遷移時、対象スレッドを自動的に開く(S-12/S-13)
  useEffect(() => {
    if (initialOpenThreadId) setOpenThreadId(initialOpenThreadId);
  }, [initialOpenThreadId]);

  /** 上方向スクロールで先頭に到達した際、古いページを1件先読みしてprependする(react-virtuosoのstartReached)。 */
  const handleStartReached = useCallback(() => {
    if (loadingOlderRef.current || !mainNextCursorRef.current) return;
    loadingOlderRef.current = true;
    setLoadingOlder(true);
    const cursor = mainNextCursorRef.current;
    messageApi
      .list(workspaceId, scope, { cursorCreatedAt: cursor.createdAt, cursorId: cursor.id })
      .then((res) => {
        const older = (res.messages as DisplayMessage[]).slice().reverse();
        if (older.length > 0) {
          setMessages((prev) => [...older, ...prev]);
          setFirstItemIndex((prev) => prev - older.length);
        }
        setMainNextCursor(res.nextCursor ?? null);
      })
      .finally(() => {
        loadingOlderRef.current = false;
        setLoadingOlder(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId, scopeKey]);

  function handleAtBottomChange(bottom: boolean) {
    atBottomRef.current = bottom;
    if (bottom) setUnseenCount(0);
  }

  function scrollToBottom(behavior: "smooth" | "auto" = "smooth") {
    virtuosoRef.current?.scrollToIndex({ index: "LAST", align: "end", behavior });
    setUnseenCount(0);
  }

  // スレッドパネルを開いた/切り替えたタイミングで初回20件を取得する(S-07)。
  useEffect(() => {
    openThreadIdRef.current = openThreadId;
    if (!openThreadId) {
      setThreadReplies([]);
      setThreadNextCursor(null);
      return;
    }
    let cancelled = false;
    setThreadLoading(true);
    messageApi.replies(workspaceId, scope, openThreadId).then((res) => {
      if (cancelled) return;
      setThreadReplies(res.messages as DisplayMessage[]);
      setThreadNextCursor(res.nextCursor ?? null);
      setThreadLoading(false);
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openThreadId, workspaceId, scopeKey]);

  async function handleSend(body: string, attachmentIds?: string[]) {
    const response = await messageApi.create(workspaceId, scope, { body, attachmentIds });
    upsertMessage(response as DisplayMessage);
    // 自分の送信は(スクロールして過去ログを読んでいた場合でも)Slack同様に必ず最下部へ連れて行く
    requestAnimationFrame(() => scrollToBottom("auto"));
  }

  async function handleSendReply(body: string, attachmentIds?: string[]) {
    if (!openThreadId) return;
    const response = await messageApi.create(workspaceId, scope, {
      body,
      parentId: openThreadId,
      attachmentIds,
    });
    upsertReply(response as DisplayMessage);
  }

  async function handleLoadMoreReplies() {
    if (!openThreadId || !threadNextCursor) return;
    setThreadLoadingMore(true);
    const res = await messageApi.replies(workspaceId, scope, openThreadId, {
      cursorCreatedAt: threadNextCursor.createdAt,
      cursorId: threadNextCursor.id,
    });
    setThreadReplies((prev) => [...prev, ...(res.messages as DisplayMessage[])]);
    setThreadNextCursor(res.nextCursor ?? null);
    setThreadLoadingMore(false);
  }

  function handleTyping() {
    const now = Date.now();
    if (now - lastTypingSentRef.current < TYPING_SEND_THROTTLE_MS) return;
    lastTypingSentRef.current = now;
    const destination = scope.channelId
      ? channelTypingSend(scope.channelId)
      : dmTypingSend(scope.dmId as string);
    sendToApp(destination);
  }

  async function handleToggleReaction(messageId: string, emoji: string) {
    const response = (await messageApi.toggleReaction(workspaceId, scope, messageId, { emoji })) as DisplayMessage;
    if (response.parentId) upsertReply(response);
    else upsertMessage(response);
  }

  async function handleEdit(messageId: string, body: string) {
    const response = (await messageApi.edit(workspaceId, scope, messageId, { body })) as DisplayMessage;
    if (response.parentId) upsertReply(response);
    else upsertMessage(response);
  }

  async function handleDelete(messageId: string, isReply: boolean) {
    await messageApi.remove(workspaceId, scope, messageId);
    // メイン一覧はSTOMPのMESSAGE_DELETED(自分の操作も含め同一トピックの購読者全員に配信される、
    // create/edit/リアクションと同じ設計)によるupsertHandlerでtombstone状態に更新されるため、
    // ここでの再取得は不要(仮想化リストのprepend済みページを巻き戻してしまうため、むしろ有害)。
    // スレッド返信は件数が少なく巻き戻りの実害が無いため、従来通り再取得して整合させる。
    if (isReply && openThreadId) {
      const res = await messageApi.replies(workspaceId, scope, openThreadId);
      setThreadReplies(res.messages as DisplayMessage[]);
      setThreadNextCursor(res.nextCursor ?? null);
    }
  }

  // グルーピング判定・日付区切り線の要否(直前メッセージとの比較)はfirstItemIndexによる仮想インデックスの
  // ズレの影響を受けないよう、表示直前にdata配列自体へ焼き込んでおく。日付区切り線は独立したVirtuoso行に
  // せず(prepend時にfirstItemIndexの差分計算が「実メッセージ件数」と食い違ってしまうため)、
  // 各メッセージ行の一部として描画する(itemContent参照)。
  const rows = useMemo(
    () =>
      messages.map((message, i) => {
        const prev = messages[i - 1];
        const dateLabel = !prev || !isSameCalendarDay(prev.createdAt, message.createdAt)
          ? formatDateLabel(message.createdAt)
          : null;
        return {
          message,
          grouped: dateLabel ? false : isSameGroup(prev, message),
          dateLabel,
        };
      }),
    [messages],
  );
  // components propは参照が変わるたびにVirtuosoが内部を作り直すため、loadingOlder変化時のみ再生成する。
  const virtuosoComponents = useMemo(
    () => ({
      Header: () =>
        loadingOlder ? (
          <p className="px-4 py-2 text-center text-xs text-slate-400">読み込み中...</p>
        ) : null,
    }),
    [loadingOlder],
  );

  if (!currentUserId) return null;

  const typingNames = Array.from(typingUserIds)
    .map((id) => userMap[id]?.displayName)
    .filter((name): name is string => !!name);

  const mentionSource = scope.channelId ? { workspaceId, channelId: scope.channelId } : undefined;

  return (
    <div className="flex h-full overflow-x-hidden">
      <div
        className={`h-full min-w-0 flex-1 flex-col ${openThreadMessage ? "hidden sm:flex" : "flex"}`}
      >
        {header}
        <div className="relative min-h-0 flex-1">
          {loading ? (
            <p className="px-4 py-4 text-sm text-slate-400">読み込み中...</p>
          ) : messages.length === 0 ? (
            <p className="px-4 py-4 text-sm text-slate-400">まだメッセージはありません。最初のメッセージを送ってみましょう。</p>
          ) : (
            <Virtuoso
              key={scopeKey}
              ref={virtuosoRef}
              className="h-full"
              data={rows}
              firstItemIndex={firstItemIndex}
              initialTopMostItemIndex={rows.length - 1}
              alignToBottom
              followOutput={(isAtBottom) => (isAtBottom ? "smooth" : false)}
              startReached={handleStartReached}
              atBottomStateChange={handleAtBottomChange}
              computeItemKey={(_, row) => row.message.id}
              components={virtuosoComponents}
              itemContent={(_, row) => (
                <>
                  {row.dateLabel && (
                    <div className="my-2 flex items-center gap-2 px-4">
                      <div className="h-px flex-1 bg-slate-200" />
                      <span className="text-xs font-medium text-slate-400">{row.dateLabel}</span>
                      <div className="h-px flex-1 bg-slate-200" />
                    </div>
                  )}
                  <MessageItem
                    message={row.message}
                    author={userMap[row.message.authorId]}
                    grouped={row.grouped}
                    currentUserId={currentUserId}
                    userMap={userMap}
                    onToggleReaction={(emoji) => handleToggleReaction(row.message.id, emoji)}
                    onEdit={(body) => handleEdit(row.message.id, body)}
                    onDelete={() => handleDelete(row.message.id, false)}
                    onOpenThread={() => setOpenThreadId(row.message.id)}
                  />
                </>
              )}
            />
          )}
          {unseenCount > 0 && (
            <button
              onClick={() => scrollToBottom()}
              className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-brand-600 px-4 py-1.5 text-xs font-medium text-white shadow-md hover:bg-brand-700"
            >
              ↓ 新着{unseenCount}件
            </button>
          )}
        </div>
        {typingNames.length > 0 && (
          <p className="px-4 pb-1 text-xs italic text-slate-400">
            {typingNames.join("、")}さんが入力しています...
          </p>
        )}
        <MessageComposer
          onSend={handleSend}
          onTyping={handleTyping}
          placeholder={placeholder}
          mentionSource={mentionSource}
        />
      </div>

      {openThreadMessage && (
        <ThreadPanel
          parentMessage={openThreadMessage}
          replies={threadReplies}
          userMap={userMap}
          currentUserId={currentUserId}
          loading={threadLoading}
          hasMore={threadNextCursor !== null}
          loadingMore={threadLoadingMore}
          onLoadMore={handleLoadMoreReplies}
          onClose={() => setOpenThreadId(null)}
          onSend={handleSendReply}
          onToggleReaction={handleToggleReaction}
          onEdit={handleEdit}
          onDelete={(messageId) => handleDelete(messageId, messageId !== openThreadId)}
          mentionSource={mentionSource}
        />
      )}
    </div>
  );
}
