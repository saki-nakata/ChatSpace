import http from "k6/http";
import { check } from "k6";
import { Trend } from "k6/metrics";
import { login, useToken } from "./lib/auth.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const WORKSPACE_ID = __ENV.WORKSPACE_ID;
const CHANNEL_ID = __ENV.CHANNEL_ID;

// seed-search-corpus.sh が投入する、ヒット件数200件で揃えた2つのマーカートークン。
// 互いに部分文字列関係を持たないよう選定している(qzはvxjkpに含まれず、その逆も無い)。
const SHORT_QUERY = "qz";
const LONG_QUERY = "vxjkp";

export const searchLatencyShortMs = new Trend("search_latency_short_ms");
export const searchLatencyLongMs = new Trend("search_latency_long_ms");

export const options = {
  vus: 5,
  duration: "30s",
  thresholds: {
    checks: ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  if (!WORKSPACE_ID || !CHANNEL_ID) {
    throw new Error(
      "setup: WORKSPACE_ID and CHANNEL_ID env vars are required " +
        "(run performance/seed-search-corpus.sh first and pass its output via -e)",
    );
  }
  const { token } = login(BASE_URL, "alice", "password123");
  return { token, baseUrl: BASE_URL };
}

function search(data, query, trend) {
  useToken(data.baseUrl, data.token);
  const url =
    `${data.baseUrl}/workspaces/${WORKSPACE_ID}/search` +
    `?q=${encodeURIComponent(query)}&channelId=${CHANNEL_ID}`;
  const res = http.get(url);
  check(res, { "status is 200": (r) => r.status === 200 });
  trend.add(res.timings.duration);
}

export default function (data) {
  search(data, SHORT_QUERY, searchLatencyShortMs);
  search(data, LONG_QUERY, searchLatencyLongMs);
}
