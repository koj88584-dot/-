(function (window) {
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

  function resolveBackendBaseUrl() {
    if (window.util && typeof window.util.commonURL === "string" && window.util.commonURL) {
      return window.util.commonURL.replace(/\/+$/, "");
    }
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

  const BACKEND_BASE_URL = resolveBackendBaseUrl();
  const DEFAULT_HOME = "/pages/index-new.html";

  function getToken() {
    return localStorage.getItem("token") || "";
  }

  function clearAuth() {
    localStorage.removeItem("token");
  }

  function normalizeRedirectPath(redirectPath) {
    let target = redirectPath || DEFAULT_HOME;
    try {
      target = decodeURIComponent(target);
    } catch (e) {
      // ignore malformed URI component and keep the original value
    }
    if (!target) {
      return DEFAULT_HOME;
    }
    if (/^https?:\/\//i.test(target)) {
      return DEFAULT_HOME;
    }
    if (!target.startsWith("/")) {
      target = "/" + target.replace(/^\/+/, "");
    }
    return target;
  }

  function buildLoginUrl(redirectPath) {
    const target = normalizeRedirectPath(redirectPath);
    return "/pages/auth/login.html?redirect=" + encodeURIComponent(target);
  }

  async function validateToken(token) {
    if (!token) {
      return false;
    }
    try {
      const response = await fetch(BACKEND_BASE_URL + "/user/me", {
        method: "GET",
        headers: {
          authorization: token
        }
      });
      if (!response.ok) {
        return false;
      }
      const payload = await response.json();
      return !!(payload && payload.success && payload.data && payload.data.id);
    } catch (e) {
      return false;
    }
  }

  async function redirectIfLoggedIn(redirectPath) {
    const token = getToken();
    if (!token) {
      return false;
    }
    const valid = await validateToken(token);
    if (!valid) {
      clearAuth();
      return false;
    }
    location.replace(normalizeRedirectPath(redirectPath));
    return true;
  }

  async function ensureLogin(redirectPath) {
    const token = getToken();
    const target = normalizeRedirectPath(redirectPath || (location.pathname + location.search));
    if (!token) {
      clearAuth();
      location.replace(buildLoginUrl(target));
      return false;
    }
    const valid = await validateToken(token);
    if (!valid) {
      clearAuth();
      location.replace(buildLoginUrl(target));
      return false;
    }
    return true;
  }

  async function redirectRoot() {
    const token = getToken();
    if (!token) {
      clearAuth();
      location.replace(buildLoginUrl(DEFAULT_HOME));
      return;
    }
    const valid = await validateToken(token);
    if (!valid) {
      clearAuth();
      location.replace(buildLoginUrl(DEFAULT_HOME));
      return;
    }
    location.replace(DEFAULT_HOME);
  }

  function navigateWithAuth(targetPath) {
    const target = normalizeRedirectPath(targetPath);
    ensureLogin(target).then(function (ok) {
      if (ok) {
        location.href = target;
      }
    });
  }

  window.authHelper = {
    getToken,
    clearAuth,
    normalizeRedirectPath,
    buildLoginUrl,
    validateToken,
    redirectIfLoggedIn,
    ensureLogin,
    redirectRoot,
    navigateWithAuth,
    defaultHome: DEFAULT_HOME
  };
}(window));
