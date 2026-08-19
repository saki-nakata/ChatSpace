package com.chatspace.api.message;

import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.notification.NotificationService;
import com.chatspace.api.notification.NotificationType;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * チャンネルメッセージ本文中の {@code @ユーザーID} を抽出し、その時点のライブなチャンネルメンバーシップとのみ突合して {@link Mention} レコード・{@code
 * MENTION} 通知を生成する(メンション機能定義書§3.1・§3.2、最重要の設計制約)。
 *
 * <p>DMメッセージは対象外(呼び出し元が常にチャンネルメッセージのみで呼ぶ)。事前にキャッシュされたメンバー一覧や クライアントから送られた候補リストは信用せず、都度{@link
 * ChannelMemberRepository}へ問い合わせる。
 */
@Component
class MentionResolver {

  private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_.-]{3,20})");

  private final UserRepository userRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final MentionRepository mentionRepository;
  private final NotificationService notificationService;

  MentionResolver(
      UserRepository userRepository,
      ChannelMemberRepository channelMemberRepository,
      MentionRepository mentionRepository,
      NotificationService notificationService) {
    this.userRepository = userRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.mentionRepository = mentionRepository;
    this.notificationService = notificationService;
  }

  /** メンションを解決し、{@code Mention}レコードの保存とMENTION通知の生成を行う。 */
  void resolveAndNotify(Message message, UUID workspaceId, UUID channelId, UUID authorId) {
    for (String handle : extractHandles(message.getBody())) {
      userRepository
          .findByUserId(handle)
          .filter(user -> !user.getId().equals(authorId)) // 自己メンションは除外(§3.2)
          .filter(
              user ->
                  channelMemberRepository
                      .findByChannelIdAndUserId(channelId, user.getId())
                      .isPresent())
          .ifPresent(user -> createMention(message, workspaceId, channelId, authorId, user));
    }
  }

  private void createMention(
      Message message, UUID workspaceId, UUID channelId, UUID authorId, User mentionedUser) {
    mentionRepository.save(new Mention(message.getId(), mentionedUser.getId()));
    notificationService.notify(
        NotificationType.MENTION,
        mentionedUser.getId(),
        authorId,
        workspaceId,
        channelId,
        null,
        message.getId(),
        null);
  }

  private Set<String> extractHandles(String body) {
    Set<String> handles = new LinkedHashSet<>();
    Matcher matcher = MENTION_PATTERN.matcher(body);
    while (matcher.find()) {
      handles.add(matcher.group(1));
    }
    return handles;
  }
}
