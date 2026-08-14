import "dotenv/config";
import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { env } from "./env.js";
import { createSocketServer } from "./realtime/socket.js";

const { app, setIo } = createApp();

const server = serve({ fetch: app.fetch, port: env.port }, (info) => {
  console.log(`[ChatSpace API] listening on http://localhost:${info.port}`);
});

setIo(createSocketServer(server as unknown as import("node:http").Server));
