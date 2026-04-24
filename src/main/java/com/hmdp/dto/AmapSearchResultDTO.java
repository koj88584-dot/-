package com.hmdp.dto;

import com.hmdp.entity.Shop;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AmapSearchResultDTO {
    private List<Shop> shops = new ArrayList<>();
    private Long totalHits = 0L;
    private Boolean hasMore = false;
    private Boolean success = false;
    private Integer page = 1;
    private Integer pageSize = 0;

    public static AmapSearchResultDTO empty(Integer page, Integer pageSize) {
        AmapSearchResultDTO dto = new AmapSearchResultDTO();
        dto.setPage(page == null || page < 1 ? 1 : page);
        dto.setPageSize(pageSize == null || pageSize < 1 ? 0 : pageSize);
        dto.setSuccess(false);
        return dto;
    }
}
