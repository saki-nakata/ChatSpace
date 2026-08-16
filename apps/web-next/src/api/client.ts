const API_BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: "include",
    headers:
      options.body && !(options.body instanceof FormData)
        ? { "Content-Type": "application/json", ...options.headers }
        : options.headers,
  });

  if (res.status === 204) return undefined as T;

  const isJson = res.headers.get("content-type")?.includes("application/json");
  const data = isJson ? await res.json().catch(() => null) : null;

  if (!res.ok) {
    // apps/api-java の GlobalExceptionHandler は { message: string } 形式でエラーを返す
    // (apps/api 旧実装の { error: string } 形式とはキー名が異なる点に注意)
    throw new ApiError(res.status, (data && data.message) || `リクエストに失敗しました (${res.status})`);
  }
  return data as T;
}

function toQueryString(params?: Record<string, string | number | boolean | undefined>): string {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) search.set(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

export const api = {
  get: <T>(path: string, query?: Record<string, string | number | boolean | undefined>) =>
    request<T>(`${path}${toQueryString(query)}`),
  post: <T>(
    path: string,
    body?: unknown,
    query?: Record<string, string | number | boolean | undefined>,
  ) =>
    request<T>(`${path}${toQueryString(query)}`, {
      method: "POST",
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, form: FormData) => request<T>(path, { method: "POST", body: form }),
};

/** `AttachmentResponse.url`(`/uploads/{storageKey}`形式の相対パス)をAPIオリジン付きの絶対URLへ変換する。 */
export function attachmentUrl(url: string): string {
  return url.startsWith("http") ? url : `${API_BASE}${url}`;
}
