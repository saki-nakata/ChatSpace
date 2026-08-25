const NUL = "\x00";

/** STOMP 1.2のCONNECTフレーム。Spring側はaccept-version/hostを期待する。heart-beatは0,0でクライアント側の処理を省略する。 */
export function buildConnectFrame(host) {
  return `CONNECT\naccept-version:1.2\nhost:${host}\nheart-beat:0,0\n\n${NUL}`;
}

/** STOMP 1.2のSUBSCRIBEフレーム。idヘッダは仕様上必須。 */
export function buildSubscribeFrame(id, destination) {
  return `SUBSCRIBE\nid:${id}\ndestination:${destination}\n\n${NUL}`;
}

export function buildDisconnectFrame(receiptId) {
  return `DISCONNECT\nreceipt:${receiptId}\n\n${NUL}`;
}

/**
 * 生WebSocketのテキストメッセージ1件に、複数のSTOMPフレームが連結されて届くことがある(NUL区切り)。
 * サーバーからの単独ハートビート(本文が"\n"のみ)は最初の要素として弾く。
 * 戻り値は { command, headers, body } の配列。
 */
export function parseStompFrames(raw) {
  return raw
    .split(NUL)
    .map((chunk) => chunk.replace(/^\n+/, ""))
    .filter((chunk) => chunk.length > 0)
    .map(parseSingleFrame);
}

function parseSingleFrame(frame) {
  const separatorIndex = frame.indexOf("\n\n");
  const headerPart = separatorIndex === -1 ? frame : frame.slice(0, separatorIndex);
  const body = separatorIndex === -1 ? "" : frame.slice(separatorIndex + 2);
  const lines = headerPart.split("\n");
  const command = lines[0];
  const headers = {};
  for (let i = 1; i < lines.length; i++) {
    const colonIndex = lines[i].indexOf(":");
    if (colonIndex === -1) continue;
    headers[lines[i].slice(0, colonIndex)] = lines[i].slice(colonIndex + 1);
  }
  return { command, headers, body };
}
