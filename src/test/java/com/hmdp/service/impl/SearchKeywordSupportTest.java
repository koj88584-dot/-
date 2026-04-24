package com.hmdp.service.impl;

import com.hmdp.dto.DeepSeekSearchDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeywordSupportTest {

    @Test
    void resolveSearchContextShouldInferTypeAndPrimaryKeywordFromAliases() {
        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword("附近咖啡 + 优惠券");

        SearchQueryContext context = SearchKeywordSupport.resolveSearchContext(dto);

        assertThat(context.originalKeyword).isEqualTo("附近咖啡 + 优惠券");
        assertThat(context.resolvedTypeId).isEqualTo(1L);
        assertThat(context.amapKeyword).isEqualTo("咖啡");
        assertThat(context.keywords).contains("咖啡");
        assertThat(context.keywords).doesNotContain("优惠券", "附近");
    }

    @Test
    void resolveSearchContextShouldFallbackToOriginalKeywordWhenNoUsefulTokenExists() {
        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword("火锅");

        SearchQueryContext context = SearchKeywordSupport.resolveSearchContext(dto);

        assertThat(context.originalKeyword).isEqualTo("火锅");
        assertThat(context.resolvedTypeId).isEqualTo(1L);
        assertThat(context.amapKeyword).isEqualTo("火锅");
        assertThat(context.keywords).contains("火锅");
    }
}
