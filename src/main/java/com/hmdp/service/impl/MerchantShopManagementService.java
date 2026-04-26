package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.FeaturedDishSaveDTO;
import com.hmdp.dto.GroupDealSaveDTO;
import com.hmdp.dto.MerchantShopDetailDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopQuickInfoDTO;
import com.hmdp.dto.ShopUpdateApplicationRecordDTO;
import com.hmdp.dto.ShopUpdateApplicationSubmitDTO;
import com.hmdp.entity.GroupDeal;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopFeaturedDish;
import com.hmdp.entity.ShopUpdateApplication;
import com.hmdp.entity.User;
import com.hmdp.service.IGroupDealService;
import com.hmdp.service.IShopFeaturedDishService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopUpdateApplicationService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MerchantApplicationConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Service
public class MerchantShopManagementService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_ONLINE = 1;
    private static final int STATUS_OFFLINE = 2;
    private static final int STATUS_ENDED = 3;
    private static final int FEATURED_DISH_ONLINE_LIMIT = 6;

    @Resource
    private IShopService shopService;
    @Resource
    private IShopUpdateApplicationService shopUpdateApplicationService;
    @Resource
    private IShopFeaturedDishService shopFeaturedDishService;
    @Resource
    private IGroupDealService groupDealService;
    @Resource
    private IUserService userService;
    @Resource
    private MerchantAuthService merchantAuthService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public MerchantShopDetailDTO buildMerchantShopDetail(Long userId, Long shopId) {
        Shop shop = shopService.getById(shopId);
        MerchantShopDetailDTO dto = new MerchantShopDetailDTO();
        dto.setShop(shop);
        dto.setMemberRole(merchantAuthService.getShopRole(userId, shopId));
        dto.setCanEdit(merchantAuthService.canEditShop(userId, shopId));
        dto.setCanVerify(merchantAuthService.canVerifyShopOrder(userId, shopId));
        ShopUpdateApplication pending = getPendingShopUpdate(shopId);
        dto.setPendingUpdate(pending != null);
        dto.setPendingUpdateApplicationId(pending == null ? null : pending.getId());
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result updateQuickInfo(Long shopId, ShopQuickInfoDTO dto) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (dto == null) {
            return Result.fail("店铺信息不能为空");
        }
        Shop patch = new Shop().setId(shopId);
        patch.setOpenHours(cleanNullable(dto.getOpenHours()));
        patch.setAvgPrice(dto.getAvgPrice());
        patch.setPhone(cleanNullable(dto.getPhone()));
        shopService.updateById(patch);
        refreshShopCache(shopId);
        return Result.ok(shopService.getById(shopId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result submitUpdateApplication(Long userId, Long shopId, ShopUpdateApplicationSubmitDTO dto) {
        if (dto == null) {
            return Result.fail("变更信息不能为空");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (getPendingShopUpdate(shopId) != null) {
            return Result.fail("该店铺已有待审核的资料变更");
        }
        Map<String, Object> payload = buildSensitivePayload(dto);
        if (payload.isEmpty()) {
            return Result.fail("请至少填写一项需审核的资料变更");
        }
        ShopUpdateApplication application = new ShopUpdateApplication()
                .setUserId(userId)
                .setShopId(shopId)
                .setChangePayload(JSONUtil.toJsonStr(payload))
                .setProofImages(cleanNullable(dto.getProofImages()))
                .setMessage(cleanNullable(dto.getMessage()))
                .setStatus(MerchantApplicationConstants.STATUS_PENDING)
                .setReviewerId(null)
                .setReviewRemark(null)
                .setReviewTime(null);
        shopUpdateApplicationService.save(application);
        return Result.ok(toUpdateRecord(application, Collections.emptyMap(), Collections.singletonMap(shopId, shop), Collections.emptyMap()));
    }

    public List<ShopUpdateApplicationRecordDTO> listUpdateApplications(Integer status) {
        List<ShopUpdateApplication> applications = shopUpdateApplicationService.lambdaQuery()
                .eq(status != null, ShopUpdateApplication::getStatus, status)
                .orderByDesc(ShopUpdateApplication::getId)
                .list();
        Map<Long, User> userMap = loadUsers(applications.stream().map(ShopUpdateApplication::getUserId).collect(Collectors.toList()));
        Map<Long, Shop> shopMap = loadShops(applications.stream().map(ShopUpdateApplication::getShopId).collect(Collectors.toList()));
        Map<Long, User> reviewerMap = loadUsers(applications.stream().map(ShopUpdateApplication::getReviewerId).collect(Collectors.toList()));
        return applications.stream()
                .map(item -> toUpdateRecord(item, userMap, shopMap, reviewerMap))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Result approveUpdateApplication(Long id, Long reviewerId, String reviewRemark) {
        ShopUpdateApplication application = shopUpdateApplicationService.getById(id);
        if (application == null) {
            return Result.fail("店铺资料变更申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        Shop shop = shopService.getById(application.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        applyShopPayload(application.getShopId(), application.getChangePayload());
        application.setStatus(MerchantApplicationConstants.STATUS_APPROVED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(reviewRemark));
        application.setReviewTime(LocalDateTime.now());
        shopUpdateApplicationService.updateById(application);
        refreshShopCache(application.getShopId());
        return Result.ok(toUpdateRecord(application, Collections.emptyMap(), Collections.singletonMap(shop.getId(), shop), Collections.emptyMap()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result rejectUpdateApplication(Long id, Long reviewerId, String reviewRemark) {
        ShopUpdateApplication application = shopUpdateApplicationService.getById(id);
        if (application == null) {
            return Result.fail("店铺资料变更申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        application.setStatus(MerchantApplicationConstants.STATUS_REJECTED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(reviewRemark));
        application.setReviewTime(LocalDateTime.now());
        shopUpdateApplicationService.updateById(application);
        return Result.ok(toUpdateRecord(application, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
    }

    public List<ShopFeaturedDish> listPublicDishes(Long shopId) {
        return shopFeaturedDishService.lambdaQuery()
                .eq(ShopFeaturedDish::getShopId, shopId)
                .eq(ShopFeaturedDish::getStatus, STATUS_ONLINE)
                .orderByAsc(ShopFeaturedDish::getSort)
                .orderByDesc(ShopFeaturedDish::getId)
                .last("limit " + FEATURED_DISH_ONLINE_LIMIT)
                .list();
    }

    public List<ShopFeaturedDish> listMerchantDishes(Long shopId) {
        return shopFeaturedDishService.lambdaQuery()
                .eq(ShopFeaturedDish::getShopId, shopId)
                .orderByAsc(ShopFeaturedDish::getStatus)
                .orderByAsc(ShopFeaturedDish::getSort)
                .orderByDesc(ShopFeaturedDish::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public Result saveDish(Long shopId, FeaturedDishSaveDTO dto) {
        String error = validateDish(shopId, dto);
        if (error != null) {
            return Result.fail(error);
        }
        ShopFeaturedDish dish = new ShopFeaturedDish()
                .setShopId(shopId)
                .setName(dto.getName().trim())
                .setDescription(cleanNullable(dto.getDescription()))
                .setImage(cleanNullable(dto.getImage()))
                .setPrice(dto.getPrice())
                .setSort(dto.getSort() == null ? 0 : dto.getSort());
        if (dto.getId() == null) {
            dish.setStatus(STATUS_DRAFT);
            shopFeaturedDishService.save(dish);
            return Result.ok(dish);
        }
        ShopFeaturedDish existing = shopFeaturedDishService.getById(dto.getId());
        if (existing == null || !shopId.equals(existing.getShopId())) {
            return Result.fail("招牌菜不存在");
        }
        dish.setId(dto.getId());
        dish.setStatus(existing.getStatus());
        shopFeaturedDishService.updateById(dish);
        return Result.ok(shopFeaturedDishService.getById(dto.getId()));
    }

    public Result publishDish(Long id) {
        ShopFeaturedDish dish = shopFeaturedDishService.getById(id);
        if (dish == null) {
            return Result.fail("招牌菜不存在");
        }
        Long onlineCount = shopFeaturedDishService.lambdaQuery()
                .eq(ShopFeaturedDish::getShopId, dish.getShopId())
                .eq(ShopFeaturedDish::getStatus, STATUS_ONLINE)
                .ne(ShopFeaturedDish::getId, id)
                .count();
        if (onlineCount >= FEATURED_DISH_ONLINE_LIMIT) {
            return Result.fail("每家店最多展示6个招牌菜");
        }
        dish.setStatus(STATUS_ONLINE);
        shopFeaturedDishService.updateById(dish);
        return Result.ok();
    }

    public Result unpublishDish(Long id) {
        ShopFeaturedDish dish = shopFeaturedDishService.getById(id);
        if (dish == null) {
            return Result.fail("招牌菜不存在");
        }
        dish.setStatus(STATUS_OFFLINE);
        shopFeaturedDishService.updateById(dish);
        return Result.ok();
    }

    public List<GroupDeal> listPublicDeals(Long shopId) {
        expireDealsIfNeeded(shopId);
        return groupDealService.lambdaQuery()
                .eq(GroupDeal::getShopId, shopId)
                .eq(GroupDeal::getStatus, STATUS_ONLINE)
                .orderByDesc(GroupDeal::getId)
                .list();
    }

    public List<GroupDeal> listMerchantDeals(Long shopId) {
        expireDealsIfNeeded(shopId);
        return groupDealService.lambdaQuery()
                .eq(GroupDeal::getShopId, shopId)
                .orderByAsc(GroupDeal::getStatus)
                .orderByDesc(GroupDeal::getId)
                .list();
    }

    @Transactional(rollbackFor = Exception.class)
    public Result saveDeal(Long shopId, GroupDealSaveDTO dto) {
        String error = validateDeal(shopId, dto);
        if (error != null) {
            return Result.fail(error);
        }
        GroupDeal deal = new GroupDeal()
                .setShopId(shopId)
                .setTitle(dto.getTitle().trim())
                .setDescription(cleanNullable(dto.getDescription()))
                .setImages(cleanNullable(dto.getImages()))
                .setRules(cleanNullable(dto.getRules()))
                .setPrice(dto.getPrice())
                .setOriginalPrice(dto.getOriginalPrice())
                .setStock(dto.getStock())
                .setBeginTime(dto.getBeginTime())
                .setEndTime(dto.getEndTime());
        if (dto.getId() == null) {
            deal.setStatus(STATUS_DRAFT);
            deal.setSold(0);
            groupDealService.save(deal);
            return Result.ok(deal);
        }
        GroupDeal existing = groupDealService.getById(dto.getId());
        if (existing == null || !shopId.equals(existing.getShopId())) {
            return Result.fail("团购不存在");
        }
        if (Objects.equals(existing.getStatus(), STATUS_ONLINE)) {
            return Result.fail("已上架团购请先下架后再编辑");
        }
        deal.setId(dto.getId());
        deal.setStatus(existing.getStatus());
        deal.setSold(existing.getSold());
        groupDealService.updateById(deal);
        return Result.ok(groupDealService.getById(dto.getId()));
    }

    public Result publishDeal(Long id) {
        GroupDeal deal = groupDealService.getById(id);
        if (deal == null) {
            return Result.fail("团购不存在");
        }
        String error = validateDealPublish(deal);
        if (error != null) {
            return Result.fail(error);
        }
        deal.setStatus(STATUS_ONLINE);
        groupDealService.updateById(deal);
        return Result.ok();
    }

    public Result unpublishDeal(Long id) {
        GroupDeal deal = groupDealService.getById(id);
        if (deal == null) {
            return Result.fail("团购不存在");
        }
        deal.setStatus(STATUS_OFFLINE);
        groupDealService.updateById(deal);
        return Result.ok();
    }

    public boolean isDishOfShop(Long dishId, Long shopId) {
        ShopFeaturedDish dish = shopFeaturedDishService.getById(dishId);
        return dish != null && shopId != null && shopId.equals(dish.getShopId());
    }

    public boolean isDealOfEditableShop(Long dealId, Long userId) {
        GroupDeal deal = groupDealService.getById(dealId);
        return deal != null && merchantAuthService.canEditShop(userId, deal.getShopId());
    }

    private ShopUpdateApplication getPendingShopUpdate(Long shopId) {
        return shopUpdateApplicationService.lambdaQuery()
                .eq(ShopUpdateApplication::getShopId, shopId)
                .eq(ShopUpdateApplication::getStatus, MerchantApplicationConstants.STATUS_PENDING)
                .orderByDesc(ShopUpdateApplication::getId)
                .last("limit 1")
                .one();
    }

    private Map<String, Object> buildSensitivePayload(ShopUpdateApplicationSubmitDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "name", dto.getName());
        if (dto.getTypeId() != null) payload.put("typeId", dto.getTypeId());
        putIfPresent(payload, "area", dto.getArea());
        putIfPresent(payload, "address", dto.getAddress());
        if (dto.getX() != null) payload.put("x", dto.getX());
        if (dto.getY() != null) payload.put("y", dto.getY());
        putIfPresent(payload, "images", dto.getImages());
        return payload;
    }

    private void applyShopPayload(Long shopId, String changePayload) {
        JSONObject payload = JSONUtil.parseObj(changePayload);
        Shop patch = new Shop().setId(shopId);
        if (payload.containsKey("name")) patch.setName(payload.getStr("name"));
        if (payload.containsKey("typeId")) patch.setTypeId(payload.getLong("typeId"));
        if (payload.containsKey("area")) patch.setArea(payload.getStr("area"));
        if (payload.containsKey("address")) patch.setAddress(payload.getStr("address"));
        if (payload.containsKey("x")) patch.setX(payload.getDouble("x"));
        if (payload.containsKey("y")) patch.setY(payload.getDouble("y"));
        if (payload.containsKey("images")) patch.setImages(payload.getStr("images"));
        shopService.updateById(patch);
    }

    private ShopUpdateApplicationRecordDTO toUpdateRecord(ShopUpdateApplication item, Map<Long, User> userMap, Map<Long, Shop> shopMap, Map<Long, User> reviewerMap) {
        ShopUpdateApplicationRecordDTO dto = new ShopUpdateApplicationRecordDTO();
        dto.setId(item.getId());
        dto.setUserId(item.getUserId());
        dto.setUserNickName(userMap.get(item.getUserId()) == null ? "" : userMap.get(item.getUserId()).getNickName());
        dto.setShopId(item.getShopId());
        dto.setShopName(shopMap.get(item.getShopId()) == null ? "" : shopMap.get(item.getShopId()).getName());
        dto.setChangePayload(item.getChangePayload());
        dto.setProofImages(item.getProofImages());
        dto.setMessage(item.getMessage());
        dto.setStatus(item.getStatus());
        dto.setStatusText(MerchantApplicationConstants.toStatusText(item.getStatus()));
        dto.setReviewerId(item.getReviewerId());
        dto.setReviewerNickName(reviewerMap.get(item.getReviewerId()) == null ? "" : reviewerMap.get(item.getReviewerId()).getNickName());
        dto.setReviewRemark(item.getReviewRemark());
        dto.setReviewTime(item.getReviewTime());
        dto.setCreateTime(item.getCreateTime());
        return dto;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds == null ? Collections.emptyList() : userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.listByIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Shop> loadShops(List<Long> shopIds) {
        List<Long> ids = shopIds == null ? Collections.emptyList() : shopIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return shopService.listByIds(ids).stream().collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left));
    }

    private String validateDish(Long shopId, FeaturedDishSaveDTO dto) {
        if (shopId == null || shopService.getById(shopId) == null) {
            return "店铺不存在";
        }
        if (dto == null || StrUtil.isBlank(dto.getName())) {
            return "请填写招牌菜名称";
        }
        if (dto.getPrice() != null && dto.getPrice() < 0) {
            return "招牌菜价格不合法";
        }
        return null;
    }

    private String validateDeal(Long shopId, GroupDealSaveDTO dto) {
        if (shopId == null || shopService.getById(shopId) == null) {
            return "店铺不存在";
        }
        if (dto == null || StrUtil.isBlank(dto.getTitle())) {
            return "请填写团购标题";
        }
        if (dto.getPrice() == null || dto.getPrice() <= 0 || dto.getOriginalPrice() == null || dto.getOriginalPrice() <= 0) {
            return "团购价格不合法";
        }
        if (dto.getOriginalPrice() < dto.getPrice()) {
            return "门市价不能小于团购价";
        }
        if (dto.getStock() == null || dto.getStock() <= 0) {
            return "团购库存必须大于0";
        }
        if (dto.getBeginTime() != null && dto.getEndTime() != null && !dto.getEndTime().isAfter(dto.getBeginTime())) {
            return "结束时间必须晚于开始时间";
        }
        return null;
    }

    private String validateDealPublish(GroupDeal deal) {
        if (deal.getStock() == null || deal.getStock() <= 0) {
            return "团购库存不足";
        }
        if (deal.getEndTime() != null && deal.getEndTime().isBefore(LocalDateTime.now())) {
            deal.setStatus(STATUS_ENDED);
            groupDealService.updateById(deal);
            return "团购已结束，无法上架";
        }
        return null;
    }

    private void expireDealsIfNeeded(Long shopId) {
        List<GroupDeal> deals = groupDealService.lambdaQuery()
                .eq(shopId != null, GroupDeal::getShopId, shopId)
                .eq(GroupDeal::getStatus, STATUS_ONLINE)
                .lt(GroupDeal::getEndTime, LocalDateTime.now())
                .list();
        if (deals.isEmpty()) {
            return;
        }
        deals.forEach(deal -> deal.setStatus(STATUS_ENDED));
        groupDealService.updateBatchById(deals);
    }

    private void refreshShopCache(Long shopId) {
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        Shop fresh = shopService.getById(shopId);
        if (fresh != null) {
            shopService.refreshShopSearchCache(fresh);
        }
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            payload.put(key, value.trim());
        }
    }

    private String cleanNullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
