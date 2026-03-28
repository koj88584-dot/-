(function (window) {
  const BACKEND_BASE_URL = "http://localhost:8081";
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
