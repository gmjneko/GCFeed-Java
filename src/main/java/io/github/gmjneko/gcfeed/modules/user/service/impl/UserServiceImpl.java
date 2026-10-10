package io.github.gmjneko.gcfeed.modules.user.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtPayload;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.common.jwt.TokenBlacklist;
import io.github.gmjneko.gcfeed.modules.user.dto.LoginDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.UpdateProfileDTO;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.user.entity.UserRelationStat;
import io.github.gmjneko.gcfeed.modules.user.mapper.UserMapper;
import io.github.gmjneko.gcfeed.modules.user.mapper.UserRelationStatMapper;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import io.github.gmjneko.gcfeed.modules.user.vo.LoginVO;
import io.github.gmjneko.gcfeed.modules.user.vo.PublicUserVO;
import io.github.gmjneko.gcfeed.modules.user.vo.UserVO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * 实现注册、凭证认证和资料读写；注册唯一性由数据库保证，不维护通用幂等记录。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRelationStatMapper relationStatMapper;
    private final VideoMapper videoMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    private final String dummyPasswordHash;

    public UserServiceImpl(UserMapper userMapper, UserRelationStatMapper relationStatMapper,
                           VideoMapper videoMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, TokenBlacklist tokenBlacklist) {
        this.userMapper = userMapper;
        this.relationStatMapper = relationStatMapper;
        this.videoMapper = videoMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklist = tokenBlacklist;
        // 不存在的账号也执行一次 BCrypt，减少账号是否存在导致的认证耗时差异。
        this.dummyPasswordHash = passwordEncoder.encode("gcfeed-dummy-password");
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO dto) {
        String account = required(dto.account(), CommonConstant.MAX_ACCOUNT_LENGTH,
                ErrorCode.EMPTY_ACCOUNT, ErrorCode.ACCOUNT_TOO_LONG);
        String password = password(dto.password());
        String nickname = required(dto.nickname(), CommonConstant.MAX_NICKNAME_LENGTH,
                ErrorCode.EMPTY_NICKNAME, ErrorCode.NICKNAME_TOO_LONG);
        User user = User.builder()
                .account(account)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .status(CommonConstant.USER_STATUS_NORMAL)
                .role(CommonConstant.ROLE_USER)
                .deleted(0)
                .build();
        try {
            // 不先查后插：唯一索引同时覆盖重试、并发和已注销账号的重复注册。
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        // 统计行与账号同事务创建，主键直接复用账号 ID；后续计数由 relation 模块维护。
        relationStatMapper.insert(
                UserRelationStat.builder()
                        .userId(user.getId())
                        .followingCount(0)
                        .followerCount(0)
                        .build()
        );
        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String account = required(dto.account(), CommonConstant.MAX_ACCOUNT_LENGTH,
                ErrorCode.EMPTY_ACCOUNT, ErrorCode.ACCOUNT_TOO_LONG);
        String password = password(dto.password());
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getAccount, account));
        boolean matches = passwordEncoder.matches(password, user == null ? dummyPasswordHash : user.getPassword());
        if (user == null || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(CommonConstant.USER_STATUS_NORMAL).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_STATUS_ABNORMAL);
        }
        return new LoginVO(jwtUtil.signAccessToken(user.getId(), user.getRole()),
                "Bearer", jwtUtil.getAccessTtlSeconds());
    }

    @Override
    public void logout(JwtPayload payload) {
        tokenBlacklist.revoke(payload);
    }

    @Override
    public UserVO getProfile(Long userId) {
        requireUserId(userId);
        // 三个单表查询均走主键或覆盖索引；deleted = 0 由全局 logic-delete 配置自动附加。
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, userId));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserRelationStat stat = relationStatMapper.selectOne(Wrappers.<UserRelationStat>lambdaQuery()
                .eq(UserRelationStat::getUserId, userId));
        user.setFollowingCount(nonNegative(stat == null ? null : stat.getFollowingCount()));
        user.setFollowerCount(nonNegative(stat == null ? null : stat.getFollowerCount()));
        user.setWorkCount(videoMapper.selectCount(Wrappers.<Video>lambdaQuery()
                .eq(Video::getAuthorId, userId)
                .eq(Video::getStatus, CommonConstant.VIDEO_STATUS_PUBLISHED)));
        return UserVO.from(user);
    }

    @Override
    public PublicUserVO getPublicProfile(Long userId) {
        return PublicUserVO.from(getProfile(userId));
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        requireUserId(userId);
        if (dto.nickname() == null && dto.avatarUrl() == null && dto.bio() == null) {
            throw new BusinessException(ErrorCode.EMPTY_PROFILE_UPDATE);
        }
        // 仅传入的字段参与 UPDATE，不能读取完整旧实体再写回，否则会覆盖其他请求的修改。
        User.Builder patchBuilder = User.builder().id(userId);
        if (dto.nickname() != null) {
            patchBuilder.nickname(required(dto.nickname(), CommonConstant.MAX_NICKNAME_LENGTH,
                    ErrorCode.EMPTY_NICKNAME, ErrorCode.NICKNAME_TOO_LONG));
        }
        if (dto.avatarUrl() != null) {
            patchBuilder.avatarUrl(optional(dto.avatarUrl(), CommonConstant.MAX_AVATAR_URL_LENGTH,
                    ErrorCode.AVATAR_URL_TOO_LONG));
        }
        if (dto.bio() != null) {
            patchBuilder.bio(optional(dto.bio(), CommonConstant.MAX_BIO_LENGTH, ErrorCode.BIO_TOO_LONG));
        }
        userMapper.updateById(patchBuilder.build());
        // 同值更新在某些 JDBC 设置下影响行数为 0，不能仅凭影响行数判断用户不存在。
        return getProfile(userId);
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }
    }

    /**
     * 统计行缺失按 0，负数视为脏数据归零，等价于原 SQL 的 GREATEST(COALESCE(x, 0), 0)。
     */
    private long nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String password(String value) {
        String normalized = required(value, CommonConstant.MAX_PASSWORD_LENGTH,
                ErrorCode.EMPTY_PASSWORD, ErrorCode.PASSWORD_TOO_LONG);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > CommonConstant.MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG);
        }
        return normalized;
    }

    private String required(String value, int maxLength, ErrorCode emptyCode, ErrorCode lengthCode) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(emptyCode);
        }
        return optional(value, maxLength, lengthCode);
    }

    private String optional(String value, int maxLength, ErrorCode lengthCode) {
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new BusinessException(lengthCode);
        }
        return normalized;
    }

}
