package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop() {
        //查询店铺的typeId
        List<Shop> shops = shopService.list();
        Map<Long,List<Shop>> shopMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //根据typeId进行店铺的分类存储
        for(Map.Entry<Long,List<Shop>> entry:shopMap.entrySet()){
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();
            String key ="shop:geo:"+typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shopList.size());
            for(Shop shop:shopList){
                locations.add(new RedisGeoCommands.GeoLocation<String>(
                        shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            //将店铺的坐标存储到Redis中
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
        //将店铺分批导入到Redis中去
    }

}
