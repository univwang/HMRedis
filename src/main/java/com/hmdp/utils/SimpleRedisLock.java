package com.hmdp.utils;

import lombok.AllArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class SimpleRedisLock implements ILock{

    private String name;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString().replace("-", "") + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取锁
        //线程的标识
        //线程的标识UUID

        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,
                String.valueOf(ThreadId), timeoutSec, TimeUnit.SECONDS);
        return absent != null && absent;
    }

//    @Override
//    public void unlock() {
//        //释放锁
//        //放置误删
//        //线程的标识UUID
//        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
//        if(ThreadId.equals(stringRedisTemplate.opsForValue().get(KEY_PREFIX + name))){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }

    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}
