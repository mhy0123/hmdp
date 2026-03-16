package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryType() {
        //从redis缓存中进行查询
        String typeList = stringRedisTemplate.opsForValue().get("cache:typeList");
        //如果存在,直接返回结果
        if(StringUtils.isNotBlank(typeList)){
            //将字符串格式转化为集合
             List<ShopType> shopTypes = JSONUtil.toList(typeList, ShopType.class);
            return Result.ok(shopTypes);
        }
        //从数据库中查询,如果不存在
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(shopTypes==null){
            return Result.fail("数据库中也不存在店铺类型");
        }
        //将结果写入缓存
        stringRedisTemplate.opsForValue().set("cache:typeList",JSONUtil.toJsonStr(shopTypes),10, TimeUnit.MINUTES);
        //返回结果
        return Result.ok(shopTypes);
    }
}
