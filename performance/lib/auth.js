import http from "k6/http";

/**
 * ログインしてchatspace_token Cookieの値を取り出す。呼び出しは各スクリプトのsetup()で1回だけ行うこと。
 * AuthRateLimiterはuserId+IP単位で15分5回の失敗上限があり、多数のVUが同時に再ログインを試みると
 * 成功リクエスト同士が競合してブロックされ得るため、以後の全VU/teardown()はこの戻り値のtokenを
 * http.cookieJar().set(baseUrl, "chatspace_token", token) で使い回すこと。
 */
export function login(baseUrl, userId, password) {
  const res = http.post(
    `${baseUrl}/auth/login`,
    JSON.stringify({ userId, password }),
    { headers: { "Content-Type": "application/json" } },
  );
  if (res.status !== 200) {
    throw new Error(`login failed for ${userId}: ${res.status} ${res.body}`);
  }
  const cookie = res.cookies.chatspace_token && res.cookies.chatspace_token[0];
  if (!cookie) {
    throw new Error(`login response for ${userId} did not set chatspace_token cookie`);
  }
  return { token: cookie.value };
}

/** 各VU・setup()・teardown()の冒頭で呼び、そのVUのCookieジャーにトークンを注入する。 */
export function useToken(baseUrl, token) {
  http.cookieJar().set(baseUrl, "chatspace_token", token);
}
