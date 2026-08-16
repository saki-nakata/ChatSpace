package com.chatspace.api.message;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmAuthorizationService;
import com.chatspace.api.dm.DmThreadRepository;
import com.chatspace.api.notification.NotificationService;
import com.chatspace.api.realtime.RealtimeEventPublisher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code MessageService}のService層認可(多層防御、レビュー指摘対応)の単体テスト。
 *
 * <p>Testcontainers統合テスト(認可クリティカルテスト群)とは別に、Mockitoによる高速な単体テストとして
 * 「Controllerを経由せず本サービスが直接呼ばれても無防備にならない」ことをピンポイントで検証する。
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  @Mock private MessageRepository messageRepository;
  @Mock private ReactionRepository reactionRepository;
  @Mock private AttachmentRepository attachmentRepository;
  @Mock private DmThreadRepository dmThreadRepository;
  @Mock private MentionResolver mentionResolver;
  @Mock private NotificationService notificationService;
  @Mock private RealtimeEventPublisher realtimeEventPublisher;
  @Mock private ChannelAuthorizationService channelAuthorizationService;
  @Mock private DmAuthorizationService dmAuthorizationService;

  @InjectMocks private MessageService messageService;

  @Test
  void create_channelMessage_verifiesChannelMembershipBeforeAnyWrite() {
    UUID channelId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    when(channelAuthorizationService.requireChannelMember(channelId, authorId, null))
        .thenThrow(new NotFoundException("チャンネルが見つかりません。"));

    assertThrows(
        NotFoundException.class,
        () ->
            messageService.create(
                UUID.randomUUID(),
                channelId,
                null,
                authorId,
                new CreateMessageRequest("hi", null, null)));

    verifyNoInteractions(messageRepository, mentionResolver, realtimeEventPublisher);
  }

  @Test
  void create_dmMessage_verifiesDmAccessBeforeAnyWrite() {
    UUID dmId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    when(dmAuthorizationService.requireDmAccess(dmId, authorId, null))
        .thenThrow(new NotFoundException("DMが見つかりません。"));

    assertThrows(
        NotFoundException.class,
        () ->
            messageService.create(
                UUID.randomUUID(),
                null,
                dmId,
                authorId,
                new CreateMessageRequest("hi", null, null)));

    verifyNoInteractions(messageRepository, dmThreadRepository, realtimeEventPublisher);
  }

  @Test
  void list_channelScope_verifiesChannelMembershipBeforeQuerying() {
    UUID channelId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    when(channelAuthorizationService.requireChannelMember(channelId, callerId, null))
        .thenThrow(new NotFoundException("チャンネルが見つかりません。"));

    assertThrows(
        NotFoundException.class, () -> messageService.list(channelId, null, callerId, null, null));

    verifyNoInteractions(messageRepository);
  }
}
