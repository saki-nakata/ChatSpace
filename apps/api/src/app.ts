import { Hono } from "hono";
import { cors } from "hono/cors";
import type { Server as SocketIOServer } from "socket.io";
import { env } from "./env.js";
import { HttpError } from "./lib/errors.js";
import type { AppEnv } from "./types.js";

import { authRoutes } from "./routes/auth.js";
import { userRoutes } from "./routes/users.js";
import { workspaceRoutes } from "./routes/workspaces.js";
import { channelRoutes } from "./routes/channels.js";
import { channelMessageRoutes } from "./routes/channel-messages.js";
import { dmRoutes } from "./routes/dm.js";
import { dmMessageRoutes } from "./routes/dm-messages.js";
import { searchRoutes } from "./routes/search.js";
import { notificationRoutes } from "./routes/notifications.js";
import { uploadRoutes } from "./routes/uploads.js";

/**
 * Hono アプリの組み立てをサーバー起動処理(index.ts)から分離する。
 * こうすることで、テストコードから実際に HTTP サーバーを起動せずに
 * `app.request(...)` で直接リクエストを送ってルートを検証できる。
 */
export function createApp(): { app: Hono<AppEnv>; setIo: (io: SocketIOServer) => void } {
  const app = new Hono<AppEnv>();

  app.use(
    "*",
    cors({
      origin: env.webOrigin,
      credentials: true,
    }),
  );

  // Socket.IO サーバーは HTTP サーバー起動後に生成されるため、
  // クロージャ経由でリクエストごとに参照する(起動時点では io は未定義)。
  const ioHolder: { current: SocketIOServer | undefined } = { current: undefined };
  app.use("*", async (c, next) => {
    if (!ioHolder.current) {
      return c.json({ error: "サーバー起動中です。しばらくして再度お試しください。" }, 503);
    }
    c.set("io", ioHolder.current);
    await next();
  });

  app.onError((err, c) => {
    if (err instanceof HttpError) {
      return c.json({ error: err.message }, err.status as 400);
    }
    console.error(err);
    return c.json({ error: "サーバーエラーが発生しました。" }, 500);
  });

  app.get("/health", (c) => c.json({ ok: true }));

  app.route("/auth", authRoutes);
  app.route("/users", userRoutes);
  app.route("/workspaces", workspaceRoutes);
  app.route("/workspaces/:workspaceId/channels", channelRoutes);
  app.route("/workspaces/:workspaceId/channels/:channelId/messages", channelMessageRoutes);
  app.route("/workspaces/:workspaceId/dms", dmRoutes);
  app.route("/workspaces/:workspaceId/dms/:dmId/messages", dmMessageRoutes);
  app.route("/workspaces/:workspaceId/search", searchRoutes);
  app.route("/notifications", notificationRoutes);
  app.route("/uploads", uploadRoutes);

  return {
    app,
    setIo: (io) => {
      ioHolder.current = io;
    },
  };
}
