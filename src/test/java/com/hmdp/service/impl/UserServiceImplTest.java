package com.hmdp.service.impl;

import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_INDEX_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private MerchantAuthService merchantAuthService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "merchantAuthService", merchantAuthService);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createLoginTokenShouldReplacePreviousSessionAndWriteUserIndex() {
        User user = new User();
        user.setId(7L);
        user.setNickName("tester");

        when(valueOperations.get(LOGIN_USER_INDEX_KEY + 7L)).thenReturn("old-token");

        String token = ReflectionTestUtils.invokeMethod(service, "createLoginToken", user);

        assertThat(token).isNotBlank().isNotEqualTo("old-token");
        verify(stringRedisTemplate).delete(LOGIN_USER_KEY + "old-token");
        verify(stringRedisTemplate).delete(LOGIN_USER_INDEX_KEY + 7L);
        verify(hashOperations).putAll(eq(LOGIN_USER_KEY + token), anyMap());
        verify(stringRedisTemplate).expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        verify(valueOperations).set(LOGIN_USER_INDEX_KEY + 7L, token, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    @Test
    void logoutShouldDeleteUserIndexWhenItStillPointsToCurrentToken() {
        String token = "token-a";
        String tokenKey = LOGIN_USER_KEY + token;
        String userIndexKey = LOGIN_USER_INDEX_KEY + 9L;

        when(hashOperations.get(tokenKey, "id")).thenReturn("9");
        when(valueOperations.get(userIndexKey)).thenReturn(token);

        service.logout(token);

        verify(stringRedisTemplate).delete(tokenKey);
        verify(stringRedisTemplate).delete(userIndexKey);
    }

    @Test
    void logoutShouldNotDeleteUserIndexWhenAnotherTokenIsCurrent() {
        String token = "token-a";
        String tokenKey = LOGIN_USER_KEY + token;
        String userIndexKey = LOGIN_USER_INDEX_KEY + 9L;

        when(hashOperations.get(tokenKey, "id")).thenReturn("9");
        when(valueOperations.get(userIndexKey)).thenReturn("token-b");

        service.logout(token);

        verify(stringRedisTemplate).delete(tokenKey);
        verify(stringRedisTemplate, never()).delete(userIndexKey);
    }
}
