new Vue({
  el: "#app",
  data() {
    return {
      radio: "",
      redirectTarget: window.authHelper.normalizeRedirectPath(
        util.getUrlParam("redirect") || "/pages/index-new.html"
      ),
      form: {
        phone: "",
        password: ""
      }
    };
  },
  computed: {
    registerUrl() {
      return "/pages/auth/register.html?redirect=" + encodeURIComponent(this.redirectTarget);
    },
    codeLoginUrl() {
      return "/pages/auth/login.html?redirect=" + encodeURIComponent(this.redirectTarget);
    }
  },
  async mounted() {
    await window.authHelper.redirectIfLoggedIn(this.redirectTarget);
  },
  methods: {
    showError(err) {
      const msg = typeof err === "string" ? err : (err && err.message) || "操作失败，请稍后重试";
      this.$message.error(msg);
    },
    validateAgreement() {
      if (!this.radio) {
        this.$message.error("请先勾选用户协议");
        return false;
      }
      return true;
    },
    async login() {
      if (!this.validateAgreement()) {
        return;
      }
      if (!this.form.phone || !this.form.password) {
        this.$message.error("请输入手机号和密码");
        return;
      }
      try {
        const result = await axios.post("/user/login", this.form);
        const token = result.data;
        if (token) {
          localStorage.setItem("token", token);
        }
        location.replace(this.redirectTarget);
      } catch (err) {
        this.showError(err);
      }
    },
    goBack() {
      history.back();
    }
  }
});
