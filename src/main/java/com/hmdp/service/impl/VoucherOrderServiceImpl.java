package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
//    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始");
        }
        //3. 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //4. 判断库存是否足够
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        Long id = UserHolder.getUser().getId();
        synchronized (id.toString().intern()) {
            //使用的this对象调用事务，而不是代理对象
            //为了生效，需要使用动态代理
            //拿到代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, id);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long id) {
        //8.一人只能秒杀一张
        //无法判断有没有修改过，加悲观锁
        //synchronized串行执行，性能差
        //应该同一个用户用一把锁
        //toString不行，每次都是一个新的String对象
        //代码块结束，锁释放，如果事务未提交，其他线程可以进入
        //所以要加到整个函数外边
        Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("一人只能秒杀一张");
        }

        //5. 检查库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock", 0).
                update();
        if (!success) {
            return Result.fail("库存错误");
        }


        //6. 生成订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //6.1 订单id
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
        //6.2 用户id
        voucherOrder.setUserId(id);
        //6.3 优惠券id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        //7. 返回订单id
        return Result.ok(order);
    }

}
