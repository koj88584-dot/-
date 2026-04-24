package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 *
 * @author CHEN
 * @date 2022/10/07
 */
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class zhouxuanDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(zhouxuanDianPingApplication.class, args);
    }

}
