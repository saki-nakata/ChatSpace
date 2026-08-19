package com.chatspace.api.config;

import com.chatspace.api.auth.PasswordService;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import com.chatspace.api.workspace.Workspace;
import com.chatspace.api.workspace.WorkspaceMember;
import com.chatspace.api.workspace.WorkspaceMemberRepository;
import com.chatspace.api.workspace.WorkspaceRepository;
import com.chatspace.api.workspace.WorkspaceRole;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ローカル開発用シードデータ投入(計画書§9)。alice/bob/carol(password123)とSample Workspaceを作成する。 旧SQLiteプロトタイプの {@code
 * seed.ts} からの移行はせず、新規シードのみを行う。
 *
 * <p>起動例: {@code ./gradlew bootRun --args='--spring.profiles.active=dev,seed'}
 */
@Component
@Profile("seed")
public class DevSeedRunner implements CommandLineRunner {

  private static final String SEED_PASSWORD = "password123";

  private final UserRepository userRepository;
  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final PasswordService passwordService;

  public DevSeedRunner(
      UserRepository userRepository,
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      PasswordService passwordService) {
    this.userRepository = userRepository;
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.passwordService = passwordService;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (userRepository.count() > 0) {
      return; // 再起動のたびに重複投入しない
    }

    User alice = createUser("alice", "Alice");
    User bob = createUser("bob", "Bob");
    User carol = createUser("carol", "Carol");
    userRepository.saveAll(List.of(alice, bob, carol));

    Workspace workspace =
        workspaceRepository.save(new Workspace("Sample Workspace", alice.getId()));

    workspaceMemberRepository.saveAll(
        List.of(
            new WorkspaceMember(workspace.getId(), alice.getId(), WorkspaceRole.OWNER),
            new WorkspaceMember(workspace.getId(), bob.getId(), WorkspaceRole.MEMBER),
            new WorkspaceMember(workspace.getId(), carol.getId(), WorkspaceRole.MEMBER)));
  }

  private User createUser(String userId, String displayName) {
    return new User(userId, passwordService.hash(SEED_PASSWORD), displayName);
  }
}
