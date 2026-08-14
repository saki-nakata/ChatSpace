import { io, type Socket } from "socket.io-client";

const API_BASE = import.meta.env.VITE_API_URL ?? "http://localhost:4000";

let socket: Socket | null = null;

/** ログイン済み(Cookieがある)前提でSocket.IO接続を確立する。多重接続は防ぐ。 */
export function getSocket(): Socket {
  if (!socket) {
    socket = io(API_BASE, {
      withCredentials: true,
      autoConnect: true,
    });
  }
  return socket;
}

export function disconnectSocket(): void {
  socket?.disconnect();
  socket = null;
}
