package com.hmdp.service.impl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private IVoucherOrderService proxy;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation( new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

    }
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while(true){
                //获取阻塞队列的订单
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                //创建订单
                handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                log.error("订单创建异常",e);
                }
            }
        }
    }
    private void handleVoucherOrder(VoucherOrder voucherorder) {
        //获取用户对象
        Long userId = voucherorder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("order:" + userId);
        //尝试获取锁
        boolean isLock = lock.tryLock();
        //失败,返回异常
        if(!isLock){
            log.error("不允许重复下单");
        }
        try{
            //获取代理事务对象
             proxy.createOrder(voucherorder);
        }finally {
            //释放锁
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //执行lua脚本
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString());
        //判断是否为0
        //不为0,返回异常
        if(result != 0){
            return Result.fail(result == 1 ? "库存不足" : "订单已存在");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //添加用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //添加到阻塞队列中
        orderTasks.add(voucherOrder);
        //todo 把订单信息传入阻塞队列
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单信息
        return Result.ok(orderId);
    }
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始");
        }
        //秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束");
        }
        //库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:"+userId, redisTemplate);
        RLock lock = redissonClient.getLock("order:" + userId);
        //尝试获取锁
        boolean isLock = lock.tryLock();
        //失败,返回异常
        if(!isLock){
            return Result.fail("订单已存在");
        }
        try{
            //获取代理事务对象
            IVoucherOrderService  proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }*/


    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //todo 一人一单
        //todo 已经下过单,返回异常
        int count =query().eq("voucher_id",voucherOrder.getVoucherId()).eq("user_id",userId ).count();
        if(count > 0){
            log.error("用户已经买过该优惠券");
            return;
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0)
                .update();
        //创建订单
        if(!success){
            log.error("库存不足");
            return;
        }
        //返回订单
        save(voucherOrder);
    }
}
