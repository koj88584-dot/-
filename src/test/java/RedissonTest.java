import com.hmdp.zhouxuanDianPingApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest(classes = zhouxuanDianPingApplication.class)
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

    private RLock rLock;
    @BeforeEach
    private void getRlock(){
        rLock=redissonClient.getLock("lock");
    }

    @Test
    public void method1() throws InterruptedException {
        boolean isLock = rLock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock){
            System.out.println("获取锁失败--1");
        }
        try {
            System.out.println("获取锁成功--1");
            method2();
            System.out.println("执行业务--1");
        }
        finally {
            System.out.println("准备释放锁--1");
            rLock.unlock();
        }
    }

    public void method2(){
        boolean isLock = rLock.tryLock();
        if (!isLock){
            System.out.println("获取锁失败--2");
        }
        try {
            System.out.println("获取锁成功--2");
            System.out.println("执行业务--2");
        }
        finally {
            System.out.println("准备释放锁--2");
            rLock.unlock();
        }
    }
}
