package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){

        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://139.9.116.24:6379").setPassword("wqy610366");
        //创建
        return Redisson.create(config);
    }
}
