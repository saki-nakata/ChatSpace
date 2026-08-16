package com.chatspace.api.message;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chatspace.api.channel.ChannelAuthorizationService;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code MentionCandidateService}のService層認可(多層防御、レビュー指摘対応)の単体テスト。候補一覧そのものが
 * メンバー構成の情報源になり得るため、対象チャンネルの非メンバーはリポジトリへ一切到達しないことを固定する。
 */
@ExtendWith(MockitoExtension.class)
class MentionCandidateServiceTest {

  @Mock private ChannelMemberRepository channelMemberRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChannelAuthorizationService channelAuthorizationService;

  @InjectMocks private MentionCandidateService mentionCandidateService;

  @Test
  void findCandidates_verifiesChannelMembershipBeforeQuerying() {
    UUID channelId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    when(channelAuthorizationService.requireChannelMember(channelId, callerId, null))
        .thenThrow(new NotFoundException("チャンネルが見つかりません。"));

    assertThrows(
        NotFoundException.class,
        () -> mentionCandidateService.findCandidates(channelId, callerId, "a"));

    verifyNoInteractions(channelMemberRepository, userRepository);
  }
}
