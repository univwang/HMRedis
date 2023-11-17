package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
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
    public Result queryTypeList() {
        //1. 查询redis缓存
        String cache = stringRedisTemplate.opsForValue().get("cache:shop:shop-type-list");
        //删除cache的第一个和最后一个字符
        //2. 如果缓存中存在数据，直接返回
        if(cache != null) {
            List<ShopType> list = JSONUtil.toList(JSONUtil.parseArray(cache), ShopType.class);
            return Result.ok(list);
        }
        //3. 如果缓存中不存在数据，从数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //4. 不存在数据，返回错误信息
        if(typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        //5. 存在数据，写入缓存
        stringRedisTemplate.opsForValue().set("cache:shop:shop-type-list", JSONUtil.toJsonStr(typeList));
        stringRedisTemplate.expire("cache:shop:shop-type-list", 60 * 24, TimeUnit.MINUTES);
        //6. 返回数据
        return Result.ok(typeList);
    }
}
