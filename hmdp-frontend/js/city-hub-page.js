const CITY_IMAGE_MAP = {
  "长沙": "https://gips0.baidu.com/it/u=3582128062,4016846276&fm=3074&app=3074&f=JPEG?w=1080&h=1307&type=normal&func=",
  "北京": "https://img2.baidu.com/it/u=4014953664,1740066810&fm=253&fmt=auto&app=138&f=JPEG?w=751&h=500",
  "上海": "https://img0.baidu.com/it/u=874806370,2717595353&fm=253&fmt=auto&app=138&f=JPEG?w=889&h=500",
  "广州": "https://pic.rmb.bdstatic.com/bjh/250417/dump/3d80c0811cf0dcb040be2edd1f56d0af.jpeg",
  "深圳": "https://img2.baidu.com/it/u=1738594483,2695461848&fm=253&app=138&f=JPEG?w=971&h=647",
  "杭州": "https://img0.baidu.com/it/u=189867947,2504093187&fm=253&fmt=auto&app=138&f=JPEG?w=750&h=500",
  "南京": "https://img0.baidu.com/it/u=1536100154,678876642&fm=253&app=138&f=JPEG?w=500&h=667",
  "成都": "https://img1.baidu.com/it/u=545108085,3087321826&fm=253&app=120&f=JPEG?w=800&h=1067",
  "武汉": "https://gips1.baidu.com/it/u=2379646813,1348658455&fm=3074&app=3074&f=JPEG?w=2167&h=2750&type=normal&func=T",
  "西安": "https://img2.baidu.com/it/u=4119001178,3319209918&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=667",
  "重庆": "https://q1.itc.cn/q_70/images03/20250904/82992cbab5aa4acfa1631bc55186a897.jpeg",
  "天津": "https://img1.baidu.com/it/u=1925672237,1270017284&fm=253&app=138&f=JPEG?w=500&h=653"
};

const getCityImageUrl = (cityName, index) => {
  return CITY_IMAGE_MAP[cityName] || `/imgs/cities/${cityName}.png`;
};

new Vue({
  el: "#app",
  data() {
    return {
      loading: true,
      brandClaim: "懂这座城，才知道今天去哪",
      cities: [],
      hotCities: [],
      activeCity: util.readCurrentCityProfile()
    };
  },
  computed: {
    openCities() {
      return this.cities.filter(city => city.open !== false);
    },
    activeCityTags() {
      return (this.activeCity.cultureTags || []).slice(0, 5);
    },
    activeCityRoutes() {
      return (this.activeCity.featuredRoutes || []).slice(0, 3);
    },
    activeCityDistricts() {
      return (this.activeCity.featuredDistricts || []).slice(0, 4);
    }
  },
  created() {
    this.loadCities();
  },
  methods: {
    async loadCities() {
      const payload = await util.fetchCityList();
      this.brandClaim = payload.brandClaim || this.brandClaim;
      this.cities = (payload.cities || []).map((city, index) => this.decorateCity(city, index));
      this.hotCities = (payload.hotCities || []).map((city, index) => this.decorateCity(city, index));
      const urlCityCode = util.getUrlParam("cityCode");
      this.activeCity = util.normalizeCityProfile(urlCityCode
        ? util.resolveCityProfile({ cityCode: urlCityCode }, this.cities)
        : this.activeCity);
      const matchedCity = this.cities.find(c => c.cityCode === this.activeCity.cityCode);
      if (matchedCity && matchedCity.cover) {
        this.activeCity.cover = matchedCity.cover;
      } else {
        this.activeCity.cover = getCityImageUrl(this.activeCity.cityName || 'city', 0);
      }
      util.applyCityTheme(this.activeCity);
      this.loading = false;
    },
    decorateCity(city, index) {
      const normalized = util.normalizeCityOverview(city);
      normalized.cover = getCityImageUrl(normalized.cityName, index);
      return normalized;
    },
    chooseCity(city) {
      this.activeCity = util.normalizeCityProfile(city);
      const matchedCity = this.cities.find(c => c.cityCode === this.activeCity.cityCode);
      if (matchedCity && matchedCity.cover) {
        this.activeCity.cover = matchedCity.cover;
      } else {
        this.activeCity.cover = getCityImageUrl(this.activeCity.cityName || 'city', 0);
      }
      util.saveCurrentCityProfile(this.activeCity);
      localStorage.setItem("currentCityManual", "true");
      util.applyCityTheme(this.activeCity);
      this.$message.success("已切换到" + this.activeCity.cityName + "城市版本");
    },
    goHome(city) {
      if (city) {
        this.chooseCity(city);
      }
      location.href = "../index-new.html";
    },
    goMap(city) {
      const profile = city ? util.normalizeCityProfile(city) : this.activeCity;
      util.saveCurrentCityProfile(profile);
      localStorage.setItem("currentCityManual", "true");
      location.href = util.buildUrl("../map/map.html", {
        cityCode: profile.cityCode,
        city: profile.cityName
      });
    },
    goSearch(keyword) {
      location.href = util.buildUrl("./search.html", {
        source: "city-hub",
        keyword,
        autoRun: true,
        cityCode: this.activeCity.cityCode
      });
    },
    handleImageError(event) {
      util.applyImageFallback(event, "/imgs/blogs/blog1.jpg");
    }
  }
});
