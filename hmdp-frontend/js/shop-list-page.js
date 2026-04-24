const shopListApp = new Vue({
  el: "#app",
  data() {
    return {
      util,
      loading: true,
      isLoadingMore: false,
      hasMore: true,
      showScrollTop: false,
      types: [],
      shops: [],
      typeName: "",
      cityProfile: util.readCurrentCityProfile(),
      params: {
        typeId: 0,
        current: 1,
        sortBy: "",
        x: 112.938814,
        y: 28.228209,
        cityCode: (util.readStorageJSON("currentCity") || {}).cityCode || util.getActiveCityCode(util.readCurrentCityProfile())
      },
      isLocating: false
    };
  },
  computed: {
    cityEditionEnabled() {
      return util.isCityEditionEnabled(this.cityProfile);
    },
    headerTitle() {
      return (this.cityEditionEnabled ? this.cityProfile.cityName + " · " : "") + (this.typeName || "店铺列表");
    }
  },
  created() {
    this.params.typeId = util.getUrlParam("type");
    this.typeName = util.getUrlParam("name");
    const cityCode = util.getUrlParam("cityCode");
    if (cityCode) {
      const matchedProfile = util.findCityProfile({ cityCode });
      this.cityProfile = matchedProfile || this.cityProfile;
      this.params.cityCode = cityCode;
      if (matchedProfile) {
        util.saveCurrentCityProfile(this.cityProfile);
      }
      util.applyCityTheme(this.cityProfile);
    } else {
      this.params.cityCode = (util.readStorageJSON("currentCity") || {}).cityCode || util.getActiveCityCode(this.cityProfile);
      util.applyCityTheme(this.cityProfile);
    }
    const urlX = util.getUrlParam("x");
    const urlY = util.getUrlParam("y");
    if (urlX && urlY) {
      this.params.x = parseFloat(urlX);
      this.params.y = parseFloat(urlY);
      util.writeStorageJSON("userLocation", {
        x: this.params.x,
        y: this.params.y
      });
    }
    this.queryTypes();
    this.getCurrentLocation();
    window.addEventListener("scroll", this.handleScroll);
  },
  beforeDestroy() {
    window.removeEventListener("scroll", this.handleScroll);
  },
  methods: {
    normalizeShops(data) {
      const shopList = Array.isArray(data) ? data : [];
      return shopList.map(shop => {
        const normalized = Object.assign({}, shop);
        if (normalized.images) {
          const firstImage = normalized.images.split(",").find(image => image && image.trim());
          normalized.images = util.resolveImageUrl(firstImage, "/imgs/icons/default-icon.png");
        } else {
          normalized.images = util.resolveImageUrl("", "/imgs/icons/default-icon.png");
        }
        if (!normalized.decisionReason) {
          normalized.decisionReason = this.cityEditionEnabled
            ? this.cityProfile.cityName + " · " + (normalized.area || "本城推荐")
            : (normalized.area || "附近推荐");
        }
        return normalized;
      });
    },
    applySort() {
      const sortBy = this.params.sortBy;
      const toNumber = (val, fallback) => {
        const num = Number(val);
        return Number.isFinite(num) ? num : fallback;
      };
      if (!sortBy) {
        this.shops.sort((a, b) => toNumber(a.distance, Infinity) - toNumber(b.distance, Infinity));
        return;
      }
      if (sortBy === "comments") {
        this.shops.sort((a, b) => toNumber(b.comments, 0) - toNumber(a.comments, 0));
        return;
      }
      if (sortBy === "score") {
        this.shops.sort((a, b) => toNumber(b.score, 0) - toNumber(a.score, 0));
      }
    },
    ensureViewportFilled() {
      if (this.loading || this.isLoadingMore || !this.hasMore) {
        return;
      }
      this.$nextTick(() => {
        if (util.isNearPageBottom(120)) {
          this.loadMoreShops();
        }
      });
    },
    getCurrentLocation() {
      this.isLocating = true;
      const location = util.readStorageJSON("userLocation");
      if (location && location.x && location.y) {
        this.params.x = location.x;
        this.params.y = location.y;
        this.resolveCityFromCoordinates(this.params.x, this.params.y).finally(() => {
          this.queryShops();
          this.isLocating = false;
        });
        return;
      }

      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            this.params.x = position.coords.longitude;
            this.params.y = position.coords.latitude;
            util.writeStorageJSON("userLocation", {
              x: this.params.x,
              y: this.params.y
            });
            this.resolveCityFromCoordinates(this.params.x, this.params.y).finally(() => {
              this.queryShops();
              this.isLocating = false;
            });
          },
          (error) => {
            console.error("Geolocation error:", error);
            this.queryShops();
            this.isLocating = false;
          },
          {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 600000
          }
        );
      } else {
        this.queryShops();
        this.isLocating = false;
      }
    },
    resolveCityFromCoordinates(longitude, latitude) {
      const manualCity = localStorage.getItem("currentCityManual") === "true";
      const urlCityCode = util.getUrlParam("cityCode");
      if (manualCity || urlCityCode) {
        return Promise.resolve();
      }
      return util.resolveLocationByCoordinates({
        longitude,
        latitude,
        source: "browser"
      }).then((context) => {
        const saved = util.saveLocationContext(context);
        this.cityProfile = util.normalizeCityProfile(saved.profile);
        this.params.cityCode = (saved.context && saved.context.cityCode) || util.getActiveCityCode(this.cityProfile);
        util.applyCityTheme(this.cityProfile);
      }).catch(() => {});
    },
    queryTypes() {
      axios.get("/shop-type/list")
        .then((result) => {
          this.types = Array.isArray(result.data) ? result.data : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载分类失败"));
        });
    },
    queryShops() {
      this.loading = true;
      this.hasMore = true;
      axios.get("/shop/of/type", {
        params: this.params
      })
        .then((result) => {
          const data = this.normalizeShops(result.data);
          this.loading = false;
          if (!data.length) {
            this.shops = [];
            this.hasMore = false;
            return;
          }
          this.shops = data;
          this.applySort();
          this.ensureViewportFilled();
        })
        .catch((err) => {
          this.loading = false;
          this.$message.error(util.getErrorMessage(err, "加载失败，请重试"));
        });
    },
    loadMoreShops() {
      if (this.loading || this.isLoadingMore || !this.hasMore) {
        return;
      }
      this.isLoadingMore = true;
      const nextCurrent = this.params.current + 1;
      axios.get("/shop/of/type", {
        params: Object.assign({}, this.params, { current: nextCurrent })
      })
        .then((result) => {
          const data = this.normalizeShops(result.data);
          if (!data.length) {
            this.hasMore = false;
            return;
          }
          this.params.current = nextCurrent;
          this.shops = this.shops.concat(data);
          this.applySort();
          this.ensureViewportFilled();
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载更多失败，请稍后重试"));
        })
        .finally(() => {
          this.isLoadingMore = false;
        });
    },
    handleCommand(type) {
      const params = [
        "type=" + type.id,
        "name=" + encodeURIComponent(type.name || ""),
        "x=" + this.params.x,
        "y=" + this.params.y,
        "cityCode=" + encodeURIComponent(this.params.cityCode || "")
      ];
      location.href = "./shop-list.html?" + params.join("&");
    },
    sortAndQuery(sortBy) {
      this.params.sortBy = sortBy;
      this.params.current = 1;
      this.shops = [];
      this.hasMore = true;
      this.queryShops();
    },
    goBack() {
      history.back();
    },
    goToSearch() {
      location.href = util.buildUrl("../misc/search.html", {
        cityCode: this.params.cityCode
      });
    },
    toDetail(id) {
      location.href = "./shop-detail.html?id=" + id;
    },
    handleImageError(event) {
      util.applyImageFallback(event, commonURL + "/imgs/icons/default-icon.png");
    },
    handleScroll() {
      this.showScrollTop = window.pageYOffset > 300;
      if (util.isNearPageBottom(120)) {
        this.loadMoreShops();
      }
    },
    scrollToTop() {
      window.scrollTo({
        top: 0,
        behavior: "smooth"
      });
    }
  }
});
