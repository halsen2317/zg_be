package com.ccnu.profile.service.impl;

import com.ccnu.common.exception.BusinessException;
import com.ccnu.common.exception.ErrorCode;
import com.ccnu.profile.api.dto.ProfilePatchRequest;
import com.ccnu.profile.api.dto.ProfileResponse;
import com.ccnu.profile.service.ProfileService;
import com.ccnu.user.domain.User;
import com.ccnu.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人资料服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    @Override
    @Transactional
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest req) {
        User current = userMapper.findById(userId);
        if (current == null) throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");

        boolean hasAny = req.nickname() != null || req.bio() != null || req.gender() != null
                || req.birthday() != null || req.zgId() != null || req.school() != null || req.tagJson() != null;
        if (!hasAny) throw new BusinessException(ErrorCode.BAD_REQUEST, "未提交任何更新字段");

        if (req.zgId() != null && !req.zgId().isBlank()) {
            if (userMapper.existsByZgIdExceptId(req.zgId(), current.getId())) {
                throw new BusinessException(ErrorCode.ZGID_EXISTS);
            }
        }

        User patch = new User();
        patch.setId(current.getId());
        if (req.nickname() != null) patch.setNickname(req.nickname().trim());
        if (req.bio() != null) patch.setBio(req.bio().trim());
        if (req.gender() != null) patch.setGender(req.gender().trim().toUpperCase());
        if (req.birthday() != null) patch.setBirthday(req.birthday());
        if (req.zgId() != null) patch.setZgId(req.zgId().trim());
        if (req.school() != null) patch.setSchool(req.school().trim());
        if (req.tagJson() != null) patch.setTagsJson(req.tagJson());
        userMapper.updateProfile(patch);
        return toResponse(userMapper.findById(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(long userId, String avatarUrl) {
        User current = userMapper.findById(userId);
        if (current == null) throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");
        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatarUrl);
        userMapper.updateProfile(patch);
        return toResponse(userMapper.findById(userId));
    }

    private ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getNickname(), u.getAvatar(), u.getBio(), u.getZgId(),
                u.getGender(), u.getBirthday(), u.getSchool(), u.getPhone(), u.getEmail(), u.getTagsJson());
    }
}
