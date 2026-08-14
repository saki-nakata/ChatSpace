import { SignJWT, jwtVerify } from "jose";
import { env } from "../env.js";

const secret = new TextEncoder().encode(env.jwtSecret);
const ALG = "HS256";
export const AUTH_COOKIE_NAME = "chatspace_token";
const TOKEN_TTL = "7d";

export interface AuthTokenPayload {
  sub: string; // User.id
}

export async function signAuthToken(userId: string): Promise<string> {
  return new SignJWT({} satisfies Record<string, never>)
    .setProtectedHeader({ alg: ALG })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime(TOKEN_TTL)
    .sign(secret);
}

export async function verifyAuthToken(token: string): Promise<AuthTokenPayload | null> {
  try {
    const { payload } = await jwtVerify(token, secret, { algorithms: [ALG] });
    if (typeof payload.sub !== "string") return null;
    return { sub: payload.sub };
  } catch {
    return null;
  }
}
