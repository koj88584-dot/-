new Vue({
  el: '#app',
  data() {
    return {
      loading: false,
      merchantProfile: null,
      activeTab: 'manage',
      manageType: 'voucher',
      verifyType: 'all',
      shops: [],
      shopKeyword: '',
      selectedShopId: util.readStorageJSON('merchant_selected_shop_id', null),
      merchantVouchers: [],
      merchantDishes: [],
      merchantGroupDeals: [],
      merchantOrders: [],
      groupOrders: [],
      dishOrders: [],
      voucherStatusFilter: null,
      productStatusFilter: null,
      orderStatusFilter: 2,
      verifyCode: '',
      dialogVisible: false,
      dialogMode: 'create',
      dialogType: 'voucher',
      form: {},
      statusOptions: [
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
      manageTypes: [
        { key: 'voucher', label: '优惠券', icon: 'el-icon-ticket' },
        { key: 'dish', label: '招牌菜', icon: 'el-icon-dish' },
        { key: 'deal', label: '团购', icon: 'el-icon-shopping-bag-1' }
      ],
      verifyTypes: [
        { key: 'all', label: '全部订单' },
        { key: 'voucher', label: '优惠券' },
        { key: 'deal', label: '团购' },
        { key: 'dish', label: '招牌菜' }
      ]
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
    },
    currentManageList() {
      if (this.manageType === 'dish') return this.filteredByStatus(this.merchantDishes, this.productStatusFilter);
      if (this.manageType === 'deal') return this.filteredByStatus(this.merchantGroupDeals, this.productStatusFilter);
      return this.filteredByStatus(this.merchantVouchers, this.voucherStatusFilter);
    },
    unifiedOrders() {
      const rows = [];
      if (this.verifyType === 'all' || this.verifyType === 'voucher') {
        rows.push.apply(rows, this.merchantOrders.map(order => this.normalizeOrder(order, 'voucher')));
      }
      if (this.verifyType === 'all' || this.verifyType === 'deal') {
        rows.push.apply(rows, this.groupOrders.map(order => this.normalizeOrder(order, 'deal')));
      }
      if (this.verifyType === 'all' || this.verifyType === 'dish') {
        rows.push.apply(rows, this.dishOrders.map(order => this.normalizeOrder(order, 'dish')));
      }
      return rows.sort((a, b) => this.dateValue(b.createTime) - this.dateValue(a.createTime));
    },
    dialogTitle() {
      const prefix = this.dialogMode === 'edit' ? '编辑' : '新建';
      const labelMap = { voucher: '优惠券', dish: '招牌菜', deal: '团购' };
      return prefix + labelMap[this.dialogType];
    }
  },
  created() {
    this.form = this.createEmptyForm('voucher');
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) return;
      this.loadMerchantProfile();
    },
    friendlyError(err, fallback) {
      const message = util.getErrorMessage(err, fallback);
      if (!message || typeof message !== 'string') return fallback || '操作失败，请稍后再试';
      if (message.length > 80 || message.indexOf('###') > -1 || message.toLowerCase().indexOf('sql') > -1) {
        return fallback || '操作失败，请稍后再试';
      }
      return message;
    },
    createEmptyForm(type) {
      if (type === 'dish') {
        return {
          id: null,
          shopId: this.selectedShopId || null,
          name: '',
          description: '',
          image: '',
          priceYuan: '',
          sort: 0
        };
      }
      if (type === 'deal') {
        return {
          id: null,
          shopId: this.selectedShopId || null,
          title: '',
          description: '',
          images: '',
          rules: '',
          priceYuan: '',
          originalPriceYuan: '',
          stock: 100,
          beginTime: null,
          endTime: null
        };
      }
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
    loadShops() {
      this.loading = true;
      axios.get('/merchant/shops', {
        params: { keyword: this.shopKeyword || undefined, current: 1 }
      }).then(({ data }) => {
        this.shops = Array.isArray(data) ? data : [];
        if (!this.shops.length) {
          this.selectedShopId = null;
          this.resetLists();
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
    resetLists() {
      this.merchantVouchers = [];
      this.merchantDishes = [];
      this.merchantGroupDeals = [];
      this.merchantOrders = [];
      this.groupOrders = [];
      this.dishOrders = [];
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
    openSelectedShopDetail() {
      if (!this.selectedShopId) {
        this.$message.warning('请先选择店铺');
        return;
      }
      location.href = '/pages/shop/shop-detail.html?id=' + encodeURIComponent(this.selectedShopId) + '&merchant=1';
    },
    handleShopChange() {
      util.writeStorageJSON('merchant_selected_shop_id', this.selectedShopId);
      this.resetLists();
      this.refreshCurrentTab();
    },
    switchTab(tab) {
      this.activeTab = tab;
      this.refreshCurrentTab();
    },
    switchManageType(type) {
      this.manageType = type;
      this.refreshCurrentTab();
    },
    switchVerifyType(type) {
      this.verifyType = type;
      this.refreshCurrentTab();
    },
    refreshCurrentTab() {
      if (!this.selectedShopId) return;
      if (this.activeTab === 'verify') {
        this.loadOrdersForVerify();
        return;
      }
      if (this.manageType === 'dish') {
        this.loadMerchantDishes();
      } else if (this.manageType === 'deal') {
        this.loadMerchantGroupDeals();
      } else {
        this.loadMerchantVouchers();
      }
    },
    setProductStatus(status) {
      if (this.manageType === 'voucher') {
        this.voucherStatusFilter = status;
      } else {
        this.productStatusFilter = status;
      }
    },
    setOrderStatus(status) {
      this.orderStatusFilter = status;
      this.loadOrdersForVerify();
    },
    filteredByStatus(list, status) {
      const rows = Array.isArray(list) ? list : [];
      if (status === null || status === undefined) return rows;
      return rows.filter(item => Number(item.status) === Number(status));
    },
    loadMerchantVouchers() {
      if (!this.selectedShopId) return;
      this.loading = true;
      axios.get('/merchant/vouchers', {
        params: { shopId: this.selectedShopId, status: this.voucherStatusFilter }
      }).then(({ data }) => {
        this.merchantVouchers = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载优惠券失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    loadMerchantDishes() {
      if (!this.selectedShopId) return;
      this.loading = true;
      axios.get('/merchant/shops/' + this.selectedShopId + '/featured-dishes').then(({ data }) => {
        this.merchantDishes = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载招牌菜失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    loadMerchantGroupDeals() {
      if (!this.selectedShopId) return;
      this.loading = true;
      axios.get('/merchant/shops/' + this.selectedShopId + '/group-deals').then(({ data }) => {
        this.merchantGroupDeals = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载团购失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    loadOrdersForVerify() {
      if (!this.selectedShopId) return;
      this.loading = true;
      const status = this.orderStatusFilter;
      const requests = [];
      if (this.verifyType === 'all' || this.verifyType === 'voucher') {
        requests.push(axios.get('/merchant/voucher-orders', {
          params: { shopId: this.selectedShopId, status, current: 1 }
        }).then(({ data }) => { this.merchantOrders = Array.isArray(data) ? data : []; }));
      } else {
        this.merchantOrders = [];
      }
      if (this.verifyType === 'all' || this.verifyType === 'deal') {
        requests.push(axios.get('/merchant/group-deal-orders', {
          params: { shopId: this.selectedShopId, status, current: 1 }
        }).then(({ data }) => { this.groupOrders = Array.isArray(data) ? data : []; }));
      } else {
        this.groupOrders = [];
      }
      if (this.verifyType === 'all' || this.verifyType === 'dish') {
        requests.push(axios.get('/merchant/featured-dish-orders', {
          params: { shopId: this.selectedShopId, status, current: 1 }
        }).then(({ data }) => { this.dishOrders = Array.isArray(data) ? data : []; }));
      } else {
        this.dishOrders = [];
      }
      Promise.all(requests).catch((err) => {
        this.$message.error(this.friendlyError(err, '加载核销订单失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    openCreateDialog(type) {
      if (!this.selectedShopId) {
        this.$message.warning('请先认领店铺或新建店铺');
        return;
      }
      this.dialogType = type || this.manageType || 'voucher';
      this.dialogMode = 'create';
      this.form = this.createEmptyForm(this.dialogType);
      this.form.shopId = this.selectedShopId;
      this.dialogVisible = true;
    },
    editItem(item, type) {
      this.dialogType = type || this.manageType;
      this.dialogMode = 'edit';
      if (this.dialogType === 'dish') {
        this.form = {
          id: item.id,
          shopId: item.shopId,
          name: item.name || '',
          description: item.description || '',
          image: item.image || '',
          priceYuan: item.price ? this.toYuan(item.price) : '',
          sort: item.sort || 0
        };
      } else if (this.dialogType === 'deal') {
        this.form = {
          id: item.id,
          shopId: item.shopId,
          title: item.title || '',
          description: item.description || '',
          images: item.images || '',
          rules: item.rules || '',
          priceYuan: item.price ? this.toYuan(item.price) : '',
          originalPriceYuan: item.originalPrice ? this.toYuan(item.originalPrice) : '',
          stock: item.stock || 1,
          beginTime: item.beginTime ? new Date(item.beginTime) : null,
          endTime: item.endTime ? new Date(item.endTime) : null
        };
      } else {
        this.form = {
          id: item.id,
          shopId: item.shopId,
          title: item.title || '',
          subTitle: item.subTitle || '',
          rules: item.rules || '',
          type: item.type || 0,
          payValueYuan: this.toYuan(item.payValue),
          actualValueYuan: this.toYuan(item.actualValue),
          stock: item.stock || 1,
          beginTime: item.beginTime ? new Date(item.beginTime) : null,
          endTime: item.endTime ? new Date(item.endTime) : null
        };
      }
      this.dialogVisible = true;
    },
    saveCurrentForm() {
      if (this.dialogType === 'dish') {
        this.saveDish();
      } else if (this.dialogType === 'deal') {
        this.saveGroupDeal();
      } else {
        this.saveVoucher();
      }
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
        this.manageType = 'voucher';
        this.loadMerchantVouchers();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '保存优惠券失败'));
      });
    },
    saveDish() {
      if (!this.form.name) {
        this.$message.warning('请填写招牌菜名称');
        return;
      }
      const payload = {
        id: this.form.id,
        shopId: this.selectedShopId,
        name: this.form.name,
        description: this.form.description,
        image: this.form.image,
        price: this.toFen(this.form.priceYuan),
        sort: this.form.sort || 0
      };
      const request = payload.id
        ? axios.put('/merchant/shops/' + this.selectedShopId + '/featured-dishes', payload)
        : axios.post('/merchant/shops/' + this.selectedShopId + '/featured-dishes', payload);
      request.then(() => {
        this.$message.success('招牌菜已保存');
        this.dialogVisible = false;
        this.manageType = 'dish';
        this.loadMerchantDishes();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '保存招牌菜失败'));
      });
    },
    saveGroupDeal() {
      if (!this.form.title) {
        this.$message.warning('请填写团购标题');
        return;
      }
      const payload = {
        id: this.form.id,
        shopId: this.selectedShopId,
        title: this.form.title,
        description: this.form.description,
        images: this.form.images,
        rules: this.form.rules,
        price: this.toFen(this.form.priceYuan),
        originalPrice: this.toFen(this.form.originalPriceYuan),
        stock: this.form.stock,
        beginTime: this.form.beginTime,
        endTime: this.form.endTime
      };
      const request = payload.id
        ? axios.put('/merchant/shops/' + this.selectedShopId + '/group-deals', payload)
        : axios.post('/merchant/shops/' + this.selectedShopId + '/group-deals', payload);
      request.then(() => {
        this.$message.success('团购已保存');
        this.dialogVisible = false;
        this.manageType = 'deal';
        this.loadMerchantGroupDeals();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '保存团购失败'));
      });
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
        payValue,
        actualValue,
        stock: this.form.stock,
        beginTime: this.form.beginTime,
        endTime: this.form.endTime
      };
    },
    publishItem(item, type) {
      const actualType = type || this.manageType;
      const url = actualType === 'dish'
        ? '/merchant/featured-dishes/' + item.id + '/publish'
        : actualType === 'deal'
          ? '/merchant/group-deals/' + item.id + '/publish'
          : '/merchant/vouchers/' + item.id + '/publish';
      axios.post(url).then(() => {
        this.$message.success('已上架');
        this.refreshCurrentTab();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '上架失败'));
      });
    },
    unpublishItem(item, type) {
      const actualType = type || this.manageType;
      const url = actualType === 'dish'
        ? '/merchant/featured-dishes/' + item.id + '/unpublish'
        : actualType === 'deal'
          ? '/merchant/group-deals/' + item.id + '/unpublish'
          : '/merchant/vouchers/' + item.id + '/unpublish';
      axios.post(url).then(() => {
        this.$message.success('已下架');
        this.refreshCurrentTab();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '下架失败'));
      });
    },
    verifyByCode() {
      if (!this.verifyCode) {
        this.$message.warning('请先输入券码');
        return;
      }
      const code = this.verifyCode.trim().toUpperCase();
      const type = code.indexOf('GD') === 0 ? 'deal' : code.indexOf('FD') === 0 ? 'dish' : 'voucher';
      const url = type === 'deal'
        ? '/merchant/group-deal-orders/verify-code'
        : type === 'dish'
          ? '/merchant/featured-dish-orders/verify-code'
          : '/merchant/voucher-orders/verify-code';
      axios.post(url, {
        shopId: this.selectedShopId,
        verifyCode: code
      }).then(() => {
        this.$message.success('核销成功');
        this.verifyCode = '';
        this.activeTab = 'verify';
        this.verifyType = type;
        this.loadOrdersForVerify();
        if (type === 'voucher') this.loadMerchantVouchers();
        if (type === 'deal') this.loadMerchantGroupDeals();
      }).catch((err) => {
        this.$message.error(this.friendlyError(err, '核销失败'));
      });
    },
    fillVerifyCode(code) {
      this.verifyCode = code || '';
    },
    normalizeOrder(order, type) {
      order = order || {};
      const titleMap = {
        voucher: order.voucherTitle || '优惠券订单',
        deal: order.dealTitle || '团购订单',
        dish: order.dishName || '招牌菜订单'
      };
      const imageMap = {
        voucher: order.voucherImages || this.getShopImage(),
        deal: order.dealImages || this.getShopImage(),
        dish: order.dishImage || this.getShopImage()
      };
      return Object.assign({}, order, {
        productType: type,
        productLabel: type === 'voucher' ? '优惠券' : type === 'deal' ? '团购' : '招牌菜',
        displayTitle: titleMap[type],
        displayImage: imageMap[type],
        displayPrice: order.payValue
      });
    },
    dateValue(value) {
      if (!value) return 0;
      const date = new Date(value);
      return isNaN(date.getTime()) ? 0 : date.getTime();
    },
    toFen(value) {
      if (value === null || value === undefined || value === '') return null;
      const num = Number(value);
      if (!isFinite(num) || num < 0) return null;
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
    getStatusText(status) {
      const map = { 0: '草稿', 1: '已上架', 2: '已下架', 3: '已结束' };
      return map[status] || '未知状态';
    },
    getOrderStatusText(status) {
      const map = { 1: '待支付', 2: '待核销', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' };
      return map[status] || '未知状态';
    },
    getShopImage() {
      const shop = this.selectedShop;
      if (!shop) return '/imgs/icons/default-icon.png';
      const raw = shop.images || '';
      const first = raw.split(',')[0] || '';
      return util.resolveImageUrl(first, '/imgs/icons/default-icon.png');
    },
    getItemImage(item) {
      if (!item) return this.getShopImage();
      const raw = item.image || item.images || item.voucherImages || item.dealImages || item.dishImage || '';
      const first = raw.split(',')[0] || '';
      return util.resolveImageUrl(first, this.getShopImage());
    },
    handleImageError(event) {
      util.applyImageFallback(event, '/imgs/icons/default-icon.png');
    }
  }
});
