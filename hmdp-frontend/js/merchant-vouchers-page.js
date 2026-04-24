new Vue({
  el: '#app',
  data() {
    return {
      loading: false,
      merchantProfile: null,
      activeTab: 'vouchers',
      shops: [],
      shopKeyword: '',
      selectedShopId: util.readStorageJSON('merchant_selected_shop_id', null),
      merchantVouchers: [],
      merchantOrders: [],
      voucherStatusFilter: null,
      orderStatusFilter: 2,
      verifyCode: '',
      dialogVisible: false,
      dialogMode: 'create',
      voucherStatusOptions: [
        { key: 'all', label: '全部', value: null },
        { key: 'draft', label: '草稿', value: 0 },
        { key: 'online', label: '已上架', value: 1 },
        { key: 'offline', label: '已下架', value: 2 },
        { key: 'ended', label: '已结束', value: 3 }
      ],
      orderStatusOptions: [
        { key: 'all', label: '全部', value: null },
        { key: 'paid', label: '待核销', value: 2 },
        { key: 'verified', label: '已核销', value: 3 },
        { key: 'pending', label: '待支付', value: 1 }
      ],
      form: this.createEmptyForm()
    };
  },
  computed: {
    selectedShop() {
      return this.shops.find(shop => String(shop.id) === String(this.selectedShopId)) || null;
    },
    isAdmin() {
      return !!(this.merchantProfile && this.merchantProfile.admin);
    },
    isMerchant() {
      return !this.isAdmin && !!(this.merchantProfile && this.merchantProfile.primaryRole === 'MERCHANT');
    },
    roleLabel() {
      if (this.isAdmin) return '管理员';
      if (this.isMerchant) return '商家';
      return '用户';
    }
  },
  created() {
    this.bootstrap();
  },
  methods: {
    createEmptyForm() {
      return {
        id: null,
        shopId: this.selectedShopId || null,
        title: '',
        subTitle: '',
        rules: '',
        type: 0,
        payValueYuan: '',
        actualValueYuan: '',
        stock: 100,
        beginTime: null,
        endTime: null
      };
    },
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) {
        return;
      }
      this.loadMerchantProfile();
    },
    friendlyError(err, fallback) {
      const message = util.getErrorMessage(err, fallback);
      if (!message || typeof message !== 'string') {
        return fallback || '操作失败，请稍后再试';
      }
      if (message.length > 80 || message.indexOf('###') > -1 || message.toLowerCase().indexOf('sql') > -1) {
        return fallback || '操作失败，请稍后再试';
      }
      return message;
    },
    loadMerchantProfile() {
      this.loading = true;
      axios.get('/merchant/me').then(({ data }) => {
        this.merchantProfile = data || {};
        if (!this.merchantProfile.merchantEnabled) {
          if (this.merchantProfile.merchantApplicationStatus === 'PENDING' || this.merchantProfile.merchantApplicationStatus === 'REJECTED') {
            location.replace('./progress.html');
            return;
          }
          location.replace('./apply.html');
          return;
        }
        this.loadShops();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '商家中心加载失败，请确认后端已重启'));
      }).finally(() => {
        this.loading = false;
      });
    },
    goBack() {
      history.back();
    },
    goToClaimShop() {
      location.href = './claim-shop.html';
    },
    goToCreateShop() {
      location.href = './create-shop.html';
    },
    goToProgress() {
      location.href = './progress.html';
    },
    goToAdminReview() {
      if (!this.merchantProfile || !this.merchantProfile.admin) {
        this.$message.warning('当前账号暂无审核权限');
        return;
      }
      location.href = '../admin/merchant-reviews.html';
    },
    loadShops() {
      this.loading = true;
      axios.get('/merchant/shops', {
        params: { keyword: this.shopKeyword || undefined, current: 1 }
      }).then(({ data }) => {
        this.shops = Array.isArray(data) ? data : [];
        if (!this.shops.length) {
          this.selectedShopId = null;
          this.merchantVouchers = [];
          this.merchantOrders = [];
          util.writeStorageJSON('merchant_selected_shop_id', null);
          return;
        }
        if (!this.selectedShopId || !this.shops.some(shop => String(shop.id) === String(this.selectedShopId))) {
          this.selectedShopId = this.shops[0].id;
        }
        util.writeStorageJSON('merchant_selected_shop_id', this.selectedShopId);
        this.refreshCurrentTab();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载店铺失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    handleShopChange() {
      util.writeStorageJSON('merchant_selected_shop_id', this.selectedShopId);
      this.refreshCurrentTab();
    },
    switchTab(tab) {
      this.activeTab = tab;
      this.refreshCurrentTab();
    },
    refreshCurrentTab() {
      if (!this.selectedShopId) {
        return;
      }
      if (this.activeTab === 'orders') {
        this.loadMerchantOrders();
      } else {
        this.loadMerchantVouchers();
      }
    },
    setVoucherStatus(status) {
      this.voucherStatusFilter = status;
      this.loadMerchantVouchers();
    },
    setOrderStatus(status) {
      this.orderStatusFilter = status;
      this.loadMerchantOrders();
    },
    loadMerchantVouchers() {
      if (!this.selectedShopId) return;
      this.loading = true;
      axios.get('/merchant/vouchers', {
        params: {
          shopId: this.selectedShopId,
          status: this.voucherStatusFilter
        }
      }).then(({ data }) => {
        this.merchantVouchers = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载优惠券失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    loadMerchantOrders() {
      if (!this.selectedShopId) return;
      this.loading = true;
      axios.get('/merchant/voucher-orders', {
        params: {
          shopId: this.selectedShopId,
          status: this.orderStatusFilter,
          current: 1
        }
      }).then(({ data }) => {
        this.merchantOrders = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载订单失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    openCreateDialog() {
      if (!this.selectedShopId) {
        this.$message.warning('请先认领店铺或新建店铺');
        return;
      }
      this.dialogMode = 'create';
      this.form = this.createEmptyForm();
      this.form.shopId = this.selectedShopId || null;
      this.dialogVisible = true;
    },
    editVoucher(voucher) {
      this.dialogMode = 'edit';
      this.form = {
        id: voucher.id,
        shopId: voucher.shopId,
        title: voucher.title,
        subTitle: voucher.subTitle,
        rules: voucher.rules,
        type: voucher.type,
        payValueYuan: this.toYuan(voucher.payValue),
        actualValueYuan: this.toYuan(voucher.actualValue),
        stock: voucher.stock || 1,
        beginTime: voucher.beginTime ? new Date(voucher.beginTime) : null,
        endTime: voucher.endTime ? new Date(voucher.endTime) : null
      };
      this.dialogVisible = true;
    },
    saveVoucher() {
      const payload = this.buildVoucherPayload();
      if (!payload) return;
      const request = this.dialogMode === 'edit'
        ? axios.put('/merchant/vouchers/' + this.form.id, payload)
        : axios.post('/merchant/vouchers', payload);
      request.then(() => {
        this.$message.success(this.dialogMode === 'edit' ? '优惠券已更新' : '优惠券已创建');
        this.dialogVisible = false;
        this.loadMerchantVouchers();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '保存优惠券失败'));
      });
    },
    publishVoucher(voucher) {
      axios.post('/merchant/vouchers/' + voucher.id + '/publish').then(() => {
        this.$message.success('优惠券已上架');
        this.loadMerchantVouchers();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '上架失败'));
      });
    },
    unpublishVoucher(voucher) {
      axios.post('/merchant/vouchers/' + voucher.id + '/unpublish').then(() => {
        this.$message.success('优惠券已下架');
        this.loadMerchantVouchers();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '下架失败'));
      });
    },
    verifyByCode() {
      if (!this.verifyCode) {
        this.$message.warning('请先输入券码');
        return;
      }
      axios.post('/merchant/voucher-orders/verify-code', {
        shopId: this.selectedShopId,
        verifyCode: this.verifyCode
      }).then(({ data }) => {
        this.$message.success('核销成功');
        this.verifyCode = '';
        if (data && data.id) {
          this.merchantOrders = [data].concat(this.merchantOrders.filter(item => item.id !== data.id));
        }
        this.loadMerchantOrders();
        this.loadMerchantVouchers();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '核销失败'));
      });
    },
    fillVerifyCode(code) {
      this.verifyCode = code || '';
    },
    buildVoucherPayload() {
      if (!this.form.shopId) {
        this.$message.warning('请选择店铺');
        return null;
      }
      if (!this.form.title) {
        this.$message.warning('请填写优惠券标题');
        return null;
      }
      const payValue = this.toFen(this.form.payValueYuan);
      const actualValue = this.toFen(this.form.actualValueYuan);
      if (!payValue || !actualValue) {
        this.$message.warning('请填写正确的金额');
        return null;
      }
      return {
        id: this.form.id,
        shopId: this.form.shopId,
        title: this.form.title,
        subTitle: this.form.subTitle,
        rules: this.form.rules,
        type: this.form.type,
        payValue: payValue,
        actualValue: actualValue,
        stock: this.form.stock,
        beginTime: this.form.beginTime,
        endTime: this.form.endTime
      };
    },
    toFen(value) {
      if (value === null || value === undefined || value === '') {
        return null;
      }
      const num = Number(value);
      if (!isFinite(num) || num <= 0) {
        return null;
      }
      return Math.round(num * 100);
    },
    toYuan(value) {
      const price = util.formatPrice(value);
      return price == null ? '0.00' : price;
    },
    formatDateTime(value) {
      if (!value) return '';
      const date = new Date(value);
      if (isNaN(date.getTime())) return '';
      const month = date.getMonth() + 1;
      const day = date.getDate();
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      return month + '月' + day + '日 ' + hours + ':' + minutes;
    },
    getVoucherStatusText(status) {
      const map = { 0: '草稿', 1: '已上架', 2: '已下架', 3: '已结束' };
      return map[status] || '未知状态';
    },
    getOrderStatusText(status) {
      const map = { 1: '待支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' };
      return map[status] || '未知状态';
    },
    getShopImage() {
      var shop = this.selectedShop;
      if (!shop) return '/imgs/icons/default-icon.png';
      var raw = shop.images || '';
      var first = raw.split(',')[0] || '';
      return util.resolveImageUrl(first, '/imgs/icons/default-icon.png');
    }
  }
});
