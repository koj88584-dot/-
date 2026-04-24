package com.hmdp.service.impl;

import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherOrderViewSupportTest {

    @Test
    void toOrderDTOsShouldMergeVoucherAndShopFields() {
        VoucherOrder order = new VoucherOrder();
        order.setId(1L);
        order.setVoucherId(2L);
        order.setUserId(3L);

        Voucher voucher = new Voucher();
        voucher.setId(2L);
        voucher.setShopId(9L);
        voucher.setTitle("新人券");
        voucher.setSubTitle("满 30 减 10");
        voucher.setPayValue(3000L);
        voucher.setActualValue(1000L);
        voucher.setType(1);

        Shop shop = new Shop();
        shop.setId(9L);
        shop.setName("咖啡店");
        shop.setImages("/imgs/a.png,/imgs/b.png");

        List<VoucherOrderDTO> dtos = VoucherOrderViewSupport.toOrderDTOs(
                Collections.singletonList(order),
                Collections.singletonMap(2L, voucher),
                Collections.singletonMap(9L, shop)
        );

        assertThat(dtos).hasSize(1);
        VoucherOrderDTO dto = dtos.get(0);
        assertThat(dto.getVoucherTitle()).isEqualTo("新人券");
        assertThat(dto.getVoucherSubTitle()).isEqualTo("满 30 减 10");
        assertThat(dto.getShopId()).isEqualTo(9L);
        assertThat(dto.getShopName()).isEqualTo("咖啡店");
        assertThat(dto.getVoucherImages()).isEqualTo("/imgs/a.png");
    }

    @Test
    void toOrderDTOsShouldFallbackToDefaultImageWhenShopMissing() {
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(2L);

        Voucher voucher = new Voucher();
        voucher.setId(2L);
        voucher.setShopId(9L);

        List<VoucherOrderDTO> dtos = VoucherOrderViewSupport.toOrderDTOs(
                Collections.singletonList(order),
                Collections.singletonMap(2L, voucher),
                Map.of()
        );

        assertThat(dtos.get(0).getVoucherImages()).isEqualTo("/imgs/icons/default-icon.png");
    }
}
