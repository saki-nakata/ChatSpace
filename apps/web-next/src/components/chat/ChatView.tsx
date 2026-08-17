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
  /**
   * 検索結果(S-12)・通知(S-13)クリック時のジャンプ先(`?highlight=`)。スレッド返信がジャンプ対象の場合も
   * 親メッセージのIDを渡す(バックエンドの`/messages/{id}/context`が親メッセージを軸にウィンドウを組むため)。
   */
  jumpMessageId?: string;
  /** ジャンプ対象自体がスレッド返信だった場合の返信ID(`?reply=`)。S-07を自動的に開く。 */
  jumpReplyId?: string;
  /** スコープを開いた瞬間の未読件数(サイドバーの`unreadCount`)。未読区切り線の基準位置に使う。 */
  initialUnreadCount?: number;
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
/** ジャンプ・既読区切り線のハイライト背景をフェードアウトさせるまでの表示時間。 */
const HIGHLIGHT_DURATION_MS = 2500;

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
  jumpMessageId,
  jumpReplyId,
  initialUnreadCount,
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

  // 未読区切り線(スコープを開いた瞬間の既読位置を基準に固定表示)。一度計算したらそのビュー滞在中は動かさない。
  const [unreadDividerMessageId, setUnreadDividerMessageId] = useState<string | null>(null);
  const unreadDividerComputedRef = useRef(false);

  // 検索・通知からのジャンプ(S-12/S-13)。読み込み済み範囲外の対象は`/messages/{id}/context`で
  // 前後ウィンドウごと取得し直すため、firstItemIndexとVirtuosoの内部状態を安全に作り直す必要がある
  // (`virtuosoRemountKey`をbumpしてVirtuosoごと再マウントする)。
  const [virtuosoRemountKey, setVirtuosoRemountKey] = useState(0);
  // Virtuoso再マウント直後の初期表示位置。remount後の命令的scrollToIndexはVirtuoso自身の
  // initialTopMostItemIndex(既定は最下部)による初期配置と競合し、後者に負けて意図した位置に
  // 着地しないことがある(実機確認で発見)。そのため再マウントを伴うジャンプでは、命令的呼び出しではなく
  // マウント時点のこのpropで直接ターゲット位置を指定する(通常時はnullで従来通り最下部)。
  const [initialTopMostIndexOverride, setInitialTopMostIndexOverride] = useState<number | null>(null);
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const handledJumpRef = useRef<string | null>(null);
  // ジャンプ先ウィンドウとライブ末尾の間に未読込の範囲(ギャップ)が残っている間はtrue。
  // この間はリアルタイム新着を末尾に追記しない(ギャップを挟んで時系列が壊れるのを防ぐ、upsertMessage参照)。
  const [jumpGapRemaining, setJumpGapRemaining] = useState(false);
  const jumpGapRemainingRef = useRef(false);
  useEffect(() => {
    jumpGapRemainingRef.current = jumpGapRemaining;
  }, [jumpGapRemaining]);

  function flashHighlight(messageId: string) {
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    setHighlightedId(messageId);
    highlightTimerRef.current = setTimeout(() => setHighlightedId(null), HIGHLIGHT_DURATION_MS);
  }

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
      if (index === -1) {
        // ジャンプ先ウィンドウとライブ末尾の間に未読込のギャップがある間は追記しない
        // (追記すると古いウィンドウの末尾に新着が直結し、間の時系列が欠けたまま繋がって見えてしまう)。
        // 「最新に戻る」導線(handleReturnToLatest)経由でのみ反映させる。
        if (jumpGapRemainingRef.current) return prev;
        return [...prev, incoming];
      }
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
    setUnreadDividerMessageId(null);
    setHighlightedId(null);
    setJumpGapRemaining(false);
    setInitialTopMostIndexOverride(null);
    handledJumpRef.current = null;
    unreadDividerComputedRef.current = false;
    atBottomRef.current = true;
    messageApi.list(workspaceId, scope).then((res) => {
      if (cancelled) return;
      // バックエンドの初回ページ取得は無限スクロール(上方向)を見越して新しい順(DESC)で返るため、
      // 表示は時系列順(古い順)に反転する。
      const loaded = (res.messages as DisplayMessage[]).slice().reverse();
      setMessages(loaded);
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
          // ジャンプ中(ギャップ有)はupsertMessage側で追記自体を抑止しているため、この件数表示も出さない
          // (jumpGapRemaining時は「最新に戻る」バナーのみで案内する)。
          if (event.type === "MESSAGE_CREATED" && !atBottomRef.current && !jumpGapRemainingRef.current) {
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

  /**
   * 未読区切り線の位置確定。`initialUnreadCount`はサイドバー(WorkspaceShellPage)側の非同期取得に
   * 依存するプロパティのため、直リンク等でこのコンポーネントの方が先にメッセージ読み込みを終える
   * ケースがある(親の`channels`/`dms`取得より先にChatViewが初回ページを取得し終える競合)。
   * そのため「メッセージ読み込み完了」と「未読件数の到着」それぞれを個別に待ち、両方揃った最初の
   * タイミングで一度だけ確定させる(`unreadDividerComputedRef`でこのスコープ内は再計算しない)。
   */
  useEffect(() => {
    if (loading || unreadDividerComputedRef.current || initialUnreadCount === undefined) return;
    unreadDividerComputedRef.current = true;
    if (initialUnreadCount > 0) {
      // 未読件数が読み込み済みページを超える場合は先頭(最古)を区切り位置とする(それより古い未読は
      // 上方向スクロールで遡らないと見えないが、区切り線自体は「ここまでは開いた時点で未読だった」という
      // 目印として妥当なので先頭に固定する)。
      const boundaryIndex = Math.max(0, messages.length - initialUnreadCount);
      setUnreadDividerMessageId(messages[boundaryIndex]?.id ?? null);
    }
  }, [initialUnreadCount, loading, messages]);

  /**
   * 検索結果(S-12)・通知(S-13)クリックからのジャンプ。対象が既に読み込み済みならその場でスクロール+
   * ハイライトするだけで済むが、初回ページより古い(未読み込みの)対象だった場合は`/messages/{id}/context`
   * で前後ウィンドウごと取得し直す(フェーズ9-Fまでの既知の制約「読み込み済み範囲外のスレッドへは
   * ジャンプできない」の解消、実装計画書phase10.md参照)。
   *
   * <p>取得し直す際はfirstItemIndexを`FIRST_ITEM_INDEX_START`へ戻した上でVirtuosoごと再マウントする
   * (`virtuosoRemountKey`)。firstItemIndexは「開始位置からの累積prepend件数」を表す値のため、
   * 配列を丸ごと入れ替える操作を安全に行うにはprependの延長ではなく新しいセッションとして扱う必要がある。
   */
  useEffect(() => {
    if (!jumpMessageId) return;
    const requestKey = `${scopeKey}:${jumpMessageId}:${jumpReplyId ?? ""}`;
    if (handledJumpRef.current === requestKey) return;
    if (loading) return; // 初回ページの読み込み完了を待ってから判定する

    const found = messages.find((m) => m.id === jumpMessageId);
    if (found) {
      handledJumpRef.current = requestKey;
      const index = messages.findIndex((m) => m.id === jumpMessageId);
      requestAnimationFrame(() => {
        virtuosoRef.current?.scrollToIndex({ index, align: "center", behavior: "smooth" });
      });
      flashHighlight(jumpMessageId);
      if (jumpReplyId) setOpenThreadId(jumpMessageId);
      return;
    }

    handledJumpRef.current = requestKey;
    messageApi.context(workspaceId, scope, jumpMessageId).then((res) => {
      const contextWindow = res.messages as DisplayMessage[];
      const targetIndex = contextWindow.findIndex((m) => m.id === jumpMessageId);
      setMessages(contextWindow);
      setFirstItemIndex(FIRST_ITEM_INDEX_START);
      setInitialTopMostIndexOverride(targetIndex >= 0 ? targetIndex : null);
      setVirtuosoRemountKey((k) => k + 1);
      setMainNextCursor(res.hasOlder ? res.olderCursor ?? null : null);
      setJumpGapRemaining(!!res.hasNewer);
      // 別ウィンドウへ切り替わるため、開いた瞬間の未読区切り線は位置の対応が取れなくなる(非表示にする)。
      setUnreadDividerMessageId(null);
      flashHighlight(jumpMessageId);
      if (jumpReplyId) setOpenThreadId(jumpMessageId);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jumpMessageId, jumpReplyId, scopeKey, loading, messages]);

  /** ジャンプ中(読み込み済みウィンドウとライブ末尾の間にギャップがある間)に表示する「最新に戻る」導線。 */
  function handleReturnToLatest() {
    messageApi.list(workspaceId, scope).then((res) => {
      setMessages((res.messages as DisplayMessage[]).slice().reverse());
      setMainNextCursor(res.nextCursor ?? null);
      setFirstItemIndex(FIRST_ITEM_INDEX_START);
      setInitialTopMostIndexOverride(null);
      setVirtuosoRemountKey((k) => k + 1);
      setJumpGapRemaining(false);
      setUnseenCount(0);
      setHighlightedId(null);
      requestAnimationFrame(() => scrollToBottom("auto"));
    });
  }

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
          showUnreadDivider: message.id === unreadDividerMessageId,
        };
      }),
    [messages, unreadDividerMessageId],
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
              key={`${scopeKey}-${virtuosoRemountKey}`}
              ref={virtuosoRef}
              className="h-full"
              data={rows}
              firstItemIndex={firstItemIndex}
              initialTopMostItemIndex={
                initialTopMostIndexOverride !== null
                  ? { index: initialTopMostIndexOverride, align: "center" }
                  : rows.length - 1
              }
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
                  {row.showUnreadDivider && (
                    <div className="my-2 flex items-center gap-2 px-4">
                      <div className="h-px flex-1 bg-red-300" />
                      <span className="text-xs font-medium text-red-500">未読</span>
                      <div className="h-px flex-1 bg-red-300" />
                    </div>
                  )}
                  <MessageItem
                    message={row.message}
                    author={userMap[row.message.authorId]}
                    grouped={row.grouped}
                    currentUserId={currentUserId}
                    userMap={userMap}
                    highlighted={row.message.id === highlightedId}
                    onToggleReaction={(emoji) => handleToggleReaction(row.message.id, emoji)}
                    onEdit={(body) => handleEdit(row.message.id, body)}
                    onDelete={() => handleDelete(row.message.id, false)}
                    onOpenThread={() => setOpenThreadId(row.message.id)}
                  />
                </>
              )}
            />
          )}
          {unseenCount > 0 && !jumpGapRemaining && (
            <button
              onClick={() => scrollToBottom()}
              className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-brand-600 px-4 py-1.5 text-xs font-medium text-white shadow-md hover:bg-brand-700"
            >
              ↓ 新着{unseenCount}件
            </button>
          )}
          {jumpGapRemaining && (
            <button
              onClick={handleReturnToLatest}
              className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-brand-600 px-4 py-1.5 text-xs font-medium text-white shadow-md hover:bg-brand-700"
            >
              ジャンプ中 · 最新のメッセージに戻る ↓
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
          highlightReplyId={jumpReplyId && openThreadId === jumpMessageId ? jumpReplyId : undefined}
        />
      )}
    </div>
  );
}
