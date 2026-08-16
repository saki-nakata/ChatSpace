package com.chatspace.api.profile;

import com.chatspace.api.common.CurrentUser;
import com.chatspace.api.user.UserResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** S-14プロフィール編集モーダルの使用API。 */
@RestController
public class UserProfileController {

  private final UserProfileService userProfileService;

  public UserProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @PatchMapping("/users/me")
  public UserResponse updateMe(
      @RequestBody UpdateProfileRequest request, @CurrentUser UUID userId) {
    return userProfileService.updateProfile(userId, request);
  }
}
