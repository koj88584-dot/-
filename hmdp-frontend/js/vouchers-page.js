new Vue({
  el: '#app',
  data: {
    activeTab: 'available',
    vouchers: [],
    myVouchers: [],
    loading: true,
    countdownTimer: null,
    codeDialogVisible: false,
    selectedOrder: null,
    shopId: util.getUrlParam('shopId')
  },
  created() {
    this.handleAssistantEntry();
    this.refreshCurrentTab();
    this.startCountdowns();
  },
  beforeDestroy() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
    }
  },
  methods: {
    goBack() {
      history.back();
    },
    goMerchantCenter() {
      if (!this.ensureLogin()) return;
      location.href = '/pages/merchant/vouchers.html';
    },
    handleAssistantEntry() {
      const source = util.getUrlParam('source');
      const tab = util.getUrlParam('tab');
      if (source === 'assistant' && (tab === 'my' || tab === 'available')) {
        this.activeTab = tab;
      }
    },
    handleTabChange() {
      this.refreshCurrentTab();
    },
    refreshCurrentTab() {
      if (this.activeTab === 'my') {
        this.loadMyVouchers();
      } else {
        this.loadVouchers();
      }
    },
    loadVouchers() {
      this.loading = true;
      const request = this.shopId
        ? axios.get('/voucher/list/' + this.shopId)
        : axios.get('/voucher/list');
      request.then(({ data }) => {
        this.vouchers = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载优惠券失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    loadMyVouchers() {
      if (!this.ensureLogin()) {
        this.loading = false;
        return;
      }
      this.loading = true;
      axios.get('/voucher-order/list').then(({ data }) => {
        this.myVouchers = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载订单失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    receiveVoucher(voucher) {
      if (!this.ensureLogin()) return;
      const request = voucher.type === 1
        ? axios.post('/voucher-order/seckill/' + voucher.id)
        : axios.post('/voucher-order/receive/' + voucher.id);
      request.then(() => {
        this.$message.success(voucher.type === 1 ? '秒杀成功' : '领取成功');
        if (typeof voucher.stock === 'number' && voucher.stock > 0) {
          voucher.stock -= 1;
        }
        this.activeTab = 'my';
        this.loadMyVouchers();
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, voucher.type === 1 ? '秒杀失败' : '领取失败'));
      });
    },
    goShop(voucher) {
      if (!voucher || !voucher.shopId) {
        this.$message.info('门店信息暂不可用');
        return;
      }
      location.href = '/pages/shop/shop-detail.html?id=' + voucher.shopId;
    },
    getVoucherImage(voucher) {
      return util.resolveImageUrl(voucher && voucher.shopImages, '/imgs/icons/default-icon.png');
    },
    getOrderImage(order) {
      return util.resolveImageUrl(order && order.voucherImages, '/imgs/icons/default-icon.png');
    },
    handleImageError(event) {
      util.applyImageFallback(event, commonURL + '/imgs/icons/default-icon.png');
    },
    payOrder(id) {
      if (!this.ensureLogin()) return;
      axios.post('/voucher-order/pay/' + id).then(({ data }) => {
        this.$message.success('支付成功');
        if (data && data.id) {
          this.selectedOrder = data;
          this.codeDialogVisible = true;
        }
        this.loadMyVouchers();
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '支付失败'));
      });
    },
    cancelOrder(id) {
      this.$confirm('确定要取消这个订单吗？', '提示', {
        confirmButtonText: '确定取消',
        cancelButtonText: '再想想',
        type: 'warning'
      }).then(() => {
        return axios.post('/voucher-order/cancel/' + id);
      }).then(() => {
        this.$message.success('取消成功');
        this.loadMyVouchers();
      }).catch((err) => {
        if (err !== 'cancel') {
          this.$message.error(util.getErrorMessage(err, '取消失败'));
        }
      });
    },
    showVoucherCode(order) {
      this.selectedOrder = order;
      this.codeDialogVisible = true;
    },
    formatDate(dateStr) {
      if (!dateStr) return '长期有效';
      const date = new Date(dateStr);
      return (date.getMonth() + 1) + '月' + date.getDate() + '日';
    },
    formatDateTime(dateStr) {
      if (!dateStr) return '';
      const date = new Date(dateStr);
      const month = date.getMonth() + 1;
      const day = date.getDate();
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      return month + '月' + day + '日 ' + hours + ':' + minutes;
    },
    isSeckillActive(voucher) {
      if (!voucher.beginTime || !voucher.endTime) return false;
      const now = Date.now();
      const begin = new Date(voucher.beginTime).getTime();
      const end = new Date(voucher.endTime).getTime();
      return now >= begin && now <= end;
    },
    formatCountdown(voucher) {
      if (!voucher.beginTime) return '';
      const now = Date.now();
      const begin = new Date(voucher.beginTime).getTime();
      const end = new Date(voucher.endTime).getTime();
      if (now < begin) {
        const diff = begin - now;
        const hours = Math.floor(diff / 3600000);
        const mins = Math.floor((diff % 3600000) / 60000);
        return '距离开始 ' + hours + '小时' + mins + '分钟';
      }
      if (now <= end) {
        const diff = end - now;
        const hours = Math.floor(diff / 3600000);
        const mins = Math.floor((diff % 3600000) / 60000);
        return '距离结束 ' + hours + '小时' + mins + '分钟';
      }
      return '已结束';
    },
    startCountdowns() {
      this.countdownTimer = setInterval(() => {
        this.vouchers = this.vouchers.slice();
      }, 60000);
    },
    getStatusText(status) {
      const map = { 1: '待支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' };
      return map[status] || '未知状态';
    },
    getStatusColor(status) {
      const map = { 1: '#ff6633', 2: '#67c23a', 3: '#909399', 4: '#909399', 5: '#e6a23c', 6: '#909399' };
      return map[status] || '#333';
    },
    toYuan(value) {
      const price = util.formatPrice(value);
      return price == null ? '0.00' : price;
    },
    ensureLogin() {
      if (util.hasToken()) return true;
      this.$message.error('请先登录');
      util.redirectToLogin(location.pathname + location.search, 200);
      return false;
    }
  }
});
