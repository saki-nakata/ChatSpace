package com.chatspace.api.message;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.channel.ChannelMember;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.BadRequestException;
import com.chatspace.api.common.Cursor;
import com.chatspace.api.common.ForbiddenException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.dm.DmThread;
import com.chatspace.api.dm.DmThreadRepository;
import com.chatspace.api.notification.NotificationService;
import com.chatspace.api.notification.NotificationType;
import com.chatspace.api.realtime.RealtimeEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * メッセージング機能定義書§3の業務ロジック。チャンネル・DM共通(計画書§3、プロトタイプの{@code messages-service.ts}を移植)。
 *
 * <p>呼び出し元のスコープメンバーシップ(チャンネル/DMメンバーか)は{@code MessageController}が先に検証しているが、
 * 本クラス自身も各メソッド先頭で同じ検証を行う(レビュー指摘対応の多層防御。将来STOMPハンドラや別のController経由で
 * 本サービスが直接呼ばれても無防備にならないようにするため、Controllerだけに検証を委ねない)。{@code messageId}が 実際にそのスコープに属するかの検証は別途{@link
 * MessageScopeGuard}(confused-deputy対策)が担う。
 */
@Service
public class MessageService {

  private static final int LIST_PAGE_SIZE = 50;
  private static final int REPLY_PAGE_SIZE = 20;
  private static final int CONTEXT_HALF_SIZE = 25;

  /**
   * ジャンプ対象の返信を含む窓の上限。対象自身はDESC境界のため上限に関わらず必ず含まれるが、上限より前の返信は
   * 取得されず、かつ返信のページングは前方(新しい方向)のみで「さらに古い返信を読み込む」導線が無いため到達不能に
   * なる。300件を超えるスレッド返信はこのアプリの現実的な使用範囲では起きない想定で、上限到達時のUI追加(hasOlder
   * 相当のAPI拡張・ThreadPanel側の後方ページング)はスコープ外とする(実装計画書phase10.md参照)。
   */
  private static final int REPLY_JUMP_MAX_SIZE = 300;

  private final MessageRepository messageRepository;
  private final ReactionRepository reactionRepository;
  private final AttachmentRepository attachmentRepository;
  private final DmThreadRepository dmThreadRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final MentionResolver mentionResolver;
  private final NotificationService notificationService;
  private final RealtimeEventPublisher realtimeEventPublisher;
  private final ChannelAuthorizationService channelAuthorizationService;
  private final DmAuthorizationService dmAuthorizationService;

  public MessageService(
      MessageRepository messageRepository,
      ReactionRepository reactionRepository,
      AttachmentRepository attachmentRepository,
      DmThreadRepository dmThreadRepository,
      ChannelMemberRepository channelMemberRepository,
      MentionResolver mentionResolver,
      NotificationService notificationService,
      RealtimeEventPublisher realtimeEventPublisher,
      ChannelAuthorizationService channelAuthorizationService,
      DmAuthorizationService dmAuthorizationService) {
    this.messageRepository = messageRepository;
    this.reactionRepository = reactionRepository;
    this.attachmentRepository = attachmentRepository;
    this.dmThreadRepository = dmThreadRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.mentionResolver = mentionResolver;
    this.notificationService = notificationService;
    this.realtimeEventPublisher = realtimeEventPublisher;
    this.channelAuthorizationService = channelAuthorizationService;
    this.dmAuthorizationService = dmAuthorizationService;
  }

  /**
   * スコープメンバーシップの再検証(多層防御)。{@code workspaceIdOrNull}が無い呼び出し元ではnullを渡す(confused-deputy
   * チェックはControllerで実施済みのため、ここではメンバーシップの有無のみを見れば十分)。
   */
  private void requireScopeAccess(UUID channelId, UUID dmId, UUID callerId) {
    if (channelId != null) {
      channelAuthorizationService.requireChannelMember(channelId, callerId, null);
    } else {
      dmAuthorizationService.requireDmAccess(dmId, callerId, null);
    }
  }

  @Transactional
  public MessageResponse create(
      UUID workspaceId, UUID channelId, UUID dmId, UUID authorId, CreateMessageRequest request) {
    requireScopeAccess(channelId, dmId, authorId);
    Message parent = resolveParent(channelId, dmId, request.parentId());
    List<Attachment> attachments = resolveAttachments(request.attachmentIds(), authorId);

    Message message =
        messageRepository.save(new Message(channelId, dmId, parent, authorId, request.body()));

    if (!attachments.isEmpty()) {
      attachments.forEach(attachment -> attachment.attachToMessage(message.getId()));
      attachmentRepository.saveAll(attachments);
    }

    if (channelId != null) {
      mentionResolver.resolveAndNotify(message, workspaceId, channelId, authorId);
    } else {
      notifyDmRecipient(workspaceId, dmId, message, authorId);
    }
    if (parent != null) {
      notificationService.notify(
          NotificationType.THREAD_REPLY,
          parent.getAuthorId(),
          authorId,
          workspaceId,
          channelId,
          dmId,
          message.getId(),
          parent.getId());
    }

    MessageResponse response = toResponse(message, authorId);
    // 配信はreactedByMeを含まないBroadcastMessageResponseへ変換する(操作者視点の値が全購読者に配信される不具合の
    // 修正、レビュー指摘対応)。REST応答(戻り値)は引き続きviewerごとに正しいMessageResponseを返す
    realtimeEventPublisher.messageCreated(channelId, dmId, BroadcastMessageResponse.from(response));
    if (parent != null) {
      // 親メッセージのreplyCountが増えたことを、スレッドパネルを開いていないユーザーにも
      // 反映するため、親メッセージ自体もMESSAGE_UPDATEDとして再配信する(親のbody等は変わらないが、
      // メインのメッセージ一覧上の「N件の返信」表示をリアルタイム更新するために必要)。
      MessageResponse parentResponse = toResponse(parent, authorId);
      realtimeEventPublisher.messageUpdated(
          channelId, dmId, BroadcastMessageResponse.from(parentResponse));
    }
    return response;
  }

  private void notifyDmRecipient(UUID workspaceId, UUID dmId, Message message, UUID authorId) {
    DmThread thread = dmThreadRepository.findById(dmId).orElseThrow(this::notFound);
    UUID recipientId =
        thread.getUserAId().equals(authorId) ? thread.getUserBId() : thread.getUserAId();
    notificationService.notify(
        NotificationType.DM, recipientId, authorId, workspaceId, null, dmId, message.getId(), null);
  }

  private Message resolveParent(UUID channelId, UUID dmId, UUID parentId) {
    if (parentId == null) {
      return null;
    }
    Message parent =
        messageRepository
            .findById(parentId)
            .orElseThrow(() -> new NotFoundException("返信先のメッセージが見つかりません。"));
    boolean sameScope =
        (channelId != null && channelId.equals(parent.getChannelId()))
            || (dmId != null && dmId.equals(parent.getDmId()));
    if (!sameScope) {
      throw new BadRequestException("返信先のメッセージが不正です。");
    }
    if (parent.getParent() != null) {
      throw new BadRequestException("スレッド内の返信にはさらに返信できません。");
    }
    return parent;
  }

  private List<Attachment> resolveAttachments(List<UUID> attachmentIds, UUID authorId) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return List.of();
    }
    List<Attachment> attachments = attachmentRepository.findAllById(attachmentIds);
    if (attachments.size() != attachmentIds.size()) {
      throw new BadRequestException("指定された添付ファイルが不正です。");
    }
    for (Attachment attachment : attachments) {
      boolean ownedByAuthor = attachment.getUploaderId().equals(authorId);
      boolean unused = attachment.getMessageId() == null;
      if (!ownedByAuthor || !unused) {
        throw new BadRequestException("指定された添付ファイルが不正です。");
      }
    }
    return attachments;
  }

  @Transactional
  public MessageResponse edit(
      UUID channelId, UUID dmId, UUID messageId, UUID callerId, EditMessageRequest request) {
    requireScopeAccess(channelId, dmId, callerId);
    Message message = loadWritable(channelId, dmId, messageId);
    if (!message.getAuthorId().equals(callerId)) {
      throw new ForbiddenException("他人のメッセージは編集できません。");
    }
    message.edit(request.body(), Instant.now());
    messageRepository.save(message);
    MessageResponse response = toResponse(message, callerId);
    realtimeEventPublisher.messageUpdated(channelId, dmId, BroadcastMessageResponse.from(response));
    return response;
  }

  @Transactional
  public void delete(UUID channelId, UUID dmId, UUID messageId, UUID callerId) {
    requireScopeAccess(channelId, dmId, callerId);
    Message message = loadWritable(channelId, dmId, messageId);
    if (!message.getAuthorId().equals(callerId)) {
      throw new ForbiddenException("他人のメッセージは削除できません。");
    }
    message.markDeleted(Instant.now());
    messageRepository.save(message);
    // MessageResponse.fromはdeleted=trueの場合bodyを空文字に置き換えるため、本文を含まずに配信される(§3.3)
    realtimeEventPublisher.messageDeleted(
        channelId, dmId, BroadcastMessageResponse.from(toResponse(message, callerId)));
  }

  @Transactional
  public MessageResponse toggleReaction(
      UUID channelId, UUID dmId, UUID messageId, UUID callerId, String emoji) {
    requireScopeAccess(channelId, dmId, callerId);
    Message message = loadWritable(channelId, dmId, messageId);
    reactionRepository
        .findByMessageIdAndUserIdAndEmoji(messageId, callerId, emoji)
        .ifPresentOrElse(
            reactionRepository::delete,
            () -> reactionRepository.save(new Reaction(messageId, callerId, emoji)));
    MessageResponse response = toResponse(message, callerId);
    realtimeEventPublisher.reactionUpdated(
        channelId, dmId, BroadcastMessageResponse.from(response));
    return response;
  }

  /** 編集・削除・リアクション等の書き込み系操作の対象を取得する。削除済みは404として扱う(§6.3)。 */
  private Message loadWritable(UUID channelId, UUID dmId, UUID messageId) {
    Message message = messageRepository.findById(messageId).orElseThrow(this::notFound);
    MessageScopeGuard.assertInScope(message, channelId, dmId);
    if (message.isDeleted()) {
      throw notFound();
    }
    return message;
  }

  @Transactional(readOnly = true)
  public MessageListResponse list(
      UUID channelId, UUID dmId, UUID callerId, Instant cursorCreatedAt, UUID cursorId) {
    requireScopeAccess(channelId, dmId, callerId);
    boolean firstPage = cursorCreatedAt == null || cursorId == null;
    Pageable pageable = PageRequest.of(0, LIST_PAGE_SIZE);
    List<Message> messages =
        firstPage
            ? messageRepository.findTopLevelFirstPage(channelId, dmId, pageable)
            : messageRepository.findTopLevelOlderThan(
                channelId, dmId, cursorCreatedAt, cursorId, pageable);
    // callerLastReadAtは未読区切り線の基準として初回ページでのみ意味を持つため、ページング継続時は
    // 無駄なDB往復を避けて問い合わせ自体をスキップする(MessageListResponseのJavadoc参照)。
    Instant callerLastReadAt = firstPage ? resolveLastReadAt(channelId, dmId, callerId) : null;
    return toListResponse(messages, callerId, messages.size() == LIST_PAGE_SIZE, callerLastReadAt);
  }

  /**
   * 呼び出し元(自分)のスコープ内既読位置(未読区切り線の基準)。{@code requireScopeAccess}が内部で呼ぶ {@code
   * ChannelAuthorizationService.requireChannelMember}は{@code ChannelMember}存在検証のためだけに
   * リポジトリを呼んで結果を捨てて{@code Channel}を返すため、ここで改めて問い合わせる必要がある。
   */
  private Instant resolveLastReadAt(UUID channelId, UUID dmId, UUID callerId) {
    if (channelId != null) {
      return channelMemberRepository
          .findByChannelIdAndUserId(channelId, callerId)
          .map(ChannelMember::getLastReadAt)
          .orElse(null);
    }
    DmThread thread = dmThreadRepository.findById(dmId).orElseThrow(this::notFound);
    return callerId.equals(thread.getUserAId()) ? thread.getLastReadAtA() : thread.getLastReadAtB();
  }

  @Transactional(readOnly = true)
  public MessageListResponse listReplies(
      UUID channelId,
      UUID dmId,
      UUID parentMessageId,
      UUID callerId,
      Instant cursorCreatedAt,
      UUID cursorId) {
    requireScopeAccess(channelId, dmId, callerId);
    Message parent = messageRepository.findById(parentMessageId).orElseThrow(this::notFound);
    MessageScopeGuard.assertInScope(parent, channelId, dmId);

    Pageable pageable = PageRequest.of(0, REPLY_PAGE_SIZE);
    List<Message> replies =
        (cursorCreatedAt == null || cursorId == null)
            ? messageRepository.findRepliesFirstPage(parentMessageId, pageable)
            : messageRepository.findRepliesAfterCursor(
                parentMessageId, cursorCreatedAt, cursorId, pageable);
    return toListResponse(replies, callerId, replies.size() == REPLY_PAGE_SIZE, null);
  }

  /** 検索/通知からスレッド返信へジャンプする際、対象返信を含む窓を取得する(21件目以降の返信への ジャンプに対応するための新設エンドポイント、実装計画書phase10.md参照)。 */
  @Transactional(readOnly = true)
  public MessageListResponse listRepliesAround(
      UUID channelId, UUID dmId, UUID parentMessageId, UUID aroundReplyId, UUID callerId) {
    requireScopeAccess(channelId, dmId, callerId);
    Message parent = messageRepository.findById(parentMessageId).orElseThrow(this::notFound);
    MessageScopeGuard.assertInScope(parent, channelId, dmId);
    Message target = messageRepository.findById(aroundReplyId).orElseThrow(this::notFound);
    if (target.getParent() == null || !target.getParent().getId().equals(parentMessageId)) {
      // confused-deputy対策: 対象返信が指定した親の配下でなければ存在自体を教えない(404で統一)
      throw notFound();
    }

    Pageable windowPage = PageRequest.of(0, REPLY_JUMP_MAX_SIZE);
    List<Message> descWindow =
        messageRepository.findRepliesUpToAndIncludingDesc(
            parentMessageId, target.getCreatedAt(), target.getId(), windowPage);
    List<Message> replies = new ArrayList<>(descWindow);
    Collections.reverse(replies);

    Pageable one = PageRequest.of(0, 1);
    boolean hasMore =
        !messageRepository
            .findRepliesAfterCursor(parentMessageId, target.getCreatedAt(), target.getId(), one)
            .isEmpty();
    Cursor nextCursor = hasMore ? cursorOf(target) : null;
    return new MessageListResponse(toResponses(replies, callerId), nextCursor, null);
  }

  private MessageListResponse toListResponse(
      List<Message> messages, UUID callerId, boolean mayHaveMore, Instant callerLastReadAt) {
    Cursor nextCursor =
        mayHaveMore && !messages.isEmpty() ? cursorOf(messages.get(messages.size() - 1)) : null;
    return new MessageListResponse(toResponses(messages, callerId), nextCursor, callerLastReadAt);
  }

  @Transactional(readOnly = true)
  public MessageContextResponse getContext(
      UUID channelId, UUID dmId, UUID messageId, UUID callerId) {
    requireScopeAccess(channelId, dmId, callerId);
    Message target = messageRepository.findById(messageId).orElseThrow(this::notFound);
    MessageScopeGuard.assertInScope(target, channelId, dmId);

    // 対象がスレッド返信の場合は親メッセージを軸にウィンドウを組む(メッセージング機能定義書§3.6)
    Message windowTarget = target.getParent() != null ? target.getParent() : target;
    UUID replyMessageId = target.getParent() != null ? target.getId() : null;

    Pageable halfPage = PageRequest.of(0, CONTEXT_HALF_SIZE);
    List<Message> olderDesc =
        messageRepository.findTopLevelOlderThan(
            channelId, dmId, windowTarget.getCreatedAt(), windowTarget.getId(), halfPage);
    List<Message> newer =
        messageRepository.findTopLevelNewerThan(
            channelId, dmId, windowTarget.getCreatedAt(), windowTarget.getId(), halfPage);

    List<Message> older = new ArrayList<>(olderDesc);
    Collections.reverse(older);

    List<Message> window = new ArrayList<>(older.size() + 1 + newer.size());
    window.addAll(older);
    window.add(windowTarget);
    window.addAll(newer);

    Cursor olderCursor = older.isEmpty() ? cursorOf(windowTarget) : cursorOf(older.get(0));
    Cursor newerCursor =
        newer.isEmpty() ? cursorOf(windowTarget) : cursorOf(newer.get(newer.size() - 1));

    return new MessageContextResponse(
        toResponses(window, callerId),
        messageId,
        replyMessageId,
        olderCursor,
        newerCursor,
        olderDesc.size() == CONTEXT_HALF_SIZE,
        newer.size() == CONTEXT_HALF_SIZE);
  }

  private MessageResponse toResponse(Message message, UUID callerId) {
    return toResponses(List.of(message), callerId).get(0);
  }

  private List<MessageResponse> toResponses(List<Message> messages, UUID callerId) {
    if (messages.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = messages.stream().map(Message::getId).toList();
    Map<UUID, List<ReactionSummary>> reactionsByMessage = reactionSummaries(ids, callerId);
    Map<UUID, Long> replyCounts = replyCounts(ids);
    Map<UUID, List<MessageAttachmentResponse>> attachmentsByMessage = attachmentResponses(ids);
    return messages.stream()
        .map(
            m ->
                MessageResponse.from(
                    m,
                    reactionsByMessage.getOrDefault(m.getId(), List.of()),
                    replyCounts.getOrDefault(m.getId(), 0L),
                    attachmentsByMessage.getOrDefault(m.getId(), List.of())))
        .toList();
  }

  private Map<UUID, List<MessageAttachmentResponse>> attachmentResponses(List<UUID> messageIds) {
    return attachmentRepository.findByMessageIdIn(messageIds).stream()
        .collect(
            Collectors.groupingBy(
                Attachment::getMessageId,
                LinkedHashMap::new,
                Collectors.mapping(MessageAttachmentResponse::from, Collectors.toList())));
  }

  private Map<UUID, List<ReactionSummary>> reactionSummaries(List<UUID> messageIds, UUID callerId) {
    List<Reaction> reactions = reactionRepository.findByMessageIdIn(messageIds);
    Map<UUID, List<Reaction>> byMessage =
        reactions.stream().collect(Collectors.groupingBy(Reaction::getMessageId));
    Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
    byMessage.forEach(
        (messageId, list) -> {
          Map<String, List<Reaction>> byEmoji =
              list.stream().collect(Collectors.groupingBy(Reaction::getEmoji));
          List<ReactionSummary> summaries =
              byEmoji.entrySet().stream()
                  .map(
                      e ->
                          new ReactionSummary(
                              e.getKey(),
                              e.getValue().size(),
                              e.getValue().stream().anyMatch(r -> r.getUserId().equals(callerId)),
                              e.getValue().stream().map(Reaction::getUserId).toList()))
                  .toList();
          result.put(messageId, summaries);
        });
    return result;
  }

  private Map<UUID, Long> replyCounts(List<UUID> messageIds) {
    return messageRepository.countRepliesByParentIds(messageIds).stream()
        .collect(
            Collectors.toMap(
                MessageRepository.ParentReplyCount::getParentId,
                MessageRepository.ParentReplyCount::getCount));
  }

  private Cursor cursorOf(Message message) {
    return Cursor.of(message.getCreatedAt(), message.getId());
  }

  private NotFoundException notFound() {
    return new NotFoundException("メッセージが見つかりません。");
  }
}
