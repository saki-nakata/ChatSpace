import http from "k6/http";
import { check } from "k6";
import { Trend } from "k6/metrics";
import { login, useToken } from "./lib/auth.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TARGET_VUS = __ENV.VUS ? parseInt(__ENV.VUS, 10) : 10;

export const messagePostDurationMs = new Trend("message_post_duration_ms");

export const options = {
  stages: [
    { duration: "10s", target: TARGET_VUS },
    { duration: "30s", target: TARGET_VUS },
    { duration: "5s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    message_post_duration_ms: ["p(95)<1000"],
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

  const channelName = `k6-msgsend-${Date.now()}`;
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

export default function (data) {
  useToken(data.baseUrl, data.token);

  const body = `k6 message-send load test — VU ${__VU} iter ${__ITER} — ${Date.now()}`;
  const res = http.post(
    `${data.baseUrl}/workspaces/${data.workspaceId}/channels/${data.channelId}/messages`,
    JSON.stringify({ body }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(res, { "status is 201": (r) => r.status === 201 });
  messagePostDurationMs.add(res.timings.duration);
}

export function teardown(data) {
  useToken(data.baseUrl, data.token);
  http.del(`${data.baseUrl}/workspaces/${data.workspaceId}/channels/${data.channelId}`);
}
