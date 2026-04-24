new Vue({
  el: '#app',
  data() {
    return {
      loading: false,
      activeTab: 'merchant',
      statusFilter: 0,
      merchantApplications: [],
      claimApplications: [],
      createApplications: [],
      currentUser: null,
      imagePreviewVisible: false,
      imagePreviewUrl: ''
    };
  },
  created() {
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) return;
      this.loading = true;
      axios.get('/user/me').then(({ data }) => {
        this.currentUser = data || {};
        if (!this.currentUser.admin) {
          this.$message.warning('当前账号暂无管理员权限');
          setTimeout(() => {
            location.replace('../user/info.html');
          }, 500);
          return;
        }
        this.reload();
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载管理员信息失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    goBack() {
      history.back();
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
    },
    reload() {
      this.loading = true;
      const params = { status: this.statusFilter === '' ? undefined : this.statusFilter };
      Promise.all([
        axios.get('/admin/merchant-applications', { params }),
        axios.get('/admin/shop-claim-applications', { params }),
        axios.get('/admin/shop-create-applications', { params })
      ]).then(([merchantRes, claimRes, createRes]) => {
        this.merchantApplications = Array.isArray(merchantRes.data) ? merchantRes.data : [];
        this.claimApplications = Array.isArray(claimRes.data) ? claimRes.data : [];
        this.createApplications = Array.isArray(createRes.data) ? createRes.data : [];
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载审核列表失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    approve(kind, id) {
      this.review(kind, id, 'approve');
    },
    reject(kind, id) {
      this.review(kind, id, 'reject');
    },
    review(kind, id, action) {
      const title = action === 'approve' ? '审核通过' : '驳回申请';
      this.$prompt('可选填写审核备注', title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：资料齐全，允许通过'
      }).then(({ value }) => {
        const base = kind === 'merchant'
          ? '/admin/merchant-applications/'
          : (kind === 'claim' ? '/admin/shop-claim-applications/' : '/admin/shop-create-applications/');
        axios.post(base + id + '/' + action, { reviewRemark: value || '' }).then(() => {
          this.$message.success(action === 'approve' ? '审核已通过' : '申请已驳回');
          this.reload();
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '审核失败'));
        });
      }).catch(() => {});
    }
  }
});
