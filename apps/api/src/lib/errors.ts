/**
 * ルートハンドラ内で throw する HTTP エラー。index.ts の app.onError で捕捉して
 * 一貫した JSON エラーレスポンスに変換する。
 */
export class HttpError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "HttpError";
  }
}

export const Forbidden = (message = "この操作を行う権限がありません。") =>
  new HttpError(403, message);
export const NotFound = (message = "リソースが見つかりません。") => new HttpError(404, message);
export const BadRequest = (message = "リクエストが不正です。") => new HttpError(400, message);
export const Conflict = (message = "競合が発生しました。") => new HttpError(409, message);
