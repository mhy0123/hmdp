package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough("CACHE_SHOP_KEY", id, Shop.class,
                //this::getById, 10L, TimeUnit.MINUTES);
        //Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //if (shop == null) {
        //    return Result.fail("店铺不存在");
        //}
        //返回
        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire("CACHE_SHOP_KEY", id, Shop.class,
                this::getById, 10L, TimeUnit.MINUTES);
            return Result.ok(shop);
    }
    /*public Shop queryWithLogicalExpire(Long id) {
        // 从redis的缓存中进行查询
        String key= "CACHE_SHOP_KEY"+id;
        String value = stringRedisTemplate.opsForValue().get(key);
        //不存在,直接返回空
        if(StringUtils.isBlank(value)){
            return null;
        }
        // 判断缓存是否过期
        // 未过期返回商家信息
        RedisData redisData = JSONUtil.toBean(value, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime=redisData.getExpireTime();
        // 未过期返回商家信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        // 获取互斥锁
        String lockKey= "CACHE_SHOP_LOCK_KEY"+id;
        Boolean isLock = tryLock(lockKey);
        if(isLock){
            // 过期了,如果获取到了锁,开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                        try {
                            this.saveShop2RedisCache(id,10L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            unlock(lockKey);
                        }
                    });
        }
        // 过期了,如果没获取锁,返回旧的商家信息
        return shop;
    }*/

    /*public Shop queryWithMutex(Long id){
        // 从redis的缓存中进行查询
        String key= "CACHE_SHOP_KEY"+id;
        String value = stringRedisTemplate.opsForValue().get(key);
        //存在,直接返回商家信息
        if(StringUtils.isNotBlank(value)){
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return shop;
        }
        //判断是否为空值
        if(value!=null){
            return null;
        }
        //不存在,根据id查询数据库
        //尝试获取互斥锁
        String lockKey= "CACHE_SHOP_LOCK_KEY"+id;
        Shop shop = null;
        try {
            Boolean isLock = tryLock(lockKey);
            //互斥锁获取失败,休眠并重试
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //互斥锁获取成功,判断是否有cache写入
            if(StringUtils.isNotBlank(stringRedisTemplate.opsForValue().get(key))){
                Shop  shop1 = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), Shop.class);
                return shop1;
            }
            //根据id查询数据库
            shop = getById(id);
            //不存在,报错
            if(shop==null) {
                //向redis中返回空字符串
                stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);
                return null;
            }
            //存在,写入redis缓存
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }
        //返回结果
        return shop;
    }*/
   private Boolean tryLock(String key){
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }
    private void unlock(String key){

        stringRedisTemplate.delete(key);
    }
    /*public Shop queryWithPassThrough(Long id){
        // 从redis的缓存中进行查询
        String key= "CACHE_SHOP_KEY"+id;
        String value = stringRedisTemplate.opsForValue().get(key);
        //存在,直接返回商家信息
        if(StringUtils.isNotBlank(value)){
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return shop;
        }
        //判断是否为空值
        if(value!=null){
            return null;
        }
        //不存在,根据id查询数据库
        Shop shop = getById(id);
        //不存在,报错
        if(shop==null) {
            //向redis中返回空字符串
            stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);
            return null;
        }
        //存在,写入redis缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),10, TimeUnit.MINUTES);
        //返回
        return shop;
    }*/
    /*public void saveShop2RedisCache(Long id,Long expireseconds) {
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireseconds));
        //写入redis
        stringRedisTemplate.opsForValue().set("CACHE_SHOP_KEY"+id,JSONUtil.toJsonStr(redisData));
    }*/

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        //校验id
        if(shop.getId()==null){

            return Result.fail("店铺id不存在");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete("CACHE_SHOP_KEY"+shop.getId());
        return Result.ok();
    }
}
