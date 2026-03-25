// 开发环境：直接访问后端
let commonURL = "http://localhost:8081";
// 生产环境（使用Nginx代理）：
// let commonURL = "/api";
// 设置后台服务地址
axios.defaults.baseURL = commonURL;
axios.defaults.timeout = 10000;
// request拦截器，将用户token放入头中
axios.interceptors.request.use(
  config => {
    let token = localStorage.getItem("token");
    console.log("========== 请求拦截器 ==========");
    console.log("请求URL:", config.url);
    console.log("请求方法:", config.method);
    console.log("Token:", token ? token.substring(0, 30) + "..." : "(无)");
    
    if(token) {
      config.headers['authorization'] = token;
      console.log("已设置 authorization header:", token.substring(0, 30) + "...");
    } else {
      console.warn("警告：token不存在，未设置 authorization header");
    }
    
    // 打印所有请求头
    console.log("所有请求头:", JSON.stringify(config.headers));
    return config
  },
  error => {
    console.error("请求拦截器错误:", error);
    return Promise.reject(error)
  }
)
axios.interceptors.response.use(function (response) {
  console.log("========== 响应拦截器（成功） ==========");
  console.log("响应URL:", response.config.url);
  console.log("响应状态:", response.status);
  console.log("响应数据:", response.data);
  
  // 判断执行结果
  if (!response.data.success) {
    console.error("业务逻辑错误:", response.data.errorMsg);
    return Promise.reject(response.data.errorMsg)
  }
  return response.data;
}, function (error) {
  // 一般是服务端异常或者网络异常
  console.error('========== 响应拦截器（错误） ==========');
  console.error('错误对象:', error);
  
  if (error.response) {
    console.error('响应状态:', error.response.status);
    console.error('响应数据:', error.response.data);
    console.error('响应头:', error.response.headers);
    
    if(error.response.status == 401){
      console.error('收到401未授权错误，即将跳转到登录页');
      // 未登录，跳转
      setTimeout(() => {
              location.href = "/pages/auth/login.html"
            }, 200);
      return Promise.reject("请先登录");
    }
  } else if (error.request) {
    console.error('请求已发送但没有收到响应:', error.request);
  } else {
    console.error('请求配置错误:', error.message);
  }
  
  return Promise.reject(error);
});
axios.defaults.paramsSerializer = function(params) {
  let p = "";
  Object.keys(params).forEach(k => {
    if(params[k]){
      p = p + "&" + k + "=" + params[k]
    }
  })
  return p;
}
const util = {
  commonURL,
  getUrlParam(name) {
    let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    let r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURI(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === 'string') {
      if (isNaN(val)) {
        return null;
      }
      // 价格转为整数
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        // 无小数
        p = val + "00";
      } else if (index === p.length - 2) {
        // 1位小数
        p = val.replace("\.", "") + "0";
      } else {
        // 2位小数
        p = val.replace("\.", "")
      }
      return parseInt(p);
    } else if (typeof val === 'number') {
      if (!val) {
        return null;
      }
      const s = val + '';
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
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2)
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        // 1位整数
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2)
      }
    }
  }
}
