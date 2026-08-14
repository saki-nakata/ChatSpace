import { FormEvent, useState } from "react";
import type { ReactionSummaryDTO } from "@chatspace/shared";

const QUICK_EMOJIS = [
  "👍",
  "🙌",
  "🎉",
  "😄",
  "😂",
  "😮",
  "😢",
  "❤️",
  "🔥",
  "👀",
  "🙏",
  "✅",
  "🚀",
  "💯",
  "🤔",
  "👏",
];

interface Props {
  reactions: ReactionSummaryDTO[];
  onToggle: (emoji: string) => void;
}

export default function ReactionBar({ reactions, onToggle }: Props) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [customEmoji, setCustomEmoji] = useState("");

  function handleCustomSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = customEmoji.trim();
    if (!trimmed) return;
    onToggle(trimmed.slice(0, 8));
    setCustomEmoji("");
    setPickerOpen(false);
  }

  return (
    <div className="mt-1 flex flex-wrap items-center gap-1">
      {reactions.map((r) => (
        <button
          key={r.emoji}
          onClick={() => onToggle(r.emoji)}
          aria-label={`${r.emoji} ${r.count}件${r.reactedByMe ? "(自分がリアクション済み)" : ""}`}
          aria-pressed={r.reactedByMe}
          className={`flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs ${
            r.reactedByMe
              ? "border-brand-400 bg-brand-50 text-brand-700"
              : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
          }`}
        >
          <span>{r.emoji}</span>
          <span>{r.count}</span>
        </button>
      ))}

      <div className="relative">
        <button
          onClick={() => setPickerOpen((v) => !v)}
          aria-label="リアクションを追加"
          title="リアクションを追加"
          className={`rounded-full border border-dashed border-slate-300 px-2 py-0.5 text-xs text-slate-500 hover:bg-slate-50 ${
            pickerOpen ? "opacity-100" : "opacity-0 focus:opacity-100 group-hover:opacity-100"
          }`}
        >
          + 😀
        </button>
        {pickerOpen && (
          <div className="absolute bottom-full left-0 z-10 mb-1 w-56 rounded-md border border-slate-200 bg-white p-2 shadow-md">
            <div className="grid grid-cols-8 gap-1">
              {QUICK_EMOJIS.map((emoji) => (
                <button
                  key={emoji}
                  onClick={() => {
                    onToggle(emoji);
                    setPickerOpen(false);
                  }}
                  aria-label={`${emoji} でリアクションする`}
                  className="rounded px-1 py-0.5 text-base hover:bg-slate-100"
                >
                  {emoji}
                </button>
              ))}
            </div>
            <form onSubmit={handleCustomSubmit} className="mt-2 flex gap-1 border-t border-slate-100 pt-2">
              <input
                value={customEmoji}
                onChange={(e) => setCustomEmoji(e.target.value)}
                placeholder="絵文字を入力"
                aria-label="絵文字を入力"
                className="min-w-0 flex-1 rounded border border-slate-200 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
              />
              <button
                type="submit"
                className="rounded bg-brand-500 px-2 py-1 text-xs font-semibold text-white hover:bg-brand-600"
              >
                追加
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
