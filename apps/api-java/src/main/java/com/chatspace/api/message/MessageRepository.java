package com.chatspace.api.message;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  @Query(
      "SELECT COUNT(m) FROM Message m WHERE m.channelId = :channelId AND m.authorId <>"
          + " :userId AND m.deletedAt IS NULL AND m.createdAt > :lastReadAt")
  long countUnreadInChannel(
      @Param("channelId") UUID channelId,
      @Param("userId") UUID userId,
      @Param("lastReadAt") Instant lastReadAt);

  /**
   * チャンネル一覧表示時の未読件数を一括取得する(N+1回避、レビュー指摘対応)。{@code ChannelMember.lastReadAt}は チャンネルごとに異なるため、{@code
   * ChannelMember}と{@code Message}をユーザーIDで相関させて1クエリで集計する (NotificationRepositoryの{@code
   * VISIBLE_SCOPE_CONDITION}と同じ「FROM A, B WHERE」形式の相関パターン)。
   */
  @Query(
      "SELECT m.channelId AS channelId, COUNT(m) AS count FROM Message m, ChannelMember cm WHERE"
          + " cm.userId = :userId AND cm.channelId IN :channelIds AND cm.channelId = m.channelId"
          + " AND m.authorId <> :userId AND m.deletedAt IS NULL AND m.createdAt >"
          + " cm.lastReadAt GROUP BY m.channelId")
  List<ChannelUnreadCount> countUnreadInChannels(
      @Param("channelIds") Collection<UUID> channelIds, @Param("userId") UUID userId);

  interface ChannelUnreadCount {
    UUID getChannelId();

    long getCount();
  }

  @Query(
      "SELECT COUNT(m) FROM Message m WHERE m.dmId = :dmId AND m.authorId <> :userId AND"
          + " m.deletedAt IS NULL AND m.createdAt > :lastReadAt")
  long countUnreadInDm(
      @Param("dmId") UUID dmId,
      @Param("userId") UUID userId,
      @Param("lastReadAt") Instant lastReadAt);

  Optional<Message> findFirstByDmIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID dmId);

  /** トップレベルメッセージ一覧の初回ページ(メッセージング機能定義書§3.6・§6.6)。 */
  @Query(
      "SELECT m FROM Message m WHERE m.parent IS NULL AND ((:channelId IS NOT NULL AND"
          + " m.channelId = :channelId) OR (:dmId IS NOT NULL AND m.dmId = :dmId)) ORDER BY"
          + " m.createdAt DESC, m.id DESC")
  List<Message> findTopLevelFirstPage(
      @Param("channelId") UUID channelId, @Param("dmId") UUID dmId, Pageable pageable);

  /** トップレベルメッセージの複合カーソルページング(次ページ = より古い方向)。コンテキスト取得の「older半分」にも使う。 */
  @Query(
      "SELECT m FROM Message m WHERE m.parent IS NULL AND ((:channelId IS NOT NULL AND"
          + " m.channelId = :channelId) OR (:dmId IS NOT NULL AND m.dmId = :dmId)) AND"
          + " (m.createdAt < :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id <"
          + " :cursorId)) ORDER BY m.createdAt DESC, m.id DESC")
  List<Message> findTopLevelOlderThan(
      @Param("channelId") UUID channelId,
      @Param("dmId") UUID dmId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  /** コンテキスト取得(around)の「newer半分」。対象より新しいトップレベルメッセージを古い順に返す。 */
  @Query(
      "SELECT m FROM Message m WHERE m.parent IS NULL AND ((:channelId IS NOT NULL AND"
          + " m.channelId = :channelId) OR (:dmId IS NOT NULL AND m.dmId = :dmId)) AND"
          + " (m.createdAt > :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id >"
          + " :cursorId)) ORDER BY m.createdAt ASC, m.id ASC")
  List<Message> findTopLevelNewerThan(
      @Param("channelId") UUID channelId,
      @Param("dmId") UUID dmId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  /** スレッド返信一覧の初回ページ(古い順、メッセージング機能定義書§3.4)。 */
  @Query("SELECT m FROM Message m WHERE m.parent.id = :parentId ORDER BY m.createdAt ASC, m.id ASC")
  List<Message> findRepliesFirstPage(@Param("parentId") UUID parentId, Pageable pageable);

  /** スレッド返信の複合カーソルページング(次ページ = より新しい方向)。 */
  @Query(
      "SELECT m FROM Message m WHERE m.parent.id = :parentId AND (m.createdAt >"
          + " :cursorCreatedAt OR (m.createdAt = :cursorCreatedAt AND m.id > :cursorId)) ORDER"
          + " BY m.createdAt ASC, m.id ASC")
  List<Message> findRepliesAfterCursor(
      @Param("parentId") UUID parentId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  /** トップレベルメッセージ一覧表示時の「返信N件」バッジ用、親IDごとの返信数をまとめて取得する(N+1回避)。 */
  @Query(
      "SELECT m.parent.id AS parentId, COUNT(m) AS count FROM Message m WHERE m.parent.id IN :parentIds GROUP BY m.parent.id")
  List<ParentReplyCount> countRepliesByParentIds(@Param("parentIds") Collection<UUID> parentIds);

  interface ParentReplyCount {
    UUID getParentId();

    long getCount();
  }

  /**
   * 検索の初回ページ(検索機能定義書§3.2)。{@code deleted_at IS NULL}を明示し、ソフトデリート済みメッセージを除外する
   * (一覧・スレッド・コンテキスト取得のtombstone方式とは異なり、検索のみの特別扱い)。{@code pattern}は呼び出し元で
   * ワイルドカードエスケープ・前後`%`付与を済ませた文字列を渡すこと。
   */
  @Query(
      value =
          "SELECT * FROM messages WHERE deleted_at IS NULL AND (channel_id IN (:channelIds) OR"
              + " dm_id IN (:dmIds)) AND body ILIKE :pattern ESCAPE '\\' ORDER BY created_at DESC,"
              + " id DESC",
      nativeQuery = true)
  List<Message> searchFirstPage(
      @Param("channelIds") List<UUID> channelIds,
      @Param("dmIds") List<UUID> dmIds,
      @Param("pattern") String pattern,
      Pageable pageable);

  /** 検索の複合カーソルページング(次ページ = より古い方向)。 */
  @Query(
      value =
          "SELECT * FROM messages WHERE deleted_at IS NULL AND (channel_id IN (:channelIds) OR"
              + " dm_id IN (:dmIds)) AND body ILIKE :pattern ESCAPE '\\' AND (created_at, id) <"
              + " (:cursorCreatedAt, :cursorId) ORDER BY created_at DESC, id DESC",
      nativeQuery = true)
  List<Message> searchOlderThan(
      @Param("channelIds") List<UUID> channelIds,
      @Param("dmIds") List<UUID> dmIds,
      @Param("pattern") String pattern,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);
}
