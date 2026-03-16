package com.hmdp.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    private static final String LOCK_ID = UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long>UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation((Resource) new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);

    }
    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        String id = LOCK_ID+Thread.currentThread().getId();
        //获取锁
        Boolean flag =redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX+name,id,
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }
    @Override
    public void unlock() {
        //执行lua脚本
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX+name),
                LOCK_ID+Thread.currentThread().getId());
    }
    /*@Override
    public void unlock() {
        //获取锁中的表示
        String id = LOCK_ID+Thread.currentThread().getId();
        //判断是否是当前线程持有的锁
        String lockId = redisTemplate.opsForValue().get(LOCK_PREFIX+name);
        if(!id.equals(lockId)){
            return;
        }
        //释放锁
        redisTemplate.delete(LOCK_PREFIX+name);
    }*/
}
