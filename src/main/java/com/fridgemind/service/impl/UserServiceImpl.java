package com.fridgemind.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.fridgemind.common.AuditLogHelper;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Family;
import com.fridgemind.entity.FamilyMember;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.FamilyMapper;
import com.fridgemind.mapper.FamilyMemberMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.security.JwtUtil;
import com.fridgemind.service.SmsService;
import com.fridgemind.service.UserService;
import com.fridgemind.vo.LoginVO;
import com.fridgemind.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** 用户服务实现（短信登录自动注册，密码需先设置） */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SmsService smsService;
    private final AuditLogHelper auditLogHelper;

    private static final String TOKEN_PREFIX = "token:";
    /** 改密后写入的哨兵值：与任何真实 JWT 都不相等，使旧 token 在 AuthInterceptor 处被判失效 */
    private static final String REVOKED_TOKEN = "REVOKED";
    private static final String PWD_ATTEMPTS_PREFIX = "pwd:attempts:";
    private static final int TOKEN_TTL_DAYS = 7;
    private static final int MAX_PWD_ATTEMPTS = 5;
    private static final int PWD_LOCK_MINUTES = 15;

    @Override
    @Transactional
    public LoginVO loginByCode(String phone, String code) {
        if (!smsService.verifyCode(phone, code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误或已过期");
        }

        // 查找用户，不存在则自动注册
        User user = userMapper.selectByPhone(phone);

        if (user == null) {
            user = registerUser(phone);
            // 新用户自动创建默认家庭
            createDefaultFamily(user.getId());
        }

        // 管理员禁用后不允许再登录（新注册用户 enabled 由库默认值给 1，不受影响）
        if (isDisabled(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 生成 JWT 并存入 Redis
        String token = generateAndStoreToken(user.getId());

        log.info("短信验证码登录成功 userId={}, phone={}", user.getId(), phone);
        auditLogHelper.info(user.getId(), null, "LOGIN_CODE", phone);

        return buildLoginVO(user, token);
    }

    @Override
    public LoginVO loginByPwd(String phone, String password) {
        // 检查密码错误锁定
        String attemptKey = PWD_ATTEMPTS_PREFIX + phone;
        Object attemptsObj = redisTemplate.opsForValue().get(attemptKey);
        int attempts = 0;
        if (attemptsObj instanceof Integer) {
            attempts = (Integer) attemptsObj;
        }
        if (attempts >= MAX_PWD_ATTEMPTS) {
            throw new BusinessException(ResultCode.PASSWORD_LOCKED);
        }

        User user = userMapper.selectByPhone(phone);
        if (user == null || user.getPassword() == null) {
            throw new BusinessException(ResultCode.PASSWORD_WRONG);
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            // 递增错误次数，15分钟后自动解除
            redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, PWD_LOCK_MINUTES, TimeUnit.MINUTES);
            int newAttempts = attempts + 1;
            if (newAttempts >= MAX_PWD_ATTEMPTS) {
                auditLogHelper.error(user.getId(), null, "LOGIN_LOCKED", phone + " 密码错误" + MAX_PWD_ATTEMPTS + "次已锁定");
            } else {
                auditLogHelper.log(user.getId(), null, "LOGIN_FAIL", phone + " 密码错误(第" + newAttempts + "次)", "WARN", "FAIL", null);
            }
            throw new BusinessException(ResultCode.PASSWORD_WRONG);
        }

        // 管理员禁用后不允许再登录
        if (isDisabled(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 密码正确：清除错误计数，生成 token
        redisTemplate.delete(attemptKey);
        String token = generateAndStoreToken(user.getId());

        log.info("密码登录成功 userId={}, phone={}", user.getId(), phone);
        auditLogHelper.info(user.getId(), null, "LOGIN_PWD", phone);

        return buildLoginVO(user, token);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String password) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 已设过密码 = 改密码，必须先验原密码；从未设过 = 首次设置，身份已由登录态确认
        boolean isFirstTime = user.getHasPassword() == null || user.getHasPassword() == 0;
        if (!isFirstTime) {
            if (oldPassword == null || user.getPassword() == null
                    || !BCrypt.checkpw(oldPassword, user.getPassword())) {
                throw new BusinessException(ResultCode.PASSWORD_WRONG, "原密码不正确");
            }
        }

        // BCrypt 加密存储
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        user.setPassword(hashed);
        user.setHasPassword(1);
        userMapper.updateById(user);

        // 作废该用户已签发的 token：密码改了，旧会话必须失效。
        // 必须写哨兵值而不是删除 —— AuthInterceptor 在 Redis key 不存在时会降级放行，
        // 删掉等于没作废，旧 token 能一直用到 JWT 自然过期（7 天）。
        redisTemplate.opsForValue().set(TOKEN_PREFIX + userId, REVOKED_TOKEN,
                TOKEN_TTL_DAYS, TimeUnit.DAYS);

        String action = isFirstTime ? "SET_PASSWORD" : "CHANGE_PASSWORD";
        log.info("用户{}密码成功 userId={}", isFirstTime ? "设置" : "修改", userId);
        auditLogHelper.info(userId, null, action, "userId=" + userId);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        return UserInfoVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .hasPassword(user.getHasPassword())
                .build();
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, String nickname) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        user.setNickname(nickname);
        userMapper.updateById(user);

        log.info("用户修改昵称 userId={}, nickname={}", userId, nickname);
        auditLogHelper.info(userId, null, "UPDATE_NICKNAME", nickname);
    }

    /** enabled 由管理员在后台维护：0=禁用；NULL 视为未禁用，避免老数据被误判 */
    private boolean isDisabled(User user) {
        return user.getEnabled() != null && user.getEnabled() == 0;
    }

    /** 注册新用户：随机昵称 "用户XXXX" */
    private User registerUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname("用户" + RandomUtil.randomNumbers(4));
        user.setHasPassword(0);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("新用户注册 userId={}, phone={}, nickname={}", user.getId(), phone, user.getNickname());
        return user;
    }

    /** 为新用户创建默认家庭 "我的家" 并设为 owner */
    private void createDefaultFamily(Long userId) {
        Family family = new Family();
        family.setName("我的家");
        family.setOwnerId(userId);
        family.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(family);

        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(0); // owner
        member.setJoinedAt(LocalDateTime.now());
        familyMemberMapper.insert(member);

        log.info("创建默认家庭 userId={}, familyId={}", userId, family.getId());
    }

    /** 生成 JWT 并存入 Redis，7 天有效 */
    private String generateAndStoreToken(Long userId) {
        String token = jwtUtil.generateToken(userId);
        redisTemplate.opsForValue().set(TOKEN_PREFIX + userId, token, TOKEN_TTL_DAYS, TimeUnit.DAYS);
        return token;
    }

    private LoginVO buildLoginVO(User user, String token) {
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .hasPassword(user.getHasPassword())
                .build();
    }
}
