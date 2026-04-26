package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.GroupDealOrderDTO;
import com.hmdp.dto.GroupDealOrderVerifyDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.GroupDeal;
import com.hmdp.entity.GroupDealOrder;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.GroupDealOrderMapper;
import com.hmdp.service.IGroupDealOrderService;
import com.hmdp.service.IGroupDealService;
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
public class GroupDealOrderServiceImpl extends ServiceImpl<GroupDealOrderMapper, GroupDealOrder> implements IGroupDealOrderService {

    public static final int STATUS_PENDING_PAYMENT = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_VERIFIED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_REFUNDING = 5;
    public static final int STATUS_REFUNDED = 6;

    @Resource
    private IGroupDealService groupDealService;
    @Resource
    private IShopService shopService;
    @Resource
    private IUserService userService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createOrder(Long dealId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        GroupDeal deal = groupDealService.getById(dealId);
        String error = validateBuyable(deal);
        if (error != null) {
            return Result.fail(error);
        }
        boolean success = groupDealService.update()
                .setSql("stock = stock - 1")
                .setSql("sold = sold + 1")
                .eq("id", dealId)
                .eq("status", 1)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("团购库存不足");
        }
        LocalDateTime now = LocalDateTime.now();
        Long orderId = redisIdWorker.nextId("groupDealOrder");
        GroupDealOrder order = new GroupDealOrder()
                .setId(orderId)
                .setDealId(dealId)
                .setShopId(deal.getShopId())
                .setUserId(user.getId())
                .setPayValue(deal.getPrice())
                .setStatus(STATUS_PAID)
                .setVerifyCode(buildVerifyCode(orderId))
                .setCommented(0)
                .setCreateTime(now)
                .setPayTime(now)
                .setEffectiveTime(resolveEffectiveTime(deal, now))
                .setExpireTime(deal.getEndTime())
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
        Page<GroupDealOrder> page = lambdaQuery()
                .eq(GroupDealOrder::getUserId, user.getId())
                .eq(status != null, GroupDealOrder::getStatus, status)
                .eq(commented != null, GroupDealOrder::getCommented, commented)
                .orderByDesc(GroupDealOrder::getCreateTime)
                .page(new Page<>(current == null || current < 1 ? 1 : current, 20));
        return Result.ok(toDTOs(page.getRecords()), page.getTotal());
    }

    @Override
    public Result queryMerchantOrders(Long shopId, Integer status, Integer current) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        Page<GroupDealOrder> page = lambdaQuery()
                .eq(GroupDealOrder::getShopId, shopId)
                .eq(status != null, GroupDealOrder::getStatus, status)
                .orderByDesc(GroupDealOrder::getCreateTime)
                .page(new Page<>(current == null || current < 1 ? 1 : current, 10));
        return Result.ok(toDTOs(page.getRecords()), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result verifyByCode(GroupDealOrderVerifyDTO dto) {
        if (dto == null || dto.getShopId() == null) {
            return Result.fail("店铺不能为空");
        }
        String code = dto.getVerifyCode() == null ? "" : dto.getVerifyCode().trim().toUpperCase();
        if (code.isEmpty()) {
            return Result.fail("团购券码不能为空");
        }
        GroupDealOrder order = lambdaQuery()
                .eq(GroupDealOrder::getVerifyCode, code)
                .last("limit 1")
                .one();
        if (order == null) {
            return Result.fail("团购券码不存在");
        }
        if (!dto.getShopId().equals(order.getShopId())) {
            return Result.fail("团购券码与当前店铺不匹配");
        }
        if (!Objects.equals(order.getStatus(), STATUS_PAID)) {
            return Result.fail("团购订单状态不正确，无法核销");
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("团购已过期");
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
                .eq(GroupDealOrder::getUserId, userId)
                .eq(GroupDealOrder::getShopId, shopId)
                .eq(GroupDealOrder::getStatus, STATUS_VERIFIED)
                .eq(GroupDealOrder::getCommented, 0)
                .count();
        return count != null && count > 0;
    }

    @Override
    public boolean markOneCommented(Long userId, Long shopId) {
        if (userId == null || shopId == null) {
            return false;
        }
        GroupDealOrder order = lambdaQuery()
                .eq(GroupDealOrder::getUserId, userId)
                .eq(GroupDealOrder::getShopId, shopId)
                .eq(GroupDealOrder::getStatus, STATUS_VERIFIED)
                .eq(GroupDealOrder::getCommented, 0)
                .orderByDesc(GroupDealOrder::getUseTime)
                .last("limit 1")
                .one();
        if (order == null) {
            return false;
        }
        order.setCommented(1);
        order.setUpdateTime(LocalDateTime.now());
        return updateById(order);
    }

    private String validateBuyable(GroupDeal deal) {
        if (deal == null) {
            return "团购不存在";
        }
        if (!Objects.equals(deal.getStatus(), 1)) {
            return "团购已下架";
        }
        if (deal.getStock() == null || deal.getStock() <= 0) {
            return "团购库存不足";
        }
        LocalDateTime now = LocalDateTime.now();
        if (deal.getBeginTime() != null && deal.getBeginTime().isAfter(now)) {
            return "团购还未开始";
        }
        if (deal.getEndTime() != null && deal.getEndTime().isBefore(now)) {
            return "团购已结束";
        }
        return null;
    }

    private List<GroupDealOrderDTO> toDTOs(List<GroupDealOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dealIds = orders.stream().map(GroupDealOrder::getDealId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, GroupDeal> dealMap = dealIds.isEmpty()
                ? Collections.emptyMap()
                : groupDealService.listByIds(dealIds).stream()
                .collect(Collectors.toMap(GroupDeal::getId, Function.identity(), (left, right) -> left));
        List<Long> shopIds = orders.stream().map(GroupDealOrder::getShopId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Shop> shopMap = shopIds.isEmpty()
                ? Collections.emptyMap()
                : shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left));
        List<Long> userIds = orders.stream().map(GroupDealOrder::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        return orders.stream().map(order -> {
            GroupDeal deal = dealMap.get(order.getDealId());
            Shop shop = shopMap.get(order.getShopId());
            User user = userMap.get(order.getUserId());
            GroupDealOrderDTO dto = new GroupDealOrderDTO();
            dto.setId(order.getId());
            dto.setDealId(order.getDealId());
            dto.setShopId(order.getShopId());
            dto.setShopName(shop == null ? "" : shop.getName());
            dto.setUserId(order.getUserId());
            dto.setUserNickName(user == null ? "" : user.getNickName());
            dto.setDealTitle(deal == null ? "团购订单" : deal.getTitle());
            dto.setDealImages(deal == null ? "" : deal.getImages());
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
        return "GD" + Long.toString(orderId, 36).toUpperCase();
    }

    private LocalDateTime resolveEffectiveTime(GroupDeal deal, LocalDateTime now) {
        if (deal == null || deal.getBeginTime() == null || deal.getBeginTime().isBefore(now)) {
            return now;
        }
        return deal.getBeginTime();
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
