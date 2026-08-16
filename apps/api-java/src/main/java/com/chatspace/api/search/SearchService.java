package com.chatspace.api.search;

import com.chatspace.api.channel.ChannelMember;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.Cursor;
import com.chatspace.api.dm.DmThread;
import com.chatspace.api.dm.DmThreadRepository;
import com.chatspace.api.message.Message;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 検索機能定義書§3の業務ロジック。検索対象は常にリクエスト時点でライブに解決した「呼び出しユーザーが参加している チャンネル/DM」の集合に限定する(キャッシュ・スナップショットは使わない)。
 *
 * <p><b>DM検索対象とワークスペースキックの関係</b>: {@code DmThreadRepository.findAllForUser}自体は {@code
 * DmThread}の参加者チェックのみで、現在の{@code WorkspaceMember}であるかは見ていない(ワークスペースキックでも {@code
 * DmThread}行は削除されないため)。本メソッド先頭の{@code WorkspaceAuthorizationService.requireMember}が
 * ワークスペースキック済みユーザーを404で弾くことで、DM検索対象からも間接的に除外される(検索エンドポイント自体が ワークスペーススコープであることによる担保)。この検証は{@link
 * SearchController}でも実施しているが、本サービスが Controllerを経由せず呼ばれても無防備にならないよう、多層防御としてサービス層でも検証する(レビュー指摘対応)。
 */
@Service
public class SearchService {

  private static final int PAGE_SIZE = 50;
  private static final int MAX_QUERY_LENGTH = 200;

  /** IN句を空リストにできないための、実際のメッセージIDと衝突し得ないダミーID。 */
  private static final UUID UNMATCHABLE_ID = new UUID(0, 0);

  private final MessageRepository messageRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final DmThreadRepository dmThreadRepository;
  private final WorkspaceAuthorizationService workspaceAuthorizationService;

  public SearchService(
      MessageRepository messageRepository,
      ChannelMemberRepository channelMemberRepository,
      DmThreadRepository dmThreadRepository,
      WorkspaceAuthorizationService workspaceAuthorizationService) {
    this.messageRepository = messageRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.dmThreadRepository = dmThreadRepository;
    this.workspaceAuthorizationService = workspaceAuthorizationService;
  }

  @Transactional(readOnly = true)
  public SearchResponse search(
      UUID workspaceId,
      UUID callerId,
      String query,
      UUID channelIdFilter,
      Instant cursorCreatedAt,
      UUID cursorId) {
    // ワークスペース自体の非メンバーは404(存在秘匿、検索機能定義書§5)。多層防御(レビュー指摘対応)
    workspaceAuthorizationService.requireMember(workspaceId, callerId);
    validateQuery(query);

    List<UUID> channelIds =
        channelMemberRepository.findByUserIdAndWorkspaceId(callerId, workspaceId).stream()
            .map(ChannelMember::getChannelId)
            .toList();
    List<UUID> dmIds =
        dmThreadRepository.findAllForUser(workspaceId, callerId).stream()
            .map(DmThread::getId)
            .toList();

    if (channelIdFilter != null) {
      // 非所属チャンネル指定は403/404を返さず「結果なし」として扱う(検索機能定義書§3.1・§6、プライベート
      // チャンネルのrequireChannelMemberが404で存在を秘匿する設計との一貫性のため)
      if (!channelIds.contains(channelIdFilter)) {
        return new SearchResponse(List.of(), null);
      }
      channelIds = List.of(channelIdFilter);
      dmIds = List.of();
    }

    if (channelIds.isEmpty() && dmIds.isEmpty()) {
      return new SearchResponse(List.of(), null);
    }

    String pattern = escapeForIlike(query);
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    List<Message> messages =
        (cursorCreatedAt == null || cursorId == null)
            ? messageRepository.searchFirstPage(
                nonEmpty(channelIds), nonEmpty(dmIds), pattern, pageable)
            : messageRepository.searchOlderThan(
                nonEmpty(channelIds),
                nonEmpty(dmIds),
                pattern,
                cursorCreatedAt,
                cursorId,
                pageable);

    List<SearchResultItem> items = messages.stream().map(SearchResultItem::from).toList();
    Cursor nextCursor =
        messages.size() == PAGE_SIZE
            ? Cursor.of(
                messages.get(messages.size() - 1).getCreatedAt(),
                messages.get(messages.size() - 1).getId())
            : null;
    return new SearchResponse(items, nextCursor);
  }

  private void validateQuery(String query) {
    if (query == null || query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
      throw new BadRequestException("検索語は1〜200文字で入力してください。");
    }
  }

  /**
   * ILIKEのワイルドカード(`%`・`_`)とエスケープ文字自身(`\`)をエスケープしてから前後に`%`を付与する (検索機能定義書§3.3)。エスケープ文字自身 → `%` → `_`
   * の順序を厳守すること(順序を誤ると二重エスケープになる)。
   */
  private String escapeForIlike(String query) {
    String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private List<UUID> nonEmpty(List<UUID> ids) {
    return ids.isEmpty() ? List.of(UNMATCHABLE_ID) : ids;
  }
}
