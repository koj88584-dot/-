package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.AdminReviewActionDTO;
import com.hmdp.dto.MerchantApplicationProfileDTO;
import com.hmdp.dto.MerchantApplicationRecordDTO;
import com.hmdp.dto.MerchantApplicationSubmitDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopClaimApplicationRecordDTO;
import com.hmdp.dto.ShopClaimApplicationSubmitDTO;
import com.hmdp.dto.ShopCreateApplicationRecordDTO;
import com.hmdp.dto.ShopCreateApplicationSubmitDTO;
import com.hmdp.entity.MerchantApplication;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopClaimApplication;
import com.hmdp.entity.ShopCreateApplication;
import com.hmdp.entity.ShopMember;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.User;
import com.hmdp.entity.UserRole;
import com.hmdp.service.IMerchantApplicationService;
import com.hmdp.service.IShopClaimApplicationService;
import com.hmdp.service.IShopCreateApplicationService;
import com.hmdp.service.IShopMemberService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IUserRoleService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MerchantApplicationConstants;
import com.hmdp.utils.MerchantRoleConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MerchantApplicationFlowService {

    @Resource
    private IMerchantApplicationService merchantApplicationService;
    @Resource
    private IShopClaimApplicationService shopClaimApplicationService;
    @Resource
    private IShopCreateApplicationService shopCreateApplicationService;
    @Resource
    private IUserService userService;
    @Resource
    private IUserRoleService userRoleService;
    @Resource
    private IShopMemberService shopMemberService;
    @Resource
    private IShopService shopService;
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private MerchantAuthService merchantAuthService;

    public MerchantApplicationProfileDTO buildApplicationProfile(Long userId) {
        MerchantApplicationProfileDTO profile = new MerchantApplicationProfileDTO();
        if (userId == null) {
            return profile;
        }
        profile.setMerchantEnabled(merchantAuthService.canAccessMerchantCenter(userId));
        profile.setAdmin(merchantAuthService.isAdmin(userId));
        profile.setPrimaryRole(merchantAuthService.buildProfile(userId).getPrimaryRole());
        profile.setManagedShopCount(merchantAuthService.buildProfile(userId).getManagedShopCount());

        MerchantApplication latestMerchant = getLatestMerchantApplication(userId);
        ShopClaimApplication latestClaim = getLatestClaimApplication(userId);
        ShopCreateApplication latestCreate = getLatestCreateApplication(userId);

        if (profile.getMerchantEnabled()) {
            profile.setMerchantApplicationStatus(MerchantApplicationConstants.PROFILE_STATUS_APPROVED);
            profile.setMerchantApplicationStatusText(MerchantApplicationConstants.toStatusText(MerchantApplicationConstants.STATUS_APPROVED));
        } else if (latestMerchant != null) {
            profile.setMerchantApplicationStatus(MerchantApplicationConstants.toProfileStatus(latestMerchant.getStatus()));
            profile.setMerchantApplicationStatusText(MerchantApplicationConstants.toStatusText(latestMerchant.getStatus()));
        }

        profile.setLatestMerchantApplication(toMerchantRecord(latestMerchant, userMap(latestMerchant), reviewerMap(latestMerchant)));
        profile.setLatestShopClaimApplication(toClaimRecord(latestClaim, userMap(latestClaim), reviewerMap(latestClaim), shopMap(latestClaim)));
        profile.setLatestShopCreateApplication(toCreateRecord(latestCreate, userMap(latestCreate), reviewerMap(latestCreate), typeMap(latestCreate)));
        return profile;
    }

    @Transactional
    public Result submitMerchantApplication(Long userId, MerchantApplicationSubmitDTO dto) {
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (dto == null) {
            return Result.fail("申请信息不能为空");
        }
        if (merchantAuthService.canAccessMerchantCenter(userId)) {
            return Result.fail("当前账号已开通商家资格");
        }
        MerchantApplication pending = merchantApplicationService.lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .eq(MerchantApplication::getStatus, MerchantApplicationConstants.STATUS_PENDING)
                .orderByDesc(MerchantApplication::getId)
                .last("limit 1")
                .one();
        if (pending != null) {
            return Result.fail("你已有审核中的商家申请");
        }
        String validateMsg = validateMerchantSubmit(dto);
        if (validateMsg != null) {
            return Result.fail(validateMsg);
        }
        MerchantApplication application = new MerchantApplication()
                .setUserId(userId)
                .setContactName(dto.getContactName().trim())
                .setContactPhone(dto.getContactPhone().trim())
                .setCompanyName(cleanNullable(dto.getCompanyName()))
                .setDescription(cleanNullable(dto.getDescription()))
                .setProofImages(normalizeImages(dto.getProofImages()))
                .setStatus(MerchantApplicationConstants.STATUS_PENDING)
                .setReviewerId(null)
                .setReviewRemark(null)
                .setReviewTime(null);
        merchantApplicationService.save(application);
        return Result.ok(toMerchantRecord(application, userMap(application), Collections.emptyMap()));
    }

    @Transactional
    public Result submitShopClaim(Long userId, ShopClaimApplicationSubmitDTO dto) {
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (!merchantAuthService.canAccessMerchantCenter(userId)) {
            return Result.fail("请先完成商家资格审核");
        }
        if (dto == null || dto.getShopId() == null) {
            return Result.fail("请选择要认领的店铺");
        }
        Shop shop = shopService.getById(dto.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (merchantAuthService.canManageShop(userId, dto.getShopId())) {
            return Result.fail("你已具备该店铺的管理权限");
        }
        ShopClaimApplication pending = shopClaimApplicationService.lambdaQuery()
                .eq(ShopClaimApplication::getUserId, userId)
                .eq(ShopClaimApplication::getShopId, dto.getShopId())
                .eq(ShopClaimApplication::getStatus, MerchantApplicationConstants.STATUS_PENDING)
                .orderByDesc(ShopClaimApplication::getId)
                .last("limit 1")
                .one();
        if (pending != null) {
            return Result.fail("该店铺已有审核中的认领申请");
        }
        if (StrUtil.isBlank(dto.getProofImages())) {
            return Result.fail("请至少提供一张证明图片");
        }
        MerchantApplication latestMerchant = getLatestApprovedMerchantApplication(userId);
        ShopClaimApplication application = new ShopClaimApplication()
                .setUserId(userId)
                .setShopId(dto.getShopId())
                .setMerchantApplicationId(latestMerchant == null ? null : latestMerchant.getId())
                .setProofImages(normalizeImages(dto.getProofImages()))
                .setMessage(cleanNullable(dto.getMessage()))
                .setStatus(MerchantApplicationConstants.STATUS_PENDING)
                .setReviewerId(null)
                .setReviewRemark(null)
                .setReviewTime(null);
        shopClaimApplicationService.save(application);
        return Result.ok(toClaimRecord(application, userMap(application), Collections.emptyMap(), shopMap(application)));
    }

    @Transactional
    public Result submitShopCreate(Long userId, ShopCreateApplicationSubmitDTO dto) {
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (!merchantAuthService.canAccessMerchantCenter(userId)) {
            return Result.fail("请先完成商家资格审核");
        }
        if (dto == null) {
            return Result.fail("申请信息不能为空");
        }
        String validateMsg = validateCreateSubmit(dto);
        if (validateMsg != null) {
            return Result.fail(validateMsg);
        }
        ShopCreateApplication pending = shopCreateApplicationService.lambdaQuery()
                .eq(ShopCreateApplication::getUserId, userId)
                .eq(ShopCreateApplication::getShopName, dto.getShopName().trim())
                .eq(ShopCreateApplication::getStatus, MerchantApplicationConstants.STATUS_PENDING)
                .orderByDesc(ShopCreateApplication::getId)
                .last("limit 1")
                .one();
        if (pending != null) {
            return Result.fail("该店铺名称已有审核中的新建申请");
        }
        MerchantApplication latestMerchant = getLatestApprovedMerchantApplication(userId);
        ShopCreateApplication application = new ShopCreateApplication()
                .setUserId(userId)
                .setMerchantApplicationId(latestMerchant == null ? null : latestMerchant.getId())
                .setShopName(dto.getShopName().trim())
                .setTypeId(dto.getTypeId())
                .setAddress(dto.getAddress().trim())
                .setX(dto.getX())
                .setY(dto.getY())
                .setContactPhone(dto.getContactPhone().trim())
                .setImages(normalizeImages(dto.getImages()))
                .setProofImages(normalizeImages(dto.getProofImages()))
                .setStatus(MerchantApplicationConstants.STATUS_PENDING)
                .setReviewerId(null)
                .setReviewRemark(null)
                .setReviewTime(null);
        shopCreateApplicationService.save(application);
        return Result.ok(toCreateRecord(application, userMap(application), Collections.emptyMap(), typeMap(application)));
    }

    public List<MerchantApplicationRecordDTO> listMerchantApplications(Integer status) {
        List<MerchantApplication> applications = merchantApplicationService.lambdaQuery()
                .eq(status != null, MerchantApplication::getStatus, status)
                .orderByDesc(MerchantApplication::getId)
                .list();
        Map<Long, User> userMap = loadUsers(applications.stream()
                .map(MerchantApplication::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, User> reviewerMap = reviewerMap(applications.stream().map(MerchantApplication::getReviewerId).filter(Objects::nonNull).collect(Collectors.toList()));
        return applications.stream()
                .map(item -> toMerchantRecord(item, userMap, reviewerMap))
                .collect(Collectors.toList());
    }

    public List<ShopClaimApplicationRecordDTO> listShopClaimApplications(Integer status) {
        List<ShopClaimApplication> applications = shopClaimApplicationService.lambdaQuery()
                .eq(status != null, ShopClaimApplication::getStatus, status)
                .orderByDesc(ShopClaimApplication::getId)
                .list();
        Map<Long, User> userMap = loadUsers(applications.stream()
                .map(ShopClaimApplication::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, User> reviewerMap = reviewerMap(applications.stream().map(ShopClaimApplication::getReviewerId).filter(Objects::nonNull).collect(Collectors.toList()));
        Map<Long, Shop> shopMap = loadShops(applications.stream()
                .map(ShopClaimApplication::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return applications.stream()
                .map(item -> toClaimRecord(item, userMap, reviewerMap, shopMap))
                .collect(Collectors.toList());
    }

    public List<ShopCreateApplicationRecordDTO> listShopCreateApplications(Integer status) {
        List<ShopCreateApplication> applications = shopCreateApplicationService.lambdaQuery()
                .eq(status != null, ShopCreateApplication::getStatus, status)
                .orderByDesc(ShopCreateApplication::getId)
                .list();
        Map<Long, User> userMap = loadUsers(applications.stream()
                .map(ShopCreateApplication::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, User> reviewerMap = reviewerMap(applications.stream().map(ShopCreateApplication::getReviewerId).filter(Objects::nonNull).collect(Collectors.toList()));
        Map<Long, ShopType> typeMap = loadShopTypes(applications.stream()
                .map(ShopCreateApplication::getTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return applications.stream()
                .map(item -> toCreateRecord(item, userMap, reviewerMap, typeMap))
                .collect(Collectors.toList());
    }

    @Transactional
    public Result approveMerchantApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        MerchantApplication application = merchantApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("商家申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_USER);
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_MERCHANT);
        application.setStatus(MerchantApplicationConstants.STATUS_APPROVED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        merchantApplicationService.updateById(application);
        return Result.ok(toMerchantRecord(application, userMap(application), reviewerMap(application)));
    }

    @Transactional
    public Result rejectMerchantApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        MerchantApplication application = merchantApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("商家申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        application.setStatus(MerchantApplicationConstants.STATUS_REJECTED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        merchantApplicationService.updateById(application);
        return Result.ok(toMerchantRecord(application, userMap(application), reviewerMap(application)));
    }

    @Transactional
    public Result approveShopClaimApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        ShopClaimApplication application = shopClaimApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("店铺认领申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        Shop shop = shopService.getById(application.getShopId());
        if (shop == null) {
            return Result.fail("目标店铺不存在");
        }
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_USER);
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_MERCHANT);
        upsertShopMember(application.getShopId(), application.getUserId(), MerchantRoleConstants.SHOP_ROLE_OWNER);
        application.setStatus(MerchantApplicationConstants.STATUS_APPROVED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        shopClaimApplicationService.updateById(application);
        return Result.ok(toClaimRecord(application, userMap(application), reviewerMap(application), shopMap(application)));
    }

    @Transactional
    public Result rejectShopClaimApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        ShopClaimApplication application = shopClaimApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("店铺认领申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        application.setStatus(MerchantApplicationConstants.STATUS_REJECTED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        shopClaimApplicationService.updateById(application);
        return Result.ok(toClaimRecord(application, userMap(application), reviewerMap(application), shopMap(application)));
    }

    @Transactional
    public Result approveShopCreateApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        ShopCreateApplication application = shopCreateApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("新建店铺申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        ShopType shopType = shopTypeService.getById(application.getTypeId());
        if (shopType == null) {
            return Result.fail("店铺类型不存在");
        }
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_USER);
        ensureUserRole(application.getUserId(), MerchantRoleConstants.ROLE_MERCHANT);

        Shop shop = new Shop();
        shop.setName(application.getShopName());
        shop.setTypeId(application.getTypeId());
        shop.setImages(application.getImages());
        shop.setArea("");
        shop.setAddress(application.getAddress());
        shop.setX(application.getX());
        shop.setY(application.getY());
        shop.setAvgPrice(0L);
        shop.setSold(0);
        shop.setComments(0);
        shop.setScore(0);
        shop.setOpenHours("10:00-22:00");
        shopService.save(shop);
        shopService.refreshShopSearchCache(shop);
        upsertShopMember(shop.getId(), application.getUserId(), MerchantRoleConstants.SHOP_ROLE_OWNER);

        application.setStatus(MerchantApplicationConstants.STATUS_APPROVED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        shopCreateApplicationService.updateById(application);
        return Result.ok(toCreateRecord(application, userMap(application), reviewerMap(application), typeMap(application)));
    }

    @Transactional
    public Result rejectShopCreateApplication(Long applicationId, Long reviewerId, AdminReviewActionDTO dto) {
        ShopCreateApplication application = shopCreateApplicationService.getById(applicationId);
        if (application == null) {
            return Result.fail("新建店铺申请不存在");
        }
        if (!MerchantApplicationConstants.STATUS_PENDING.equals(application.getStatus())) {
            return Result.fail("当前申请状态不可重复审核");
        }
        application.setStatus(MerchantApplicationConstants.STATUS_REJECTED);
        application.setReviewerId(reviewerId);
        application.setReviewRemark(cleanNullable(dto == null ? null : dto.getReviewRemark()));
        application.setReviewTime(LocalDateTime.now());
        shopCreateApplicationService.updateById(application);
        return Result.ok(toCreateRecord(application, userMap(application), reviewerMap(application), typeMap(application)));
    }

    public List<Shop> searchClaimableShops(String keyword) {
        return shopService.lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), Shop::getName, keyword == null ? null : keyword.trim())
                .orderByAsc(Shop::getId)
                .last("limit 20")
                .list();
    }

    private String validateMerchantSubmit(MerchantApplicationSubmitDTO dto) {
        if (StrUtil.isBlank(dto.getContactName())) {
            return "请填写联系人";
        }
        if (StrUtil.isBlank(dto.getContactPhone())) {
            return "请填写联系电话";
        }
        if (StrUtil.isBlank(dto.getProofImages())) {
            return "请至少提供一张证明图片";
        }
        return null;
    }

    private String validateCreateSubmit(ShopCreateApplicationSubmitDTO dto) {
        if (StrUtil.isBlank(dto.getShopName())) {
            return "请填写店铺名称";
        }
        if (dto.getTypeId() == null) {
            return "请选择店铺类型";
        }
        if (StrUtil.isBlank(dto.getAddress())) {
            return "请填写店铺地址";
        }
        if (StrUtil.isBlank(dto.getContactPhone())) {
            return "请填写联系电话";
        }
        if (StrUtil.isBlank(dto.getProofImages())) {
            return "请至少提供一张证明图片";
        }
        return null;
    }

    private void ensureUserRole(Long userId, String roleCode) {
        if (userId == null || StrUtil.isBlank(roleCode)) {
            return;
        }
        boolean exists = userRoleService.lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleCode, roleCode)
                .count() > 0;
        if (!exists) {
            userRoleService.save(new UserRole().setUserId(userId).setRoleCode(roleCode));
        }
    }

    private void upsertShopMember(Long shopId, Long userId, String roleCode) {
        ShopMember member = shopMemberService.lambdaQuery()
                .eq(ShopMember::getShopId, shopId)
                .eq(ShopMember::getUserId, userId)
                .last("limit 1")
                .one();
        if (member == null) {
            shopMemberService.save(new ShopMember()
                    .setShopId(shopId)
                    .setUserId(userId)
                    .setRoleCode(roleCode)
                    .setStatus(MerchantRoleConstants.MEMBER_STATUS_ENABLED));
            return;
        }
        member.setRoleCode(roleCode);
        member.setStatus(MerchantRoleConstants.MEMBER_STATUS_ENABLED);
        shopMemberService.updateById(member);
    }

    private MerchantApplication getLatestMerchantApplication(Long userId) {
        return merchantApplicationService.lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .orderByDesc(MerchantApplication::getId)
                .last("limit 1")
                .one();
    }

    private MerchantApplication getLatestApprovedMerchantApplication(Long userId) {
        return merchantApplicationService.lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .eq(MerchantApplication::getStatus, MerchantApplicationConstants.STATUS_APPROVED)
                .orderByDesc(MerchantApplication::getId)
                .last("limit 1")
                .one();
    }

    private ShopClaimApplication getLatestClaimApplication(Long userId) {
        return shopClaimApplicationService.lambdaQuery()
                .eq(ShopClaimApplication::getUserId, userId)
                .orderByDesc(ShopClaimApplication::getId)
                .last("limit 1")
                .one();
    }

    private ShopCreateApplication getLatestCreateApplication(Long userId) {
        return shopCreateApplicationService.lambdaQuery()
                .eq(ShopCreateApplication::getUserId, userId)
                .orderByDesc(ShopCreateApplication::getId)
                .last("limit 1")
                .one();
    }

    private String normalizeImages(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return StrUtil.split(raw, ',', true, true).stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(","));
    }

    private String cleanNullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private Map<Long, User> userMap(MerchantApplication application) {
        if (application == null || application.getUserId() == null) {
            return Collections.emptyMap();
        }
        User user = userService.getById(application.getUserId());
        return user == null ? Collections.emptyMap() : Collections.singletonMap(user.getId(), user);
    }

    private Map<Long, User> userMap(ShopClaimApplication application) {
        if (application == null || application.getUserId() == null) {
            return Collections.emptyMap();
        }
        User user = userService.getById(application.getUserId());
        return user == null ? Collections.emptyMap() : Collections.singletonMap(user.getId(), user);
    }

    private Map<Long, User> userMap(ShopCreateApplication application) {
        if (application == null || application.getUserId() == null) {
            return Collections.emptyMap();
        }
        User user = userService.getById(application.getUserId());
        return user == null ? Collections.emptyMap() : Collections.singletonMap(user.getId(), user);
    }

    private Map<Long, User> reviewerMap(MerchantApplication application) {
        if (application == null || application.getReviewerId() == null) {
            return Collections.emptyMap();
        }
        return reviewerMap(Collections.singletonList(application.getReviewerId()));
    }

    private Map<Long, User> reviewerMap(ShopClaimApplication application) {
        if (application == null || application.getReviewerId() == null) {
            return Collections.emptyMap();
        }
        return reviewerMap(Collections.singletonList(application.getReviewerId()));
    }

    private Map<Long, User> reviewerMap(ShopCreateApplication application) {
        if (application == null || application.getReviewerId() == null) {
            return Collections.emptyMap();
        }
        return reviewerMap(Collections.singletonList(application.getReviewerId()));
    }

    private Map<Long, User> reviewerMap(List<Long> reviewerIds) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return loadUsers(reviewerIds);
    }

    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Shop> loadShops(Collection<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, ShopType> loadShopTypes(Collection<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return shopTypeService.listByIds(typeIds).stream()
                .collect(Collectors.toMap(ShopType::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Shop> shopMap(ShopClaimApplication application) {
        if (application == null || application.getShopId() == null) {
            return Collections.emptyMap();
        }
        Shop shop = shopService.getById(application.getShopId());
        return shop == null ? Collections.emptyMap() : Collections.singletonMap(shop.getId(), shop);
    }

    private Map<Long, ShopType> typeMap(ShopCreateApplication application) {
        if (application == null || application.getTypeId() == null) {
            return Collections.emptyMap();
        }
        ShopType type = shopTypeService.getById(application.getTypeId());
        return type == null ? Collections.emptyMap() : Collections.singletonMap(type.getId(), type);
    }

    private MerchantApplicationRecordDTO toMerchantRecord(MerchantApplication application, Map<Long, User> users, Map<Long, User> reviewers) {
        if (application == null) {
            return null;
        }
        MerchantApplicationRecordDTO dto = BeanUtil.copyProperties(application, MerchantApplicationRecordDTO.class);
        User user = users.get(application.getUserId());
        if (user != null) {
            dto.setUserNickName(user.getNickName());
        }
        User reviewer = reviewers.get(application.getReviewerId());
        if (reviewer != null) {
            dto.setReviewerName(reviewer.getNickName());
        }
        dto.setStatusText(MerchantApplicationConstants.toStatusText(application.getStatus()));
        return dto;
    }

    private ShopClaimApplicationRecordDTO toClaimRecord(ShopClaimApplication application, Map<Long, User> users, Map<Long, User> reviewers, Map<Long, Shop> shops) {
        if (application == null) {
            return null;
        }
        ShopClaimApplicationRecordDTO dto = BeanUtil.copyProperties(application, ShopClaimApplicationRecordDTO.class);
        User user = users.get(application.getUserId());
        if (user != null) {
            dto.setUserNickName(user.getNickName());
        }
        User reviewer = reviewers.get(application.getReviewerId());
        if (reviewer != null) {
            dto.setReviewerName(reviewer.getNickName());
        }
        Shop shop = shops.get(application.getShopId());
        if (shop != null) {
            dto.setShopName(shop.getName());
        }
        dto.setStatusText(MerchantApplicationConstants.toStatusText(application.getStatus()));
        return dto;
    }

    private ShopCreateApplicationRecordDTO toCreateRecord(ShopCreateApplication application, Map<Long, User> users, Map<Long, User> reviewers, Map<Long, ShopType> types) {
        if (application == null) {
            return null;
        }
        ShopCreateApplicationRecordDTO dto = BeanUtil.copyProperties(application, ShopCreateApplicationRecordDTO.class);
        User user = users.get(application.getUserId());
        if (user != null) {
            dto.setUserNickName(user.getNickName());
        }
        User reviewer = reviewers.get(application.getReviewerId());
        if (reviewer != null) {
            dto.setReviewerName(reviewer.getNickName());
        }
        ShopType type = types.get(application.getTypeId());
        if (type != null) {
            dto.setTypeName(type.getName());
        }
        dto.setStatusText(MerchantApplicationConstants.toStatusText(application.getStatus()));
        return dto;
    }
}
