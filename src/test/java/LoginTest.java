import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.zhouxuanDianPingApplication;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_INDEX_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@SpringBootTest(classes = zhouxuanDianPingApplication.class)
public class LoginTest {
    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testGenerateUser() {
        long phone = 17600000000L + (System.currentTimeMillis() % 1000000L);
        for (int i = 0; i < 1000; i++) {
            User user = new User();
            user.setPhone(String.valueOf(phone));
            phone++;
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            userMapper.insert(user);
        }
    }

    @Test
    public void testLogin() throws IOException {
        LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userLambdaQueryWrapper.gt(true, User::getId, 1011L);
        List<User> users = userMapper.selectList(userLambdaQueryWrapper);

        Path outputDir = Paths.get("target", "test-output");
        Files.createDirectories(outputDir);
        Path tokens = outputDir.resolve("tokens.txt");

        try (BufferedWriter writer = Files.newBufferedWriter(tokens, StandardCharsets.UTF_8)) {
            for (User user : users) {
                String token = UUID.randomUUID().toString(true);
                writer.write(token);
                writer.newLine();

                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> map = BeanUtil.beanToMap(
                        userDTO,
                        new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((name, value) -> value == null ? null : value.toString())
                );

                stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
                stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
                stringRedisTemplate.opsForValue().set(LOGIN_USER_INDEX_KEY + user.getId(), token, LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
        }
    }
}
