# Renderへのデプロイ用イメージ(実装計画書/phase14.md、docs/インフラ構成書.md §4)。
# フロントエンド(apps/web)をビルドし、Spring Boot(apps/api)の静的リソースとして同梱・同一オリジン配信する。

# --- 1. フロントエンドビルド ---
FROM node:22-slim AS frontend-build
WORKDIR /repo
RUN corepack enable
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile
COPY apps/web apps/web
# 同一オリジン配信のため相対パス(空文字)を明示する。apps/web/.env は .dockerignore で除外済みだが、
# 仮にビルドコンテキストへ混入しても dotenv は既存の process.env 値を上書きしないため、
# ここでの明示設定が優先される(client.ts の `?? "http://localhost:8080"` を確実に迂回する)。
# VITE_WS_URL は空文字ではなくあえて未設定のままにし、stomp.ts の window.location フォールバックを発火させる。
ENV VITE_API_URL=""
RUN pnpm --filter @chatspace/web run build

# --- 2. バックエンドビルド ---
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /repo
COPY apps/api apps/api
# apps/api を先にCOPYした後にフロントの成果物を static/ へ重ねる(順序を逆にすると上書きされる)
COPY --from=frontend-build /repo/apps/web/dist apps/api/src/main/resources/static
WORKDIR /repo/apps/api
RUN ./gradlew bootJar --console=plain -x test

# --- 3. 実行用イメージ ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend-build /repo/apps/api/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
