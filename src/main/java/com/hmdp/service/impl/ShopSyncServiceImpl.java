package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopExtend;
import com.hmdp.mapper.ShopExtendMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 店铺同步服务实现类
 */
@Slf4j
@Service
public class ShopSyncServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopSyncService {

    @Resource
    private ShopExtendMapper shopExtendMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IAmapService amapService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop syncToLocal(Shop amapShop) {
        if (amapShop == null || amapShop.getId() == null) {
            return null;
        }

        // 检查是否已同步
        ShopExtend existExtend = queryExtendByAmapId(amapShop.getId());
        if (existExtend != null) {
            // 已同步，返回本地店铺
            return getById(existExtend.getShopId());
        }

        try {
            // 创建本地店铺（正数ID，自增）
            Shop localShop = new Shop();
            localShop.setName(amapShop.getName());
            localShop.setTypeId(amapShop.getTypeId());
            localShop.setImages(amapShop.getImages());
            localShop.setArea(amapShop.getArea());
            localShop.setAddress(amapShop.getAddress());
            localShop.setX(amapShop.getX());
            localShop.setY(amapShop.getY());
            localShop.setScore(amapShop.getScore());
            localShop.setComments(amapShop.getComments());
            localShop.setOpenHours(amapShop.getOpenHours());
            localShop.setAvgPrice(amapShop.getAvgPrice());
            localShop.setSold(0);
            localShop.setCreateTime(LocalDateTime.now());
            localShop.setUpdateTime(LocalDateTime.now());

            // 保存到数据库
            save(localShop);

            // 创建扩展信息记录
            ShopExtend extend = new ShopExtend();
            extend.setShopId(localShop.getId());
            extend.setSource("amap");
            extend.setAmapPoiId(String.valueOf(amapShop.getId())); // 保存原始高德ID
            extend.setBusinessArea(amapShop.getArea());
            extend.setCreateTime(LocalDateTime.now());
            extend.setUpdateTime(LocalDateTime.now());

            shopExtendMapper.insert(extend);

            log.info("店铺同步成功: {} -> {}", amapShop.getId(), localShop.getId());

            // 更新Redis缓存映射
            String syncKey = "shop:sync:" + amapShop.getId();
            stringRedisTemplate.opsForValue().set(syncKey, String.valueOf(localShop.getId()), 
                    1, TimeUnit.DAYS);

            return localShop;

        } catch (Exception e) {
            log.error("店铺同步失败: {}", amapShop.getId(), e);
            return null;
        }
    }

    @Override
    public Shop queryByAmapPoiId(String amapPoiId) {
        if (amapPoiId == null || amapPoiId.isEmpty()) {
            return null;
        }

        // 查询扩展表
        ShopExtend extend = shopExtendMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ShopExtend>()
                        .eq("amap_poi_id", amapPoiId)
        );

        if (extend != null) {
            return getById(extend.getShopId());
        }

        return null;
    }

    @Override
    public boolean isSynced(Long amapShopId) {
        if (amapShopId == null) {
            return false;
        }

        // 先查Redis缓存
        String syncKey = "shop:sync:" + amapShopId;
        String localId = stringRedisTemplate.opsForValue().get(syncKey);
        if (localId != null) {
            return true;
        }

        // 再查数据库
        ShopExtend extend = queryExtendByAmapId(amapShopId);
        if (extend != null) {
            // 缓存结果
            stringRedisTemplate.opsForValue().set(syncKey, String.valueOf(extend.getShopId()), 
                    1, TimeUnit.DAYS);
            return true;
        }

        return false;
    }

    @Override
    public Shop getOrSync(Shop amapShop) {
        if (amapShop == null) {
            return null;
        }

        // 检查是否已同步
        ShopExtend extend = queryExtendByAmapId(amapShop.getId());
        if (extend != null) {
            return getById(extend.getShopId());
        }

        // 未同步，执行同步
        return syncToLocal(amapShop);
    }

    @Override
    public Result triggerSync(Long shopId, String action) {
        if (shopId == null || shopId > 0) {
            // 正数ID说明已经是本地店铺，无需同步
            return Result.ok("无需同步");
        }

        // 从Redis获取高德店铺数据
        String shopKey = RedisConstants.CACHE_SHOP_KEY + shopId;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        if (shopJson == null) {
            return Result.fail("店铺数据已过期，请重新搜索");
        }

        Shop amapShop = JSONUtil.toBean(shopJson, Shop.class);

        // 根据行为决定是否同步
        boolean shouldSync = false;
        switch (action) {
            case "favorite":
            case "comment":
                // 收藏和评价必须同步
                shouldSync = true;
                break;
            case "view":
                // 浏览可以不同步，或者达到一定次数再同步
                String viewKey = "shop:view:" + shopId;
                Long views = stringRedisTemplate.opsForValue().increment(viewKey);
                if (views != null && views >= 10) {
                    shouldSync = true;
                    // 重置计数
                    stringRedisTemplate.delete(viewKey);
                }
                stringRedisTemplate.expire(viewKey, 7, TimeUnit.DAYS);
                break;
            default:
                shouldSync = false;
        }

        if (shouldSync) {
            Shop localShop = syncToLocal(amapShop);
            if (localShop != null) {
                return Result.ok(localShop.getId());
            } else {
                return Result.fail("同步失败");
            }
        }

        return Result.ok("已记录，暂不同步");
    }

    @Override
    public Result batchSync(Integer typeId, String cityCode, String keywords) {
        // 管理员批量同步功能
        // TODO: 调用高德API批量搜索并同步
        return Result.ok(0);
    }

    /**
     * 根据高德店铺ID查询扩展信息
     */
    private ShopExtend queryExtendByAmapId(Long amapShopId) {
        return shopExtendMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ShopExtend>()
                        .eq("amap_poi_id", String.valueOf(amapShopId))
        );
    }
}
