package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_INDEX_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RefreshTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RefreshTokenInterceptor(stringRedisTemplate);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void preHandleShouldInvalidateStaleTokenWhenUserIndexPointsElsewhere() throws Exception {
        String token = "old-token";
        String tokenKey = LOGIN_USER_KEY + token;
        String userIndexKey = LOGIN_USER_INDEX_KEY + 12L;
        Map<Object, Object> userMap = buildUserMap(12L);

        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("authorization")).thenReturn(token);
        when(hashOperations.entries(tokenKey)).thenReturn(userMap);
        when(valueOperations.get(userIndexKey)).thenReturn("new-token");

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(UserHolder.getUser()).isNull();
        verify(stringRedisTemplate).delete(tokenKey);
        verify(stringRedisTemplate, never()).expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        verify(stringRedisTemplate, never()).expire(userIndexKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    @Test
    void preHandleShouldRefreshBothTokenAndUserIndexWhenSessionIsCurrent() throws Exception {
        String token = "live-token";
        String tokenKey = LOGIN_USER_KEY + token;
        String userIndexKey = LOGIN_USER_INDEX_KEY + 12L;
        Map<Object, Object> userMap = buildUserMap(12L);

        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("authorization")).thenReturn(token);
        when(hashOperations.entries(tokenKey)).thenReturn(userMap);
        when(valueOperations.get(userIndexKey)).thenReturn(token);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        UserDTO currentUser = UserHolder.getUser();
        assertThat(currentUser).isNotNull();
        assertThat(currentUser.getId()).isEqualTo(12L);
        verify(stringRedisTemplate).expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        verify(stringRedisTemplate).expire(userIndexKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    @Test
    void preHandleShouldHealMissingUserIndexForLegacyToken() throws Exception {
        String token = "legacy-token";
        String tokenKey = LOGIN_USER_KEY + token;
        String userIndexKey = LOGIN_USER_INDEX_KEY + 12L;
        Map<Object, Object> userMap = buildUserMap(12L);

        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("authorization")).thenReturn(token);
        when(hashOperations.entries(tokenKey)).thenReturn(userMap);
        when(valueOperations.get(userIndexKey)).thenReturn(null);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(UserHolder.getUser()).isNotNull();
        verify(valueOperations).set(userIndexKey, token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        verify(stringRedisTemplate).expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    }

    private Map<Object, Object> buildUserMap(Long userId) {
        Map<Object, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userId));
        userMap.put("nickName", "tester");
        userMap.put("icon", "");
        return userMap;
    }
}
