package com.ccnu.profile.service;

import com.ccnu.profile.api.dto.ProfilePatchRequest;
import com.ccnu.profile.api.dto.ProfileResponse;

public interface ProfileService {
    ProfileResponse updateProfile(long userId, ProfilePatchRequest req);
    ProfileResponse updateAvatar(long userId, String avatarUrl);
}
