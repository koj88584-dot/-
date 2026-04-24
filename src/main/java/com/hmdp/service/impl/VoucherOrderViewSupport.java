package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class VoucherOrderViewSupport {

    private static final String DEFAULT_VOUCHER_IMAGE = "/imgs/icons/default-icon.png";

    private VoucherOrderViewSupport() {
    }

    static List<VoucherOrderDTO> toOrderDTOs(List<VoucherOrder> orders, Map<Long, Voucher> voucherMap,
                                             Map<Long, Shop> shopMap) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        return orders.stream()
                .map(order -> toOrderDTO(order, voucherMap.get(order.getVoucherId()), shopMap))
                .collect(Collectors.toList());
    }

    private static VoucherOrderDTO toOrderDTO(VoucherOrder order, Voucher voucher, Map<Long, Shop> shopMap) {
        VoucherOrderDTO dto = BeanUtil.copyProperties(order, VoucherOrderDTO.class);
        dto.setStatusText(VoucherViewSupport.orderStatusText(order.getStatus()));
        if (voucher != null) {
            dto.setVoucherTitle(voucher.getTitle());
            dto.setVoucherSubTitle(voucher.getSubTitle());
            dto.setPayValue(voucher.getPayValue());
            dto.setActualValue(voucher.getActualValue());
            dto.setShopId(voucher.getShopId());
            dto.setVoucherType(voucher.getType());
        }

        Shop shop = voucher == null ? null : shopMap.get(voucher.getShopId());
        if (shop != null) {
            dto.setShopName(shop.getName());
            dto.setVoucherImages(firstImage(shop.getImages()));
        } else {
            dto.setVoucherImages(DEFAULT_VOUCHER_IMAGE);
        }
        return dto;
    }

    static String firstImage(String images) {
        if (images == null || images.trim().isEmpty()) {
            return DEFAULT_VOUCHER_IMAGE;
        }
        String[] parts = images.split(",");
        return parts.length == 0 || parts[0].trim().isEmpty()
                ? DEFAULT_VOUCHER_IMAGE
                : parts[0].trim();
    }
}
