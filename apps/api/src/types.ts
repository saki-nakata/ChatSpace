import type { Server as SocketIOServer } from "socket.io";

export interface AppEnv {
  Variables: {
    userId: string;
    io: SocketIOServer;
  };
}
