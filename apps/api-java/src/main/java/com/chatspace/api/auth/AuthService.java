package com.chatspace.api.auth;

import com.chatspace.api.common.ConflictException;
import com.chatspace.api.common.NotFoundException;
import com.chatspace.api.user.User;
import com.chatspace.api.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証機能定義書§3の業務ロジック(ビジネスロジック層)。HTTP・Cookieの概念は持たず、ControllerがCookie組み立てを担う。 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthResult signup(SignupRequest request) {
    if (userRepository.existsByUserId(request.userId())) {
      throw new ConflictException("このユーザーIDは既に使用されています。");
    }
    User user =
        new User(request.userId(), passwordService.hash(request.password()), request.displayName());
    userRepository.save(user);
    return new AuthResult(user, jwtService.issue(user.getId()));
  }

  @Transactional(readOnly = true)
  public AuthResult login(LoginRequest request) {
    Optional<User> userOpt = userRepository.findByUserId(request.userId());
    if (userOpt.isEmpty()) {
      // ユーザー不存在時もダミーハッシュに対してbcrypt照合を実行し、応答時間差を無くす(§6タイミング攻撃対策)
      passwordService.matchAgainstDummyHash(request.password());
      throw new InvalidCredentialsException();
    }
    User user = userOpt.get();
    if (!passwordService.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    return new AuthResult(user, jwtService.issue(user.getId()));
  }

  @Transactional(readOnly = true)
  public User currentUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("ユーザーが見つかりません。"));
  }
}
