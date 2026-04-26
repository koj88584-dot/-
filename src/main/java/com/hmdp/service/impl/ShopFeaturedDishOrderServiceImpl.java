package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopFeaturedDishOrderDTO;
import com.hmdp.dto.ShopFeaturedDishOrderVerifyDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopFeaturedDish;
import com.hmdp.entity.ShopFeaturedDishOrder;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopFeaturedDishOrderMapper;
import com.hmdp.service.IShopFeaturedDishOrderService;
import com.hmdp.service.IShopFeaturedDishService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopFeaturedDishOrderServiceImpl extends ServiceImpl<ShopFeaturedDishOrderMapper, ShopFeaturedDishOrder> implements IShopFeaturedDishOrderService {

    public static final int STATUS_PENDING_PAYMENT = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_VERIFIED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDING = 5;
    public static final int STATUS_REFUNDED = 6;
    private static final int DISH_STATUS_ONLINE = 1;

    @Resource
    private IShopFeaturedDishService shopFeaturedDishService;
    @Resource
    private IShopService shopService;
    @Resource
    private IUserService userService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createOrder(Long dishId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        ShopFeaturedDish dish = shopFeaturedDishService.getById(dishId);
        String error = validateBuyable(dish);
        if (error != null) {
            return Result.fail(error);
        }
        LocalDateTime now = LocalDateTime.now();
        Long orderId = redisIdWorker.nextId("featuredDishOrder");
        ShopFeaturedDishOrder order = new ShopFeaturedDishOrder()
                .setId(orderId)
                .setDishId(dishId)
                .setShopId(dish.getShopId())
                .setUserId(user.getId())
                .setPayValue(dish.getPrice())
                .setStatus(STATUS_PAID)
                .setVerifyCode(buildVerifyCode(orderId))
                .setCommented(0)
                .setCreateTime(now)
                .setPayTime(now)
                .setEffectiveTime(now)
                .setExpireTime(now.plusDays(90))
                .setUpdateTime(now);
        save(order);
        return Result.ok(toDTOs(Collections.singletonList(order)).get(0));
    }

    @Override
    public Result queryMyOrders(Integer status, Integer commented, Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Page<ShopFeaturedDishOrder> page = lambdaQuery()
                .eq(ShopFeaturedDishOrder::getUserId, user.getId())
                .eq(status != null, ShopFeaturedDishOrder::getStatus, status)
                .eq(commented != null, ShopFeaturedDishOrder::getCommented, commented)
                .orderByDesc(ShopFeaturedDishOrder::getCreateTime)
                .page(new Page<>(current == null || current < 1 ? 1 : current, 20));
        return Result.ok(toDTOs(page.getRecords()), page.getTotal());
    }

    @Override
    public Result queryMerchantOrders(Long shopId, Integer status, Integer current) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        Page<ShopFeaturedDishOrder> page = lambdaQuery()
                .eq(ShopFeaturedDishOrder::getShopId, shopId)
                .eq(status != null, ShopFeaturedDishOrder::getStatus, status)
                .orderByDesc(ShopFeaturedDishOrder::getCreateTime)
                .page(new Page<>(current == null || current < 1 ? 1 : current, 10));
        return Result.ok(toDTOs(page.getRecords()), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result verifyByCode(ShopFeaturedDishOrderVerifyDTO dto) {
        if (dto == null || dto.getShopId() == null) {
            return Result.fail("店铺不能为空");
        }
        String code = dto.getVerifyCode() == null ? "" : dto.getVerifyCode().trim().toUpperCase();
        if (code.isEmpty()) {
            return Result.fail("招牌菜券码不能为空");
        }
        ShopFeaturedDishOrder order = lambdaQuery()
                .eq(ShopFeaturedDishOrder::getVerifyCode, code)
                .last("limit 1")
                .one();
        if (order == null) {
            return Result.fail("招牌菜券码不存在");
        }
        if (!dto.getShopId().equals(order.getShopId())) {
            return Result.fail("招牌菜券码与当前店铺不匹配");
        }
        if (!Objects.equals(order.getStatus(), STATUS_PAID)) {
            return Result.fail("订单状态不正确，无法核销");
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("招牌菜券已过期");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(STATUS_VERIFIED);
        order.setUseTime(now);
        order.setUpdateTime(now);
        updateById(order);
        return Result.ok(toDTOs(Collections.singletonList(order)).get(0));
    }

    @Override
    public boolean hasVerifiedUncommentedOrder(Long userId, Long shopId) {
        if (userId == null || shopId == null) {
            return false;
        }
        Long count = lambdaQuery()
                .eq(ShopFeaturedDishOrder::getUserId, userId)
                .eq(ShopFeaturedDishOrder::getShopId, shopId)
                .eq(ShopFeaturedDishOrder::getStatus, STATUS_VERIFIED)
                .eq(ShopFeaturedDishOrder::getCommented, 0)
                .count();
        return count != null && count > 0;
    }

    @Override
    public boolean markOneCommented(Long userId, Long shopId) {
        if (userId == null || shopId == null) {
            return false;
        }
        ShopFeaturedDishOrder order = lambdaQuery()
                .eq(ShopFeaturedDishOrder::getUserId, userId)
                .eq(ShopFeaturedDishOrder::getShopId, shopId)
                .eq(ShopFeaturedDishOrder::getStatus, STATUS_VERIFIED)
                .eq(ShopFeaturedDishOrder::getCommented, 0)
                .orderByDesc(ShopFeaturedDishOrder::getUseTime)
                .last("limit 1")
                .one();
        if (order == null) {
            return false;
        }
        order.setCommented(1);
        order.setUpdateTime(LocalDateTime.now());
        return updateById(order);
    }

    private String validateBuyable(ShopFeaturedDish dish) {
        if (dish == null) {
            return "招牌菜不存在";
        }
        if (!Objects.equals(dish.getStatus(), DISH_STATUS_ONLINE)) {
            return "招牌菜已下架";
        }
        if (dish.getPrice() == null || dish.getPrice() <= 0) {
            return "招牌菜价格不正确";
        }
        return null;
    }

    private List<ShopFeaturedDishOrderDTO> toDTOs(List<ShopFeaturedDishOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dishIds = orders.stream().map(ShopFeaturedDishOrder::getDishId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ShopFeaturedDish> dishMap = dishIds.isEmpty()
                ? Collections.emptyMap()
                : shopFeaturedDishService.listByIds(dishIds).stream()
                .collect(Collectors.toMap(ShopFeaturedDish::getId, Function.identity(), (left, right) -> left));
        List<Long> shopIds = orders.stream().map(ShopFeaturedDishOrder::getShopId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Shop> shopMap = shopIds.isEmpty()
                ? Collections.emptyMap()
                : shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left));
        List<Long> userIds = orders.stream().map(ShopFeaturedDishOrder::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        return orders.stream().map(order -> {
            ShopFeaturedDish dish = dishMap.get(order.getDishId());
            Shop shop = shopMap.get(order.getShopId());
            User user = userMap.get(order.getUserId());
            ShopFeaturedDishOrderDTO dto = new ShopFeaturedDishOrderDTO();
            dto.setId(order.getId());
            dto.setDishId(order.getDishId());
            dto.setShopId(order.getShopId());
            dto.setShopName(shop == null ? "" : shop.getName());
            dto.setUserId(order.getUserId());
            dto.setUserNickName(user == null ? "" : user.getNickName());
            dto.setDishName(dish == null ? "招牌菜订单" : dish.getName());
            dto.setDishImage(dish == null ? "" : dish.getImage());
            dto.setDishDescription(dish == null ? "" : dish.getDescription());
            dto.setPayValue(order.getPayValue());
            dto.setStatus(order.getStatus());
            dto.setStatusText(statusText(order.getStatus()));
            dto.setVerifyCode(order.getVerifyCode());
            dto.setCommented(order.getCommented() == null ? 0 : order.getCommented());
            dto.setCreateTime(order.getCreateTime());
            dto.setPayTime(order.getPayTime());
            dto.setUseTime(order.getUseTime());
            dto.setEffectiveTime(order.getEffectiveTime());
            dto.setExpireTime(order.getExpireTime());
            return dto;
        }).collect(Collectors.toList());
    }

    private String buildVerifyCode(Long orderId) {
        return "FD" + Long.toString(orderId, 36).toUpperCase();
    }

    private String statusText(Integer status) {
        if (Objects.equals(status, STATUS_PENDING_PAYMENT)) return "未支付";
        if (Objects.equals(status, STATUS_PAID)) return "待核销";
        if (Objects.equals(status, STATUS_VERIFIED)) return "已核销";
        if (Objects.equals(status, STATUS_CANCELLED)) return "已取消";
        if (Objects.equals(status, STATUS_REFUNDING)) return "退款中";
        if (Objects.equals(status, STATUS_REFUNDED)) return "已退款";
        return "未知";
    }
}
