package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
        //从redis中查询是否有店铺列表的缓存
        String shopTypeJson=stringRedisTemplate.opsForValue().get("cache:shopType:list");

        if(StrUtil.isNotBlank(shopTypeJson))
        {
            //有缓存，直接返回
            ShopType shopType= JSONUtil.toBean(shopTypeJson, ShopType.class);
            return Result.ok(shopType);
        }

        // 缓存为空，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //查询为空，直接返回错误
        if(shopTypeList==null)
        {
            return Result.fail("店铺类型不存在！");
        }

        //查询不为空，将其放入redis缓存中
        stringRedisTemplate.opsForValue().set("cache:shopType:list",JSONUtil.toJsonStr(shopTypeList));
        //最后返回
        return Result.ok(shopTypeList);

    }
}
