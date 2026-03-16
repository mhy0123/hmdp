package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate  stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }
    public void setWithExpireTime(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R,ID> R queryWithPassThrough(String prefix , ID id, Class<R>type, Function<ID,R> dbFunction
            ,Long time, TimeUnit timeUnit) {
        // 从redis的缓存中进行查询
        String key= prefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //存在,直接返回商家信息
        if(StringUtils.isNotBlank(json)){
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //判断是否为空值
        if(json!=null){
            return null;
        }
        //不存在,根据id查询数据库
        R r = dbFunction.apply(id);
        //不存在,报错
        if(r==null) {
            //向redis中返回空字符串
            stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);
            return null;
        }
        //存在,写入redis缓存
        set(key,r,time,timeUnit);
        //返回
        return r;
    }
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(String prefix, ID id, Class<R> type, Function<ID,R> dbFunction
            ,Long time, TimeUnit timeUnit) {
        // 从redis的缓存中进行查询
        String key= prefix+id;
        String value = stringRedisTemplate.opsForValue().get(key);
        //不存在,直接返回空
        if(StringUtils.isBlank(value)){
            return null;
        }
        // 判断缓存是否过期
        // 未过期返回商家信息
        RedisData redisData = JSONUtil.toBean(value, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime=redisData.getExpireTime();
        // 未过期返回商家信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        // 获取互斥锁
        String lockKey= "CACHE_SHOP_LOCK_KEY"+id;
        Boolean isLock = tryLock(lockKey);
        if(isLock){
            // 过期了,如果获取到了锁,开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.setWithExpireTime(lockKey,JSONUtil.toJsonStr(r),time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 过期了,如果没获取锁,返回旧的商家信息
        return r;
    }
    private Boolean tryLock(String key){
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }
    private void unlock(String key){

        stringRedisTemplate.delete(key);
    }
}
