package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 * @author 86152
 * @version 1.0
 * Create by 2024/2/10 22:20
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient()
    {
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("123456");

        //创建RedissonClient对象
        return Redisson.create(config);
    }

}
