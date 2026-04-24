function isLocalDevHost(hostname) {
  if (!hostname) {
    return false;
  }
  return hostname === "localhost"
    || hostname === "127.0.0.1"
    || hostname === "0.0.0.0"
    || /^10\./.test(hostname)
    || /^192\.168\./.test(hostname)
    || /^172\.(1[6-9]|2\d|3[0-1])\./.test(hostname);
}

function shouldUseLocalBackend(locationInfo) {
  if (!locationInfo) {
    return false;
  }
  const hostname = locationInfo.hostname || "";
  const port = locationInfo.port || "";
  if (!isLocalDevHost(hostname)) {
    return false;
  }
  return ["3000", "3001", "5173", "5500", "5501", "8080"].indexOf(port) > -1;
}

function resolveCommonURL() {
  const explicitBase = window.__HMDP_API_BASE__;
  if (typeof explicitBase === "string" && explicitBase.trim()) {
    return explicitBase.trim().replace(/\/+$/, "");
  }
  if (window.location && window.location.origin && window.location.origin !== "null") {
    if (shouldUseLocalBackend(window.location)) {
      return (window.location.protocol || "http:") + "//" + window.location.hostname + ":8081";
    }
    return window.location.origin.replace(/\/+$/, "");
  }
  return "http://localhost:8081";
}

let commonURL = resolveCommonURL();

const HMDP_DEFAULT_CITY_CODE = "430100";
const HMDP_LOCATION_CONTEXT_KEY = "currentLocationContext";
const HMDP_LOCATION_CONTEXT_TTL = 10 * 60 * 1000;
const HMDP_FALLBACK_CITY_PROFILES = [
  {
    cityCode: "110100",
    cityName: "北京",
    province: "北京",
    longitude: 116.4074,
    latitude: 39.9042,
    heroTitle: "老城新潮，一天逛出北京层次感",
    cityTagline: "胡同、老字号、展览和夜场连成一条更像北京人的消费路线。",
    cultureTags: ["老字号", "胡同", "展览", "夜场"],
    defaultScenes: ["工作日午餐", "朋友聚会", "博物馆周边", "夜场续摊"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "健身运动"],
    priceTone: "品质感偏稳，适合经典场景决策。",
    featuredDistricts: ["三里屯", "国贸", "什刹海", "簋街"],
    seasonalHooks: ["春日赏花", "暑期亲子", "秋日看展", "冬夜涮锅"],
    hotSearches: ["北京烤鸭", "胡同咖啡", "夜游什刹海", "国贸聚餐"],
    featuredRoutes: ["老北京一日吃喝", "看展到晚餐的东城路线", "夜场续摊不绕路"],
    visualTheme: { primary: "#d55b3d", primaryDark: "#7a3022", background: "#fff7f2", soft: "#ffe2d7" },
    open: true
  },
  {
    cityCode: "310100",
    cityName: "上海",
    province: "上海",
    longitude: 121.4737,
    latitude: 31.2304,
    heroTitle: "高效、体面、好看，上海消费要有节奏感",
    cityTagline: "先替你把效率和质感排好，再决定今天去哪里。",
    cultureTags: ["精致", "效率", "西餐", "展览"],
    defaultScenes: ["午休快吃", "约会晚餐", "展后续摊", "周末慢逛"],
    primaryCategories: ["美食", "美容SPA", "酒吧", "丽人·美发"],
    priceTone: "高客单更重视环境和效率。",
    featuredDistricts: ["静安寺", "新天地", "徐家汇", "陆家嘴"],
    seasonalHooks: ["梧桐区散步", "梅雨季室内局", "夜景约会", "周末 brunch"],
    hotSearches: ["上海brunch", "静安咖啡", "陆家嘴夜景餐厅", "新天地约会"],
    featuredRoutes: ["梧桐区慢逛路线", "展览到晚餐的静安版", "效率型商务聚餐"],
    visualTheme: { primary: "#ff7a45", primaryDark: "#24304f", background: "#fff8f3", soft: "#ffe8d8" },
    open: true
  },
  {
    cityCode: "440100",
    cityName: "广州",
    province: "广东",
    longitude: 113.2644,
    latitude: 23.1291,
    heroTitle: "白天讲效率，晚上讲烟火",
    cityTagline: "早茶、夜宵、老广口味和商圈节奏一起算进推荐。",
    cultureTags: ["烟火气", "老字号", "夜宵", "粤味"],
    defaultScenes: ["早茶", "夜宵", "朋友聚会", "周末扫街"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "按摩·足疗"],
    priceTone: "价格带更宽，适合高频复购型消费。",
    featuredDistricts: ["天河路", "北京路", "琶洲", "上下九"],
    seasonalHooks: ["夏夜大排档", "老广早茶", "节日夜游", "台风天室内局"],
    hotSearches: ["广州夜宵", "老广早茶", "广州烧鹅", "北京路边吃边逛"],
    featuredRoutes: ["老广早茶一天", "夜宵从烧烤到糖水", "北京路扫街路线"],
    visualTheme: { primary: "#ff7043", primaryDark: "#7a2315", background: "#fff7f3", soft: "#ffe0d6" },
    open: true
  },
  {
    cityCode: "440300",
    cityName: "深圳",
    province: "广东",
    longitude: 114.0579,
    latitude: 22.5431,
    heroTitle: "快、准、爽，下班后的选择不能拖泥带水",
    cityTagline: "更适合高效率消费决策，下班就能直接走到场景里。",
    cultureTags: ["效率", "新消费", "夜场", "商务"],
    defaultScenes: ["下班聚餐", "轻运动", "夜咖啡", "周末海边"],
    primaryCategories: ["美食", "健身运动", "酒吧", "美容SPA"],
    priceTone: "节奏快，适合通勤友好型推荐。",
    featuredDistricts: ["南山", "福田", "万象天地", "海岸城"],
    seasonalHooks: ["雨天商场局", "海边日落", "深夜加班餐", "周末轻运动"],
    hotSearches: ["深圳下班聚餐", "南山夜咖啡", "深圳海边晚餐", "福田效率午餐"],
    featuredRoutes: ["深南大道下班路线", "海边日落到晚餐", "万象天地社交局"],
    visualTheme: { primary: "#ff6a3d", primaryDark: "#102542", background: "#fff8f3", soft: "#ffe1d4" },
    open: true
  },
  {
    cityCode: "330100",
    cityName: "杭州",
    province: "浙江",
    longitude: 120.1551,
    latitude: 30.2741,
    heroTitle: "西湖不只是景点，杭州要把江南感和周末感一起安排",
    cityTagline: "留白、景致和体验感都重要，适合慢半拍的城市版本。",
    cultureTags: ["江南", "咖啡", "周末感", "citywalk"],
    defaultScenes: ["西湖漫游", "朋友下午茶", "周末轻聚餐", "夜景散步"],
    primaryCategories: ["美食", "丽人·美发", "美容SPA", "亲子游乐"],
    priceTone: "消费更偏周末体验和品质休闲。",
    featuredDistricts: ["西湖", "湖滨", "天目里", "钱江新城"],
    seasonalHooks: ["梅雨季室内馆", "秋日桂花", "周末西湖", "夜游钱江"],
    hotSearches: ["西湖下午茶", "杭州小酒馆", "杭州周末约会", "天目里看展"],
    featuredRoutes: ["西湖半日慢游", "天目里到晚餐", "湖滨夜游路线"],
    visualTheme: { primary: "#eb6b2d", primaryDark: "#1b4d3e", background: "#fff9f2", soft: "#d8efe3" },
    open: true
  },
  {
    cityCode: "320100",
    cityName: "南京",
    province: "江苏",
    longitude: 118.7969,
    latitude: 32.0603,
    heroTitle: "六朝气质和年轻生活并存",
    cityTagline: "白天有文化底色，晚上有夜游和夜宵，路线感很重要。",
    cultureTags: ["古都", "夜游", "鸭血粉丝", "梧桐路"],
    defaultScenes: ["秦淮夜游", "工作日午餐", "朋友小聚", "周末散步"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "健身运动"],
    priceTone: "适合文化体验和夜间消费联动。",
    featuredDistricts: ["新街口", "夫子庙", "老门东", "河西"],
    seasonalHooks: ["梅花季", "秦淮灯会", "秋天梧桐路", "周末博物馆"],
    hotSearches: ["南京夜游", "老门东小吃", "新街口聚餐", "南京下午茶"],
    featuredRoutes: ["秦淮河夜游路线", "老门东吃逛一条线", "新街口效率局"],
    visualTheme: { primary: "#cc5a36", primaryDark: "#52281d", background: "#fff8f3", soft: "#ffe3d7" },
    open: true
  },
  {
    cityCode: "510100",
    cityName: "成都",
    province: "四川",
    longitude: 104.0668,
    latitude: 30.5728,
    heroTitle: "松弛不是慢，是把吃喝和社交安排得刚刚好",
    cityTagline: "更重氛围感、朋友局和夜生活，不必急着做决定。",
    cultureTags: ["松弛感", "火锅", "社交", "夜生活"],
    defaultScenes: ["下班火锅", "夜宵", "酒馆聊天", "周末摆龙门阵"],
    primaryCategories: ["美食", "酒吧", "轰趴馆", "按摩·足疗"],
    priceTone: "夜经济和社交型消费更活跃。",
    featuredDistricts: ["太古里", "建设路", "玉林", "春熙路"],
    seasonalHooks: ["雨天火锅", "夜间酒馆", "周末公园局", "深夜小吃"],
    hotSearches: ["成都火锅", "玉林小酒馆", "建设路夜宵", "成都周末松弛路线"],
    featuredRoutes: ["太古里到玉林", "火锅后去酒馆", "夜宵不绕路路线"],
    visualTheme: { primary: "#ff6f3c", primaryDark: "#5b2c1e", background: "#fff7f1", soft: "#ffe4d7" },
    open: true
  },
  {
    cityCode: "420100",
    cityName: "武汉",
    province: "湖北",
    longitude: 114.3054,
    latitude: 30.5931,
    heroTitle: "江城的魅力在桥、江、夜色，也在一顿热乎乎的晚餐",
    cityTagline: "桥头到商圈的路线推荐，夜景和夜宵一起安排。",
    cultureTags: ["江城", "热干面", "江滩", "夜景"],
    defaultScenes: ["江滩散步", "朋友聚会", "宵夜", "周末打卡"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "轰趴馆"],
    priceTone: "夜景路线和重口味餐饮更容易成交。",
    featuredDistricts: ["江汉路", "楚河汉街", "光谷", "汉口江滩"],
    seasonalHooks: ["樱花季", "江滩夜风", "暑期夜游", "周末看江"],
    hotSearches: ["武汉夜宵", "江汉路小吃", "武汉朋友聚会", "江滩散步"],
    featuredRoutes: ["江汉路到江滩", "看江到夜宵", "光谷聚会局"],
    visualTheme: { primary: "#d95d30", primaryDark: "#1b3558", background: "#fff8f3", soft: "#ffe6d8" },
    open: true
  },
  {
    cityCode: "610100",
    cityName: "西安",
    province: "陕西",
    longitude: 108.9398,
    latitude: 34.3416,
    heroTitle: "古城不只看城墙，西安要把夜游和碳水局一起安排",
    cityTagline: "更适合文化夜游型推荐，先逛后吃，节奏比单点更重要。",
    cultureTags: ["古都", "城墙", "回民街", "夜游"],
    defaultScenes: ["城墙夜游", "朋友聚餐", "深夜碳水", "周末文化线"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "轰趴馆"],
    priceTone: "路线型消费和夜游成交路径更强。",
    featuredDistricts: ["钟楼", "大雁塔", "曲江", "小寨"],
    seasonalHooks: ["春日城墙", "暑期夜游", "秋日博物馆", "冬天羊肉泡馍"],
    hotSearches: ["西安夜游", "肉夹馍", "大雁塔晚餐", "钟楼小吃"],
    featuredRoutes: ["城墙夜游到碳水", "大雁塔夜景晚餐", "小寨年轻聚餐"],
    visualTheme: { primary: "#d96b3c", primaryDark: "#432818", background: "#fff8f1", soft: "#ffe6d4" },
    open: true
  },
  {
    cityCode: "500100",
    cityName: "重庆",
    province: "重庆",
    longitude: 106.5516,
    latitude: 29.563,
    heroTitle: "山城路线很重要，吃喝也要顺路上坡下坡",
    cityTagline: "把夜景、火锅、交通和坡坎路线一起算，少走冤枉路。",
    cultureTags: ["山城", "火锅", "夜景", "江湖菜"],
    defaultScenes: ["火锅局", "夜景路线", "朋友聚会", "周末江边"],
    primaryCategories: ["美食", "酒吧", "按摩·足疗", "亲子游乐"],
    priceTone: "夜经济和目的地型消费更强。",
    featuredDistricts: ["解放碑", "观音桥", "洪崖洞", "南滨路"],
    seasonalHooks: ["雾都夜景", "夏夜江边", "火锅季", "周末轻轨线"],
    hotSearches: ["重庆火锅", "洪崖洞夜景", "观音桥聚餐", "南滨路晚餐"],
    featuredRoutes: ["解放碑到洪崖洞", "火锅后看夜景", "轻轨沿线吃喝"],
    visualTheme: { primary: "#e65335", primaryDark: "#2c1b18", background: "#fff7f3", soft: "#ffe0d8" },
    open: true
  },
  {
    cityCode: "430100",
    cityName: "长沙",
    province: "湖南",
    longitude: 112.9388,
    latitude: 28.2282,
    heroTitle: "年轻、热辣、好拍，长沙消费从傍晚开始升温",
    cityTagline: "夜生活、茶饮、小吃和年轻社交是长沙版本的主线。",
    cultureTags: ["年轻夜生活", "湘味", "茶饮", "潮流"],
    defaultScenes: ["夜宵", "朋友聚会", "周末打卡", "下班小吃"],
    primaryCategories: ["美食", "酒吧", "KTV", "美容SPA"],
    priceTone: "高频低决策，适合优惠券和榜单推动成交。",
    featuredDistricts: ["五一广场", "黄兴路", "坡子街", "梅溪湖"],
    seasonalHooks: ["夏夜夜宵", "节日打卡", "周末茶饮", "演唱会前后"],
    hotSearches: ["长沙夜宵", "五一广场小吃", "长沙茶饮", "湘菜聚餐"],
    featuredRoutes: ["五一广场吃喝一晚", "湘菜到夜宵", "演唱会后续摊"],
    visualTheme: { primary: "#ff6b35", primaryDark: "#2d3436", background: "#fff8f4", soft: "#ffe4d8" },
    open: true
  },
  {
    cityCode: "120100",
    cityName: "天津",
    province: "天津",
    longitude: 117.2009,
    latitude: 39.0842,
    heroTitle: "海河、相声和老城烟火，天津适合慢慢逛也适合痛快吃",
    cityTagline: "把海河夜景、老城小吃和朋友聚会连在一起。",
    cultureTags: ["海河", "相声", "老城", "津味"],
    defaultScenes: ["海河夜游", "老城小吃", "朋友聚会", "周末亲子"],
    primaryCategories: ["美食", "酒吧", "亲子游乐", "KTV"],
    priceTone: "熟人聚餐和文化路线更适合转化。",
    featuredDistricts: ["五大道", "意式风情区", "滨江道", "南市"],
    seasonalHooks: ["海河夜风", "夏日滨海", "周末相声", "冬天锅气"],
    hotSearches: ["天津小吃", "海河晚餐", "五大道咖啡", "相声前后吃饭"],
    featuredRoutes: ["五大道到晚餐", "海河夜景一条线", "相声前后不绕路"],
    visualTheme: { primary: "#d9643a", primaryDark: "#16404d", background: "#fff9f3", soft: "#dff1f2" },
    open: true
  }
];

axios.defaults.baseURL = commonURL;
axios.defaults.timeout = 10000;

axios.interceptors.request.use(
  config => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers["authorization"] = token;
    }
    return config;
  },
  error => Promise.reject(error)
);

axios.interceptors.response.use(
  response => {
    if (!response.data.success) {
      const businessMsg = response.data.errorMsg || response.data.message || "请求失败，请稍后重试";
      return Promise.reject(businessMsg);
    }
    return response.data;
  },
  error => {
    if (error.response && error.response.status === 401) {
      if (window.authHelper) {
        window.authHelper.clearAuth();
      } else {
        localStorage.removeItem("token");
      }
      const redirectTarget = location.pathname + location.search;
      const loginUrl = window.authHelper
        ? window.authHelper.buildLoginUrl(redirectTarget)
        : ("/pages/auth/login.html?redirect=" + encodeURIComponent(redirectTarget));
      setTimeout(() => {
        location.replace(loginUrl);
      }, 120);
      return Promise.reject("请先登录");
    }

    if (error.response && error.response.data) {
      const payload = error.response.data;
      if (typeof payload === "string") {
        return Promise.reject(payload);
      }
      return Promise.reject(payload.errorMsg || payload.message || "请求失败，请稍后重试");
    }

    if (error.message) {
      return Promise.reject(error.message);
    }
    return Promise.reject(error);
  }
);

axios.defaults.paramsSerializer = function(params) {
  return Object.keys(params || {})
    .filter(key => params[key] !== undefined && params[key] !== null && params[key] !== "")
    .map(key => encodeURIComponent(key) + "=" + encodeURIComponent(params[key]))
    .join("&");
};

const util = {
  commonURL,
  fallbackCityProfiles: HMDP_FALLBACK_CITY_PROFILES,
  defaultCityCode: HMDP_DEFAULT_CITY_CODE,
  getUrlParam(name) {
    const reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    const r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURIComponent(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === "string") {
      if (isNaN(val)) {
        return null;
      }
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        p = val + "00";
      } else if (index === val.length - 2) {
        p = val.replace(".", "") + "0";
      } else {
        p = val.replace(".", "");
      }
      return parseInt(p, 10);
    }
    if (typeof val === "number") {
      if (!val) {
        return null;
      }
      const s = val + "";
      if (s.length === 0) {
        return "0.00";
      }
      if (s.length === 1) {
        return "0.0" + val;
      }
      if (s.length === 2) {
        return "0." + val;
      }
      const i = s.indexOf(".");
      if (i < 0) {
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2);
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2);
      }
    }
    return val;
  },
  buildQuery(params) {
    if (!params || typeof params !== "object") {
      return "";
    }
    return Object.keys(params)
      .filter(key => params[key] !== undefined && params[key] !== null && params[key] !== "")
      .map(key => encodeURIComponent(key) + "=" + encodeURIComponent(params[key]))
      .join("&");
  },
  buildUrl(path, params) {
    const query = this.buildQuery(params);
    if (!query) {
      return path || "";
    }
    return (path || "") + ((path || "").indexOf("?") > -1 ? "&" : "?") + query;
  },
  normalizeCityName(cityName) {
    return String(cityName || "").trim().replace(/市$/, "");
  },
  normalizeCityProfile(profile) {
    const source = profile || {};
    const isGenericCity = source.genericCity === true
      || source.cityEditionEnabled === false
      || source.cityHubEnabled === false
      || source.open === false;
    const cityCode = source.cityCode || source.code || source.adcode || (isGenericCity ? "" : HMDP_DEFAULT_CITY_CODE);
    const cityName = this.normalizeCityName(source.cityName || source.name || source.city || "长沙");
    const theme = source.visualTheme || {};
    return Object.assign({}, source, {
      cityCode,
      code: cityCode,
      cityName,
      name: cityName,
      province: source.province || cityName,
      longitude: Number(source.longitude || source.lng || source.x || 112.9388),
      latitude: Number(source.latitude || source.lat || source.y || 28.2282),
      heroTitle: source.heroTitle || (cityName + "今天该怎么消费"),
      cityTagline: source.cityTagline || "懂这座城，才知道今天去哪。",
      cultureTags: Array.isArray(source.cultureTags) ? source.cultureTags : [],
      defaultScenes: Array.isArray(source.defaultScenes) ? source.defaultScenes : [],
      primaryCategories: Array.isArray(source.primaryCategories) ? source.primaryCategories : [],
      featuredDistricts: Array.isArray(source.featuredDistricts) ? source.featuredDistricts : [],
      seasonalHooks: Array.isArray(source.seasonalHooks) ? source.seasonalHooks : [],
      hotSearches: Array.isArray(source.hotSearches) ? source.hotSearches : [],
      featuredRoutes: Array.isArray(source.featuredRoutes) ? source.featuredRoutes : [],
      visualTheme: {
        primary: theme.primary || "#ff6b35",
        primaryLight: theme.primaryLight || theme.soft || "#ff8c61",
        primaryDark: theme.primaryDark || "#2d3436",
        background: theme.background || "#fff8f4",
        soft: theme.soft || "#ffe4d8"
      },
      description: source.description || source.cityTagline || "",
      open: !isGenericCity && source.open !== false,
      cityEditionEnabled: !isGenericCity && !!cityCode && source.cityEditionEnabled !== false,
      cityHubEnabled: !isGenericCity && !!cityCode && source.cityHubEnabled !== false,
      genericCity: isGenericCity
    });
  },
  findCityProfile(input, cityList) {
    const source = typeof input === "string"
      ? { cityCode: input, cityName: input, name: input, city: input }
      : (input || {});
    const key = String(source.cityCode || source.code || source.adcode || "").trim();
    const name = this.normalizeCityName(source.cityName || source.name || source.city || "");
    const candidates = Array.isArray(cityList) && cityList.length ? cityList : HMDP_FALLBACK_CITY_PROFILES;
    const matched = candidates.find(city => {
      const normalized = this.normalizeCityProfile(city);
      const cityCode = String(normalized.cityCode || "");
      const sameCode = key && (
        cityCode === key
        || normalized.adcode === key
        || (key.length >= 4 && cityCode.indexOf(key.substring(0, 4)) === 0)
      );
      return normalized.open !== false && (sameCode || (name && normalized.cityName === name));
    });
    return matched ? this.normalizeCityProfile(matched) : null;
  },
  createGenericCityProfile(input) {
    const source = input || {};
    const cityName = this.normalizeCityName(source.cityName || source.name || source.city || "当前位置");
    const lng = Number(source.longitude || source.lng || source.x);
    const lat = Number(source.latitude || source.lat || source.y);
    return this.normalizeCityProfile({
      cityCode: "",
      code: "",
      cityName,
      name: cityName,
      province: source.province || cityName,
      longitude: Number.isFinite(lng) ? lng : 112.9388,
      latitude: Number.isFinite(lat) ? lat : 28.2282,
      heroTitle: cityName + "附近好店",
      cityTagline: "当前城市暂未开通城市馆，先按原来的附近、分类和搜索体验使用。",
      cultureTags: [],
      defaultScenes: [],
      primaryCategories: [],
      featuredDistricts: [],
      seasonalHooks: [],
      hotSearches: [],
      featuredRoutes: [],
      visualTheme: { primary: "#ff6b35", primaryDark: "#2d3436", background: "#fff8f4", soft: "#ffe4d8" },
      open: false,
      cityEditionEnabled: false,
      cityHubEnabled: false,
      genericCity: true
    });
  },
  isCityEditionEnabled(profile, cityList) {
    const normalized = this.normalizeCityProfile(profile);
    if (!normalized.cityCode || normalized.open === false || normalized.cityEditionEnabled === false || normalized.genericCity) {
      return false;
    }
    return !!this.findCityProfile(normalized, cityList);
  },
  getActiveCityCode(profile, cityList) {
    return this.isCityEditionEnabled(profile, cityList)
      ? this.normalizeCityProfile(profile).cityCode
      : "";
  },
  getFallbackCityProfile(cityCodeOrName) {
    const key = String(cityCodeOrName || "").trim();
    const normalizedName = this.normalizeCityName(key);
    const matched = HMDP_FALLBACK_CITY_PROFILES.find(city =>
      city.cityCode === key
      || city.code === key
      || city.cityName === normalizedName
      || city.name === normalizedName
      || (key.length >= 4 && city.cityCode.indexOf(key.substring(0, 4)) === 0)
    );
    return this.normalizeCityProfile(matched || HMDP_FALLBACK_CITY_PROFILES.find(city => city.cityCode === HMDP_DEFAULT_CITY_CODE));
  },
  normalizeCityOverview(overview) {
    const normalized = this.normalizeCityProfile(overview);
    return Object.assign({}, overview || {}, normalized, {
      title: (overview && overview.title) || normalized.heroTitle,
      tagline: (overview && overview.tagline) || normalized.cityTagline,
      tags: (overview && (overview.tags || overview.cultureTags)) || normalized.cultureTags,
      districts: (overview && (overview.districts || overview.featuredDistricts)) || normalized.featuredDistricts,
      routes: (overview && (overview.routes || overview.featuredRoutes)) || normalized.featuredRoutes
    });
  },
  fallbackCityList() {
    const cities = HMDP_FALLBACK_CITY_PROFILES.map(city => this.normalizeCityOverview(city));
    return {
      cities,
      hotCities: cities.slice(0, 8),
      total: cities.length,
      brandClaim: "懂这座城，才知道今天去哪"
    };
  },
  fetchCityList() {
    if (!window.axios) {
      return Promise.resolve(this.fallbackCityList());
    }
    return axios.get("/city/list").then((result) => {
      const data = this.extractResultData(result);
      const fallback = this.fallbackCityList();
      const cities = Array.isArray(data.cities) && data.cities.length ? data.cities : fallback.cities;
      const hotCities = Array.isArray(data.hotCities) && data.hotCities.length ? data.hotCities : cities.slice(0, 8);
      return {
        cities: cities.map(city => this.normalizeCityOverview(city)),
        hotCities: hotCities.map(city => this.normalizeCityOverview(city)),
        total: data.total || cities.length,
        brandClaim: data.brandClaim || fallback.brandClaim
      };
    }).catch(() => this.fallbackCityList());
  },
  fetchCityProfile(cityCode) {
    const fallback = this.getFallbackCityProfile(cityCode);
    if (!window.axios || !cityCode) {
      return Promise.resolve(fallback);
    }
    return axios.get("/city/" + encodeURIComponent(cityCode))
      .then((result) => this.normalizeCityProfile(this.extractResultData(result)))
      .catch(() => fallback);
  },
  fetchCityScenes(cityCode) {
    if (!window.axios || !cityCode) {
      return Promise.resolve([]);
    }
    return axios.get("/city/" + encodeURIComponent(cityCode) + "/scenes")
      .then((result) => {
        const data = this.extractResultData(result);
        return Array.isArray(data) ? data : [];
      })
      .catch(() => []);
  },
  resolveCityProfile(input, cityList) {
    const source = input || {};
    if (typeof input === "string") {
      return this.findCityProfile(input, cityList) || this.getFallbackCityProfile(input);
    }
    const key = String(source.cityCode || source.code || source.adcode || "").trim();
    const name = this.normalizeCityName(source.cityName || source.name || source.city || "");
    return this.findCityProfile(input, cityList) || this.getFallbackCityProfile(key || name || HMDP_DEFAULT_CITY_CODE);
  },
  readCurrentCityProfile() {
    const savedProfile = this.readStorageJSON("currentCityProfile");
    if (savedProfile && (savedProfile.cityCode || savedProfile.cityName || savedProfile.name)) {
      return this.normalizeCityProfile(savedProfile);
    }
    const savedCity = this.readStorageJSON("currentCity");
    if (savedCity && typeof savedCity === "object") {
      return this.findCityProfile(savedCity) || this.createGenericCityProfile(savedCity);
    }
    const rawCity = localStorage.getItem("currentCity");
    if (rawCity && rawCity.charAt(0) !== "{") {
      return this.findCityProfile(rawCity) || this.createGenericCityProfile({ cityName: rawCity });
    }
    return this.getFallbackCityProfile(HMDP_DEFAULT_CITY_CODE);
  },
  saveCurrentCityProfile(profile, extra) {
    const normalized = this.normalizeCityProfile(profile);
    const payload = Object.assign({
      name: normalized.cityName,
      cityName: normalized.cityName,
      cityCode: normalized.cityCode,
      province: normalized.province,
      city: normalized.cityName,
      longitude: normalized.longitude,
      latitude: normalized.latitude,
      cityEditionEnabled: normalized.cityEditionEnabled !== false,
      cityHubEnabled: normalized.cityHubEnabled !== false,
      genericCity: normalized.genericCity === true
    }, extra || {});
    this.writeStorageJSON("currentCity", payload);
    this.writeStorageJSON("currentCityProfile", normalized);
    return normalized;
  },
  normalizeLocationContext(context) {
    const source = context || {};
    const lng = Number(source.longitude !== undefined ? source.longitude : (source.lng !== undefined ? source.lng : source.x));
    const lat = Number(source.latitude !== undefined ? source.latitude : (source.lat !== undefined ? source.lat : source.y));
    const city = this.normalizeCityName(source.city || source.cityName || source.name || source.province || "当前位置");
    const cityProfile = source.cityProfile ? this.normalizeCityProfile(source.cityProfile) : null;
    return {
      longitude: Number.isFinite(lng) ? lng : null,
      latitude: Number.isFinite(lat) ? lat : null,
      x: Number.isFinite(lng) ? lng : null,
      y: Number.isFinite(lat) ? lat : null,
      accuracy: source.accuracy || null,
      source: source.source || "unknown",
      provider: source.provider || "amap",
      province: this.normalizeCityName(source.province || ""),
      city,
      district: this.normalizeCityName(source.district || ""),
      adcode: source.adcode || "",
      cityCode: source.cityCode || "",
      formattedAddress: source.formattedAddress || source.address || "",
      amapAvailable: source.amapAvailable === true,
      confidence: source.confidence || "unknown",
      cityEditionEnabled: source.cityEditionEnabled === true,
      cityProfile
    };
  },
  getProfileForLocationContext(context, cityList) {
    const normalized = this.normalizeLocationContext(context);
    if (normalized.cityEditionEnabled && normalized.cityProfile) {
      return normalized.cityProfile;
    }
    const matched = normalized.cityEditionEnabled !== false
      ? this.findCityProfile({
        cityCode: normalized.cityCode,
        adcode: normalized.adcode,
        city: normalized.city,
        cityName: normalized.city,
        province: normalized.province
      }, cityList)
      : null;
    return matched || this.createGenericCityProfile({
      cityName: normalized.city || normalized.province || "当前位置",
      city: normalized.city,
      province: normalized.province,
      district: normalized.district,
      adcode: normalized.adcode,
      cityCode: normalized.cityCode,
      x: normalized.longitude,
      y: normalized.latitude
    });
  },
  saveLocationContext(context, cityList) {
    const normalized = this.normalizeLocationContext(context);
    const profile = this.getProfileForLocationContext(normalized, cityList);
    const payload = Object.assign({}, normalized, {
      ts: Date.now(),
      cityProfile: normalized.cityEditionEnabled ? profile : null,
      cityEditionEnabled: this.isCityEditionEnabled(profile, cityList)
    });
    this.writeStorageJSON(HMDP_LOCATION_CONTEXT_KEY, payload);
    if (normalized.longitude !== null && normalized.latitude !== null) {
      this.writeStorageJSON("userLocation", {
        x: normalized.longitude,
        y: normalized.latitude,
        longitude: normalized.longitude,
        latitude: normalized.latitude,
        accuracy: normalized.accuracy,
        province: normalized.province,
        city: normalized.city,
        district: normalized.district,
        adcode: normalized.adcode,
        cityCode: normalized.cityCode,
        formattedAddress: normalized.formattedAddress
      });
    }
    this.saveCurrentCityProfile(profile, {
      name: normalized.city || profile.cityName,
      city: normalized.city || profile.cityName,
      province: normalized.province || profile.province,
      district: normalized.district,
      adcode: normalized.adcode,
      cityCode: payload.cityEditionEnabled ? profile.cityCode : normalized.cityCode,
      x: normalized.longitude,
      y: normalized.latitude,
      longitude: normalized.longitude,
      latitude: normalized.latitude,
      formattedAddress: normalized.formattedAddress,
      cityEditionEnabled: payload.cityEditionEnabled,
      cityHubEnabled: payload.cityEditionEnabled,
      genericCity: !payload.cityEditionEnabled
    });
    return { context: payload, profile };
  },
  readLocationContext() {
    const cached = this.readStorageJSON(HMDP_LOCATION_CONTEXT_KEY);
    if (!cached || !cached.ts || Date.now() - cached.ts > HMDP_LOCATION_CONTEXT_TTL) {
      return null;
    }
    return cached;
  },
  resolveBrowserPosition(options) {
    const opts = options || {};
    const timeout = opts.timeout || 10000;
    return new Promise((resolve, reject) => {
      if (window.AMap && typeof window.AMap.Geolocation === "function") {
        try {
          const geolocation = new window.AMap.Geolocation({
            enableHighAccuracy: true,
            timeout,
            convert: true,
            showButton: false,
            showMarker: false,
            showCircle: false
          });
          geolocation.getCurrentPosition((status, result) => {
            if (status === "complete" && result && result.position) {
              resolve({
                longitude: result.position.lng,
                latitude: result.position.lat,
                accuracy: result.accuracy || result.position.accuracy,
                source: "amap-js"
              });
              return;
            }
            reject(result || new Error("AMap geolocation failed"));
          });
          return;
        } catch (e) {
        }
      }
      if (!navigator.geolocation) {
        reject(new Error("Geolocation unsupported"));
        return;
      }
      navigator.geolocation.getCurrentPosition((position) => {
        resolve({
          longitude: position.coords.longitude,
          latitude: position.coords.latitude,
          accuracy: position.coords.accuracy,
          source: "browser"
        });
      }, reject, {
        enableHighAccuracy: true,
        timeout,
        maximumAge: opts.maximumAge || 300000
      });
    });
  },
  resolveLocationByCoordinates(position) {
    const point = position || {};
    return axios.get("/map/location/resolve", {
      params: {
        longitude: point.longitude,
        latitude: point.latitude,
        accuracy: point.accuracy,
        source: point.source || "browser"
      }
    }).then((result) => this.extractResultData(result));
  },
  resolveIpLocation() {
    return axios.get("/map/location/ip").then((result) => this.extractResultData(result));
  },
  resolveUserLocation(options) {
    const opts = options || {};
    const cached = !opts.force ? this.readLocationContext() : null;
    if (cached) {
      return Promise.resolve(this.saveLocationContext(cached, opts.cityList));
    }
    const useIpFallback = opts.allowIpFallback !== false;
    return this.resolveBrowserPosition(opts)
      .then((position) => this.resolveLocationByCoordinates(position)
        .catch(() => Object.assign({}, position, {
          provider: "browser",
          amapAvailable: false,
          confidence: "medium"
        })))
      .catch((error) => {
        if (!useIpFallback) {
          return Promise.reject(error);
        }
        return this.resolveIpLocation();
      })
      .then((context) => {
        if (!context || (!context.city && !context.province && context.longitude == null)) {
          return Promise.reject(new Error("Location context unavailable"));
        }
        return this.saveLocationContext(context, opts.cityList);
      })
      .catch(() => {
        const stale = this.readStorageJSON(HMDP_LOCATION_CONTEXT_KEY);
        if (stale) {
          return this.saveLocationContext(stale, opts.cityList);
        }
        const profile = this.readCurrentCityProfile();
        return {
          context: null,
          profile
        };
      });
  },
  applyCityTheme(profile) {
    const normalized = this.normalizeCityProfile(profile);
    const theme = normalized.visualTheme || {};
    if (!document || !document.documentElement) {
      return normalized;
    }
    const root = document.documentElement;
    root.style.setProperty("--primary", theme.primary || "#ff6b35");
    root.style.setProperty("--primary-light", theme.primaryLight || theme.soft || "#ff8c61");
    root.style.setProperty("--primary-dark", theme.primaryDark || "#2d3436");
    root.style.setProperty("--accent", theme.primaryDark || "#00b894");
    root.style.setProperty("--city-bg", theme.background || "#fff8f4");
    root.style.setProperty("--city-soft", theme.soft || "#ffe4d8");
    return normalized;
  },
  normalizeAmapRouteMode(mode) {
    const modeMap = {
      driving: "car",
      car: "car",
      walking: "walk",
      walk: "walk",
      riding: "ride",
      ride: "ride",
      transit: "bus",
      bus: "bus"
    };
    return modeMap[String(mode || "").toLowerCase()] || "car";
  },
  getShopCoordinate(shop, axis) {
    if (!shop) {
      return null;
    }
    const value = axis === "x"
      ? (shop.x !== undefined && shop.x !== null && shop.x !== "" ? shop.x : shop.longitude)
      : (shop.y !== undefined && shop.y !== null && shop.y !== "" ? shop.y : shop.latitude);
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  },
  getShopLngLat(shop) {
    const lng = this.getShopCoordinate(shop, "x");
    const lat = this.getShopCoordinate(shop, "y");
    if (lng === null || lat === null) {
      return null;
    }
    if (lng < -180 || lng > 180 || lat < -90 || lat > 90 || (lng === 0 && lat === 0)) {
      return null;
    }
    return [lng, lat];
  },
  normalizeAmapText(value) {
    return String(value === undefined || value === null ? "" : value).trim();
  },
  getShopLocationKeyword(shop) {
    if (!shop) {
      return "";
    }
    const parts = [this.normalizeAmapText(shop.name), this.normalizeAmapText(shop.address)]
      .filter(item => item !== "暂无地址")
      .filter(Boolean);
    return Array.from(new Set(parts)).join(" ");
  },
  buildAmapNavigationPayload(shop, options) {
    const opts = options || {};
    const point = this.getShopLngLat(shop);
    const address = this.normalizeAmapText(shop && shop.address);
    return {
      id: shop && shop.id != null ? shop.id : null,
      name: this.normalizeAmapText(shop && shop.name) || "目标店铺",
      address: address === "暂无地址" ? "" : address,
      longitude: point ? point[0] : null,
      latitude: point ? point[1] : null,
      mode: this.normalizeAmapRouteMode(opts.mode),
      policy: opts.policy === undefined || opts.policy === null ? 0 : opts.policy,
      source: opts.source || "hmdp"
    };
  },
  buildAmapNavigationUrl(shop, options) {
    const opts = options || {};
    const point = this.getShopLngLat(shop);
    if (!point) {
      return "";
    }
    const name = this.normalizeAmapText(shop && shop.name) || "目标店铺";
    return this.buildUrl("https://uri.amap.com/navigation", {
      to: point[0] + "," + point[1] + "," + name,
      mode: this.normalizeAmapRouteMode(opts.mode),
      policy: opts.policy === undefined || opts.policy === null ? 0 : opts.policy,
      src: opts.source || "hmdp",
      callnative: 1
    });
  },
  buildAmapSearchUrl(shop, options) {
    const opts = options || {};
    const keyword = this.getShopLocationKeyword(shop);
    if (!keyword) {
      return "";
    }
    return this.buildUrl("https://uri.amap.com/search", {
      keyword,
      view: "map",
      src: opts.source || "hmdp",
      callnative: 1
    });
  },
  parseAmapLocation(value) {
    const parts = String(value || "").split(",");
    if (parts.length < 2) {
      return null;
    }
    const lng = Number(parts[0]);
    const lat = Number(parts[1]);
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
      return null;
    }
    return [lng, lat];
  },
  resolveAmapPlace(shop, options) {
    const keyword = this.getShopLocationKeyword(shop);
    if (!keyword || !window.axios) {
      return Promise.resolve(null);
    }
    const opts = options || {};
    const city = this.normalizeAmapText(opts.city);
    const hasCity = !!city && city.indexOf("定位") === -1 && city.indexOf("未知") === -1;
    return axios.get("/map/place-text", {
      params: {
        keyword,
        offset: 1,
        page: 1,
        citylimit: hasCity,
        city: hasCity ? city : ""
      }
    }).then((result) => {
      const data = this.extractResultData(result);
      const pois = Array.isArray(data.pois) ? data.pois : [];
      if (!pois.length) {
        return null;
      }
      const poi = pois[0];
      const point = this.parseAmapLocation(poi.location || (poi.x && poi.y ? poi.x + "," + poi.y : ""));
      if (!point) {
        return null;
      }
      return Object.assign({}, shop || {}, {
        name: shop && shop.name ? shop.name : (poi.name || "目标店铺"),
        address: shop && shop.address ? shop.address : (poi.address || ""),
        x: point[0],
        y: point[1],
        longitude: point[0],
        latitude: point[1]
      });
    }).catch(() => null);
  },
  invokeNativeAmapNavigation(shop, options) {
    const bridge = window.HmdpNative && window.HmdpNative.openAmapNavigation;
    if (typeof bridge !== "function") {
      return false;
    }
    try {
      const payload = this.buildAmapNavigationPayload(shop || {}, options);
      const result = bridge.call(window.HmdpNative, JSON.stringify(payload));
      return result !== false;
    } catch (e) {
      return false;
    }
  },
  isRestrictedAmapBrowser() {
    const ua = (navigator && navigator.userAgent ? navigator.userAgent : "").toLowerCase();
    return ua.indexOf("micromessenger") > -1 || /qq\//.test(ua);
  },
  showAmapMessage(message, type) {
    if (!message) {
      return;
    }
    const level = type || "info";
    if (window.ELEMENT && typeof window.ELEMENT.Message === "function") {
      window.ELEMENT.Message[level] ? window.ELEMENT.Message[level](message) : window.ELEMENT.Message(message);
      return;
    }
    if (window.Vue && window.Vue.prototype && typeof window.Vue.prototype.$message === "function") {
      const messenger = window.Vue.prototype.$message;
      messenger[level] ? messenger[level](message) : messenger(message);
    }
  },
  openResolvedAmapNavigation(shop, options) {
    if (this.invokeNativeAmapNavigation(shop, options)) {
      return true;
    }
    const navigationUrl = this.buildAmapNavigationUrl(shop, options);
    if (navigationUrl) {
      if (this.isRestrictedAmapBrowser()) {
        this.showAmapMessage("如未唤起高德，请在系统浏览器中打开", "warning");
      }
      window.location.href = navigationUrl;
      return true;
    }
    const searchUrl = this.buildAmapSearchUrl(shop, options);
    if (searchUrl) {
      this.showAmapMessage("未找到精确坐标，正在用店名打开高德搜索", "warning");
      window.location.href = searchUrl;
      return true;
    }
    this.showAmapMessage("店铺位置不可用", "error");
    return false;
  },
  openAmapNavigation(shop, options) {
    const opts = options || {};
    if (!shop || (!this.getShopLocationKeyword(shop) && !this.getShopLngLat(shop))) {
      this.showAmapMessage("店铺位置不可用", "error");
      return Promise.resolve(false);
    }
    if (this.invokeNativeAmapNavigation(shop, opts)) {
      return Promise.resolve(true);
    }
    if (this.getShopLngLat(shop)) {
      return Promise.resolve(this.openResolvedAmapNavigation(shop, opts));
    }
    return this.resolveAmapPlace(shop, opts).then((resolvedShop) => {
      return this.openResolvedAmapNavigation(resolvedShop || shop, opts);
    });
  },
  extractResultData(result) {
    if (!result || typeof result !== "object") {
      return {};
    }
    return result.data || {};
  },
  mergeUnique(existing, incoming, keyBuilder) {
    const merged = Array.isArray(existing) ? existing.slice() : [];
    const resolveKey = typeof keyBuilder === "function"
      ? keyBuilder
      : (item) => (item && item.id != null ? String(item.id) : JSON.stringify(item));
    const seen = new Set(merged.map(item => resolveKey(item)));
    (incoming || []).forEach((item) => {
      const key = resolveKey(item);
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
      merged.push(item);
    });
    return merged;
  },
  isNearPageBottom(threshold) {
    const offset = typeof threshold === "number" ? threshold : 140;
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
    const clientHeight = window.innerHeight || document.documentElement.clientHeight || document.body.clientHeight || 0;
    const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight || 0;
    return scrollTop + clientHeight + offset >= scrollHeight;
  },
  formatDistance(distance) {
    if (distance === undefined || distance === null || distance === "") {
      return "";
    }
    const value = Number(distance);
    if (!isFinite(value)) {
      return String(distance);
    }
    if (value < 1000) {
      return Math.round(value) + "m";
    }
    return (value / 1000).toFixed(1) + "km";
  },
  readStorageJSON(key, fallback) {
    const defaultValue = fallback === undefined ? null : fallback;
    if (!key) {
      return defaultValue;
    }
    const raw = localStorage.getItem(key);
    if (!raw) {
      return defaultValue;
    }
    try {
      return JSON.parse(raw);
    } catch (e) {
      return defaultValue;
    }
  },
  writeStorageJSON(key, value) {
    if (!key) {
      return;
    }
    localStorage.setItem(key, JSON.stringify(value));
  },
  removeStorageItems(keys) {
    const list = Array.isArray(keys) ? keys : [keys];
    list.filter(Boolean).forEach((key) => {
      localStorage.removeItem(key);
    });
  },
  hasToken() {
    if (window.authHelper && typeof window.authHelper.getToken === "function") {
      return !!window.authHelper.getToken();
    }
    return !!localStorage.getItem("token");
  },
  getErrorMessage(err, fallback) {
    if (typeof err === "string") {
      return err;
    }
    if (err && err.response && err.response.data) {
      const payload = err.response.data;
      if (typeof payload === "string") {
        return payload;
      }
      if (payload.errorMsg || payload.message) {
        return payload.errorMsg || payload.message;
      }
    }
    if (err && err.message) {
      return err.message;
    }
    return fallback || "操作失败，请稍后重试";
  },
  normalizeAssetPath(path) {
    if (!path) {
      return "";
    }
    const value = String(path).trim().replace(/^['"]+|['"]+$/g, "");
    if (!value) {
      return "";
    }
    if (value.startsWith("http") || value.startsWith("data:")) {
      return value;
    }
    if (value.startsWith(commonURL)) {
      return value;
    }
    // Reject garbage paths: no dot (no file extension) and contains non-ASCII
    if (value.indexOf(".") === -1 && /[^\x00-\x7F]/.test(value)) {
      return "";
    }
    if (value.startsWith("/blogs/") || value.startsWith("/icons/")) {
      return "/imgs" + value;
    }
    if (value.startsWith("blogs/") || value.startsWith("icons/")) {
      return "/imgs/" + value;
    }
    return value.startsWith("/") ? value : "/" + value;
  },
  resolveImageUrl(path, fallback) {
    const normalized = this.normalizeAssetPath(path);
    const defaultImage = fallback || "/imgs/icons/default-icon.png";
    if (!normalized) {
      return defaultImage;
    }
    if (normalized.startsWith("http") || normalized.startsWith("data:")) {
      return normalized;
    }
    return commonURL + normalized;
  },
  applyImageFallback(event, fallback) {
    if (!event || !event.target) {
      return;
    }
    if (event.target.dataset.fallbackApplied === "1") {
      return;
    }
    event.target.dataset.fallbackApplied = "1";
    event.target.src = fallback || "/imgs/icons/default-icon.png";
  },
  csvToImageList(value) {
    if (!value) {
      return [];
    }
    if (Array.isArray(value)) {
      return value.filter(Boolean);
    }
    return String(value)
      .split(",")
      .map(item => item.trim())
      .filter(Boolean);
  },
  imageListToCsv(list) {
    if (!Array.isArray(list)) {
      return "";
    }
    return list
      .map(item => String(item || "").trim())
      .filter(Boolean)
      .join(",");
  },
  validateImageFile(file, options) {
    if (!file) {
      return "请选择图片";
    }
    const opts = options || {};
    const validTypes = opts.types || ["image/jpeg", "image/png", "image/gif", "image/jpg", "image/webp"];
    if (validTypes.indexOf(file.type) === -1) {
      return opts.typeMessage || "请上传 jpg、png、gif 或 webp 格式的图片";
    }
    const maxSize = typeof opts.maxSize === "number" ? opts.maxSize : 5 * 1024 * 1024;
    if (!opts.skipSizeCheck && file.size > maxSize) {
      const sizeMb = (maxSize / 1024 / 1024).toFixed(0);
      return opts.sizeMessage || ("图片大小不能超过 " + sizeMb + "MB");
    }
    return "";
  },
  compressImageFile(file, options) {
    const opts = options || {};
    const maxUploadSize = typeof opts.maxUploadSize === "number" ? opts.maxUploadSize : 5 * 1024 * 1024;
    const maxOriginalSize = typeof opts.maxOriginalSize === "number" ? opts.maxOriginalSize : 20 * 1024 * 1024;
    const threshold = typeof opts.compressThreshold === "number" ? opts.compressThreshold : 900 * 1024;
    const maxDimension = typeof opts.maxDimension === "number" ? opts.maxDimension : 1600;
    const quality = typeof opts.quality === "number" ? opts.quality : 0.82;
    const outputType = opts.outputType || "image/jpeg";
    const compressibleTypes = ["image/jpeg", "image/jpg", "image/png", "image/webp"];

    if (!file) {
      return Promise.reject("请选择图片");
    }
    if (file.size > maxOriginalSize) {
      const sizeMb = (maxOriginalSize / 1024 / 1024).toFixed(0);
      return Promise.reject("原图太大，请选择 " + sizeMb + "MB 以内的图片");
    }
    if (file.size <= threshold || compressibleTypes.indexOf(file.type) === -1) {
      return Promise.resolve(file);
    }

    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onerror = () => resolve(file);
      reader.onload = () => {
        const image = new Image();
        image.onerror = () => resolve(file);
        image.onload = () => {
          const ratio = Math.min(1, maxDimension / Math.max(image.width, image.height));
          const width = Math.max(1, Math.round(image.width * ratio));
          const height = Math.max(1, Math.round(image.height * ratio));
          const canvas = document.createElement("canvas");
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext("2d");
          if (!ctx || !canvas.toBlob) {
            resolve(file);
            return;
          }
          ctx.fillStyle = "#fff";
          ctx.fillRect(0, 0, width, height);
          ctx.drawImage(image, 0, 0, width, height);
          canvas.toBlob((blob) => {
            if (!blob) {
              resolve(file);
              return;
            }
            const compressedName = String(file.name || "image").replace(/\.[^.]+$/, "") + ".jpg";
            const compressedFile = new File([blob], compressedName, {
              type: outputType,
              lastModified: Date.now()
            });
            resolve(compressedFile.size < file.size ? compressedFile : file);
          }, outputType, quality);
        };
        image.src = reader.result;
      };
      reader.readAsDataURL(file);
    }).then((compressedFile) => {
      if (compressedFile.size > maxUploadSize) {
        const sizeMb = (maxUploadSize / 1024 / 1024).toFixed(0);
        return Promise.reject("图片压缩后仍超过 " + sizeMb + "MB，请换一张更小的图片");
      }
      return compressedFile;
    });
  },
  normalizeUploadedImagePath(path) {
    const normalized = this.normalizeAssetPath(path);
    if (!normalized) {
      return "";
    }
    if (normalized.startsWith("http") || normalized.startsWith("data:")) {
      return normalized;
    }
    if (normalized.startsWith("/imgs/")) {
      return normalized;
    }
    if (normalized.startsWith("/")) {
      return "/imgs" + normalized;
    }
    return "/imgs/" + normalized;
  },
  uploadImageFile(file, options) {
    const errorMessage = this.validateImageFile(file, Object.assign({}, options || {}, { skipSizeCheck: true }));
    if (errorMessage) {
      return Promise.reject(errorMessage);
    }
    return this.compressImageFile(file, options).then((uploadFile) => {
      const formData = new FormData();
      formData.append("file", uploadFile);
      return axios.post("/upload/blog", formData, {
        headers: { "Content-Type": "multipart/form-data" }
      });
    }).then((result) => this.normalizeUploadedImagePath(result.data));
  },
  deleteUploadedImage(path) {
    if (!path) {
      return Promise.resolve();
    }
    return axios.get("/upload/blog/delete", {
      params: { name: path }
    });
  },
  redirectToLogin(redirectPath, delayMs) {
    const target = redirectPath || (location.pathname + location.search);
    const loginUrl = window.authHelper
      ? window.authHelper.buildLoginUrl(target)
      : ("/pages/auth/login.html?redirect=" + encodeURIComponent(target));
    const timeout = typeof delayMs === "number" ? delayMs : 120;
    setTimeout(() => {
      location.replace(loginUrl);
    }, timeout);
  },
  escapeHtml(content) {
    return String(content == null ? "" : content)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }
};

const DEFAULT_MESSAGE_OFFSET = 88;
const MESSAGE_HEADER_GAP = 12;
const MESSAGE_HEADER_TOP_LIMIT = 140;
const MESSAGE_HEADER_SELECTORS = [
  ".header",
  ".search-header",
  ".page-header",
  ".top-bar",
  ".top-nav",
  ".nav-bar",
  ".top-header",
  ".el-page-header",
  "header"
];

function isVisibleElement(element) {
  if (!element || typeof window === "undefined" || typeof window.getComputedStyle !== "function") {
    return false;
  }
  const style = window.getComputedStyle(element);
  if (!style || style.display === "none" || style.visibility === "hidden" || style.opacity === "0") {
    return false;
  }
  const rect = element.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0 && rect.bottom > 0;
}

function collectMessageHeaderCandidates() {
  if (typeof document === "undefined" || typeof document.querySelectorAll !== "function") {
    return [];
  }
  const seen = new Set();
  const candidates = [];
  MESSAGE_HEADER_SELECTORS.forEach((selector) => {
    document.querySelectorAll(selector).forEach((element) => {
      if (seen.has(element)) {
        return;
      }
      seen.add(element);
      candidates.push(element);
    });
  });
  return candidates;
}

function resolveMessageOffset() {
  if (typeof window === "undefined") {
    return DEFAULT_MESSAGE_OFFSET;
  }
  let maxBottom = 0;
  let foundHeader = false;
  collectMessageHeaderCandidates().forEach((element) => {
    if (!isVisibleElement(element)) {
      return;
    }
    const style = window.getComputedStyle(element);
    if (!style || (style.position !== "sticky" && style.position !== "fixed")) {
      return;
    }
    const rect = element.getBoundingClientRect();
    if (rect.top > MESSAGE_HEADER_TOP_LIMIT) {
      return;
    }
    foundHeader = true;
    maxBottom = Math.max(maxBottom, Math.ceil(rect.bottom));
  });
  return foundHeader ? maxBottom + MESSAGE_HEADER_GAP : DEFAULT_MESSAGE_OFFSET;
}

function normalizeMessageOptions(input, type) {
  let options;
  if (typeof input === "string" || typeof input === "number") {
    options = { message: input };
  } else if (input && typeof input === "object") {
    options = Object.assign({}, input);
  } else {
    options = { message: input };
  }

  if (type && !options.type) {
    options.type = type;
  }
  if (options.offset == null) {
    options.offset = resolveMessageOffset();
  }
  if (options.showClose == null) {
    options.showClose = false;
  }
  if (options.duration == null) {
    options.duration = options.type === "error" ? 2200 : 1500;
  }
  return options;
}

function createMessageWrapper(originalMessage) {
  if (typeof originalMessage !== "function") {
    return null;
  }

  const wrappedMessage = function(options) {
    return originalMessage(normalizeMessageOptions(options));
  };

  ["success", "warning", "info", "error"].forEach((type) => {
    wrappedMessage[type] = function(options) {
      return originalMessage(normalizeMessageOptions(options, type));
    };
  });

  Object.keys(originalMessage).forEach((key) => {
    if (wrappedMessage[key] === undefined) {
      wrappedMessage[key] = originalMessage[key];
    }
  });

  return wrappedMessage;
}

function installGlobalMessageDefaults() {
  if (window.__HMDP_MESSAGE_WRAPPED__) {
    return;
  }
  if (!window.Vue || !window.Vue.prototype || typeof window.Vue.prototype.$message !== "function") {
    return;
  }

  const wrappedMessage = createMessageWrapper(window.Vue.prototype.$message);
  if (!wrappedMessage) {
    return;
  }

  window.Vue.prototype.$message = wrappedMessage;
  if (window.ELEMENT && typeof window.ELEMENT === "object") {
    window.ELEMENT.Message = wrappedMessage;
  }
  window.__HMDP_MESSAGE_WRAPPED__ = true;
}

window.commonURL = commonURL;
window.util = util;
installGlobalMessageDefaults();
