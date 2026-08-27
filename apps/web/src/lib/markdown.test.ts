/**
 * @vitest-environment jsdom
 */
import { describe, expect, it } from "vitest";
import { renderMessageBody } from "./markdown";

/**
 * メッセージ本文のMarkdown描画とサニタイズの検証。
 *
 * この経路は**XSS対策の要**でありながら自動テストが無く、`marked` を 14 → 18(メジャー4つ飛び)へ
 * 更新した際に描画結果の変化を検出できない状態だった。`DOMPurify` と `marked` のどちらの
 * メジャー更新でも回帰を捕まえられるよう、許可/禁止の境界を固定する。
 *
 * `renderMessageBody` は `document` を使う(`highlightMentions`)ため jsdom 環境で実行する。
 */
describe("renderMessageBody", () => {
  describe("Markdownの描画", () => {
    it("強調・斜体・コードを描画する", () => {
      const html = renderMessageBody("**太字** と *斜体* と `code`");

      expect(html).toContain("<strong>太字</strong>");
      expect(html).toContain("<em>斜体</em>");
      expect(html).toContain("<code>code</code>");
    });

    it("リストを描画する", () => {
      const html = renderMessageBody("- 一つ目\n- 二つ目");

      expect(html).toContain("<ul>");
      expect(html).toContain("<li>一つ目</li>");
    });

    /** `gfm: true` の設定が効いていること。 */
    it("GFMの取り消し線を描画する", () => {
      const html = renderMessageBody("~~取り消し~~");

      expect(html).toMatch(/<(del|s)>取り消し<\/(del|s)>/);
    });

    /** `breaks: true` の設定が効いていること(Slack風に単一改行をそのまま改行として扱う)。 */
    it("単一の改行を<br>に変換する", () => {
      const html = renderMessageBody("1行目\n2行目");

      expect(html).toContain("<br>");
    });
  });

  describe("XSS対策", () => {
    it("scriptタグを除去する", () => {
      const html = renderMessageBody("<script>alert('xss')</script>こんにちは");

      expect(html).not.toContain("<script");
      expect(html).not.toContain("alert(");
      expect(html).toContain("こんにちは");
    });

    it("インラインのイベントハンドラを除去する", () => {
      const html = renderMessageBody('<p onclick="alert(1)">クリック</p>');

      expect(html).not.toContain("onclick");
      expect(html).toContain("クリック");
    });

    it("javascript:スキームのリンクを無効化する", () => {
      const html = renderMessageBody("[危険](javascript:alert(1))");

      expect(html).not.toContain("javascript:");
    });

    /** iframe等、許可タグ一覧に無いものは全て落ちること。 */
    it("許可していないタグを除去する", () => {
      const html = renderMessageBody('<iframe src="https://example.com"></iframe>本文');

      expect(html).not.toContain("<iframe");
      expect(html).toContain("本文");
    });
  });

  describe("意図的に許可していないもの", () => {
    /**
     * Markdown本文中の外部画像は、スクリプト実行を伴わない「トラッキングピクセル」
     * (閲覧者のIPアドレス・閲覧タイミングの収集)に使えるため許可しない。
     * 実際の画像・動画添付は自ドメインの /uploads/ から配信する別経路で行う。
     */
    it("Markdown記法の画像を描画しない", () => {
      const html = renderMessageBody("![alt](https://tracker.example.com/pixel.png)");

      expect(html).not.toContain("<img");
      expect(html).not.toContain("tracker.example.com");
    });

    it("生のimgタグも描画しない", () => {
      const html = renderMessageBody('<img src="https://tracker.example.com/pixel.png">');

      expect(html).not.toContain("<img");
    });

    /**
     * class属性を許可すると、アプリ自身がビルドに含むTailwindユーティリティクラス
     * (例: fixed inset-0 z-50 bg-white)を本文経由で任意の許可タグに付与でき、
     * スクリプト実行なしで偽オーバーレイによるUI偽装・クリックジャッキングが成立する。
     */
    it("class属性を除去する", () => {
      const html = renderMessageBody('<p class="fixed inset-0 z-50 bg-white">偽オーバーレイ</p>');

      expect(html).not.toContain("fixed inset-0");
      expect(html).not.toContain('class="fixed');
      expect(html).toContain("偽オーバーレイ");
    });

    it("style属性を除去する", () => {
      const html = renderMessageBody('<p style="position:fixed">偽装</p>');

      expect(html).not.toContain("style=");
    });
  });

  describe("リンク", () => {
    /** タブナビング攻撃(開かれた側から window.opener 経由で元タブを操作される)への対策。 */
    it("新規タブで開き、noopener noreferrerを付与する", () => {
      const html = renderMessageBody("[例](https://example.com)");

      expect(html).toContain('target="_blank"');
      expect(html).toContain('rel="noopener noreferrer"');
      expect(html).toContain('href="https://example.com"');
    });
  });

  describe("メンション強調", () => {
    it("メンションをdata-mention付きのspanにする", () => {
      const html = renderMessageBody("@alice おはよう");

      expect(html).toContain('data-mention="alice"');
      expect(html).toContain("@alice");
    });

    it("複数のメンションをすべて強調する", () => {
      const html = renderMessageBody("@alice と @bob");

      expect(html).toContain('data-mention="alice"');
      expect(html).toContain('data-mention="bob"');
    });

    /** バックエンドの MentionResolver と同一の正規表現(3〜20文字)であること。 */
    it("3文字未満のメンションは強調しない", () => {
      const html = renderMessageBody("@ab は短すぎる");

      expect(html).not.toContain("data-mention");
    });

    /**
     * メンション強調はサニタイズ後にDOM操作で行うため、本文がHTMLとして再解釈されないこと。
     * ここが文字列置換だと、サニタイズ済みHTMLへ後からタグを注入できてしまう。
     */
    it("メンション強調の過程でHTMLが再解釈されない", () => {
      const html = renderMessageBody("@alice <script>alert(1)</script>");

      expect(html).not.toContain("<script");
      expect(html).toContain('data-mention="alice"');
    });

    it("コードブロック内のテキストも含め、scriptは常に除去される", () => {
      const html = renderMessageBody("```\n<script>alert(1)</script>\n```");

      expect(html).not.toContain("<script>alert");
    });
  });
});
