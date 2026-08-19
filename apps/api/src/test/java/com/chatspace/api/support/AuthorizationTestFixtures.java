package com.chatspace.api.support;

import com.chatspace.api.auth.JwtService;
import com.chatspace.api.auth.PasswordService;
import com.chatspace.api.channel.Channel;
import com.chatspace.api.channel.ChannelMember;
import com.chatspace.api.channel.ChannelMemberRepository;
import com.chatspace.api.channel.ChannelRepository;
import com.chatspace.api.channel.ChannelType;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceMember;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import com.chatspace.api.workspace.WorkspaceRepository;
import com.chatspace.api.workspace.WorkspaceRole;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 認可クリティカルテスト用のフィクスチャ作成ヘルパー(テスト設計書§5)。プロトタイプの {@code test/helpers.ts} の {@code
 * createTestUser}/{@code createWorkspaceWithOwner}/{@code createChannel}
 * パターンを踏襲し、HTTP経由ではなくRepositoryへ直接保存することでテストを高速化する。
 */
@Component
public class AuthorizationTestFixtures {

  private static final String COOKIE_NAME = "chatspace_token";

  private final UserRepository userRepository;
  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final ChannelRepository channelRepository;
  private final ChannelMemberRepository channelMemberRepository;
  private final JwtService jwtService;
  private final PasswordService passwordService;

  public AuthorizationTestFixtures(
      UserRepository userRepository,
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      ChannelRepository channelRepository,
      ChannelMemberRepository channelMemberRepository,
      JwtService jwtService,
      PasswordService passwordService) {
    this.userRepository = userRepository;
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.channelRepository = channelRepository;
    this.channelMemberRepository = channelMemberRepository;
    this.jwtService = jwtService;
    this.passwordService = passwordService;
  }

  public User createUser() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return userRepository.save(
        new User("user-" + suffix, "not-a-real-hash", "Test User " + suffix));
  }

  /**
   * 実際にログインできるユーザーを作る(パスワードを本物のハッシュで保存する)。{@link #createUser()}は
   * ハッシュがダミーのためログインに必ず失敗する点に注意。レート制限テスト等、ログイン成功を伴う ケースでのみ使う(bcryptのコストがかかるため既定の{@code
   * createUser()}とは分けている)。
   */
  public User createUserWithPassword(String rawPassword) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return userRepository.save(
        new User("user-" + suffix, passwordService.hash(rawPassword), "Test User " + suffix));
  }

  public Workspace createWorkspaceWithOwner(User owner) {
    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", owner.getId()));
    workspaceMemberRepository.save(
        new WorkspaceMember(workspace.getId(), owner.getId(), WorkspaceRole.OWNER));
    return workspace;
  }

  public WorkspaceMember addWorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
    return workspaceMemberRepository.save(
        new WorkspaceMember(workspace.getId(), user.getId(), role));
  }

  public Channel createChannel(Workspace workspace, ChannelType type, User... members) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Channel channel =
        channelRepository.save(new Channel(workspace.getId(), "channel-" + suffix, type));
    for (User member : members) {
      channelMemberRepository.save(new ChannelMember(channel.getId(), member.getId()));
    }
    return channel;
  }

  /**
   * MockMvcの {@code .cookie(...)} にそのまま渡せる認証Cookieを返す。
   *
   * <p>{@code MockHttpServletRequest} は生の {@code Cookie} ヘッダを {@code getCookies()} へ自動変換しない
   * (実サーブレットコンテナと異なりパース処理を持たない)ため、{@code .header(HttpHeaders.COOKIE, ...)} ではなく 必ずこのCookieオブジェクトを
   * {@code .cookie(...)} で渡すこと。
   */
  public Cookie authCookie(User user) {
    return new Cookie(COOKIE_NAME, jwtService.issue(user.getId()));
  }
}
