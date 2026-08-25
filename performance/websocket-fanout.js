import http from "k6/http";
import ws from "k6/ws";
import { check } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";
import { login, useToken } from "./lib/auth.js";
import { buildConnectFrame, buildSubscribeFrame, parseStompFrames } from "./lib/stomp.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const WS_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, "ws") + "/ws";
const WEB_ORIGIN = __ENV.WEB_ORIGIN || "http://localhost:5173";
const SUBSCRIBERS = __ENV.SUBSCRIBERS ? parseInt(__ENV.SUBSCRIBERS, 10) : 20;
const SMOKE = __ENV.SMOKE === "true";

// REST送信〜DBコミット〜AFTER_COMMIT送出〜WS受信までのエンドツーエンド遅延。
// 純粋なブローカーのファンアウト遅延ではない(結果ドキュメントに明記すること)。
export const wsEndToEndLatencyMs = new Trend("ws_end_to_end_latency_ms");
export const wsMessageReceivedRate = new Rate("ws_message_received_rate");
export const wsConnected = new Counter("ws_connected_total");
export const wsConnectFailed = new Counter("ws_connect_failed_total");
// POST自体の応答時間。ws_end_to_end_latency_msとは別指標として参考比較するのみで、
// 単純な減算による「内訳」算出はしない(サンプル数・分布が異なるため)。
export const messagePostLatencyMs = new Trend("message_post_latency_ms");

export const options = {
  scenarios: SMOKE
    ? {
        subscribers: {
          executor: "per-vu-iterations",
          vus: 1,
          iterations: 1,
          maxDuration: "15s",
          exec: "subscriberVU",
        },
        publisher: {
          executor: "shared-iterations",
          vus: 1,
          iterations: 1,
          startTime: "3s",
          maxDuration: "15s",
          exec: "publisherVU",
        },
      }
    : {
        subscribers: {
          executor: "per-vu-iterations",
          vus: SUBSCRIBERS,
          iterations: 1,
          maxDuration: "90s",
          exec: "subscriberVU",
        },
        publisher: {
          executor: "constant-arrival-rate",
          rate: 1,
          timeUnit: "2s",
          duration: "60s",
          preAllocatedVUs: 2,
          maxVUs: 5,
          startTime: "10s",
          exec: "publisherVU",
        },
      },
  thresholds: {
    ws_message_received_rate: ["rate>0.95"],
  },
  summaryTrendStats: ["avg", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const { token } = login(BASE_URL, "alice", "password123");
  useToken(BASE_URL, token);

  const workspaces = http.get(`${BASE_URL}/workspaces`).json();
  const workspace = workspaces.find((w) => w.name === "Sample Workspace");
  if (!workspace) {
    throw new Error('setup: "Sample Workspace" not found for alice');
  }

  const channelName = `k6-wsfanout-${Date.now()}`;
  const createRes = http.post(
    `${BASE_URL}/workspaces/${workspace.id}/channels`,
    JSON.stringify({ name: channelName, type: "PUBLIC" }),
    { headers: { "Content-Type": "application/json" } },
  );
  if (createRes.status !== 201) {
    throw new Error(`setup: channel creation failed: ${createRes.status} ${createRes.body}`);
  }
  const channelId = createRes.json("id");

  return { token, baseUrl: BASE_URL, workspaceId: workspace.id, channelId };
}

const HOLD_MS = SMOKE ? 10_000 : 85_000;

export function subscriberVU(data) {
  const destination = `/topic/channels.${data.channelId}`;
  const subscriptionId = `sub-${__VU}`;

  const res = ws.connect(
    WS_URL,
    {
      headers: {
        Cookie: `chatspace_token=${data.token}`,
        Origin: WEB_ORIGIN,
      },
    },
    (socket) => {
      let connected = false;

      socket.on("open", () => {
        socket.send(buildConnectFrame("localhost"));
      });

      socket.on("message", (raw) => {
        const frames = parseStompFrames(raw);
        for (const frame of frames) {
          if (frame.command === "CONNECTED" && !connected) {
            connected = true;
            wsConnected.add(1);
            socket.send(buildSubscribeFrame(subscriptionId, destination));
            continue;
          }
          if (frame.command === "MESSAGE") {
            let event;
            try {
              event = JSON.parse(frame.body);
            } catch (e) {
              continue;
            }
            if (event.type === "MESSAGE_CREATED" && typeof event.payload.body === "string") {
              const match = event.payload.body.match(/^k6-fanout:(\d+)/);
              if (match) {
                const sentAt = parseInt(match[1], 10);
                wsEndToEndLatencyMs.add(Date.now() - sentAt);
                wsMessageReceivedRate.add(true);
              }
            }
          }
        }
      });

      socket.on("error", () => {
        if (!connected) wsConnectFailed.add(1);
      });

      socket.setTimeout(() => {
        socket.close();
      }, HOLD_MS);
    },
  );

  check(res, { "handshake status is 101": (r) => r && r.status === 101 });
}

export function publisherVU(data) {
  useToken(data.baseUrl, data.token);
  const body = `k6-fanout:${Date.now()}`;
  const res = http.post(
    `${data.baseUrl}/workspaces/${data.workspaceId}/channels/${data.channelId}/messages`,
    JSON.stringify({ body }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(res, { "status is 201": (r) => r.status === 201 });
  messagePostLatencyMs.add(res.timings.duration);
}

export function teardown(data) {
  useToken(data.baseUrl, data.token);
  http.del(`${data.baseUrl}/workspaces/${data.workspaceId}/channels/${data.channelId}`);
}
