package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
//        解决缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
//        逻辑过期时间解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null) {
            return Result.fail("查询商铺信息失败");
        }
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.如果缓存中存在数据，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if(shopJson != null ) {
            //不是null，说明缓存中存在空值，直接返回
            return null;
        }
        //3. 实现缓存重建
        //3.1.获取分布式锁
        Shop shop;
        try {
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
            //3.2 如果失败，休眠
            if(!flag) {
                TimeUnit.MILLISECONDS.sleep(100);
                //重试
                return queryWithMutex(id);
            }
            //3.3 如果成功，从数据库查询
            shop = getById(id);
            TimeUnit.MILLISECONDS.sleep(200);
            //4.不存在数据，返回错误信息
            if(shop == null) {
                //将空值写入缓存，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5.存在数据，写入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("查询商铺信息失败");
        } finally {
            //6.释放锁
            unlock(LOCK_SHOP_KEY + id);
        }
        //7.返回数据
        return shop;
    }


    
    private static final ExecutorService CACHE_REBUILD_POOL = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.如果缓存中存在数据，直接返回
        if(StrUtil.isBlank(shopJson)) {
            //3.如果缓存中不存在数据，返回
            return null;
        }
        //4.命中，获取缓存中的对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        System.out.println("缓存过期时间：" + expireTime + "，当前时间：" + LocalDateTime.now());
        //5. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回
            System.out.println("命中缓存");
            return shop;
        }
        //5.2 过期，异步更新缓存
        //6.缓存重建
        //6.1 获取分布式锁
        boolean flag = tryLock(LOCK_SHOP_KEY + id);
        //6.2 如果成功，开启异步线程更新缓存
        if(flag) {
            //检查缓存是否过期
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJson2)) {
                RedisData redisData2 = JSONUtil.toBean(shopJson2, RedisData.class);
                LocalDateTime expireTime2 = redisData2.getExpireTime();
                if(expireTime2.isAfter(LocalDateTime.now())) {
                    //未过期，直接返回
                    return shop;
                }
            }
            //过期，开启异步线程更新缓存
            System.out.println("开启异步线程更新缓存");
            CACHE_REBUILD_POOL.submit(() -> {
                //重建缓存
                try {
                    this.saveShop2Redis(id, 20L);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    unlock(LOCK_SHOP_KEY + id);
                }
            });

        }
        //6.返回过期数据
        return shop;
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.如果缓存中存在数据，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if(shopJson != null ) {
            //不是null，说明缓存中存在空值，直接返回
            return null;
        }
        //3.如果缓存中不存在数据，从数据库查询
        Shop shop = getById(id);
        //4.不存在数据，返回错误信息
        if(shop == null) {
            //将空值写入缓存，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.存在数据，写入缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回数据
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2. 封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3. 写入redis
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        //没有实际的过期时间
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        //3. 返回结果
        return Result.ok();
    }
}
