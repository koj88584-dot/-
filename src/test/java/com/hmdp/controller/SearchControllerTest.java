package com.hmdp.controller;

import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private IDeepSeekSearchService deepSeekSearchService;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController();
        ReflectionTestUtils.setField(controller, "deepSeekSearchService", deepSeekSearchService);
    }

    @Test
    void searchShouldDelegateLegacyPayloadToUnifiedSearchService() {
        Result expected = Result.ok(new HashMap<>());
        when(deepSeekSearchService.search(org.mockito.ArgumentMatchers.any(DeepSeekSearchDTO.class))).thenReturn(expected);

        Map<String, Object> request = new HashMap<>();
        request.put("keyword", "奶茶");
        request.put("type", "shop");
        request.put("sortBy", "distance");
        request.put("current", 2);
        request.put("longitude", 120.12D);
        request.put("latitude", 30.28D);
        request.put("typeId", 1L);
        request.put("minPrice", 10L);
        request.put("maxPrice", 50L);

        Result result = controller.search(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<DeepSeekSearchDTO> captor = ArgumentCaptor.forClass(DeepSeekSearchDTO.class);
        verify(deepSeekSearchService).search(captor.capture());
        DeepSeekSearchDTO dto = captor.getValue();
        assertThat(dto.getKeyword()).isEqualTo("奶茶");
        assertThat(dto.getType()).isEqualTo("shop");
        assertThat(dto.getSortBy()).isEqualTo("distance");
        assertThat(dto.getCurrent()).isEqualTo(2);
        assertThat(dto.getLongitude()).isEqualTo(120.12D);
        assertThat(dto.getLatitude()).isEqualTo(30.28D);
        assertThat(dto.getTypeId()).isEqualTo(1L);
        assertThat(dto.getMinPrice()).isEqualTo(10L);
        assertThat(dto.getMaxPrice()).isEqualTo(50L);
    }

    @Test
    void historyAndHotEndpointsShouldDelegateToUnifiedSearchService() {
        Result hot = Result.ok("hot");
        Result history = Result.ok("history");
        Result recorded = Result.ok();
        Result cleared = Result.ok();
        when(deepSeekSearchService.getHotSearch()).thenReturn(hot);
        when(deepSeekSearchService.getSearchHistory()).thenReturn(history);
        when(deepSeekSearchService.recordSearchHistory("咖啡")).thenReturn(recorded);
        when(deepSeekSearchService.clearSearchHistory()).thenReturn(cleared);

        assertThat(controller.getHotSearches()).isSameAs(hot);
        assertThat(controller.getSearchHistory()).isSameAs(history);
        assertThat(controller.recordSearchHistory("咖啡")).isSameAs(recorded);
        assertThat(controller.clearSearchHistory()).isSameAs(cleared);
    }
}
