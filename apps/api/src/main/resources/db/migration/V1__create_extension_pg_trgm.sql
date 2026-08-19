-- pg_trgm拡張の有効化(検索機能: ILIKE部分一致のGINインデックス高速化に使用。DB設計書§1.1・§3.7参照)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
