package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchResultDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BrowseHistory;
import com.hmdp.entity.Favorites;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IBrowseHistoryService;
import com.hmdp.service.IFavoritesService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepSeekSearchServiceImplTest {

    @Mock
    private IShopService shopService;

    @Mock
    private IBlogService blogService;

    @Mock
    private IBrowseHistoryService browseHistoryService;

    @Mock
    private IFavoritesService favoritesService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private IAmapService amapService;

    @Mock
    private IShopSyncService shopSyncService;

    private DeepSeekSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeepSeekSearchServiceImpl();
        ReflectionTestUtils.setField(service, "shopService", shopService);
        ReflectionTestUtils.setField(service, "blogService", blogService);
        ReflectionTestUtils.setField(service, "browseHistoryService", browseHistoryService);
        ReflectionTestUtils.setField(service, "favoritesService", favoritesService);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "amapService", amapService);
        ReflectionTestUtils.setField(service, "shopSyncService", shopSyncService);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void getRecommendationsShouldUseBatchShopLookupInsteadOfLoopingGetById() {
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);

        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<BrowseHistory> browseQuery =
                mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<Favorites> favoritesQuery =
                mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        @SuppressWarnings("unchecked")
        LambdaQueryChainWrapper<Shop> shopQuery =
                mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);

        when(browseHistoryService.lambdaQuery()).thenReturn(browseQuery);
        when(favoritesService.lambdaQuery()).thenReturn(favoritesQuery);
        when(shopService.lambdaQuery()).thenReturn(shopQuery);

        List<BrowseHistory> histories = Arrays.asList(
                new BrowseHistory().setTargetId(101L).setType(1),
                new BrowseHistory().setTargetId(102L).setType(1)
        );
        List<Favorites> favorites = Arrays.asList(
                new Favorites().setTargetId(102L).setType(1),
                new Favorites().setTargetId(103L).setType(1)
        );
        when(browseQuery.list()).thenReturn(histories);
        when(favoritesQuery.list()).thenReturn(favorites);

        when(shopService.listByIds(argThat(ids ->
                ids.size() == 3 && ids.containsAll(Arrays.asList(101L, 102L, 103L)))))
                .thenReturn(Arrays.asList(
                        new Shop().setId(101L).setTypeId(1L),
                        new Shop().setId(102L).setTypeId(2L),
                        new Shop().setId(103L).setTypeId(1L)
                ));

        Page<Shop> page = new Page<>();
        page.setRecords(Collections.singletonList(new Shop().setId(104L).setTypeId(1L)));
        when(shopQuery.page(any(Page.class))).thenReturn(page);

        Result result = service.getRecommendations(1);

        assertThat(result.getSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Shop> records = (List<Shop>) result.getData();
        assertThat(records).extracting(Shop::getId).containsExactly(104L);
        verify(shopService).listByIds(argThat(ids ->
                ids.size() == 3 && ids.containsAll(Arrays.asList(101L, 102L, 103L))));
        verify(shopService, never()).getById(anyLong());
        verify(shopQuery).in(any(), argThat((Collection<Long> ids) ->
                ids.size() == 2 && ids.containsAll(Arrays.asList(1L, 2L))));
        verify(shopQuery).notIn(eq(true), any(), argThat((Collection<Long> ids) ->
                ids.size() == 3 && ids.containsAll(Arrays.asList(101L, 102L, 103L))));
    }

    @Test
    void searchShouldExposeCurrentCountTotalHitsAndHasMoreForRealtimeShopResults() {
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(any(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

        AmapSearchResultDTO amapResult = AmapSearchResultDTO.empty(1, 10);
        amapResult.setSuccess(true);
        amapResult.setShops(Collections.singletonList(
                new Shop()
                        .setId(9_000_000_021L)
                        .setAmapPoiId("B0FFTEST001")
                        .setName("附近咖啡馆")
                        .setAddress("天心区")
                        .setTypeId(1L)
        ));
        amapResult.setTotalHits(24L);
        amapResult.setHasMore(true);

        when(amapService.searchTextShopsWithMeta(any(), any(), any(), any(), eq(1), any()))
                .thenReturn(amapResult);
        when(shopSyncService.getOrSync(any(Shop.class))).thenAnswer(invocation -> {
            Shop source = invocation.getArgument(0);
            return new Shop()
                    .setId(101L)
                    .setAmapPoiId(source.getAmapPoiId())
                    .setName(source.getName())
                    .setAddress(source.getAddress())
                    .setTypeId(source.getTypeId())
                    .setScore(source.getScore())
                    .setComments(source.getComments())
                    .setImages(source.getImages())
                    .setX(source.getX())
                    .setY(source.getY());
        });

        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword("咖啡");
        dto.setType("shop");
        dto.setCurrent(1);
        dto.setLongitude(112.9388D);
        dto.setLatitude(28.2282D);

        Result result = service.search(dto);

        assertThat(result.getSuccess()).isTrue();
        SearchResultDTO payload = (SearchResultDTO) result.getData();
        @SuppressWarnings("unchecked")
        List<Shop> shops = (List<Shop>) payload.getShops();
        assertThat(payload.getCurrentCount()).isEqualTo((long) payload.getShops().size());
        assertThat(payload.getTotal()).isEqualTo(payload.getCurrentCount());
        assertThat(payload.getTotalHits()).isEqualTo(24L);
        assertThat(payload.getHasMore()).isTrue();
        assertThat(payload.getCurrentPage()).isEqualTo(1);
        assertThat(payload.getPageSize()).isEqualTo(10);
        assertThat(shops).extracting("id").containsExactly(101L);
        verify(shopService).count(any());
        verify(shopService).list(any());
    }

    @Test
    void searchShouldPreferAmapRealtimeResultsForAliasKeyword() {
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(any(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

        AmapSearchResultDTO amapResult = AmapSearchResultDTO.empty(1, 10);
        amapResult.setSuccess(true);
        amapResult.setShops(Arrays.asList(
                new Shop().setId(9_000_000_031L).setAmapPoiId("B0FFMILK001").setName("一点点(麓谷店)").setAddress("麓谷路").setTypeId(1L).setScore(47),
                new Shop().setId(9_000_000_032L).setAmapPoiId("B0FFMILK002").setName("茶颜悦色(天心店)").setAddress("岳麓区").setTypeId(1L).setScore(46)
        ));
        amapResult.setTotalHits(2L);
        amapResult.setHasMore(false);

        when(amapService.searchTextShopsWithMeta(any(), any(), any(), any(), eq(1), any()))
                .thenReturn(amapResult);
        when(shopSyncService.getOrSync(any(Shop.class))).thenAnswer(invocation -> {
            Shop source = invocation.getArgument(0);
            return new Shop()
                    .setId(source.getAmapPoiId().endsWith("1") ? 201L : 202L)
                    .setAmapPoiId(source.getAmapPoiId())
                    .setName(source.getName())
                    .setAddress(source.getAddress())
                    .setTypeId(source.getTypeId())
                    .setScore(source.getScore())
                    .setComments(source.getComments())
                    .setImages(source.getImages());
        });

        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword("奶茶");
        dto.setType("shop");
        dto.setCurrent(1);
        dto.setLongitude(112.9388D);
        dto.setLatitude(28.2282D);

        Result result = service.search(dto);

        assertThat(result.getSuccess()).isTrue();
        SearchResultDTO payload = (SearchResultDTO) result.getData();
        @SuppressWarnings("unchecked")
        List<Shop> shops = (List<Shop>) payload.getShops();
        assertThat(shops).extracting("id").containsExactly(201L, 202L);
        assertThat(shops).extracting("name").containsExactly("一点点(麓谷店)", "茶颜悦色(天心店)");
        assertThat(payload.getTotalHits()).isEqualTo(2L);
        assertThat(payload.getHasMore()).isFalse();
        verify(shopService).count(any());
        verify(shopService).list(any());
    }

    @Test
    void searchShouldFallbackToLocalMirrorWhenAmapFails() {
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(any(), anyLong(), anyLong())).thenReturn(Collections.emptySet());

        AmapSearchResultDTO amapResult = AmapSearchResultDTO.empty(1, 10);
        amapResult.setSuccess(false);
        when(amapService.searchTextShopsWithMeta(any(), any(), any(), any(), eq(1), any()))
                .thenReturn(amapResult);

        when(shopService.count(any())).thenReturn(2L);
        when(shopService.list(any())).thenReturn(Arrays.asList(
                new Shop().setId(301L).setName("本地奶茶标题店").setAddress("测试路").setTypeId(1L),
                new Shop().setId(302L).setName("本地饮品店").setAddress("开发路").setTypeId(1L)
        ));

        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword("奶茶");
        dto.setType("shop");
        dto.setCurrent(1);
        dto.setLongitude(112.9388D);
        dto.setLatitude(28.2282D);

        Result result = service.search(dto);

        assertThat(result.getSuccess()).isTrue();
        SearchResultDTO payload = (SearchResultDTO) result.getData();
        @SuppressWarnings("unchecked")
        List<Shop> shops = (List<Shop>) payload.getShops();
        assertThat(shops).extracting("id").containsExactly(301L, 302L);
        assertThat(payload.getTotalHits()).isEqualTo(2L);
        verify(shopService).count(any());
        verify(shopService).list(any());
    }
}
