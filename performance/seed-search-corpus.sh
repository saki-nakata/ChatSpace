#!/usr/bin/env bash
# search.js 実行前に1回だけ手動で実行する。
# 1) alice としてログインし、専用のPUBLICチャンネルを作成する
# 2) そのチャンネルへ、5万件のひらがなフィラー + 短queryマーカー(qz)200件 + 長queryマーカー(vxjkp)200件を
#    docker execでpsqlに直接INSERTする(HTTP経由だとk6のsetupTimeoutに収まらない規模のため)
# 3) 投入件数がちょうど200件ずつであることをSQL側で検証し、ANALYZE messagesで統計を更新する
#
# 前提: 専用の使い捨てPostgreSQLコンテナが起動済みであること(既存の開発DBは使わない)。
# 出力される CHANNEL_ID / WORKSPACE_ID を search.js に -e で渡すこと。
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PGSQL_CONTAINER="${PGSQL_CONTAINER:-chatspace-perf-postgres}"
PGSQL_USER="${PGSQL_USER:-chatspace}"
PGSQL_DB="${PGSQL_DB:-chatspace}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "== 1. alice としてログイン ==" >&2
LOGIN_RES="$(curl -sf -c "$COOKIE_JAR" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"alice","password":"password123"}')"
ALICE_ID="$(echo "$LOGIN_RES" | jq -r '.id')"
if [ -z "$ALICE_ID" ] || [ "$ALICE_ID" = "null" ]; then
  echo "エラー: alice のログインに失敗しました: $LOGIN_RES" >&2
  exit 1
fi

echo "== 2. Sample Workspace のID取得 ==" >&2
WORKSPACE_ID="$(curl -sf -b "$COOKIE_JAR" "$BASE_URL/workspaces" \
  | jq -r '.[] | select(.name == "Sample Workspace") | .id')"
if [ -z "$WORKSPACE_ID" ]; then
  echo "エラー: alice に紐づく Sample Workspace が見つかりません(dev,seedプロファイルで起動していますか?)" >&2
  exit 1
fi

echo "== 3. 検索用チャンネル作成 ==" >&2
CHANNEL_NAME="k6-search-$(date +%s)"
CREATE_RES="$(curl -sf -b "$COOKIE_JAR" -X POST "$BASE_URL/workspaces/$WORKSPACE_ID/channels" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$CHANNEL_NAME\",\"type\":\"PUBLIC\"}")"
CHANNEL_ID="$(echo "$CREATE_RES" | jq -r '.id')"
if [ -z "$CHANNEL_ID" ] || [ "$CHANNEL_ID" = "null" ]; then
  echo "エラー: チャンネル作成に失敗しました: $CREATE_RES" >&2
  exit 1
fi

echo "== 4. コーパスをSQLで直接投入(5万件フィラー + マーカー200件×2) ==" >&2
docker exec -i "$PGSQL_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$PGSQL_USER" -d "$PGSQL_DB" <<SQL
-- 5万件のひらがなフィラー(短queryマーカー・長queryマーカーいずれのASCII文字列も出現し得ない)
INSERT INTO messages (id, channel_id, author_id, body, created_at, updated_at)
SELECT
  gen_random_uuid(),
  '$CHANNEL_ID'::uuid,
  '$ALICE_ID'::uuid,
  (SELECT string_agg(x, '') FROM (
     SELECT (ARRAY['あ','い','う','え','お','か','き','く','け','こ','さ','し','す','せ','そ'])[1 + floor(random() * 15)::int] AS x
     FROM generate_series(1, 30)
   ) t),
  now() - (random() * interval '30 days'),
  now()
FROM generate_series(1, 50000) AS s(i);

-- 短queryマーカー(2文字、ASCII "qz")— ちょうど200件
INSERT INTO messages (id, channel_id, author_id, body, created_at, updated_at)
SELECT gen_random_uuid(), '$CHANNEL_ID'::uuid, '$ALICE_ID'::uuid,
  'filler text marker qz marker text filler ' || s.i, now(), now()
FROM generate_series(1, 200) AS s(i);

-- 長queryマーカー(5文字、ASCII "vxjkp"、qzを含まずqzにも含まれない)— ちょうど200件
INSERT INTO messages (id, channel_id, author_id, body, created_at, updated_at)
SELECT gen_random_uuid(), '$CHANNEL_ID'::uuid, '$ALICE_ID'::uuid,
  'filler text marker vxjkp marker text filler ' || s.i, now(), now()
FROM generate_series(1, 200) AS s(i);

-- ヒット件数の検証(包含関係バグの再発防止)
DO \$\$
DECLARE
  short_count integer;
  long_count integer;
BEGIN
  SELECT count(*) INTO short_count FROM messages
    WHERE channel_id = '$CHANNEL_ID'::uuid AND body ILIKE '%qz%';
  SELECT count(*) INTO long_count FROM messages
    WHERE channel_id = '$CHANNEL_ID'::uuid AND body ILIKE '%vxjkp%';
  IF short_count != 200 THEN
    RAISE EXCEPTION 'short marker (qz) count mismatch: got %, expected 200', short_count;
  END IF;
  IF long_count != 200 THEN
    RAISE EXCEPTION 'long marker (vxjkp) count mismatch: got %, expected 200', long_count;
  END IF;
  RAISE NOTICE 'marker counts OK: short=%, long=%', short_count, long_count;
END \$\$;

ANALYZE messages;
SQL

echo "== 完了 ==" >&2
echo "CHANNEL_ID=$CHANNEL_ID"
echo "WORKSPACE_ID=$WORKSPACE_ID"
echo "" >&2
echo "search.js の実行例:" >&2
echo "  k6 run -e WORKSPACE_ID=$WORKSPACE_ID -e CHANNEL_ID=$CHANNEL_ID performance/search.js" >&2
