import { useEffect, useState } from "react";
import { SocketEvents } from "@chatspace/shared";
import { getSocket } from "../../lib/socket";
import { useAuthStore } from "../../store/authStore";

type ScopeType = "channel" | "dm";

interface Props {
  scopeType: ScopeType;
  scopeId: string;
}

interface TypingPayload {
  userId: string;
  displayName: string;
  channelId: string | null;
  dmId: string | null;
}

const DISPLAY_TIMEOUT_MS = 4000;

export default function TypingIndicator({ scopeType, scopeId }: Props) {
  const myUserId = useAuthStore((s) => s.user?.id);
  const [typingUsers, setTypingUsers] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    setTypingUsers(new Map());
    const socket = getSocket();
    const timers = new Map<string, ReturnType<typeof setTimeout>>();

    const onTyping = (payload: TypingPayload) => {
      const matches = scopeType === "channel" ? payload.channelId === scopeId : payload.dmId === scopeId;
      if (!matches || payload.userId === myUserId) return;

      setTypingUsers((prev) => new Map(prev).set(payload.userId, payload.displayName));

      const existing = timers.get(payload.userId);
      if (existing) clearTimeout(existing);
      timers.set(
        payload.userId,
        setTimeout(() => {
          setTypingUsers((prev) => {
            const next = new Map(prev);
            next.delete(payload.userId);
            return next;
          });
          timers.delete(payload.userId);
        }, DISPLAY_TIMEOUT_MS),
      );
    };

    socket.on(SocketEvents.UserTyping, onTyping);
    return () => {
      socket.off(SocketEvents.UserTyping, onTyping);
      timers.forEach((t) => clearTimeout(t));
    };
  }, [scopeType, scopeId, myUserId]);

  if (typingUsers.size === 0) return null;

  return (
    <div className="px-4 py-1 text-xs italic text-slate-500">
      {Array.from(typingUsers.values()).join("、")} が入力中...
    </div>
  );
}
