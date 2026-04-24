import com.hmdp.zhouxuanDianPingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;

@SpringBootTest(classes = zhouxuanDianPingApplication.class)
public class RedisCleanupTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void cleanupRedundantCache() {
        // 清理 favorite/shop/ 路径下的所有键
        Set<String> keys = stringRedisTemplate.keys("favorite:shop:*");
        if (keys != null && !keys.isEmpty()) {
            System.out.println("清理前的键数量: " + keys.size());
            stringRedisTemplate.delete(keys);
            System.out.println("已清理 " + keys.size() + " 个冗余缓存键");
        } else {
            System.out.println("没有找到需要清理的冗余缓存键");
        }

        // 验证清理结果
        Set<String> remainingKeys = stringRedisTemplate.keys("favorite:shop:*");
        if (remainingKeys != null && !remainingKeys.isEmpty()) {
            System.out.println("清理后仍存在的键: " + remainingKeys);
        } else {
            System.out.println("清理完成，没有剩余的冗余缓存键");
        }
    }
}
