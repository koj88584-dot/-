const SEARCH_CITY_LIST = [
  { code: "beijing", name: "北京", longitude: 116.4074, latitude: 39.9042, description: "北京市中心" },
  { code: "shanghai", name: "上海", longitude: 121.4737, latitude: 31.2304, description: "上海市中心" },
  { code: "guangzhou", name: "广州", longitude: 113.2644, latitude: 23.1291, description: "广州市中心" },
  { code: "shenzhen", name: "深圳", longitude: 114.0579, latitude: 22.5431, description: "深圳市中心" },
  { code: "hangzhou", name: "杭州", longitude: 120.1551, latitude: 30.2741, description: "杭州市中心" },
  { code: "nanjing", name: "南京", longitude: 118.7969, latitude: 32.0603, description: "南京市中心" },
  { code: "chengdu", name: "成都", longitude: 104.0668, latitude: 30.5728, description: "成都市中心" },
  { code: "wuhan", name: "武汉", longitude: 114.3054, latitude: 30.5931, description: "武汉市中心" },
  { code: "xian", name: "西安", longitude: 108.9398, latitude: 34.3416, description: "西安市中心" },
  { code: "changsha", name: "长沙", longitude: 112.9388, latitude: 28.2282, description: "长沙市中心" },
  { code: "chongqing", name: "重庆", longitude: 106.5516, latitude: 29.563, description: "重庆市中心" },
  { code: "suzhou", name: "苏州", longitude: 120.5853, latitude: 31.2989, description: "苏州市中心" },
  { code: "tianjin", name: "天津", longitude: 117.2009, latitude: 39.0842, description: "天津市中心" },
  { code: "qingdao", name: "青岛", longitude: 120.3826, latitude: 36.0671, description: "青岛市中心" },
  { code: "xiamen", name: "厦门", longitude: 118.0894, latitude: 24.4798, description: "厦门市中心" },
  { code: "kunming", name: "昆明", longitude: 102.8329, latitude: 24.8801, description: "昆明市中心" },
  { code: "zhengzhou", name: "郑州", longitude: 113.6253, latitude: 34.7466, description: "郑州市中心" },
  { code: "shenyang", name: "沈阳", longitude: 123.4315, latitude: 41.8057, description: "沈阳市中心" },
  { code: "jinan", name: "济南", longitude: 117.1205, latitude: 36.651, description: "济南市中心" },
  { code: "harbin", name: "哈尔滨", longitude: 126.534, latitude: 45.8038, description: "哈尔滨市中心" }
];

function buildSearchResultKey(item) {
  if (!item) {
    return "empty";
  }
  return String(item.id) + "-" + (item.title ? "blog" : "shop");
}

new Vue({
  el: "#app",
  data: {
    keyword: "",
    type: "all",
    sortBy: "relevance",
    current: 1,
    shops: [],
    blogs: [],
    total: 0,
    loading: false,
    isLoadingMore: false,
    hasMore: true,
    hasSearched: false,
    longitude: null,
    latitude: null,
    locationReady: false,
    assistantAutoRunPending: false,
    assistantCouponIntent: false,
    assistantCouponKeyword: "",
    assistantCouponTab: "available",
    hotSearches: [],
    searchHistory: [],
    showCitySelector: false,
    currentCity: { code: "locating", name: "定位中...", description: "" },
    cityProfile: util.readCurrentCityProfile(),
    cityProfiles: [],
    cityScenes: [],
    hotCities: SEARCH_CITY_LIST.slice(0, 10),
    allCities: SEARCH_CITY_LIST.slice(10)
  },
  computed: {
    cityEditionEnabled() {
      return util.isCityEditionEnabled(this.cityProfile, this.cityProfiles);
    },
    currentCityCode() {
      const code = this.currentCity && this.currentCity.code;
      if (code && code !== "locating" && code !== "real") {
        return code;
      }
      return (this.cityProfile && this.cityProfile.cityCode) || "";
    },
    filteredResults() {
      let results = [];
      if (this.type === "all" || this.type === "shop") {
        results = results.concat(this.shops.map(item => Object.assign({}, item, { type: "shop" })));
      }
      if (this.type === "all" || this.type === "blog") {
        results = results.concat(this.blogs.map(item => Object.assign({}, item, { type: "blog" })));
      }
      return results;
    }
  },
  mounted() {
    this.loadCityConfig().then(() => {
      this.loadHotSearches();
      this.loadSearchHistory();
      this.initLocation();
      this.handleEntryParams();
    });
    window.addEventListener("scroll", this.handleScroll);
  },
  beforeDestroy() {
    window.removeEventListener("scroll", this.handleScroll);
  },
  methods: {
    async loadCityConfig() {
      const payload = await util.fetchCityList();
      this.cityProfiles = payload.cities || util.fallbackCityProfiles;
      const hotProfiles = payload.hotCities && payload.hotCities.length ? payload.hotCities : this.cityProfiles.slice(0, 10);
      this.hotCities = hotProfiles.map(city => this.toSearchCity(city));
      this.allCities = this.cityProfiles
        .filter(city => !this.hotCities.some(hot => hot.code === (city.cityCode || city.code)))
        .map(city => this.toSearchCity(city));
      const urlCityCode = util.getUrlParam("cityCode");
      const profile = urlCityCode
        ? (util.findCityProfile({ cityCode: urlCityCode }, this.cityProfiles) || util.readCurrentCityProfile())
        : util.readCurrentCityProfile();
      this.applyCityProfile(profile, false);
      this.cityScenes = this.cityEditionEnabled ? await util.fetchCityScenes(this.cityProfile.cityCode) : [];
    },
    toSearchCity(city) {
      const normalized = util.normalizeCityProfile(city);
      return {
        code: normalized.cityCode,
        name: normalized.cityName,
        longitude: normalized.longitude,
        latitude: normalized.latitude,
        description: normalized.cityTagline || normalized.province,
        profile: normalized
      };
    },
    applyCityProfile(profile, persist = true) {
      const normalized = util.normalizeCityProfile(profile);
      this.cityProfile = normalized;
      util.applyCityTheme(normalized);
      this.currentCity = this.toSearchCity(normalized);
      this.longitude = normalized.longitude;
      this.latitude = normalized.latitude;
      if (persist) {
        util.saveCurrentCityProfile(normalized);
        localStorage.setItem("currentCityManual", "true");
      }
      this.applyCityHotSearches();
    },
    applyCityHotSearches() {
      const cityKeywords = this.cityProfile && Array.isArray(this.cityProfile.hotSearches)
        ? this.cityProfile.hotSearches
        : [];
      if (!this.hotSearches.length && cityKeywords.length) {
        this.hotSearches = cityKeywords.map((keyword, index) => ({ keyword, score: 100 - index }));
      }
    },
    goBack() {
      if (!this.hasSearched) {
        history.back();
        return;
      }
      this.hasSearched = false;
      this.keyword = "";
      this.current = 1;
      this.shops = [];
      this.blogs = [];
      this.total = 0;
      this.hasMore = true;
      this.assistantAutoRunPending = false;
      this.assistantCouponIntent = false;
      this.assistantCouponKeyword = "";
    },
    initLocation() {
      const urlCityCode = util.getUrlParam("cityCode");
      const manualCity = localStorage.getItem("currentCityManual") === "true";
      if (urlCityCode || manualCity) {
        this.locationReady = true;
        this.tryAutoRunSearch();
        return;
      }
      this.getLocation();
    },
    handleEntryParams() {
      const source = util.getUrlParam("source") || "";
      const keyword = util.getUrlParam("keyword");
      const type = util.getUrlParam("type");
      const sortBy = util.getUrlParam("sortBy");
      const autoRun = util.getUrlParam("autoRun");
      const couponIntent = util.getUrlParam("couponIntent");
      const couponTab = util.getUrlParam("couponTab");
      const hasEntryParams = Boolean(source || keyword || type || sortBy || autoRun);

      if (!hasEntryParams) {
        return;
      }

      if (keyword) {
        this.keyword = keyword;
      }
      if (type) {
        this.type = type;
      }
      if (sortBy) {
        this.sortBy = sortBy;
      }
      if (source === "assistant" && couponIntent === "true") {
        this.assistantCouponIntent = true;
        this.assistantCouponKeyword = this.keyword.trim();
      }
      if (source === "assistant" && couponTab) {
        this.assistantCouponTab = couponTab;
      }
      if (autoRun === "true" && this.keyword.trim()) {
        this.assistantAutoRunPending = true;
        this.tryAutoRunSearch();
      }
    },
    tryAutoRunSearch() {
      if (!this.assistantAutoRunPending || !this.locationReady || !this.keyword.trim()) {
        return;
      }
      this.assistantAutoRunPending = false;
      this.$nextTick(() => this.doSearch());
    },
    useRealLocation() {
      this.showCitySelector = false;
      this.getLocation();
      this.$message.success("正在获取您的位置...");
    },
    selectCity(city) {
      const profile = util.findCityProfile(city.profile || city, this.cityProfiles)
        || util.createGenericCityProfile(city.profile || city);
      this.applyCityProfile(profile, true);
      this.showCitySelector = false;
      this.locationReady = true;
      this.$message.success("已切换到" + city.name);
      util.fetchCityScenes(this.cityEditionEnabled ? this.cityProfile.cityCode : "").then(scenes => {
        this.cityScenes = scenes;
      });
      if (this.hasSearched) {
        this.doSearch();
      }
    },
    setType(type) {
      if (this.type === type) {
        return;
      }
      this.type = type;
      if (this.hasSearched) {
        this.doSearch();
      }
    },
    setSort(sortBy) {
      if (this.sortBy === sortBy) {
        return;
      }
      this.sortBy = sortBy;
      if (this.hasSearched) {
        this.doSearch();
      }
    },
    doSearch() {
      if (!this.keyword.trim()) {
        this.$message.warning("请输入搜索关键词");
        return;
      }
      let searchStr = this.keyword.trim();
      const cityName = this.currentCity && this.currentCity.name ? this.currentCity.name.replace(/市$/, '') : '';
      if (cityName && searchStr.startsWith(cityName) && searchStr.length > cityName.length) {
        searchStr = searchStr.substring(cityName.length).trim();
        this.keyword = searchStr;
      }
      
      this.loading = true;
      this.hasSearched = true;
      this.current = 1;
      this.hasMore = true;
      this.fetchSearch(1, true);
    },
    fetchSearch(pageNo, reset) {
      if (reset) {
        this.loading = true;
      } else {
        this.isLoadingMore = true;
      }

      const payload = {
        keyword: this.keyword.trim(),
        type: this.type,
        sortBy: this.sortBy,
        current: pageNo,
        cityCode: this.currentCityCode
      };

      if (this.longitude && this.latitude) {
        payload.longitude = this.longitude;
        payload.latitude = this.latitude;
      }

      axios.post("/search", payload)
        .then((result) => {
          const data = util.extractResultData(result);
          const nextShops = Array.isArray(data.shops) ? data.shops : [];
          const nextBlogs = Array.isArray(data.blogs) ? data.blogs : [];

          if (reset) {
            this.shops = nextShops;
            this.blogs = nextBlogs;
            this.current = Number(data.currentPage || 1);
            this.recordSearchHistory(this.keyword.trim());
            this.loadSearchHistory();
          } else {
            this.current = Number(data.currentPage || pageNo);
            this.shops = this.mergeItems(this.shops, nextShops);
            this.blogs = this.mergeItems(this.blogs, nextBlogs);
          }

          const compatibleTotal = Number(data.total || 0);
          const totalHits = Number(data.totalHits || 0);
          this.total = totalHits || compatibleTotal || (this.shops.length + this.blogs.length);
          this.hasMore = typeof data.hasMore === "boolean"
            ? data.hasMore
            : (nextShops.length > 0 || nextBlogs.length > 0);
          this.$nextTick(() => this.ensureViewportFilled());
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "搜索失败"));
        })
        .finally(() => {
          this.loading = false;
          this.isLoadingMore = false;
        });
    },
    loadMoreResults() {
      if (!this.hasSearched || this.loading || this.isLoadingMore || !this.hasMore) {
        return;
      }
      this.fetchSearch(this.current + 1, false);
    },
    mergeItems(existing, incoming) {
      return util.mergeUnique(existing, incoming, buildSearchResultKey);
    },
    isNearPageBottom() {
      return util.isNearPageBottom(140);
    },
    ensureViewportFilled() {
      if (!this.hasSearched || this.loading || this.isLoadingMore || !this.hasMore) {
        return;
      }
      if (this.isNearPageBottom()) {
        this.loadMoreResults();
      }
    },
    handleScroll() {
      if (this.isNearPageBottom()) {
        this.loadMoreResults();
      }
    },
    loadHotSearches() {
      axios.get("/search/hot")
        .then((result) => {
          var raw = util.extractResultData(result);
          // Filter out garbage entries (JSON objects, [object Object], too long)
          this.hotSearches = Array.isArray(raw) ? raw.filter(function(item) {
            var kw = item && item.keyword;
            if (!kw || typeof kw !== "string") return false;
            if (kw.length > 20 || kw.indexOf("{") === 0 || kw.indexOf("[") === 0) return false;
            if (kw === "[object Object]") return false;
            return true;
          }) : [];
          this.applyCityHotSearches();
        })
        .catch(() => this.applyCityHotSearches());
    },
    loadSearchHistory() {
      axios.get("/search/history")
        .then((result) => {
          this.searchHistory = util.extractResultData(result);
        })
        .catch(() => {});
    },
    recordSearchHistory(keyword) {
      axios.post("/search/history?keyword=" + encodeURIComponent(keyword))
        .catch(() => {});
    },
    clearHistory() {
      this.$confirm("确定要清空搜索历史吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        axios.delete("/search/history")
          .then(() => {
            this.searchHistory = [];
            this.$message.success("已清空搜索历史");
          })
          .catch(() => {
            this.$message.error("清空失败");
          });
      }).catch(() => {});
    },
    removeHistoryItem(keyword) {
      const index = this.searchHistory.indexOf(keyword);
      if (index > -1) {
        this.searchHistory.splice(index, 1);
      }
    },
    searchByKeyword(keyword) {
      this.keyword = keyword;
      this.doSearch();
    },
    openAssistantCoupons() {
      location.href = util.buildUrl("./vouchers.html", {
        source: "assistant",
        tab: this.assistantCouponTab || "available"
      });
    },
    getLocation() {
      util.resolveUserLocation({
        cityList: this.cityProfiles,
        allowIpFallback: true
      }).then(({ context, profile }) => {
        const location = context || {};
        this.applyCityProfile(profile || util.readCurrentCityProfile(), false);
        this.longitude = location.longitude || this.longitude;
        this.latitude = location.latitude || this.latitude;
        this.currentCity = {
          code: location.cityCode || this.currentCityCode || "real",
          name: location.city || (profile && profile.cityName) || "当前位置",
          longitude: this.longitude,
          latitude: this.latitude,
          description: location.district || location.formattedAddress || "基于定位"
        };
        this.locationReady = true;
        this.$message.success("已获取您的位置");
        this.tryAutoRunSearch();
      }).catch(() => {
        this.useDefaultLocation();
      });
    },
    resolveCityFromCoordinates(longitude, latitude) {
      return util.resolveLocationByCoordinates({
        longitude,
        latitude,
        source: "browser"
      }).then((context) => {
        const saved = util.saveLocationContext(context, this.cityProfiles);
        this.applyCityProfile(saved.profile, false);
      }).catch(() => {});
    },
    useDefaultLocation() {
      const defaultCity = util.getFallbackCityProfile("430100");
      this.applyCityProfile(defaultCity, false);
      this.locationReady = true;
      this.tryAutoRunSearch();
    },
    formatDistance(distance) {
      return util.formatDistance(distance);
    },
    getItemImage(item) {
      const raw = item && item.images
        ? String(item.images).split(",").find(image => image && image.trim())
        : "";
      return util.resolveImageUrl(raw, "/imgs/icons/default-icon.png");
    },
    handleImageError(event) {
      util.applyImageFallback(event, commonURL + "/imgs/icons/default-icon.png");
    },
    openDetail(item) {
      if (item.type === "blog" || item.title) {
        location.href = "../blog/blog-detail.html?id=" + item.id;
        return;
      }
      location.href = "../shop/shop-detail.html?id=" + item.id;
    }
  }
});
