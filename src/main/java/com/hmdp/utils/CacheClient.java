package com.hmdp.utils;

import cn.hutool.core.text.replacer.StrReplacer;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author 86152
 * @version 1.0
 * Create by 2024/2/4 23:31
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit)
    {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit)
    {
        //设置逻辑过期
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     * @param keyPrefix（类似CACHE_SHOP_KEY的前缀）
     * @param id
     * @param type
     * @param dbFallback 提供数据库查询的逻辑函数Function（包含参数（ID id）和返回值（R）
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit)
    {
        String key = keyPrefix+id;

        String json= stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(json))
        {

            return JSONUtil.toBean(json,type);
        }

        //判断命中的是否是空值
        if(json!=null)
        {
            //返回一个错误信息
            return null;
        }

        R r= dbFallback.apply(id);
        if(r==null)
        {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        this.set(key, r, time, unit);

        return r;
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿（使用逻辑过期解决）
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit)
    {
        String key = keyPrefix+id;

        String json= stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isBlank(json))
        {

            return null;
        }

        // 4.
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r=JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime=redisData.getExpireTime();

        // 5.
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            // 5.1
            return r;
        }
        // 5.2

        // 6.
        // 6.1
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
        //6.2
        if(isLock)
        {
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1= dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        return r;
    }

    /**
     * 创建互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key)
    {
        // setIfAbsent起setnx的作用：key不存在才能set成功，key存在不能set。因此可以用来做互斥锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param key
     * @return
     */
    private void unlock(String key)
    {
        stringRedisTemplate.delete(key);
    }
}
