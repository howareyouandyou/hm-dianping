package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.DefaultedRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author 86152
 * @version 1.0
 * Create by 2024/2/7 13:15
 */
public class SimpleRedisLock implements ILock{

    private String name;  //业务的名字
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true) +"-";

    //unlock的脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //初始化脚本
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功; false代表获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识（UUID+线程号）
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success=stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);

        //防止出现空指针
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock()
    {
        //调用lua脚本，脚本保证了原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,  //org.springframework.data.redis.core.script.RedisScript<T> script
                Collections.singletonList(KEY_PREFIX+name),  //java.util.List<K> keys
                ID_PREFIX+Thread.currentThread().getId() //Object... args
        );
    }
//    public void unlock() {
//        //获取线程标示
//        String threadId=ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标示
//        String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        //判断标示是否一致
//        if(threadId.equals(id))
//        {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//    }
}
