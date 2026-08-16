package com.chatspace.api.search;

import com.chatspace.api.common.Cursor;
import java.util.List;

/** 検索結果のカーソルページングレスポンス(検索機能定義書§4)。 */
public record SearchResponse(List<SearchResultItem> messages, Cursor nextCursor) {}
