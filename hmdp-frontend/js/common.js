// 开发环境直接访问后端服务
let commonURL = "http://localhost:8081";
// 生产环境可改成 Nginx 代理路径
// let commonURL = "/api";

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
      return Promise.reject(error.response.data.errorMsg || error.response.data.message || "请求失败，请稍后重试");
    }
    if (error.message) {
      return Promise.reject(error.message);
    }
    return Promise.reject(error);
  }
);

axios.defaults.paramsSerializer = function(params) {
  let p = "";
  Object.keys(params).forEach(k => {
    if (params[k] !== undefined && params[k] !== null && params[k] !== "") {
      p = p + "&" + k + "=" + params[k];
    }
  });
  return p;
};

const util = {
  commonURL,
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
  }
};
