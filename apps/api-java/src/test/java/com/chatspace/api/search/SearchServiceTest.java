package com.chatspace.api.search;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.dm.DmThreadRepository;
import com.chatspace.api.message.MessageRepository;
import com.chatspace.api.workspace.WorkspaceAuthorizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code SearchService}のService層認可(多層防御、レビュー指摘対応)の単体テスト。ワークスペース非メンバーは
 * チャンネル/DMの解決すら行わず即座に拒否されることを、Mockitoによる高速な単体テストで固定する。
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock private MessageRepository messageRepository;
  @Mock private ChannelMemberRepository channelMemberRepository;
  @Mock private DmThreadRepository dmThreadRepository;
  @Mock private WorkspaceAuthorizationService workspaceAuthorizationService;

  @InjectMocks private SearchService searchService;

  @Test
  void search_verifiesWorkspaceMembershipBeforeQuerying() {
    UUID workspaceId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    when(workspaceAuthorizationService.requireMember(workspaceId, callerId))
        .thenThrow(new NotFoundException("ワークスペースが見つかりません。"));

    assertThrows(
        NotFoundException.class,
        () -> searchService.search(workspaceId, callerId, "test query", null, null, null));

    verifyNoInteractions(channelMemberRepository, dmThreadRepository, messageRepository);
  }
}
