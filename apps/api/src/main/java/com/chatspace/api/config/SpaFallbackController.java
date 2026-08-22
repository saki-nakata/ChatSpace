package com.chatspace.api.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * フロントエンド(apps/web)をSpring Bootに同梱配信する際のSPAフォールバック(実装計画書/phase14.md)。
 *
 * <p>{@code apps/web/src/App.tsx}のクライアント側ルートは{@code /login}・{@code /signup}・{@code
 * /w/:workspaceId}配下の 有限集合であるため、除外リスト方式(否定先読み正規表現)ではなくこれらを個別に列挙する許可リスト方式で実装する。 除外リスト方式は{@code
 * /assets/**}等の静的ファイルパスを誤って飲み込む・新規APIパス追加のたびに除外リストの 更新が要るという欠陥があった(レビュー指摘対応)。{@code
 * /assets/**}は一切マッピングせず、Spring Bootの既定 静的リソース配信にすべて委ねる。ルート{@code /}自体はSpring
 * Bootのウェルカムページ機構でそのまま{@code index.html}が 返るため、個別マッピング不要。
 *
 * <p>API側の404({@code NotFoundException})は{@code GlobalExceptionHandler}が先に処理してJSONを返すため、
 * このフォールバックとは競合しない。
 */
@Controller
public class SpaFallbackController {

  @GetMapping({"/login", "/signup"})
  public String forwardAuthPages() {
    return "forward:/index.html";
  }

  @GetMapping("/w/**")
  public String forwardWorkspace() {
    return "forward:/index.html";
  }
}
