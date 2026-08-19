import { marked } from "marked";
import DOMPurify, { type Config } from "dompurify";

/** バックエンド`MentionResolver`の正規表現と同一(メッセージング機能定義書§3・メンション機能定義書§3.2)。 */
export const MENTION_REGEX = /@([a-zA-Z0-9_.-]{3,20})/g;

marked.setOptions({ gfm: true, breaks: true });

// リンクは新規タブ + noopener/noreferrer を強制する(タブナビング攻撃対策)
DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A") {
    node.setAttribute("target", "_blank");
    node.setAttribute("rel", "noopener noreferrer");
  }
});

// img タグは意図的に許可しない。Markdown本文中に外部URLの画像(![alt](url))を埋め込めてしまうと、
// DOMPurifyではスクリプト実行を伴わない「トラッキングピクセル」(画像取得リクエストを使った閲覧者の
// IPアドレス・閲覧タイミングの収集)を防げないため。実際の画像・動画添付はMarkdown経由ではなく、
// 自ドメインの/uploads/APIから配信される別経路(添付ファイルプレビュー)で行う。
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
  // classは意図的に許可しない。許可すると、アプリ自身がビルドに含むTailwindユーティリティクラス
  // (例: fixed inset-0 z-50 bg-white)をメッセージ本文経由で任意の許可タグに付与でき、スクリプト実行
  // 無しで偽オーバーレイを表示するUI偽装・クリックジャッキングが成立してしまう(レビュー指摘対応)。
  // メンションハイライト用のspanクラスはサニタイズ後にhighlightMentions()がJSで動的付与するため、
  // ALLOWED_ATTRに無くても問題ない。
  ALLOWED_ATTR: ["href", "title", "target", "rel", "data-mention"],
};

/**
 * メッセージ本文をMarkdown→サニタイズ済みHTMLに変換する。XSS対策として、必ずmarkedでのHTML化直後に
 * DOMPurifyでサニタイズしてから利用する(計画書§8: Markdownレンダリングはクライアント側のみで行い、
 * バックエンドは入力値のバリデーションのみ担当する方針)。
 */
export function renderMessageBody(body: string): string {
  const rawHtml = marked.parse(body, { async: false }) as string;
  const clean = DOMPurify.sanitize(rawHtml, {
    ...SANITIZE_CONFIG,
    RETURN_TRUSTED_TYPE: false,
  }) as unknown as string;
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
      span.className = "rounded bg-brand-100 px-1 font-medium text-brand-700";
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
