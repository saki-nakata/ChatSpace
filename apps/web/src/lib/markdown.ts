import { marked } from "marked";
import DOMPurify, { type Config } from "dompurify";
import { MENTION_REGEX } from "@chatspace/shared";

marked.setOptions({ gfm: true, breaks: true });

// リンクは新規タブ + noopener/noreferrer を強制する(タブナビング攻撃対策)
DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A") {
    node.setAttribute("target", "_blank");
    node.setAttribute("rel", "noopener noreferrer");
  }
});

// img タグは意図的に許可しない。Markdown本文中に外部URLの画像(![alt](url))を
// 埋め込めてしまうと、DOMPurifyではスクリプト実行を伴わない「トラッキングピクセル」
// (画像取得リクエストを使った閲覧者のIPアドレス・閲覧タイミングの収集)を防げないため。
// 実際の画像・動画添付は Markdown 経由ではなく、自ドメインの /uploads/ API から配信される
// 別経路(MessageItem の attachments 表示)で行われるため、img を外しても添付機能自体には影響しない。
const SANITIZE_CONFIG: Config = {
  ALLOWED_TAGS: [
    "p",
    "br",
    "strong",
    "em",
    "del",
    "s",
    "code",
    "pre",
    "blockquote",
    "ul",
    "ol",
    "li",
    "a",
    "h1",
    "h2",
    "h3",
    "h4",
    "hr",
    "table",
    "thead",
    "tbody",
    "tr",
    "th",
    "td",
    "span",
  ],
  ALLOWED_ATTR: ["href", "title", "class", "target", "rel", "data-mention"],
};

/**
 * メッセージ本文を Markdown -> サニタイズ済み HTML に変換する。
 * XSS対策として、必ず marked でのHTML化直後に DOMPurify でサニタイズしてから利用する。
 */
export function renderMessageBody(body: string): string {
  const rawHtml = marked.parse(body, { async: false }) as string;
  const clean = DOMPurify.sanitize(rawHtml, { ...SANITIZE_CONFIG, RETURN_TRUSTED_TYPE: false }) as string;
  return highlightMentions(clean);
}

function highlightMentions(html: string): string {
  if (typeof document === "undefined") return html;
  const container = document.createElement("div");
  container.innerHTML = html;

  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  const textNodes: Text[] = [];
  let current: Node | null;
  while ((current = walker.nextNode())) {
    textNodes.push(current as Text);
  }

  for (const textNode of textNodes) {
    const text = textNode.data;
    MENTION_REGEX.lastIndex = 0;
    if (!MENTION_REGEX.test(text)) continue;
    MENTION_REGEX.lastIndex = 0;

    const frag = document.createDocumentFragment();
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = MENTION_REGEX.exec(text))) {
      const [full, handle] = match;
      frag.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
      const span = document.createElement("span");
      span.className = "rounded bg-brand-100 px-1 font-medium text-brand-700 dark:bg-brand-900/50 dark:text-brand-200";
      span.textContent = full;
      span.dataset.mention = handle;
      frag.appendChild(span);
      lastIndex = match.index + full.length;
    }
    frag.appendChild(document.createTextNode(text.slice(lastIndex)));
    textNode.parentNode?.replaceChild(frag, textNode);
  }

  return container.innerHTML;
}
