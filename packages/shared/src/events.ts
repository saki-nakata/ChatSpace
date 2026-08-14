// Socket.IO イベント名を一元管理する（フロント・バックのタイポによる不整合を防ぐ）

export const SocketEvents = {
  // クライアント → サーバー
  JoinWorkspace: "workspace:join",
  JoinChannel: "channel:join",
  LeaveChannel: "channel:leave",
  JoinDM: "dm:join",
  Typing: "typing",

  // サーバー → クライアント
  MessageCreated: "message:created",
  MessageUpdated: "message:updated",
  MessageDeleted: "message:deleted",
  ReactionUpdated: "reaction:updated",
  ChannelCreated: "channel:created",
  ChannelDeleted: "channel:deleted",
  DMThreadCreated: "dm:created",
  ChannelMemberKicked: "channel:member-kicked",
  WorkspaceMemberKicked: "workspace:member-kicked",
  Notification: "notification:new",
  PresenceUpdated: "presence:updated",
  UserTyping: "typing:update",
} as const;

export type SocketEventName = (typeof SocketEvents)[keyof typeof SocketEvents];
