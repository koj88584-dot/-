new Vue({
  el: '#app',
  data() {
    return {
      loading: false,
      profile: null,
      imagePreviewVisible: false,
      imagePreviewUrl: ''
    };
  },
  computed: {
    isAdmin() {
      return !!(this.profile && this.profile.admin);
    }
  },
  created() {
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) return;
      this.loadProfile();
    },
    goBack() {
      history.back();
    },
    loadProfile() {
      this.loading = true;
      axios.get('/merchant/application/me').then(({ data }) => {
        this.profile = data || {};
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载进度失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    goApply() {
      location.href = './apply.html';
    },
    goClaimShop() {
      location.href = './claim-shop.html';
    },
    goCreateShop() {
      location.href = './create-shop.html';
    },
    goMerchantCenter() {
      location.href = './vouchers.html';
    },
    goAdminReview() {
      location.href = '../admin/merchant-reviews.html';
    },
    splitImages(value) {
      return util.csvToImageList(value);
    },
    resolveImage(path) {
      return util.resolveImageUrl(path);
    },
    previewImage(path) {
      this.imagePreviewUrl = this.resolveImage(path);
      this.imagePreviewVisible = true;
    }
  }
});
