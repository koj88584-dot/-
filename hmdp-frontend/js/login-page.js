new Vue({
  el: "#app",
  data() {
    return {
      radio: "",
      disabled: false,
      codeBtnMsg: "发送验证码",
      countdownTimer: null,
      redirectTarget: window.authHelper.normalizeRedirectPath(
        util.getUrlParam("redirect") || "/pages/index-new.html"
      ),
      form: {
        phone: "",
        code: ""
      }
    };
  },
  computed: {
    registerUrl() {
      return "/pages/auth/register.html?redirect=" + encodeURIComponent(this.redirectTarget);
    },
    passwordLoginUrl() {
      return "/pages/auth/login2.html?redirect=" + encodeURIComponent(this.redirectTarget);
    }
  },
  async mounted() {
    await window.authHelper.redirectIfLoggedIn(this.redirectTarget);
  },
  beforeDestroy() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
    }
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
      if (!this.form.phone || !this.form.code) {
        this.$message.error("请输入手机号和验证码");
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
    async sendCode() {
      if (!this.validateAgreement()) {
        return;
      }
      if (!this.form.phone) {
        this.$message.error("请输入手机号");
        return;
      }
      try {
        await axios.post("/user/code?phone=" + encodeURIComponent(this.form.phone));
        this.$message.success("验证码已发送，请注意查收");
        this.startCountdown();
      } catch (err) {
        this.showError(err);
      }
    },
    startCountdown() {
      this.disabled = true;
      let seconds = 60;
      this.codeBtnMsg = seconds + "秒后重发";
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer);
      }
      this.countdownTimer = setInterval(() => {
        seconds -= 1;
        if (seconds <= 0) {
          clearInterval(this.countdownTimer);
          this.countdownTimer = null;
          this.disabled = false;
          this.codeBtnMsg = "发送验证码";
          return;
        }
        this.codeBtnMsg = seconds + "秒后重发";
      }, 1000);
    },
    goBack() {
      history.back();
    }
  }
});
