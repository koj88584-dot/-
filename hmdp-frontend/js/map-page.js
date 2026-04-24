const DEFAULT_LOCATION = { lng: 112.9388, lat: 28.2282, accuracy: 1000 };

  async function bootstrapMapPage() {
    try {
      const configResult = await axios.get('/map/amap-config');
      const config = util.extractResultData(configResult);
      if (!config.webKey) {
        throw new Error('未配置高德地图 Key');
      }
      window._AMapSecurityConfig = { securityJsCode: config.securityJsCode || '' };
      const AMap = await AMapLoader.load({
        key: config.webKey,
        version: '2.0',
        plugins: ['AMap.PlaceSearch','AMap.AutoComplete','AMap.Geolocation','AMap.Driving','AMap.Walking','AMap.Riding','AMap.Transfer','AMap.Weather','AMap.ToolBar','AMap.Scale','AMap.MapType','AMap.Traffic','AMap.CircleMarker','AMap.Geocoder']
      });
      initApp(AMap);
    } catch (error) {
      console.error('高德地图加载失败:', error);
      alert('地图加载失败，请检查配置或网络后重试');
    }
  }

  bootstrapMapPage();

  function initApp(AMap) {
    new Vue({
      el: '#app',
      data: {
        viewMode: 'results', isLoading: false, map: null, mapReady: false, keyword: '', radius: 5000, typeId: null,
        types: [{ id: 1, name: '美食' },{ id: 2, name: 'KTV' },{ id: 3, name: '丽人' },{ id: 4, name: '美甲' },{ id: 5, name: '按摩' },{ id: 6, name: 'SPA' },{ id: 7, name: '亲子' },{ id: 8, name: '酒吧' },{ id: 9, name: '轰趴' },{ id: 10, name: '运动' }],
        shops: [], selectedShop: null, currentLocation: null, currentLocationMarker: null, currentLocationCircle: null,
        placeSearch: null, autoComplete: null, geocoder: null, weather: null, weatherData: null,
        suggestions: [], showSuggestions: false, searchTimer: null, currentCity: util.readCurrentCityProfile().cityName,
        cityProfile: util.readCurrentCityProfile(), cityScenes: [],
        routeStart: '', routeEnd: '', routeStartPoint: null, routeEndPoint: null, routeType: 'driving', routeResults: [], currentRoute: null, currentRouteIndex: -1,
        showRoutePanel: false, routePanelCollapsed: false, autoPlanned: false,
        driving: null, walking: null, riding: null, transfer: null, trafficEnabled: false, trafficLayer: null,
        loadingMessage: null, selectedSearchCenter: null, resultsScrollTop: 0,
        pendingTarget: null, shopMarkers: {}, shopInfoWindows: {}, lastOpenedInfoWindow: null
      },
      computed: {
        cityEditionEnabled() { return util.isCityEditionEnabled(this.cityProfile); },
        currentCityCode() { return util.getActiveCityCode(this.cityProfile); },
        cityMapTitle() {
          return this.cityEditionEnabled
            ? this.currentCity + '玩法地图 · ' + this.cityProfile.cityTagline
            : this.currentCity + '附近地图';
        },
        radiusLabel() { return this.radius < 1000 ? this.radius + '米' : (this.radius / 1000) + '公里'; },
        currentListTitle() { const currentType = this.types.find((item) => item.id === this.typeId); return this.keyword ? this.keyword + ' · 搜索结果' : (currentType ? currentType.name + ' · 附近推荐' : '附近推荐'); }
      },
      mounted() {
        this.initCityContext();
        this.initServices();
        document.addEventListener('click', this.handleClickOutside);
        this.pendingTarget = this.readTargetFromUrl();
        this.getCurrentLocation(() => {
          if (this.pendingTarget) {
            this.openIncomingTarget();
          }
        });
      },
      beforeDestroy() {
        document.removeEventListener('click', this.handleClickOutside);
      },
      methods: {
        goBack() { history.back(); },
        initCityContext() {
          const cityCode = util.getUrlParam('cityCode');
          const keyword = util.getUrlParam('keyword');
          const profile = cityCode
            ? (util.findCityProfile({ cityCode: cityCode }) || util.readCurrentCityProfile())
            : util.readCurrentCityProfile();
          this.cityProfile = util.normalizeCityProfile(profile);
          this.currentCity = this.cityProfile.cityName;
          if (this.cityEditionEnabled) {
            util.saveCurrentCityProfile(this.cityProfile);
          }
          util.applyCityTheme(this.cityProfile);
          DEFAULT_LOCATION.lng = this.cityProfile.longitude;
          DEFAULT_LOCATION.lat = this.cityProfile.latitude;
          if (keyword) {
            this.keyword = keyword;
          }
          util.fetchCityScenes(this.currentCityCode).then((scenes) => {
            this.cityScenes = scenes || [];
          });
        },
        openCityScene(scene) {
          this.keyword = scene.searchKeyword || scene.keyword || scene.title || scene.sceneTitle || scene.name || scene.sceneName || '';
          this.typeId = scene.typeId || this.typeId;
          this.selectedSearchCenter = null;
          this.searchShops();
        },
        openDistrict(district) {
          this.keyword = district;
          this.selectedSearchCenter = null;
          this.searchShops();
        },
        initServices() {
          this.placeSearch = new AMap.PlaceSearch({ pageSize: 20, pageIndex: 1, extensions: 'all', city: this.currentCity });
          this.autoComplete = new AMap.AutoComplete({ city: this.currentCity });
          this.weather = new AMap.Weather();
          this.trafficLayer = new AMap.TileLayer.Traffic({ zIndex: 10 });
          this.geocoder = new AMap.Geocoder({ city: this.currentCity, radius: 1000 });
        },
        parseUrlCoordinate(value) {
          if (value === undefined || value === null || value === '') return null;
          const parsed = Number(value);
          return Number.isFinite(parsed) ? parsed : null;
        },
        normalizeShop(shop) {
          if (!shop) return null;
          const normalized = Object.assign({}, shop);
          const lng = normalized.longitude !== undefined && normalized.longitude !== null && normalized.longitude !== '' ? Number(normalized.longitude) : Number(normalized.x);
          const lat = normalized.latitude !== undefined && normalized.latitude !== null && normalized.latitude !== '' ? Number(normalized.latitude) : Number(normalized.y);
          normalized.longitude = Number.isFinite(lng) ? lng : 0;
          normalized.latitude = Number.isFinite(lat) ? lat : 0;
          normalized.images = normalized.images || normalized.image || '/imgs/icons/default-icon.png';
          normalized.name = normalized.name || '目标店铺';
          normalized.address = normalized.address || '暂无地址';
          return normalized;
        },
        getShopKey(shop) {
          return String((shop && shop.id) || ((shop && shop.name) || '') + '|' + ((shop && shop.address) || ''));
        },
        getShopPoint(shop) {
          const normalized = this.normalizeShop(shop);
          if (!normalized || !normalized.longitude || !normalized.latitude) return null;
          return [normalized.longitude, normalized.latitude];
        },
        getRouteEndText(shop) {
          if (!shop) return '';
          return shop.address && shop.address !== '暂无地址' ? shop.address : (shop.name || '');
        },
        getAmapRouteMode() {
          const modeMap = { driving: 'car', walking: 'walk', riding: 'ride', transit: 'bus' };
          return modeMap[this.routeType] || 'car';
        },
        getAmapNavigationTarget() {
          const target = this.selectedShop ? this.normalizeShop(this.selectedShop) : {};
          if (!target.name && this.routeEnd) target.name = this.routeEnd;
          if ((!target.address || target.address === '暂无地址') && this.routeEnd) target.address = this.routeEnd;
          if (Array.isArray(this.routeEndPoint) && this.routeEndPoint.length === 2) {
            target.longitude = this.routeEndPoint[0];
            target.latitude = this.routeEndPoint[1];
            target.x = this.routeEndPoint[0];
            target.y = this.routeEndPoint[1];
          }
          return target;
        },
        openAmapNavigationForRoute() {
          const target = this.getAmapNavigationTarget();
          const options = { mode: this.getAmapRouteMode(), city: this.currentCity };
          if (this.getShopPoint(target) || !this.routeEnd) {
            util.openAmapNavigation(target, options);
            return;
          }
          this.resolveRoutePoint('end', this.routeEnd, this.routeEndPoint, (endPoint) => {
            if (endPoint) {
              this.routeEndPoint = endPoint;
              target.longitude = endPoint[0];
              target.latitude = endPoint[1];
              target.x = endPoint[0];
              target.y = endPoint[1];
            }
            util.openAmapNavigation(target, options);
          });
        },
        setRouteTarget(shop) {
          const normalized = this.normalizeShop(shop);
          if (!normalized) return;
          this.selectedShop = normalized;
          this.routeStart = this.routeStart || '我的位置';
          this.routeStartPoint = null;
          this.routeEnd = this.getRouteEndText(normalized);
          this.routeEndPoint = this.getShopPoint(normalized);
        },
        readTargetFromUrl() {
          const targetId = util.getUrlParam('targetId');
          const targetName = util.getUrlParam('targetName');
          const targetAddress = util.getUrlParam('targetAddress');
          const targetX = this.parseUrlCoordinate(util.getUrlParam('targetX'));
          const targetY = this.parseUrlCoordinate(util.getUrlParam('targetY'));
          const mode = util.getUrlParam('mode') || 'preview';
          if (!targetId && !targetName && targetX === null && targetY === null) {
            return null;
          }
          return this.normalizeShop({
            id: targetId || ('target-' + Date.now()),
            name: targetName || '目标店铺',
            address: targetAddress || '暂无地址',
            longitude: targetX,
            latitude: targetY,
            images: '/imgs/icons/default-icon.png',
            score: '暂无',
            avgPrice: 0,
            distance: 0,
            mode
          });
        },
        ensureSelectedShopInList(shop) {
          const normalized = this.normalizeShop(shop);
          if (!normalized) return;
          const key = this.getShopKey(normalized);
          const exists = this.shops.some(item => this.getShopKey(item) === key);
          if (!exists) {
            this.shops.unshift(normalized);
          }
        },
        resolveTargetLocation(shop, callback) {
          const normalized = this.normalizeShop(shop);
          if (!normalized) {
            callback && callback(null);
            return;
          }
          if (this.getShopPoint(normalized)) {
            callback && callback(normalized);
            return;
          }
          const query = normalized.address && normalized.address !== '暂无地址' ? normalized.address : normalized.name;
          if (!query) {
            callback && callback(normalized);
            return;
          }
          this.geocodeAddress(query, (lnglat) => {
            if (lnglat) {
              normalized.longitude = lnglat[0];
              normalized.latitude = lnglat[1];
            }
            callback && callback(normalized);
          });
        },
        openIncomingTarget() {
          if (!this.pendingTarget) return;
          const target = Object.assign({}, this.pendingTarget);
          this.pendingTarget = null;
          this.resolveTargetLocation(target, (resolvedTarget) => {
            this.ensureSelectedShopInList(resolvedTarget);
            this.setRouteTarget(resolvedTarget);
            this.saveResultsScroll();
            this.viewMode = 'navigation';
            this.showRoutePanel = resolvedTarget && resolvedTarget.mode === 'navigation';
            this.routePanelCollapsed = false;
            this.autoPlanned = false;
            this.$nextTick(() => {
              this.ensureMapReady(() => {
                this.refreshNavigationScene(true);
                if (resolvedTarget && resolvedTarget.mode === 'navigation') {
                  this.searchRoute();
                }
              });
            });
          });
        },
        ensureMapReady(callback) {
          if (this.mapReady && this.map) { this.$nextTick(() => { this.map.resize(); if (callback) callback(); }); return; }
          this.$nextTick(() => {
            const mapElement = this.$refs.mapCanvas; if (!mapElement) return;
            this.map = new AMap.Map(mapElement, { zoom: 14, center: [DEFAULT_LOCATION.lng, DEFAULT_LOCATION.lat], viewMode: '2D' });
            this.driving = new AMap.Driving({ map: this.map, panel: false, policy: AMap.DrivingPolicy.LEAST_TIME, outlineColor: '#ff6b6b', strokeColor: '#ff6b6b', strokeWeight: 8, showTraffic: true, hideMarkers: false });
            this.walking = new AMap.Walking({ map: this.map, panel: false, outlineColor: '#4ECDC4', strokeColor: '#4ECDC4', strokeWeight: 8 });
            this.riding = new AMap.Riding({ map: this.map, panel: false, policy: 1, outlineColor: '#45B7D1', strokeColor: '#45B7D1', strokeWeight: 8, hideMarkers: false, autoFitView: true });
            this.transfer = new AMap.Transfer({ map: this.map, panel: false, city: this.currentCity, outlineColor: '#96CEB4', strokeColor: '#96CEB4', strokeWeight: 8, hideMarkers: false, autoFitView: true });
            this.mapReady = true; if (this.currentLocation) this.addCurrentLocationMarker(this.currentLocation.lng, this.currentLocation.lat, this.currentLocation.accuracy || 100); this.renderMarkers(); if (callback) callback();
          });
        },
        refreshResults() { if (this.keyword) this.searchShops(); else this.searchNearbyWithAMap(); },
        openMapOverview(shop) {
          if (shop) {
            this.ensureSelectedShopInList(shop);
            this.setRouteTarget(shop);
          }
          this.saveResultsScroll(); this.viewMode = 'navigation'; this.showRoutePanel = false; this.routePanelCollapsed = false; this.autoPlanned = false;
          this.$nextTick(() => { this.ensureMapReady(() => { this.refreshNavigationScene(!!shop); }); });
        },
        returnToResults() { this.viewMode = 'results'; this.showSuggestions = false; this.$nextTick(() => { if (this.$refs.resultsScroller) this.$refs.resultsScroller.scrollTop = this.resultsScrollTop || 0; }); },
        saveResultsScroll() { if (this.$refs.resultsScroller) this.resultsScrollTop = this.$refs.resultsScroller.scrollTop || 0; },
        refreshNavigationScene(focusSelected) {
          if (!this.mapReady || !this.map) return; this.renderMarkers();
          if (this.currentLocation) this.addCurrentLocationMarker(this.currentLocation.lng, this.currentLocation.lat, this.currentLocation.accuracy || 100);
          if (focusSelected && this.selectedShop && this.getShopPoint(this.selectedShop)) { const point = this.getShopPoint(this.selectedShop); this.map.setCenter(point); this.map.setZoom(16); this.$nextTick(() => this.openShopInfoWindow(this.selectedShop)); return; }
          if (this.shops.length) this.map.setFitView(); else if (this.currentLocation) { this.map.setCenter([this.currentLocation.lng, this.currentLocation.lat]); this.map.setZoom(14); } else { this.map.setCenter([DEFAULT_LOCATION.lng, DEFAULT_LOCATION.lat]); this.map.setZoom(13); }
        },
        toggleRoutePanel() {
          if (this.showRoutePanel) {
            this.showRoutePanel = false;
            return;
          }
          if (!this.selectedShop && !this.routeEnd) {
            this.$message.warning('请先选择目标店铺');
            return;
          }
          if (this.selectedShop) {
            this.setRouteTarget(this.selectedShop);
            this.openShopInfoWindow(this.selectedShop);
          }
          this.showRoutePanel = !this.showRoutePanel;
          this.routePanelCollapsed = false;
        },
        closeRoutePanel() { this.showRoutePanel = false; this.clearRoute(); },
        selectShop(shop) { this.selectedShop = this.normalizeShop(shop); if (this.viewMode === 'navigation') { this.setRouteTarget(this.selectedShop); this.openShopInfoWindow(this.selectedShop); } },
        onKeywordInput() { if (this.searchTimer) clearTimeout(this.searchTimer); if (!this.keyword || this.keyword.length < 1) { this.suggestions = []; this.showSuggestions = false; return; } this.searchTimer = setTimeout(() => { this.searchAMapSuggestions(); }, 300); },
        searchAMapSuggestions() {
          if (!this.autoComplete) return;
          this.autoComplete.search(this.keyword, (status, result) => { if (status === 'complete' && result.info === 'OK') { this.suggestions = result.tips.map((tip) => ({ name: tip.name, address: tip.address || tip.district || '', type: tip.type || '地点', location: tip.location })).filter((item) => item.name); this.showSuggestions = this.suggestions.length > 0; } else { this.suggestions = []; this.showSuggestions = false; } });
        },
        selectSuggestion(item) { this.keyword = item.name; this.showSuggestions = false; this.selectedSearchCenter = item.location && item.location.lng && item.location.lat ? { lng: item.location.lng, lat: item.location.lat } : null; this.searchShops(); },
        handleClickOutside(event) { const wrapper = document.querySelector('.search-box'); if (wrapper && !wrapper.contains(event.target)) this.showSuggestions = false; },
        getCurrentLocation(done) {
          const manualCity = localStorage.getItem('currentCityManual') === 'true';
          const urlCityCode = util.getUrlParam('cityCode');
          if (manualCity || urlCityCode) {
            this.currentLocation = { lng: this.cityProfile.longitude, lat: this.cityProfile.latitude, accuracy: 1000 };
            if (this.mapReady) { this.map.setCenter([this.currentLocation.lng, this.currentLocation.lat]); this.addCurrentLocationMarker(this.currentLocation.lng, this.currentLocation.lat, this.currentLocation.accuracy); }
            if (!this.keyword && !this.pendingTarget) this.searchNearbyWithAMap();
            if (this.keyword && !this.pendingTarget) this.searchShops();
            if (done) done();
            return;
          }
          util.resolveUserLocation({ force: true, allowIpFallback: true }).then(({ context, profile }) => {
            const location = context || {};
            this.cityProfile = util.normalizeCityProfile(profile || util.readCurrentCityProfile());
            this.currentCity = location.city || this.cityProfile.cityName;
            util.applyCityTheme(this.cityProfile);
            DEFAULT_LOCATION.lng = this.cityProfile.longitude;
            DEFAULT_LOCATION.lat = this.cityProfile.latitude;
            this.currentLocation = {
              lng: location.longitude || this.cityProfile.longitude,
              lat: location.latitude || this.cityProfile.latitude,
              accuracy: location.accuracy || 1000
            };
            if (this.placeSearch && typeof this.placeSearch.setCity === 'function') this.placeSearch.setCity(this.currentCity);
            if (this.autoComplete && typeof this.autoComplete.setCity === 'function') this.autoComplete.setCity(this.currentCity);
            if (this.geocoder && typeof this.geocoder.setCity === 'function') this.geocoder.setCity(this.currentCity);
            util.fetchCityScenes(this.currentCityCode).then((scenes) => { this.cityScenes = scenes || []; });
            if (this.mapReady) { this.map.setCenter([this.currentLocation.lng, this.currentLocation.lat]); this.addCurrentLocationMarker(this.currentLocation.lng, this.currentLocation.lat, this.currentLocation.accuracy); }
            if (!this.keyword && !this.pendingTarget) this.searchNearbyWithAMap();
            if (this.keyword && !this.pendingTarget) this.searchShops();
            if (done) done();
          }).catch(() => {
            this.currentLocation = Object.assign({}, DEFAULT_LOCATION);
            if (this.mapReady) { this.map.setCenter([DEFAULT_LOCATION.lng, DEFAULT_LOCATION.lat]); this.addCurrentLocationMarker(DEFAULT_LOCATION.lng, DEFAULT_LOCATION.lat, DEFAULT_LOCATION.accuracy); }
            if (!this.keyword && !this.pendingTarget) this.searchNearbyWithAMap();
            if (done) done();
          });
        },
        updateCityContextByCoordinates(longitude, latitude) {
          const manualCity = localStorage.getItem('currentCityManual') === 'true';
          const urlCityCode = util.getUrlParam('cityCode');
          if (manualCity || urlCityCode) {
            return Promise.resolve();
          }
          return util.resolveLocationByCoordinates({
            longitude,
            latitude,
            source: 'browser'
          }).then((data) => {
            if (!data || (!data.city && !data.province && !data.district)) {
              return;
            }
            const cityName = data.city || data.province || this.currentCity;
            const matched = util.findCityProfile({
              cityCode: data.adcode,
              adcode: data.adcode,
              city: cityName,
              cityName,
              province: data.province
            });
            const profile = matched || util.createGenericCityProfile({
              cityName,
              city: cityName,
              province: data.province,
              district: data.district,
              adcode: data.adcode,
              x: longitude,
              y: latitude
            });
            this.cityProfile = util.normalizeCityProfile(profile);
            this.currentCity = this.cityProfile.cityName;
            util.applyCityTheme(this.cityProfile);
            if (this.placeSearch && typeof this.placeSearch.setCity === 'function') this.placeSearch.setCity(this.currentCity);
            if (this.autoComplete && typeof this.autoComplete.setCity === 'function') this.autoComplete.setCity(this.currentCity);
            if (this.geocoder && typeof this.geocoder.setCity === 'function') this.geocoder.setCity(this.currentCity);
            util.saveCurrentCityProfile(this.cityProfile, {
              name: this.cityProfile.cityName,
              province: data.province,
              city: cityName,
              district: data.district,
              adcode: data.adcode,
              x: longitude,
              y: latitude,
              formattedAddress: data.formattedAddress || data.address || '',
              cityEditionEnabled: !!matched
            });
            DEFAULT_LOCATION.lng = this.cityProfile.longitude;
            DEFAULT_LOCATION.lat = this.cityProfile.latitude;
            util.fetchCityScenes(this.currentCityCode).then((scenes) => {
              this.cityScenes = scenes || [];
            });
          }).catch(() => {});
        },
        wgs84ToGcj02(lng, lat) { const pi = 3.1415926535897932384626; const a = 6378245.0; const ee = 0.00669342162296594323; if (this.outOfChina(lng, lat)) return [lng, lat]; let dLat = this.transformLat(lng - 105.0, lat - 35.0); let dLng = this.transformLng(lng - 105.0, lat - 35.0); const radLat = lat / 180.0 * pi; let magic = Math.sin(radLat); magic = 1 - ee * magic * magic; const sqrtMagic = Math.sqrt(magic); dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * pi); dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * pi); return [lng + dLng, lat + dLat]; },
        outOfChina(lng, lat) { return !(lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55); },
        transformLat(x, y) { const pi = 3.1415926535897932384626; let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x)); ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0; ret += (20.0 * Math.sin(y * pi) + 40.0 * Math.sin(y / 3.0 * pi)) * 2.0 / 3.0; ret += (160.0 * Math.sin(y / 12.0 * pi) + 320 * Math.sin(y * pi / 30.0)) * 2.0 / 3.0; return ret; },
        transformLng(x, y) { const pi = 3.1415926535897932384626; let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x)); ret += (20.0 * Math.sin(6.0 * x * pi) + 20.0 * Math.sin(2.0 * x * pi)) * 2.0 / 3.0; ret += (20.0 * Math.sin(x * pi) + 40.0 * Math.sin(x / 3.0 * pi)) * 2.0 / 3.0; ret += (150.0 * Math.sin(x / 12.0 * pi) + 300.0 * Math.sin(x * pi / 30.0)) * 2.0 / 3.0; return ret; },
        addCurrentLocationMarker(lng, lat, accuracy) {
          if (!this.mapReady || !this.map) return; if (this.currentLocationMarker) this.map.remove(this.currentLocationMarker); if (this.currentLocationCircle) this.map.remove(this.currentLocationCircle);
          const markerContent = '<div style="position:relative;width:30px;height:30px;"><div style="position:absolute;inset:0;border-radius:50%;background:rgba(255,107,53,0.24);animation:pulse 2s ease-out infinite;"></div><div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:16px;height:16px;border-radius:50%;background:#ff6b35;border:3px solid #fff;box-shadow:0 0 10px rgba(255,107,53,0.4);"></div></div>';
          this.currentLocationMarker = new AMap.Marker({ position: [lng, lat], map: this.map, title: '我的位置', content: markerContent, offset: new AMap.Pixel(-15, -15), zIndex: 200 });
          this.currentLocationCircle = new AMap.Circle({ center: [lng, lat], radius: Math.max(accuracy || 100, 50), fillColor: '#ff6b35', fillOpacity: 0.08, strokeColor: '#ff6b35', strokeWeight: 1, strokeOpacity: 0.3, map: this.map, zIndex: 100 });
        },
        onRadiusChange() { this.refreshResults(); },
        onTypeChange() { this.refreshResults(); },
        searchNearbyWithAMap() {
          if (!this.placeSearch) return; const baseLocation = this.currentLocation || DEFAULT_LOCATION; let searchType = '';
          if (this.typeId) { const typeMap = { 1: '餐饮服务', 2: 'KTV', 3: '丽人', 4: '美甲', 5: '按摩', 6: 'SPA', 7: '亲子', 8: '酒吧', 9: '轰趴', 10: '运动' }; searchType = typeMap[this.typeId] || ''; }
          const searchKeyword = searchType || '美食|酒店|购物|生活服务'; this.isLoading = true;
          this.placeSearch.searchNearBy(searchKeyword, [baseLocation.lng, baseLocation.lat], this.radius, (status, result) => { this.handleAMapSearchResult(status, result, false); });
        },
        searchShops() {
          this.showSuggestions = false; if (!this.keyword) { this.searchNearbyWithAMap(); return; }
          if (!this.placeSearch) { this.$message.warning('地图服务初始化中，请稍后再试'); return; }
          this.searchWithAMap();
        },
        searchWithAMap() {
          const center = this.selectedSearchCenter || this.currentLocation || DEFAULT_LOCATION; let searchKeyword = this.keyword;
          if (this.typeId) { const typeMap = { 1: '美食', 2: 'KTV', 3: '丽人', 4: '美甲', 5: '按摩', 6: 'SPA', 7: '亲子', 8: '酒吧', 9: '轰趴', 10: '运动' }; const typeName = typeMap[this.typeId]; if (typeName && !this.keyword.includes(typeName)) searchKeyword = typeName + ' ' + this.keyword; }
          this.isLoading = true; this.placeSearch.searchNearBy(searchKeyword, [center.lng, center.lat], 100000, (status, result) => { this.handleAMapSearchResult(status, result, true); });
        },
        handleAMapSearchResult(status, result, preserveCenter) {
          this.isLoading = false;
          if (status === 'complete' && result.info === 'OK' && result.poiList && result.poiList.pois) {
            const pois = result.poiList.pois || [];
            if (!pois.length) { this.shops = []; this.selectedShop = null; if (this.mapReady) this.renderMarkers(); return; }
            this.shops = pois.map((poi, index) => ({ id: poi.id || ('poi-' + index), name: poi.name, address: poi.address || '暂无地址', longitude: poi.location ? poi.location.lng : 0, latitude: poi.location ? poi.location.lat : 0, score: (poi.biz_ext && poi.biz_ext.rating) ? parseFloat(poi.biz_ext.rating) : (Math.random() * 2 + 3.5).toFixed(1), avgPrice: (poi.biz_ext && poi.biz_ext.cost) ? parseInt(poi.biz_ext.cost, 10) : Math.floor(Math.random() * 80 + 25), distance: poi.distance || 0, images: (poi.photos && poi.photos.length > 0) ? poi.photos[0].url : '/imgs/icons/default-icon.png', typeId: this.getTypeFromPoi(poi.type) }));
            if (this.pendingTarget) {
              this.ensureSelectedShopInList(this.pendingTarget);
            }
            this.selectedShop = this.shops[0] || null; if (this.mapReady) { this.renderMarkers(); if (this.viewMode === 'navigation') this.refreshNavigationScene(!preserveCenter && !!this.selectedShop); }
          } else { this.shops = []; this.selectedShop = null; if (this.mapReady) this.renderMarkers(); }
        },
        getTypeFromPoi(poiType) { if (!poiType) return null; const typeMap = { '美食': 1, '餐饮': 1, 'KTV': 2, '丽人': 3, '美容': 3, '美发': 3, '美甲': 4, '按摩': 5, '足疗': 5, 'SPA': 6, '亲子': 7, '儿童': 7, '酒吧': 8, '轰趴': 9, '运动': 10, '健身': 10, '体育': 10 }; for (let key in typeMap) { if (poiType.includes(key)) return typeMap[key]; } return null; },
        renderMarkers() {
          if (!this.mapReady || !this.map) return;
          Object.keys(this.shopInfoWindows).forEach((key) => { if (this.shopInfoWindows[key]) this.shopInfoWindows[key].close(); });
          const overlays = this.map.getAllOverlays('marker'); overlays.forEach((marker) => { if (marker !== this.currentLocationMarker) this.map.remove(marker); });
          this.shopMarkers = {};
          this.shopInfoWindows = {};
          this.shops.forEach((rawShop) => {
            const shop = this.normalizeShop(rawShop);
            const point = this.getShopPoint(shop);
            if (!point) return;
            const key = this.getShopKey(shop);
            const marker = new AMap.Marker({ position: point, map: this.map, title: shop.name });
            const infoWindow = new AMap.InfoWindow({ content: this.buildShopInfoContent(shop), offset: new AMap.Pixel(0, -30) });
            this.shopMarkers[key] = marker;
            this.shopInfoWindows[key] = infoWindow;
            marker.on('click', () => { this.selectedShop = shop; this.setRouteTarget(shop); this.openShopInfoWindow(shop); });
          });
        },
        buildShopInfoContent(shop) {
          const image = this.escapeHtml(shop.images || '/imgs/icons/default-icon.png');
          const name = this.escapeHtml(shop.name || '目标店铺');
          const address = this.escapeHtml(shop.address || '暂无地址');
          const score = this.escapeHtml(shop.score || '暂无');
          const distance = this.escapeHtml(this.formatDistance(shop.distance));
          return '<div style="padding:12px;max-width:260px;">'
            + '<img src="' + image + '" alt="" style="width:100%;height:120px;object-fit:cover;border-radius:8px;margin-bottom:8px;" onerror="this.src=\'/imgs/icons/default-icon.png\'">'
            + '<div style="font-weight:700;font-size:15px;margin-bottom:4px;color:#1e293b;">' + name + '</div>'
            + '<div style="font-size:12px;line-height:1.6;color:#64748b;margin-bottom:8px;">' + address + '</div>'
            + '<div style="display:flex;justify-content:space-between;gap:10px;font-size:12px;"><span style="color:#ff6b35;font-weight:700;">评分 ' + score + '</span><span style="color:#94a3b8;">' + distance + '</span></div>'
            + '</div>';
        },
        openShopInfoWindow(shop) {
          if (!this.mapReady || !this.map || !shop) return;
          const key = this.getShopKey(shop);
          const marker = this.shopMarkers[key];
          const infoWindow = this.shopInfoWindows[key];
          if (!marker || !infoWindow) return;
          if (this.lastOpenedInfoWindow && this.lastOpenedInfoWindow !== infoWindow) this.lastOpenedInfoWindow.close();
          infoWindow.open(this.map, marker.getPosition());
          this.lastOpenedInfoWindow = infoWindow;
        },
        navigateToShop(shop) {
          if (!shop) { this.$message.error('店铺信息无效'); return; }
          this.resolveTargetLocation(shop, (resolvedShop) => {
            const target = resolvedShop || shop;
            this.ensureSelectedShopInList(target);
            this.setRouteTarget(target);
            util.openAmapNavigation(target, {
              mode: this.getAmapRouteMode(),
              city: this.currentCity
            });
          });
        },
        onRouteStartInput() { this.routeStartPoint = null; this.autoPlanned = false; },
        onRouteEndInput() { this.routeEndPoint = null; this.autoPlanned = false; },
        selectRouteType(type) { this.routeType = type; if (this.routeStart && this.routeEnd && this.viewMode === 'navigation' && this.showRoutePanel) this.$nextTick(() => { this.searchRoute(); }); },
        searchRoute() {
          if (!this.routeStart || !this.routeEnd) { this.$message.warning('请填写起点和终点'); return; }
          if (!this.mapReady) { this.ensureMapReady(() => this.searchRoute()); return; }
          if (this.loadingMessage) this.loadingMessage.close();
          this.loadingMessage = this.$message({ message: '正在规划路线，请稍候…', duration: 0, showClose: true, type: 'info' });
          this.clearRouteOnMap(); this.routeResults = []; this.currentRoute = null; this.currentRouteIndex = -1;
          this.resolveRoutePoint('start', this.routeStart, this.routeStartPoint, (startPoint) => {
            if (!startPoint) { this.closeLoadingMessage(); this.$message.error('无法识别起点位置'); return; }
            this.resolveRoutePoint('end', this.routeEnd, this.routeEndPoint, (endPoint) => {
              if (!endPoint) { this.closeLoadingMessage(); this.$message.error('无法识别终点位置'); return; }
              this.doSearchRoute(startPoint, endPoint);
            });
          });
        },
        resolveRoutePoint(kind, text, cachedPoint, callback) {
          const value = String(text || '').trim();
          if (kind === 'start' && (value === '我的位置' || value === '当前位置')) {
            callback(this.currentLocation ? [this.currentLocation.lng, this.currentLocation.lat] : null);
            return;
          }
          if (Array.isArray(cachedPoint) && cachedPoint.length === 2) {
            callback(cachedPoint);
            return;
          }
          this.geocodeAddress(value, callback);
        },
        geocodeAddress(address, callback) {
          if (!address || !address.trim()) { callback(null); return; }
          this.geocoder.getLocation(address, (status, result) => {
            if (status === 'complete' && result.info === 'OK' && result.geocodes && result.geocodes.length > 0) { const location = result.geocodes[0].location; callback([location.lng, location.lat]); return; }
            this.placeSearch.search(address, (searchStatus, searchResult) => { if (searchStatus === 'complete' && searchResult.info === 'OK' && searchResult.poiList.pois.length > 0) { const poi = searchResult.poiList.pois[0]; callback([poi.location.lng, poi.location.lat]); } else callback(null); });
          });
        },
        doSearchRoute(startPoint, endPoint) {
          const callback = (status, result) => { this.closeLoadingMessage(); if (status === 'complete') this.parseRouteResult(result); else this.$message.error('路线规划失败，请稍后重试'); };
          switch (this.routeType) { case 'walking': this.walking.search(startPoint, endPoint, callback); break; case 'riding': this.riding.search(startPoint, endPoint, callback); break; case 'transit': this.transfer.search(startPoint, endPoint, callback); break; default: this.driving.search(startPoint, endPoint, callback); }
        },
        closeLoadingMessage() { if (this.loadingMessage) { this.loadingMessage.close(); this.loadingMessage = null; } },
        parseRouteResult(result) {
          this.routeResults = []; let routes = [];
          if (this.routeType === 'transit') routes = result.plans || []; else { routes = result.routes || []; if (!routes.length && result.route) routes = [result.route]; }
          if (routes.length > 0) {
            routes.forEach((route, index) => {
              let time; let distance; let detail;
              if (this.routeType === 'transit') { time = this.formatTime(route.time || 0); distance = this.formatDistance(route.distance || 0); detail = route.segments && route.segments.length > 0 ? route.segments.map((segment) => { if (segment.transit_mode === 'BUS' || segment.transit_mode === 'SUBWAY') return segment.transit.lines && segment.transit.lines[0] ? segment.transit.lines[0].name : '公交'; if (segment.transit_mode === 'WALK') return '步行'; return ''; }).filter(Boolean).join(' → ') || '公交路线' : '公交路线'; }
              else if (this.routeType === 'riding') { time = this.formatTime(route.time || 0); distance = this.formatDistance(route.distance || 0); detail = route.steps ? '骑行途经 ' + route.steps.length + ' 个路段' : '骑行路线'; }
              else { time = this.formatTime(route.time || 0); distance = this.formatDistance(route.distance || 0); detail = route.steps ? '途经 ' + route.steps.length + ' 个路段' : '路线规划'; }
              this.routeResults.push({ index: index, time: time, distance: distance, detail: detail, route: route });
            });
            this.currentRouteIndex = 0; this.currentRoute = routes[0]; this.autoPlanned = true; this.$message.success('已找到 ' + routes.length + ' 条可选路线');
          } else this.$message.warning('没有找到可用路线');
        },
        selectRoute(routeInfo, index) {
          this.currentRoute = routeInfo.route; this.currentRouteIndex = index;
          if (this.routeStart && this.routeEnd) { this.clearRouteOnMap(); switch (this.routeType) { case 'transit': this.drawTransitRoute(index); break; case 'walking': this.drawWalkingRoute(); break; case 'riding': this.drawRidingRoute(); break; default: this.drawDrivingRoute(index); } }
        },
        clearRouteOnMap() { if (this.driving) this.driving.clear(); if (this.walking) this.walking.clear(); if (this.riding) this.riding.clear(); if (this.transfer) this.transfer.clear(); },
        drawTransitRoute(planIndex) { this.getRoutePoints((startPoint, endPoint) => { if (!startPoint || !endPoint) return; this.transfer.search(startPoint, endPoint, { city: this.currentCity, cityd: this.currentCity, policy: planIndex }); }); },
        drawDrivingRoute(policyIndex) { this.getRoutePoints((startPoint, endPoint) => { if (!startPoint || !endPoint) return; const policies = [AMap.DrivingPolicy.LEAST_TIME, AMap.DrivingPolicy.LEAST_FEE, AMap.DrivingPolicy.LEAST_DISTANCE, AMap.DrivingPolicy.REAL_TRAFFIC, AMap.DrivingPolicy.HIGHWAY, AMap.DrivingPolicy.NOHIGHWAY]; const policy = policies[policyIndex] || AMap.DrivingPolicy.LEAST_TIME; this.driving.search(startPoint, endPoint, { policy: policy, showTraffic: true }); }); },
        drawWalkingRoute() { this.getRoutePoints((startPoint, endPoint) => { if (!startPoint || !endPoint) return; this.walking.search(startPoint, endPoint); }); },
        drawRidingRoute() { this.getRoutePoints((startPoint, endPoint) => { if (!startPoint || !endPoint) return; this.riding.search(startPoint, endPoint); }); },
        getRoutePoints(callback) {
          this.resolveRoutePoint('start', this.routeStart, this.routeStartPoint, (startPoint) => {
            if (!startPoint) { this.$message.error('无法识别起点位置'); callback(null, null); return; }
            this.resolveRoutePoint('end', this.routeEnd, this.routeEndPoint, (endPoint) => {
              if (!endPoint) { this.$message.error('无法识别终点位置'); callback(null, null); return; }
              callback(startPoint, endPoint);
            });
          });
        },
        clearRoute() { this.closeLoadingMessage(); this.clearRouteOnMap(); this.routeResults = []; this.currentRoute = null; this.currentRouteIndex = -1; this.autoPlanned = false; },
        getWeather() { if (!this.weather) return; this.weather.getLive(this.currentCity, (error, data) => { if (!error) { this.weatherData = data; this.$message.success(this.currentCity + ' 当前天气：' + data.weather + ' ' + data.temperature + '°C'); } else this.$message.error('获取天气失败'); }); },
        toggleTraffic() { if (!this.mapReady || !this.map) { this.$message.warning('请先打开地图模式'); return; } this.trafficEnabled = !this.trafficEnabled; if (this.trafficEnabled) this.trafficLayer.setMap(this.map); else this.trafficLayer.setMap(null); },
        formatTime(seconds) { if (!seconds) return '约 0 分钟'; if (seconds < 60) return seconds + '秒'; if (seconds < 3600) return Math.floor(seconds / 60) + '分钟'; const hours = Math.floor(seconds / 3600); const minutes = Math.floor((seconds % 3600) / 60); return hours + '小时' + minutes + '分钟'; },
        formatDistance(distance) { if (distance === null || distance === undefined || distance === '') return ''; if (distance < 1000) return Math.round(distance) + 'm'; return (distance / 1000).toFixed(1) + 'km'; },
        escapeHtml(value) {
          return String(value === undefined || value === null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
        }
      }
    });
  }


