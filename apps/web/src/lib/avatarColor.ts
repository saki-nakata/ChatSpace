/** アバター画像未設定時、userIdから決定的に色を割り当てる(Slackのイニシャルアバターに倣う)。 */
const PALETTE = [
  "#6b3fc7",
  "#0ea5e9",
  "#16a34a",
  "#d97706",
  "#dc2626",
  "#7c3aed",
  "#0891b2",
  "#65a30d",
];

export function avatarColorFor(userId: string): string {
  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = (hash * 31 + userId.charCodeAt(i)) >>> 0;
  }
  return PALETTE[hash % PALETTE.length];
}

export function initialOf(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || "?";
}
