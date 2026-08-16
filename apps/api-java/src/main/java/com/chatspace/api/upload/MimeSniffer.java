package com.chatspace.api.upload;

import com.chatspace.api.message.AttachmentKind;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * ファイル実データの先頭バイト(マジックナンバー)からMIMEタイプを判定する(添付ファイル機能定義書§3.1・§6)。
 *
 * <p>クライアントが申告する {@code Content-Type} やファイル名の拡張子は一切信用せず、対応する6形式(PNG/JPEG/GIF/WEBP/MP4/WEBM)の
 * 固定シグネチャとの一致判定のみで許可可否・保存拡張子・レスポンスの{@code Content-Type}を決定する。
 */
public final class MimeSniffer {

  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] GIF87_SIGNATURE = "GIF87a".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] GIF89_SIGNATURE = "GIF89a".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] WEBP_SIGNATURE = "WEBP".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] FTYP_SIGNATURE = "ftyp".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] WEBM_SIGNATURE = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};

  /**
   * {@code ftyp}マジックバイトはISO base media file format全般(MP4動画だけでなくM4A音声・MOV等)に共通のため、 ブランド(offset
   * 8〜11の4バイト)を許可リストで検証してMP4動画のみを通す(レビュー指摘: ブランド未検証だと.m4a音声も
   * video/mp4として通ってしまう)。代表的な動画系ブランドのみを許可し、M4A/M4B等の音声系ブランドは含めない。
   */
  private static final Set<String> MP4_VIDEO_BRANDS =
      Set.of("isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "3gp4", "3gp5");

  private MimeSniffer() {}

  /** 判定結果。{@code extension}は保存ファイル名({@code storageKey})の固定拡張子決定にのみ使う。 */
  public record Detection(String mimeType, AttachmentKind kind, String extension) {}

  public static Optional<Detection> detect(byte[] header) {
    if (matches(header, 0, PNG_SIGNATURE)) {
      return Optional.of(new Detection("image/png", AttachmentKind.IMAGE, "png"));
    }
    if (matches(header, 0, JPEG_SIGNATURE)) {
      return Optional.of(new Detection("image/jpeg", AttachmentKind.IMAGE, "jpg"));
    }
    if (matches(header, 0, GIF87_SIGNATURE) || matches(header, 0, GIF89_SIGNATURE)) {
      return Optional.of(new Detection("image/gif", AttachmentKind.IMAGE, "gif"));
    }
    if (matches(header, 0, RIFF_SIGNATURE) && matches(header, 8, WEBP_SIGNATURE)) {
      return Optional.of(new Detection("image/webp", AttachmentKind.IMAGE, "webp"));
    }
    if (isMp4Video(header)) {
      return Optional.of(new Detection("video/mp4", AttachmentKind.VIDEO, "mp4"));
    }
    if (matches(header, 0, WEBM_SIGNATURE)) {
      return Optional.of(new Detection("video/webm", AttachmentKind.VIDEO, "webm"));
    }
    return Optional.empty();
  }

  private static boolean isMp4Video(byte[] header) {
    if (!matches(header, 4, FTYP_SIGNATURE) || header.length < 12) {
      return false;
    }
    String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
    return MP4_VIDEO_BRANDS.contains(brand);
  }

  private static boolean matches(byte[] data, int offset, byte[] signature) {
    if (data.length < offset + signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (data[offset + i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
