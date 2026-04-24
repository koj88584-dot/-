package com.hmdp.service.impl;

import com.hmdp.dto.MerchantProfileDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.MerchantApplication;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopMember;
import com.hmdp.entity.UserRole;
import com.hmdp.service.IMerchantApplicationService;
import com.hmdp.service.IShopMemberService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserRoleService;
import com.hmdp.utils.MerchantApplicationConstants;
import com.hmdp.utils.MerchantRoleConstants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MerchantAuthService {

    @Resource
    private IUserRoleService userRoleService;
    @Resource
    private IMerchantApplicationService merchantApplicationService;
    @Resource
    private IShopMemberService shopMemberService;
    @Resource
    private IShopService shopService;

    public MerchantProfileDTO buildProfile(Long userId) {
        MerchantProfileDTO profile = new MerchantProfileDTO();
        if (userId == null) {
            profile.getRoles().add(MerchantRoleConstants.ROLE_USER);
            return profile;
        }
        List<String> roleCodes = listRoleCodes(userId);
        profile.setRoles(roleCodes);
        profile.setAdmin(roleCodes.contains(MerchantRoleConstants.ROLE_ADMIN));
        profile.setMerchantEnabled(profile.getAdmin() || roleCodes.contains(MerchantRoleConstants.ROLE_MERCHANT));
        profile.setPrimaryRole(resolvePrimaryRole(profile.getAdmin(), roleCodes));
        profile.setManagedShopCount(countManagedShops(userId, profile.getAdmin()));
        profile.setMerchantApplicationStatus(resolveMerchantApplicationStatus(userId, profile.getMerchantEnabled()));
        return profile;
    }

    public void fillUserFlags(UserDTO userDTO) {
        if (userDTO == null) {
            return;
        }
        MerchantProfileDTO profile = buildProfile(userDTO.getId());
        userDTO.setPrimaryRole(profile.getPrimaryRole());
        userDTO.setMerchantEnabled(profile.getMerchantEnabled());
        userDTO.setAdmin(profile.getAdmin());
        userDTO.setMerchantApplicationStatus(profile.getMerchantApplicationStatus());
        userDTO.setManagedShopCount(profile.getManagedShopCount());
    }

    public boolean canAccessMerchantCenter(Long userId) {
        MerchantProfileDTO profile = buildProfile(userId);
        return Boolean.TRUE.equals(profile.getMerchantEnabled());
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return buildProfile(userId).getAdmin();
    }

    public boolean canManageShop(Long userId, Long shopId) {
        if (userId == null || shopId == null) {
            return false;
        }
        MerchantProfileDTO profile = buildProfile(userId);
        if (!Boolean.TRUE.equals(profile.getMerchantEnabled())) {
            return false;
        }
        if (Boolean.TRUE.equals(profile.getAdmin())) {
            return shopService.getById(shopId) != null;
        }
        return shopMemberService.lambdaQuery()
                .eq(ShopMember::getUserId, userId)
                .eq(ShopMember::getShopId, shopId)
                .eq(ShopMember::getStatus, MerchantRoleConstants.MEMBER_STATUS_ENABLED)
                .count() > 0;
    }

    public List<Shop> listManagedShops(Long userId, String keyword, Integer current) {
        MerchantProfileDTO profile = buildProfile(userId);
        if (!Boolean.TRUE.equals(profile.getMerchantEnabled())) {
            return Collections.emptyList();
        }
        if (Boolean.TRUE.equals(profile.getAdmin())) {
            return shopService.lambdaQuery()
                    .like(keyword != null && !keyword.trim().isEmpty(), Shop::getName, keyword)
                    .orderByAsc(Shop::getId)
                    .list();
        }

        List<Long> shopIds = shopMemberService.lambdaQuery()
                .eq(ShopMember::getUserId, userId)
                .eq(ShopMember::getStatus, MerchantRoleConstants.MEMBER_STATUS_ENABLED)
                .orderByAsc(ShopMember::getShopId)
                .list()
                .stream()
                .map(ShopMember::getShopId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (shopIds.isEmpty()) {
            return Collections.emptyList();
        }
        return shopService.lambdaQuery()
                .in(Shop::getId, shopIds)
                .like(keyword != null && !keyword.trim().isEmpty(), Shop::getName, keyword)
                .orderByAsc(Shop::getId)
                .list();
    }

    private List<String> listRoleCodes(Long userId) {
        if (userId == null) {
            return new ArrayList<>(Collections.singletonList(MerchantRoleConstants.ROLE_USER));
        }
        Set<String> roleCodes = userRoleService.lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .orderByAsc(UserRole::getId)
                .list()
                .stream()
                .map(UserRole::getRoleCode)
                .filter(code -> code != null && !code.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleCodes.isEmpty()) {
            roleCodes.add(MerchantRoleConstants.ROLE_USER);
        } else if (!roleCodes.contains(MerchantRoleConstants.ROLE_USER)) {
            roleCodes.add(MerchantRoleConstants.ROLE_USER);
        }
        return new ArrayList<>(roleCodes);
    }

    private String resolvePrimaryRole(Boolean admin, List<String> roleCodes) {
        if (Boolean.TRUE.equals(admin)) {
            return MerchantRoleConstants.ROLE_ADMIN;
        }
        if (roleCodes.contains(MerchantRoleConstants.ROLE_MERCHANT)) {
            return MerchantRoleConstants.ROLE_MERCHANT;
        }
        return MerchantRoleConstants.ROLE_USER;
    }

    private Long countManagedShops(Long userId, Boolean admin) {
        if (userId == null) {
            return 0L;
        }
        if (Boolean.TRUE.equals(admin)) {
            return shopService.count();
        }
        return (long) shopMemberService.lambdaQuery()
                .eq(ShopMember::getUserId, userId)
                .eq(ShopMember::getStatus, MerchantRoleConstants.MEMBER_STATUS_ENABLED)
                .list()
                .stream()
                .map(ShopMember::getShopId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private String resolveMerchantApplicationStatus(Long userId, Boolean merchantEnabled) {
        if (Boolean.TRUE.equals(merchantEnabled)) {
            return MerchantApplicationConstants.PROFILE_STATUS_APPROVED;
        }
        MerchantApplication latest = merchantApplicationService.lambdaQuery()
                .eq(MerchantApplication::getUserId, userId)
                .orderByDesc(MerchantApplication::getId)
                .last("limit 1")
                .one();
        return latest == null
                ? MerchantApplicationConstants.PROFILE_STATUS_NONE
                : MerchantApplicationConstants.toProfileStatus(latest.getStatus());
    }
}
