const NEARBY_CACHE_KEY = 'hmdp_nearby_shops_cache';
    const NEARBY_CACHE_TTL = 10 * 60 * 1000;
    const LOCATION_CHANGE_THRESHOLD = 200;
    const CITY_NAME_MAP = {
      '鍖椾含': '北京',
      '涓婃捣': '上海',
      '骞垮窞': '广州',
      '娣卞湷': '深圳',
      '鏉窞': '杭州',
      '鍗椾含': '南京',
      '鎴愰兘': '成都',
      '姝︽眽': '武汉',
      '瑗垮畨': '西安',
      '閲嶅簡': '重庆',
      '闀挎矙': '长沙',
      '澶╂触': '天津',
      '瀹氫綅涓?..': '定位中...'
    };

    new Vue({
      el: '#app',
      data: {
        currentCity: '长沙',
        cityProfile: util.getFallbackCityProfile('430100'),
        cityOverviews: [],
        hotCityProfiles: [],
        cityScenes: [],
        homeTab: 'city',
        cityBrandClaim: '懂这座城，才知道今天去哪',
        searchKeyword: '',
        isSearchFocused: false,
        isScrolled: false,
        isLoading: true,
        showCityDialog: false,  // 鏄剧ず鍩庡競閫夋嫨瀵硅瘽妗?
        citySearchKeyword: '',  // 鍩庡競鎼滅储鍏抽敭璇?
        hotCities: ['北京', '上海', '广州', '深圳', '杭州', '南京', '成都', '武汉', '西安', '重庆', '长沙', '天津'],
        cityList: [],
        categories: [
          { id: 1, name: '美食', icon: '🍜', class: 'cat-food' },
          { id: 2, name: 'KTV', icon: '🎤', class: 'cat-ktv' },
          { id: 3, name: '丽人·美发', icon: '💇', class: 'cat-beauty' },
          { id: 4, name: '健身运动', icon: '🏃', class: 'cat-fitness' },
          { id: 5, name: '按摩·足疗', icon: '💆', class: 'cat-massage' },
          { id: 6, name: '美容SPA', icon: '✨', class: 'cat-spa' },
          { id: 7, name: '亲子游乐', icon: '👶', class: 'cat-kids' },
          { id: 8, name: '酒吧', icon: '🍺', class: 'cat-bar' },
          { id: 9, name: '轰趴馆', icon: '🎉', class: 'cat-party' },
          { id: 10, name: '美睫·美甲', icon: '💅', class: 'cat-nail' },
        ],
        notes: [],  // 鎺ㄨ崘绗旇锛屼粠鍚庣鑾峰彇
        nearbyShops: [],
        currentLocation: null,  // 褰撳墠鍧愭爣
        locationMeta: { province: '', city: '', district: '', adcode: '', formattedAddress: '' },
        noteFallbackImage: '/imgs/blogs/blog1.jpg',
        avatarFallbackImage: '/imgs/icons/default-icon.png',
        isLocating: false,       // 鏄惁姝ｅ湪瀹氫綅
        nearbyCurrent: 1,
        nearbyHasMore: true,
        nearbyLoadingMore: false
      },
      computed: {
        cityEditionEnabled() {
          return util.isCityEditionEnabled(this.cityProfile, this.cityOverviews);
        },
        homeTabItems() {
          return [
            { key: 'follow', label: '关注' },
            { key: 'city', label: this.currentCity && this.currentCity !== '定位中...' ? this.currentCity : '定位中' },
            { key: 'hot', label: '热点' }
          ];
        },
        currentCityCode() {
          return (this.currentLocation && this.currentLocation.cityCode)
            || (this.locationMeta && this.locationMeta.cityCode)
            || (this.cityProfile && this.cityProfile.cityCode)
            || "";
        },
        cityHeroTags() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          const tags = this.cityProfile && Array.isArray(this.cityProfile.cultureTags)
            ? this.cityProfile.cultureTags
            : [];
          return tags.slice(0, 4);
        },
        cityHotSearches() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          const searches = this.cityProfile && Array.isArray(this.cityProfile.hotSearches)
            ? this.cityProfile.hotSearches
            : [];
          return searches.slice(0, 4);
        },
        cityFeaturedRoutes() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          const routes = this.cityProfile && Array.isArray(this.cityProfile.featuredRoutes)
            ? this.cityProfile.featuredRoutes
            : [];
          return routes.slice(0, 3);
        },
        cityFeaturedDistricts() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          const districts = this.cityProfile && Array.isArray(this.cityProfile.featuredDistricts)
            ? this.cityProfile.featuredDistricts
            : [];
          return districts.slice(0, 4);
        },
        cityPrimaryCategories() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          const categories = this.cityProfile && Array.isArray(this.cityProfile.primaryCategories)
            ? this.cityProfile.primaryCategories
            : [];
          return categories.slice(0, 4);
        },
        citySceneCards() {
          if (!this.cityEditionEnabled) {
            return [];
          }
          if (Array.isArray(this.cityScenes) && this.cityScenes.length) {
            return this.cityScenes.slice(0, 4);
          }
          const scenes = this.cityProfile && Array.isArray(this.cityProfile.defaultScenes)
            ? this.cityProfile.defaultScenes
            : [];
          return scenes.slice(0, 4).map((name, index) => ({
            sceneId: 'fallback-' + index,
            sceneName: name,
            sceneTitle: name,
            decisionHint: '按' + this.currentCity + '的消费节奏推荐',
            keyword: this.currentCity + name,
            prompt: '我在' + this.currentCity + '，想安排' + name + '，给我一个靠谱选择'
          }));
        },
        citySelectorLabel() {
          return this.locationMeta.district || this.currentCity || '定位中...';
        },
        locationDisplay() {
          const parts = [this.locationMeta.province, this.locationMeta.city, this.locationMeta.district]
            .map(item => this.normalizeCityName(item))
            .filter(Boolean);
          if (parts.length) {
            return Array.from(new Set(parts)).join(' ');
          }
          return this.currentCity || '定位中...';
        },
        categorySectionTitle() {
          return this.cityEditionEnabled ? '本城特色榜单' : '分类导航';
        },
        categorySectionMeta() {
          return this.cityEditionEnabled
            ? (this.cityPrimaryCategories.join(' · ') || '城市品类')
            : '按原分类查找附近好店';
        },
        displayNotes() {
          return this.notes;
        },
        noteSectionTitle() {
          return '推荐笔记';
        },
        cityHotEvents() {
          const shops = (this.nearbyShops || []).slice(0, 3).map((shop, index) => ({
            title: (shop.area || this.currentCity) + ' · ' + (shop.name || '热门店铺'),
            subtitle: (shop.score ? (Number(shop.score) / 10).toFixed(1) + '星 · ' : '') + (shop.distanceText || '本城热度上升'),
            image: shop.image,
            keyword: shop.name || shop.area || this.currentCity,
            rank: index + 1
          }));
          const searches = (this.cityHotSearches || []).map((keyword, index) => ({
            title: keyword,
            subtitle: this.currentCity + '正在被更多人搜索',
            image: this.noteFallbackImage,
            keyword,
            rank: shops.length + index + 1
          }));
          return shops.concat(searches).slice(0, 6);
        }
      },
      mounted() {
        this.loadCityConfig().finally(() => {
          // 浼樺厛灏濊瘯鑾峰彇褰撳墠瀹氫綅
          this.autoLocateAndLoad();
        });
        // 鍔犺浇鎺ㄨ崘绗旇
        this.loadHotNotes();
        this.handleScroll();
        window.addEventListener('scroll', this.handleScroll);
      },
      beforeDestroy() {
        window.removeEventListener('scroll', this.handleScroll);
      },

      methods: {
        switchHomeTab(tab) {
          if (tab === 'follow') {
            window.location.href = util.buildUrl('misc/follow-feed.html', {
              cityCode: this.currentCityCode,
              city: this.currentCity
            });
            return;
          }
          if (tab === 'hot') {
            window.location.href = util.buildUrl('misc/city-hot.html', {
              cityCode: this.currentCityCode,
              city: this.currentCity
            });
            return;
          }
          this.homeTab = 'city';
          window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        scanCode() {
          const bridge = window.HmdpNative || window.HMDPNative || window.Android;
          if (bridge && typeof bridge.scanQRCode === 'function') {
            bridge.scanQRCode();
            return;
          }
          this.$message.info('当前 Web 环境未接入扫码，App 原生层接入后会直接打开相机');
        },
        async loadCityConfig() {
          const payload = await util.fetchCityList();
          this.cityOverviews = payload.cities || [];
          this.hotCityProfiles = payload.hotCities || [];
          this.hotCities = this.hotCityProfiles.length
            ? this.hotCityProfiles.map(city => city.cityName || city.name)
            : this.hotCities;
          this.cityBrandClaim = payload.brandClaim || this.cityBrandClaim;
          const savedProfile = util.readCurrentCityProfile();
          this.setCityProfile(savedProfile, false);
          this.cityScenes = this.cityEditionEnabled ? await util.fetchCityScenes(this.cityProfile.cityCode) : [];
        },
        async loadCityScenes() {
          this.cityScenes = this.cityEditionEnabled ? await util.fetchCityScenes(this.cityProfile.cityCode) : [];
        },
        setCityProfile(profile, persist = true, extra = {}) {
          const normalized = util.normalizeCityProfile(profile);
          this.cityProfile = normalized;
          this.currentCity = normalized.cityName;
          util.applyCityTheme(normalized);
          if (persist) {
            util.saveCurrentCityProfile(normalized, extra);
          }
          return normalized;
        },
        isKnownCityProfile(profile, cityName, adcode) {
          if (!profile) {
            return false;
          }
          const normalizedName = this.normalizeCityName(cityName || '');
          const code = String(adcode || '');
          return (normalizedName && profile.cityName === normalizedName)
            || (code && profile.cityCode && profile.cityCode.indexOf(code.substring(0, 4)) === 0);
        },
        readNearbyCache() {
          const parsed = util.readStorageJSON(NEARBY_CACHE_KEY);
          if (!parsed || !parsed.location || !Array.isArray(parsed.shops)) {
            return null;
          }
          if (Date.now() - (parsed.ts || 0) > NEARBY_CACHE_TTL) {
            return null;
          }
          if (parsed.cityCode && parsed.cityCode !== this.currentCityCode) {
            return null;
          }
          return parsed;
        },
        restoreNearbyCache() {
          const cached = this.readNearbyCache();
          if (!cached) {
            return false;
          }
          this.nearbyShops = cached.shops;
          this.currentLocation = cached.location;
          this.nearbyCurrent = cached.current || cached.page || 1;
          this.nearbyHasMore = cached.hasMore !== false;
          if (cached.locationMeta) {
            this.locationMeta = this.normalizeLocationMeta(cached.locationMeta);
          } else if (cached.location) {
            this.locationMeta = this.normalizeLocationMeta(cached.location);
          }
          if (cached.city) {
            this.currentCity = this.normalizeCityName(cached.city);
          }
          if (cached.cityProfile) {
            this.setCityProfile(cached.cityProfile, false);
          }
          this.isLoading = false;
          return true;
        },
        normalizeCityName(cityName) {
          const raw = String(cityName || '').trim();
          return (CITY_NAME_MAP[raw] || raw).replace(/市$/, '');
        },
        normalizeLocationMeta(meta) {
          const source = meta || {};
          const city = this.normalizeCityName(source.city || source.name || this.currentCity || '');
          return {
            province: this.normalizeCityName(source.province || ''),
            city,
            district: this.normalizeCityName(source.district || ''),
            adcode: source.adcode || '',
            cityCode: source.cityCode || (source.adcode && String(source.adcode).length >= 6 ? String(source.adcode).substring(0, 4) + '00' : ''),
            formattedAddress: source.formattedAddress || source.address || '',
            x: source.x || source.longitude || source.lng || '',
            y: source.y || source.latitude || source.lat || ''
          };
        },
        applyLocationMeta(meta) {
          const normalized = this.normalizeLocationMeta(meta);
          this.locationMeta = normalized;
          this.currentCity = normalized.city || normalized.province || this.currentCity || '未知城市';
          const candidate = util.findCityProfile({
            cityCode: normalized.adcode,
            adcode: normalized.adcode,
            city: normalized.city || this.currentCity,
            cityName: normalized.city || this.currentCity,
            province: normalized.province
          }, this.cityOverviews);
          const profile = this.isKnownCityProfile(candidate, normalized.city || this.currentCity, normalized.adcode)
            ? candidate
            : util.createGenericCityProfile({
              cityName: this.currentCity,
              city: normalized.city || this.currentCity,
              province: normalized.province,
              district: normalized.district,
              adcode: normalized.adcode,
              x: normalized.x,
              y: normalized.y
            });
          this.setCityProfile(profile, false);
          util.saveCurrentCityProfile(profile, {
            name: this.currentCity,
            province: normalized.province,
            city: normalized.city,
            district: normalized.district,
            adcode: normalized.adcode,
            cityCode: normalized.cityCode,
            formattedAddress: normalized.formattedAddress,
            x: normalized.x,
            y: normalized.y,
            cityEditionEnabled: !!candidate
          });
          this.loadCityScenes();
        },
        getShopCoordinate(shop, axis) {
          return util.getShopCoordinate(shop, axis);
        },
        saveNearbyCache() {
          if (!this.currentLocation || !Array.isArray(this.nearbyShops)) {
            return;
          }
          const payload = {
            ts: Date.now(),
            location: this.currentLocation,
            locationMeta: this.locationMeta,
            city: this.currentCity,
            current: this.nearbyCurrent,
            hasMore: this.nearbyHasMore,
            cityCode: this.currentCityCode,
            cityProfile: this.cityProfile,
            shops: this.nearbyShops
          };
          util.writeStorageJSON(NEARBY_CACHE_KEY, payload);
        },
        isLocationChanged(newLocation, baseLocation) {
          if (!newLocation || !baseLocation) {
            return true;
          }
          const distance = this.calculateDistance(newLocation.x, newLocation.y, baseLocation.x, baseLocation.y);
          return distance > LOCATION_CHANGE_THRESHOLD;
        },
        // 自动定位并加载
        async autoLocateAndLoad() {
          const savedCity = localStorage.getItem('currentCity');
          const parsedCity = util.readStorageJSON('currentCity', savedCity);
          const manualCity = localStorage.getItem('currentCityManual') === 'true';
          if (parsedCity && parsedCity.name) {
            this.applyLocationMeta(parsedCity);
          } else if (savedCity) {
            this.currentCity = this.normalizeCityName(savedCity);
            this.applyLocationMeta({ city: this.currentCity });
          } else {
            this.currentCity = '定位中...';
          }

          const hasCache = this.restoreNearbyCache();
          if (manualCity) {
            const profile = util.readCurrentCityProfile();
            this.setCityProfile(profile, true);
            this.currentLocation = {
              x: profile.longitude,
              y: profile.latitude,
              province: profile.province,
              city: profile.cityName,
              district: '',
              adcode: profile.cityCode
            };
            this.applyLocationMeta(this.currentLocation);
            if (!hasCache) {
              this.loadNearbyShops(true);
            }
            return;
          }

          this.getCurrentLocation(!hasCache);
          return;
        },

        // 澶勭悊婊氬姩
        handleScroll() {
          this.isScrolled = window.scrollY > 10;
          if (util.isNearPageBottom(160)) {
            this.loadMoreNearbyShops();
          }
        },

        runNearbyRefresh() {
          util.removeStorageItems(NEARBY_CACHE_KEY);
          this.nearbyCurrent = 1;
          this.nearbyHasMore = true;
          this.nearbyLoadingMore = false;
          this.nearbyShops = [];
          this.getCurrentLocation(true);
        },

        // 鍔犺浇鐑棬绗旇
        async loadHotNotes() {
          try {
            const res = await axios.get('/blog/hot', {
              params: { current: 1 }
            });
            // common.js 鎷︽埅鍣ㄨ繑鍥炵殑鏄?Result 瀵硅薄锛屾暟鎹湪 res.data 涓?
            const result = res.data;
            if (result && Array.isArray(result)) {
              this.notes = result.map(blog => ({
                id: blog.id,
                title: blog.title,
                image: this.extractFirstImage(blog.images),
                authorAvatar: blog.icon || '/imgs/icons/default-icon.png',
                authorName: blog.name || '匿名用户',
                likes: blog.liked || 0
              }));
            }
          } catch (e) {
            console.error('加载推荐笔记失败:', e);
            this.notes = [];
          }
        },

        // 鑾峰彇褰撳墠浣嶇疆
        getCurrentLocation(forceRefresh = false) {
          this.isLocating = true;
          util.resolveUserLocation({
            force: forceRefresh,
            cityList: this.cityOverviews,
            allowIpFallback: true
          }).then(({ context, profile }) => {
            const location = context || {};
            const nextProfile = profile || util.readCurrentCityProfile();
            this.setCityProfile(nextProfile, false);
            this.currentCity = location.city || nextProfile.cityName || this.currentCity;
            this.locationMeta = this.normalizeLocationMeta({
              province: location.province || nextProfile.province,
              city: location.city || nextProfile.cityName,
              district: location.district || '',
              adcode: location.adcode || location.cityCode || nextProfile.cityCode,
              formattedAddress: location.formattedAddress || ''
            });
            this.currentLocation = {
              x: location.longitude || nextProfile.longitude,
              y: location.latitude || nextProfile.latitude,
              accuracy: location.accuracy || 1000,
              province: this.locationMeta.province,
              city: this.locationMeta.city,
              district: this.locationMeta.district,
              adcode: this.locationMeta.adcode,
              cityCode: location.cityCode || '',
              formattedAddress: this.locationMeta.formattedAddress
            };
            this.loadCityScenes();
            this.loadNearbyShops(forceRefresh);
          }).catch((error) => {
            console.error('定位失败:', error);
            this.$message.warning('定位失败，先使用默认位置');
            const fallback = util.getFallbackCityProfile('430100');
            this.setCityProfile(fallback, false);
            this.currentLocation = {
              x: fallback.longitude,
              y: fallback.latitude,
              province: fallback.province,
              city: fallback.cityName,
              district: '',
              adcode: fallback.cityCode
            };
            this.locationMeta = this.normalizeLocationMeta(this.currentLocation);
            this.loadNearbyShops(forceRefresh);
          }).finally(() => {
            this.isLocating = false;
          });
        },

        // 鏍规嵁鍧愭爣鑾峰彇鍩庡競鍚嶇О
        async getCityByLocation(lng, lat) {
          try {
            const data = await util.resolveLocationByCoordinates({
              longitude: lng,
              latitude: lat,
              source: 'browser'
            });
            if (data && (data.city || data.province || data.district)) {
              this.applyLocationMeta(Object.assign({}, data, { x: lng, y: lat }));
              if (this.currentLocation) {
                this.currentLocation = {
                  ...this.currentLocation,
                  province: this.locationMeta.province,
                  city: this.locationMeta.city,
                  district: this.locationMeta.district,
                  adcode: this.locationMeta.adcode,
                  formattedAddress: this.locationMeta.formattedAddress
                };
                util.writeStorageJSON('userLocation', this.currentLocation);
              }
            }
          } catch (e) {
            console.error('获取城市名称失败:', e);
          }
        },

        // 鍒锋柊闄勮繎搴楅摵锛堥噸鏂拌幏鍙栦綅缃級
        refreshNearbyShops() {
          this.$message.info('正在刷新附近好店...');
          this.runNearbyRefresh();
        },

        async fetchNearbyShopPage(page) {
          if (!this.currentLocation) {
            return [];
          }
          const { x, y } = this.currentLocation;
          const cityCode = this.currentCityCode;
          const requests = Array.from({ length: 10 }, (_, index) => index + 1).map(typeId =>
            axios.get('/shop/of/type', {
              params: { typeId, current: page, x, y, cityCode }
            }).catch(() => ({ data: [] }))
          );

          const responses = await Promise.all(requests);
          let allShops = [];

          responses.forEach(res => {
            const result = res.data;
            if (result && Array.isArray(result)) {
              const shopsWithDistance = result.map(shop => {
                const shopX = this.getShopCoordinate(shop, 'x');
                const shopY = this.getShopCoordinate(shop, 'y');
                shop.x = shopX;
                shop.y = shopY;
                shop.longitude = shopX;
                shop.latitude = shopY;
                if (shopX && shopY) {
                  shop.distance = this.calculateDistance(x, y, shopX, shopY);
                }
                return shop;
              });
              allShops = allShops.concat(shopsWithDistance);
            }
          });

          const uniqueShopMap = new Map();
          allShops.forEach(shop => {
            const key = shop.id || `${shop.name || ''}|${shop.address || ''}`;
            const existing = uniqueShopMap.get(key);
            if (!existing || (shop.distance || 999999) < (existing.distance || 999999)) {
              uniqueShopMap.set(key, shop);
            }
          });

          return Array.from(uniqueShopMap.values())
            .sort((a, b) => (a.distance || 999999) - (b.distance || 999999))
            .slice(0, 20)
            .map(shop => ({
              ...shop,
              x: this.getShopCoordinate(shop, 'x'),
              y: this.getShopCoordinate(shop, 'y'),
              longitude: this.getShopCoordinate(shop, 'x'),
              latitude: this.getShopCoordinate(shop, 'y'),
              score: shop.score ? (shop.score / 10).toFixed(1) : '4.0',
              image: this.resolveImageUrl(shop.images ? shop.images.split(',')[0] : ''),
              tags: this.getShopTags(shop.typeId),
              distanceText: shop.distance ? this.formatDistance(shop.distance) : ''
            }));
        },

        appendUniqueNearbyShops(newShops) {
          const knownKeys = new Set(this.nearbyShops.map(shop => shop.id || `${shop.name || ''}|${shop.address || ''}`));
          return newShops.filter(shop => {
            const key = shop.id || `${shop.name || ''}|${shop.address || ''}`;
            if (knownKeys.has(key)) {
              return false;
            }
            knownKeys.add(key);
            return true;
          });
        },

        ensureNearbyViewportFilled() {
          if (this.isLoading || this.nearbyLoadingMore || !this.nearbyHasMore) {
            return;
          }
          this.$nextTick(() => {
            if (util.isNearPageBottom(160)) {
              this.loadMoreNearbyShops();
            }
          });
        },

        // 鍔犺浇闄勮繎搴楅摵
        async loadNearbyShops(forceRefresh = false) {
          if (!forceRefresh) {
            const cached = this.readNearbyCache();
            if (cached && (!this.currentLocation || !this.isLocationChanged(this.currentLocation, cached.location))) {
              this.nearbyShops = cached.shops;
              this.currentLocation = cached.location;
              this.nearbyCurrent = cached.current || cached.page || 1;
              this.nearbyHasMore = cached.hasMore !== false;
              this.isLoading = false;
              this.ensureNearbyViewportFilled();
              return;
            }
          } else {
            this.nearbyCurrent = 1;
            this.nearbyHasMore = true;
            this.nearbyLoadingMore = false;
          }

          this.isLoading = true;

          try {
            // 濡傛灉娌℃湁鍧愭爣锛屽厛鑾峰彇
            if (!this.currentLocation) {
              this.getCurrentLocation(forceRefresh);
              return;
            }

            const shops = await this.fetchNearbyShopPage(1);
            this.nearbyCurrent = 1;
            this.nearbyShops = shops;
            this.nearbyHasMore = shops.length > 0;
            if (shops.length > 0) {
              this.saveNearbyCache();
            } else {
              this.nearbyShops = [];
              this.nearbyHasMore = false;
            }
          } catch (e) {
            console.error('加载店铺失败:', e);
            this.nearbyShops = [];
            this.nearbyHasMore = false;
          } finally {
            this.isLoading = false;
            this.ensureNearbyViewportFilled();
          }
        },

        async loadMoreNearbyShops() {
          if (this.isLoading || this.nearbyLoadingMore || !this.nearbyHasMore || !this.currentLocation) {
            return;
          }
          this.nearbyLoadingMore = true;
          const nextCurrent = (this.nearbyCurrent || 1) + 1;
          try {
            const shops = await this.fetchNearbyShopPage(nextCurrent);
            const appended = this.appendUniqueNearbyShops(shops);
            if (!appended.length) {
              this.nearbyHasMore = false;
              return;
            }
            this.nearbyCurrent = nextCurrent;
            this.nearbyShops = this.nearbyShops.concat(appended);
            this.saveNearbyCache();
          } catch (e) {
            console.error('加载更多附近好店失败:', e);
            this.$message.error('加载更多失败，请稍后重试');
          } finally {
            this.nearbyLoadingMore = false;
            this.ensureNearbyViewportFilled();
          }
        },

        // 璁＄畻涓ょ偣涔嬮棿鐨勮窛绂伙紙绫筹級
        calculateDistance(lng1, lat1, lng2, lat2) {
          const R = 6371000; // 鍦扮悆鍗婂緞锛堢背锛?
          const dLat = (lat2 - lat1) * Math.PI / 180;
          const dLng = (lng2 - lng1) * Math.PI / 180;
          const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                    Math.sin(dLng / 2) * Math.sin(dLng / 2);
          const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
          return Math.round(R * c);
        },

        // 鏍煎紡鍖栬窛绂绘樉绀?
        formatDistance(distance) {
          if (distance < 1000) {
            return distance + 'm';
          } else {
            return (distance / 1000).toFixed(1) + 'km';
          }
        },

        // 鏍规嵁搴楅摵绫诲瀷鑾峰彇鏍囩
        getShopTags(typeId) {
          const tagMap = {
            1: ['口味赞', '环境好', '服务热情'],
            2: ['音响好', '曲库全', '装修棒'],
            3: ['技术好', '服务佳', '环境舒适'],
            4: ['设备新', '教练专业', '环境好'],
            5: ['手法好', '环境舒适', '服务热情'],
            6: ['产品好', '技术专业', '环境优雅'],
            7: ['好玩', '安全', '适合亲子'],
            8: ['氛围好', '酒水全', '音乐棒'],
            9: ['场地大', '设施全', '适合聚会'],
            10: ['技术好', '产品优质', '服务细致']
          };
          const tags = tagMap[typeId] || ['推荐'];
          return tags.slice(0, Math.floor(Math.random() * 3) + 1);
        },

        // 澶勭悊鍩庡競涓嬫媺鑿滃崟鍛戒护
        handleCityCommand(command) {
          if (command === 'current') {
            // 閲嶆柊瀹氫綅锛堝己鍒跺埛鏂帮級
            this.$message.info('正在重新定位...');
            localStorage.removeItem('currentCityManual');
            // 娓呴櫎淇濆瓨鐨勪綅缃紝寮哄埗閲嶆柊鑾峰彇
            util.removeStorageItems(['userLocation', NEARBY_CACHE_KEY]);
            this.nearbyShops = [];
            this.nearbyCurrent = 1;
            this.nearbyHasMore = true;
            this.nearbyLoadingMore = false;
            this.getCurrentLocation(true);
          } else if (command === 'more') {
            // 鎵撳紑鏇村鍩庡競瀵硅瘽妗?
            this.selectCity();
          } else {
            // 閫夋嫨鐑棬鍩庡競
            this.selectCityByName(command);
          }
        },

        // 閫夋嫨鍩庡競锛堟墦寮€瀹屾暣瀵硅瘽妗嗭級
        selectCity() {
          this.showCityDialog = true;
          this.citySearchKeyword = '';
          this.cityList = [];
        },

        // 浣跨敤褰撳墠瀹氫綅
        useCurrentLocation() {
          this.showCityDialog = false;
          localStorage.removeItem('currentCityManual');
          util.removeStorageItems(['userLocation', NEARBY_CACHE_KEY]);
          this.nearbyShops = [];
          this.nearbyCurrent = 1;
          this.nearbyHasMore = true;
          this.nearbyLoadingMore = false;
          this.$message.info('正在重新定位...');
          this.getCurrentLocation(true);
        },

        // 鏍规嵁鍩庡競鍚嶉€夋嫨锛堜娇鐢ㄩ璁惧潗鏍囷級
        selectCityByName(cityName) {
          const normalizedCityName = this.normalizeCityName(cityName);
          const profile = util.findCityProfile(normalizedCityName, this.cityOverviews)
            || util.createGenericCityProfile({ cityName: normalizedCityName });
          this.setCityProfile(profile, true);
          this.currentLocation = {
            x: profile.longitude,
            y: profile.latitude,
            province: profile.province || '',
            city: profile.cityName,
            district: '',
            adcode: profile.cityCode
          };
          this.locationMeta = this.normalizeLocationMeta(this.currentLocation);
          util.writeStorageJSON('userLocation', this.currentLocation);
          localStorage.setItem('currentCityManual', 'true');
          util.removeStorageItems(NEARBY_CACHE_KEY);
          this.nearbyShops = [];
          this.nearbyCurrent = 1;
          this.nearbyHasMore = true;
          this.nearbyLoadingMore = false;
          this.showCityDialog = false;
          this.$message.success(this.cityEditionEnabled ? `已切换到${profile.cityName}版本` : `已切换到${profile.cityName}`);
          this.loadCityScenes();
          this.loadNearbyShops(true);
        },

        // 鏍规嵁鎼滅储缁撴灉閫夋嫨浣嶇疆
        selectCityByLocation(city) {
          this.currentLocation = {
            x: city.x,
            y: city.y,
            province: city.province || '',
            city: city.city || city.name,
            district: city.district || '',
            adcode: city.adcode || '',
            formattedAddress: city.address || ''
          };
          this.applyLocationMeta(this.currentLocation);

          // 淇濆瓨鍒版湰鍦板瓨鍌?
          util.writeStorageJSON('userLocation', this.currentLocation);
          localStorage.setItem('currentCityManual', 'true');
          util.removeStorageItems(NEARBY_CACHE_KEY);
          this.nearbyShops = [];
          this.nearbyCurrent = 1;
          this.nearbyHasMore = true;
          this.nearbyLoadingMore = false;

          this.showCityDialog = false;
          this.$message.success(this.cityEditionEnabled ? `已切换到${this.currentCity}城市馆` : `已切换到${this.currentCity}`);

          // 鍒锋柊闄勮繎搴楅摵
          this.loadNearbyShops(true);
        },

        // 鎼滅储鍩庡競/鍦扮偣
                async searchCity() {
          if (!this.citySearchKeyword.trim()) {
            this.cityList = [];
            return;
          }

          try {
            const keyword = this.citySearchKeyword.trim();
            const result = await axios.get('/map/place-text', {
              params: {
                keyword,
                citylimit: true,
                offset: 10,
                page: 1
              }
            });
            const data = util.extractResultData(result);
            const pois = Array.isArray(data.pois) ? data.pois : [];

            if (pois.length > 0) {
              this.cityList = pois.map(poi => {
                const location = String(poi.location || '').split(',');
                return {
                  name: poi.name,
                  address: poi.address || poi.adname || '',
                  province: poi.pname || poi.province || '',
                  city: poi.cityname || poi.city || poi.name,
                  district: poi.adname || poi.district || '',
                  adcode: poi.adcode || '',
                  x: parseFloat(location[0]),
                  y: parseFloat(location[1])
                };
              }).filter(item => Number.isFinite(item.x) && Number.isFinite(item.y));
            } else {
              this.cityList = [];
              this.$message.warning('未找到相关地点');
            }
          } catch (e) {
            console.error('搜索地点失败:', e);
            this.$message.error('搜索失败，请重试');
          }
        },

        // 鎼滅储
        search() {
          if (this.searchKeyword.trim()) {
            window.location.href = util.buildUrl('misc/search.html', {
              source: 'home',
              keyword: this.searchKeyword.trim(),
              autoRun: true,
              cityCode: this.currentCityCode
            });
          } else {
            this.$message.warning('请输入搜索关键词');
          }
        },

        // 鍒嗙被鐐瑰嚮
        goToCategory(cat) {
          window.location.href = util.buildUrl('shop/shop-list.html', {
            type: cat.id,
            name: cat.name,
            x: this.currentLocation && this.currentLocation.x,
            y: this.currentLocation && this.currentLocation.y,
            cityCode: this.currentCityCode
          });
        },

        // 鏌ョ湅鏇村绗旇
        viewMoreNotes() {
          this.$message.info('查看更多笔记功能开发中');
        },

        // 鏌ョ湅绗旇
        viewNote(note) {
          window.location.href = `blog/blog-detail.html?id=${note.id}`;
        },

        // 杩涘叆搴楅摵
        goToShop(shop) {
          window.location.href = `shop/shop-detail.html?id=${shop.id}`;
        },
        navigateNearbyShop(shop) {
          util.openAmapNavigation(shop, {
            mode: 'car',
            city: this.currentCity
          });
        },

        // 瀵艰埅
        goHome() {
          window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        goMap() {
          window.location.href = util.buildUrl('map/map.html', {
            cityCode: this.currentCityCode,
            city: this.currentCity
          });
        },
        publish() {
          window.location.href = 'blog/blog-edit.html';
        },
        goAssistant(scene = '首页') {
          window.location.href = util.buildUrl('misc/assistant.html', {
            scene,
            city: this.currentCity,
            cityCode: this.currentCityCode
          });
        },
        openAssistantWithPrompt(prompt, scene = '首页推荐') {
          window.location.href = util.buildUrl('misc/assistant.html', {
            scene,
            prompt,
            city: this.currentCity,
            cityCode: this.currentCityCode
          });
        },
        goCityHub() {
          window.location.href = util.buildUrl('misc/city-hub.html', {
            cityCode: this.currentCityCode
          });
        },
        openCityScene(scene) {
          const keyword = typeof scene === 'string' ? scene : (scene.searchKeyword || scene.keyword || scene.title || scene.sceneTitle || scene.sceneName || scene.name || JSON.stringify(scene));
          window.location.href = util.buildUrl('misc/search.html', {
            source: 'city-scene',
            keyword,
            autoRun: true,
            cityCode: this.currentCityCode
          });
        },
        openCityRoute(route) {
          window.location.href = util.buildUrl('map/map.html', {
            cityCode: this.currentCityCode,
            city: this.currentCity,
            keyword: route,
            routeMode: 'city'
          });
        },
        searchCityHotKeyword(keyword) {
          let searchStr = keyword;
          if (searchStr.startsWith(this.currentCity) && searchStr.length > this.currentCity.length) {
            searchStr = searchStr.substring(this.currentCity.length).trim();
          }
          this.searchKeyword = searchStr;
          this.search();
        },
        goMessages() {
          window.location.href = 'misc/messages.html';
        },
        goProfile() {
          window.location.href = 'user/info.html';
        },

        // 鏍煎紡鍖栬窛绂?
        formatDistance(distance) {
          if (!distance) return '';
          if (distance < 1000) return Math.round(distance) + 'm';
          return (distance / 1000).toFixed(1) + 'km';
        },

        // 鏍煎紡鍖栨暟瀛?
        formatNumber(num) {
          if (num >= 10000) {
            return (num / 10000).toFixed(1) + 'w';
          } else if (num >= 1000) {
            return (num / 1000).toFixed(1) + 'k';
          }
          return num;
        },

        extractFirstImage(images) {
          if (!images) {
            return this.noteFallbackImage;
          }
          const first = String(images).split(',').find(item => item && item.trim());
          return first || this.noteFallbackImage;
        },

        resolveImageUrl(path) {
          return util.resolveImageUrl(path, this.noteFallbackImage);
        },

        handleImageError(event, type) {
          util.applyImageFallback(event, type === 'avatar' ? this.avatarFallbackImage : this.noteFallbackImage);
        }
      }
    });


