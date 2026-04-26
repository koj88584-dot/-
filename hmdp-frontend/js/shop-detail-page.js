const app = new Vue({
    el: "#app",
    data: {
      util,
      shopId: util.getUrlParam("id"),
      shop: {},
      cityProfile: util.readCurrentCityProfile(),
      vouchers: [],
      featuredDishes: [],
      groupDeals: [],
      merchantAccess: {
        canEdit: false,
        canVerify: false,
        pendingUpdate: false,
        memberRole: ''
      },
      merchantShopSnapshot: null,
      merchantDialogVisible: false,
      merchantDialogMode: 'shop',
      merchantLoading: false,
      quickForm: {
        openHours: '',
        avgPrice: '',
        phone: ''
      },
      updateForm: {
        name: '',
        typeId: '',
        area: '',
        address: '',
        x: '',
        y: '',
        images: '',
        proofImages: '',
        message: ''
      },
      merchantDishes: [],
      dishForm: {
        id: null,
        name: '',
        description: '',
        image: '',
        priceYuan: '',
        sort: 0
      },
      merchantGroupDeals: [],
      groupDealForm: {
        id: null,
        title: '',
        description: '',
        images: '',
        rules: '',
        priceYuan: '',
        originalPriceYuan: '',
        stock: 100,
        beginTime: null,
        endTime: null
      },
      groupOrders: [],
      dishOrders: [],
      orderManageType: 'group',
      groupOrderStatusFilter: 2,
      dishOrderStatusFilter: 2,
      groupVerifyCode: '',
      dishVerifyCode: '',
      merchantImageUploading: false,
      isFavorite: false,
      fallbackImage: '/imgs/blogs/blog1.jpg',
      comments: [],
      commentTotal: 0,
      commentGoodRate: 0,
      commentTagOptions: [],
      activeCommentFilter: 'all',
      commentForm: {
        score: 5,
        content: '',
        images: []
      },
      commentSubmitting: false,
      commentImageUploading: false,
      showAllVouchers: false,
      toastInstance: null,
      toastTimer: null,
      currentUser: null,
      shareSheetVisible: false,
      shareFriendPanelVisible: false,
      shareFriends: [],
      shareFriendLoading: false,
      shareSendingId: 0,
      shareImageGenerating: false
    },
    computed: {
      visibleVouchers() {
        const usable = (this.vouchers || []).filter(v => !this.isEnd(v));
        return this.showAllVouchers ? usable : usable.slice(0, 2);
      },
      filteredComments() {
        if (this.activeCommentFilter === 'all') {
          return this.comments;
        }
        return this.comments.filter(comment => Array.isArray(comment.tags) && comment.tags.includes(this.activeCommentFilter));
      },
      shopTagScores() {
        const scoreFallback = this.shop && this.shop.score ? Number(this.shop.score) / 10 : 4.5;
        const avg = (key, fallbackOffset) => {
          const valid = this.comments.filter(item => item[key]);
          if (!valid.length) {
            return (scoreFallback + fallbackOffset).toFixed(1);
          }
          const total = valid.reduce((sum, item) => sum + Number(item[key] || 0), 0);
          return (total / valid.length / 10).toFixed(1);
        };
        return {
          taste: avg('tasteScore', 0.2),
          env: avg('envScore', 0.1),
          service: avg('serviceScore', 0)
        };
      },
      cityReasonTags() {
        const tags = [];
        if (this.shop.area) tags.push(this.shop.area);
        if (this.shop.district) tags.push(this.shop.district);
        if (Array.isArray(this.shop.sceneTags)) tags.push(...this.shop.sceneTags);
        if (Array.isArray(this.cityProfile.cultureTags)) tags.push(...this.cityProfile.cultureTags.slice(0, 2));
        return Array.from(new Set(tags.filter(Boolean))).slice(0, 5);
      },
      cityWorthReason() {
        if (this.shop.decisionReason) {
          return this.shop.decisionReason;
        }
        const cityName = this.cityProfile.cityName || this.shop.city || '本城';
        const area = this.shop.area || this.shop.district || '本地商圈';
        const scene = this.cityProfile.defaultScenes && this.cityProfile.defaultScenes[0] ? this.cityProfile.defaultScenes[0] : '今天的消费决策';
        return cityName + '版本里，' + area + '更适合' + scene + '，这家店可以作为顺路成交点。';
      },
      cityEditionEnabled() {
        return util.isCityEditionEnabled(this.cityProfile);
      },
      canManageShop() {
        return !!(this.merchantAccess && (this.merchantAccess.canEdit || this.merchantAccess.canVerify));
      },
      canEditShop() {
        return !!(this.merchantAccess && this.merchantAccess.canEdit);
      },
      merchantDialogTitle() {
        const titles = {
          shop: '商家管理',
          dishes: '招牌菜管理',
          deals: '团购上架',
          orders: '团购订单核销'
        };
        return titles[this.merchantDialogMode] || '商家管理';
      },
      shareUrl() {
        return location.origin + '/pages/shop/shop-detail.html?id=' + this.shopId;
      },
      shareTitle() {
        return this.shop.name || '一家值得去的本地店';
      },
      shareSummary() {
        const parts = [
          this.shop.area || this.shop.district,
          this.shop.address,
          this.shop.avgPrice ? ('人均 ¥' + this.shop.avgPrice) : ''
        ].filter(Boolean);
        return parts.length ? parts.join(' · ') : '点击查看店铺详情、团购和真实评价';
      },
      shareCover() {
        if (Array.isArray(this.shop.images) && this.shop.images.length) {
          return this.shop.images[0];
        }
        return this.fallbackImage;
      }
    },
    created() {
      let shopId = this.shopId;
      this.queryShopById(shopId);
      this.queryVoucher(shopId);
      this.checkFavorite(shopId);
      this.queryComments(shopId);
      this.queryFeaturedDishes(shopId);
      this.queryGroupDeals(shopId);
      this.loadMerchantAccess(shopId);
    },
    mounted() {
      if (util.getUrlParam('comment') === '1') {
        setTimeout(() => {
          const target = document.querySelector('.comment-composer');
          if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 700);
      }
    },
    methods: {
      goBack() {
        history.back();
      },
      goToMap(mode = 'preview') {
        if (this.shop.id) {
          if (mode === 'navigation') {
            util.openAmapNavigation(this.shop, { mode: 'car' });
            return;
          }
          const x = util.getShopCoordinate(this.shop, 'x');
          const y = util.getShopCoordinate(this.shop, 'y');
          location.href = util.buildUrl('../map/map.html', {
            targetId: this.shop.id,
            targetName: this.shop.name || '',
            targetAddress: this.shop.address || '',
            targetX: x,
            targetY: y,
            mode,
            cityCode: util.getActiveCityCode(this.cityProfile),
            city: this.cityProfile.cityName
          });
        }
      },
      callShop() {
        if (this.shop.phone) {
          location.href = 'tel:' + this.shop.phone;
          return;
        }
        this.$message.info('商家暂未填写联系电话');
      },
      loadMerchantAccess(shopId) {
        if (!shopId || !this.hasToken()) return;
        axios.get('/merchant/shops/' + shopId).then(({ data }) => {
          this.merchantAccess = Object.assign({
            canEdit: false,
            canVerify: false,
            pendingUpdate: false,
            memberRole: ''
          }, data || {});
          this.merchantShopSnapshot = data && data.shop ? data.shop : null;
          if (data && data.shop) {
            this.quickForm = {
              openHours: data.shop.openHours || '',
              avgPrice: data.shop.avgPrice || '',
              phone: data.shop.phone || ''
            };
          }
          if (util.getUrlParam('merchant') === '1' && this.canManageShop) {
            this.openMerchantDialog('shop');
          }
        }).catch(() => {
          this.merchantAccess = { canEdit: false, canVerify: false, pendingUpdate: false, memberRole: '' };
        });
      },
      openMerchantDialog(mode) {
        if (!this.canManageShop) {
          this.$message.warning('当前账号暂无该店铺管理权限');
          return;
        }
        this.merchantDialogMode = mode || 'shop';
        this.merchantDialogVisible = true;
        if (this.merchantDialogMode === 'shop') {
          const sourceShop = this.shop && this.shop.id ? this.shop : (this.merchantShopSnapshot || {});
          const imageCsv = Array.isArray(sourceShop.images) ? sourceShop.images.join(',') : (sourceShop.images || '');
          this.quickForm = {
            openHours: sourceShop.openHours || '',
            avgPrice: sourceShop.avgPrice || '',
            phone: sourceShop.phone || ''
          };
          this.updateForm = {
            name: sourceShop.name || '',
            typeId: sourceShop.typeId || '',
            area: sourceShop.area || '',
            address: sourceShop.address || '',
            x: sourceShop.x || '',
            y: sourceShop.y || '',
            images: imageCsv,
            proofImages: '',
            message: ''
          };
        }
        if (this.merchantDialogMode === 'dishes') {
          this.resetDishForm();
          this.loadMerchantDishes();
        }
        if (this.merchantDialogMode === 'deals') {
          this.resetGroupDealForm();
          this.loadMerchantGroupDeals();
        }
        if (this.merchantDialogMode === 'orders') {
          this.loadGroupOrders();
          this.loadDishOrders();
        }
      },
      saveQuickInfo() {
        if (!this.canEditShop) {
          this.$message.warning('只有店铺负责人或管理员可以修改资料');
          return;
        }
        axios.put('/merchant/shops/' + this.shopId + '/quick-info', {
          openHours: this.quickForm.openHours,
          avgPrice: this.quickForm.avgPrice === '' ? null : Number(this.quickForm.avgPrice),
          phone: this.quickForm.phone
        }).then(({ data }) => {
          this.$message.success('基础资料已更新');
          if (data) {
            this.shop = Object.assign({}, this.shop, data);
          }
          this.queryShopById(this.shopId);
          this.loadMerchantAccess(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '保存失败')));
      },
      submitUpdateApplication() {
        if (!this.canEditShop) {
          this.$message.warning('只有店铺负责人或管理员可以提交变更');
          return;
        }
        axios.post('/merchant/shops/' + this.shopId + '/update-applications', {
          name: this.updateForm.name,
          typeId: this.updateForm.typeId === '' ? null : Number(this.updateForm.typeId),
          area: this.updateForm.area,
          address: this.updateForm.address,
          x: this.updateForm.x === '' ? null : Number(this.updateForm.x),
          y: this.updateForm.y === '' ? null : Number(this.updateForm.y),
          images: this.updateForm.images,
          proofImages: this.updateForm.proofImages,
          message: this.updateForm.message
        }).then(() => {
          this.$message.success('已提交资料变更，审核通过后生效');
          this.loadMerchantAccess(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '提交审核失败')));
      },
      queryFeaturedDishes(shopId) {
        if (!shopId) return;
        axios.get('/shop/' + shopId + '/featured-dishes').then(({ data }) => {
          this.featuredDishes = Array.isArray(data) ? data : [];
        }).catch(() => {
          this.featuredDishes = [];
        });
      },
      queryGroupDeals(shopId) {
        if (!shopId) return;
        axios.get('/shop/' + shopId + '/group-deals').then(({ data }) => {
          this.groupDeals = Array.isArray(data) ? data.map(this.normalizeGroupDeal) : [];
        }).catch(() => {
          this.groupDeals = [];
        });
      },
      loadMerchantDishes() {
        if (!this.canEditShop) return;
        this.merchantLoading = true;
        axios.get('/merchant/shops/' + this.shopId + '/featured-dishes').then(({ data }) => {
          this.merchantDishes = Array.isArray(data) ? data : [];
        }).catch(err => this.$message.error(util.getErrorMessage(err, '加载招牌菜失败')))
          .finally(() => { this.merchantLoading = false; });
      },
      resetDishForm() {
        this.dishForm = {
          id: null,
          name: '',
          description: '',
          image: '',
          priceYuan: '',
          sort: 0
        };
      },
      editDish(dish) {
        this.dishForm = {
          id: dish.id,
          name: dish.name || '',
          description: dish.description || '',
          image: dish.image || '',
          priceYuan: dish.price ? this.priceToYuan(dish.price) : '',
          sort: dish.sort || 0
        };
      },
      saveDish() {
        if (!this.dishForm.name) {
          this.$message.warning('请填写招牌菜名称');
          return;
        }
        const payload = {
          id: this.dishForm.id,
          name: this.dishForm.name,
          description: this.dishForm.description,
          image: this.dishForm.image,
          price: this.yuanToFen(this.dishForm.priceYuan),
          sort: this.dishForm.sort || 0
        };
        const request = payload.id
          ? axios.put('/merchant/shops/' + this.shopId + '/featured-dishes', payload)
          : axios.post('/merchant/shops/' + this.shopId + '/featured-dishes', payload);
        request.then(() => {
          this.$message.success('招牌菜已保存');
          this.resetDishForm();
          this.loadMerchantDishes();
          this.queryFeaturedDishes(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '保存招牌菜失败')));
      },
      publishDish(dish) {
        axios.post('/merchant/featured-dishes/' + dish.id + '/publish').then(() => {
          this.$message.success('招牌菜已上架');
          this.loadMerchantDishes();
          this.queryFeaturedDishes(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '上架失败')));
      },
      unpublishDish(dish) {
        axios.post('/merchant/featured-dishes/' + dish.id + '/unpublish').then(() => {
          this.$message.success('招牌菜已下架');
          this.loadMerchantDishes();
          this.queryFeaturedDishes(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '下架失败')));
      },
      buyFeaturedDish(dish) {
        if (!this.hasToken()) {
          this.$message.error('请先登录');
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        if (!dish || !dish.id) return;
        axios.post('/featured-dishes/' + dish.id + '/orders').then(({ data }) => {
          this.showOrderCreatedDialog('招牌菜已支付', data.verifyCode);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '购买失败')));
      },
      loadMerchantGroupDeals() {
        if (!this.canEditShop) return;
        this.merchantLoading = true;
        axios.get('/merchant/shops/' + this.shopId + '/group-deals').then(({ data }) => {
          this.merchantGroupDeals = Array.isArray(data) ? data.map(this.normalizeGroupDeal) : [];
        }).catch(err => this.$message.error(util.getErrorMessage(err, '加载团购失败')))
          .finally(() => { this.merchantLoading = false; });
      },
      resetGroupDealForm() {
        this.groupDealForm = {
          id: null,
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
      },
      editGroupDeal(deal) {
        this.groupDealForm = {
          id: deal.id,
          title: deal.title || '',
          description: deal.description || '',
          images: deal.images || '',
          rules: deal.rules || '',
          priceYuan: this.priceToYuan(deal.price),
          originalPriceYuan: this.priceToYuan(deal.originalPrice),
          stock: deal.stock || 1,
          beginTime: deal.beginTime ? new Date(deal.beginTime) : null,
          endTime: deal.endTime ? new Date(deal.endTime) : null
        };
      },
      saveGroupDeal() {
        if (!this.groupDealForm.title) {
          this.$message.warning('请填写团购标题');
          return;
        }
        const payload = {
          id: this.groupDealForm.id,
          title: this.groupDealForm.title,
          description: this.groupDealForm.description,
          images: this.groupDealForm.images,
          rules: this.groupDealForm.rules,
          price: this.yuanToFen(this.groupDealForm.priceYuan),
          originalPrice: this.yuanToFen(this.groupDealForm.originalPriceYuan),
          stock: this.groupDealForm.stock,
          beginTime: this.groupDealForm.beginTime,
          endTime: this.groupDealForm.endTime
        };
        const request = payload.id
          ? axios.put('/merchant/shops/' + this.shopId + '/group-deals', payload)
          : axios.post('/merchant/shops/' + this.shopId + '/group-deals', payload);
        request.then(() => {
          this.$message.success('团购已保存');
          this.resetGroupDealForm();
          this.loadMerchantGroupDeals();
          this.queryGroupDeals(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '保存团购失败')));
      },
      publishGroupDeal(deal) {
        axios.post('/merchant/group-deals/' + deal.id + '/publish').then(() => {
          this.$message.success('团购已上架');
          this.loadMerchantGroupDeals();
          this.queryGroupDeals(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '上架失败')));
      },
      unpublishGroupDeal(deal) {
        axios.post('/merchant/group-deals/' + deal.id + '/unpublish').then(() => {
          this.$message.success('团购已下架');
          this.loadMerchantGroupDeals();
          this.queryGroupDeals(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '下架失败')));
      },
      buyGroupDeal(deal) {
        if (!this.hasToken()) {
          this.$message.error('请先登录');
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        axios.post('/group-deals/' + deal.id + '/orders').then(({ data }) => {
          this.showOrderCreatedDialog('团购已支付', data.verifyCode);
          this.queryGroupDeals(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '购买失败')));
      },
      showOrderCreatedDialog(title, verifyCode) {
        this.$confirm(
          '购买成功，到店出示券码：' + (verifyCode || '待生成'),
          title,
          {
            confirmButtonText: '查看订单',
            cancelButtonText: '继续逛逛',
            distinguishCancelAndClose: true,
            type: 'success'
          }
        ).then(() => {
          location.href = '/pages/order/orders.html';
        }).catch(() => {});
      },
      loadGroupOrders() {
        if (!this.merchantAccess.canVerify) return;
        this.merchantLoading = true;
        axios.get('/merchant/group-deal-orders', {
          params: {
            shopId: this.shopId,
            status: this.groupOrderStatusFilter,
            current: 1
          }
        }).then(({ data }) => {
          this.groupOrders = Array.isArray(data) ? data : [];
        }).catch(err => this.$message.error(util.getErrorMessage(err, '加载团购订单失败')))
          .finally(() => { this.merchantLoading = false; });
      },
      verifyGroupDealOrder() {
        if (!this.groupVerifyCode) {
          this.$message.warning('请先输入团购券码');
          return;
        }
        axios.post('/merchant/group-deal-orders/verify-code', {
          shopId: this.shopId,
          verifyCode: this.groupVerifyCode
        }).then(() => {
          this.$message.success('团购核销成功');
          this.groupVerifyCode = '';
          this.loadGroupOrders();
        }).catch(err => this.$message.error(util.getErrorMessage(err, '核销失败')));
      },
      loadDishOrders() {
        if (!this.merchantAccess.canVerify) return;
        this.merchantLoading = true;
        axios.get('/merchant/featured-dish-orders', {
          params: {
            shopId: this.shopId,
            status: this.dishOrderStatusFilter,
            current: 1
          }
        }).then(({ data }) => {
          this.dishOrders = Array.isArray(data) ? data : [];
        }).catch(err => this.$message.error(util.getErrorMessage(err, '加载招牌菜订单失败')))
          .finally(() => { this.merchantLoading = false; });
      },
      verifyDishOrder() {
        if (!this.dishVerifyCode) {
          this.$message.warning('请先输入招牌菜券码');
          return;
        }
        axios.post('/merchant/featured-dish-orders/verify-code', {
          shopId: this.shopId,
          verifyCode: this.dishVerifyCode
        }).then(() => {
          this.$message.success('招牌菜核销成功');
          this.dishVerifyCode = '';
          this.loadDishOrders();
        }).catch(err => this.$message.error(util.getErrorMessage(err, '核销失败')));
      },
      normalizeGroupDeal(deal) {
        const copy = Object.assign({}, deal || {});
        copy.imageList = this.splitImages(copy.images);
        return copy;
      },
      firstImage(value) {
        const images = this.splitImages(value);
        return this.resolveImageUrl(images[0] || (this.shop.images && this.shop.images[0]));
      },
      splitImages(value) {
        return util.csvToImageList(value);
      },
      appendCsvImage(current, image) {
        const list = this.splitImages(current);
        if (image) list.push(image);
        return util.imageListToCsv(list);
      },
      removeCsvImage(target, image) {
        const list = this.splitImages(this[target] || '').filter(item => item !== image);
        this[target] = util.imageListToCsv(list);
      },
      handleMerchantImageUpload(event, target) {
        const file = event && event.target && event.target.files ? event.target.files[0] : null;
        if (!file) return;
        this.merchantImageUploading = true;
        util.uploadImageFile(file).then((path) => {
          if (target === 'dish') {
            this.dishForm.image = path;
          } else if (target === 'deal') {
            this.groupDealForm.images = this.appendCsvImage(this.groupDealForm.images, path);
          } else if (target === 'shopImages') {
            this.updateForm.images = this.appendCsvImage(this.updateForm.images, path);
          } else if (target === 'proofImages') {
            this.updateForm.proofImages = this.appendCsvImage(this.updateForm.proofImages, path);
          }
          this.$message.success('图片已上传');
        }).catch(err => this.$message.error(util.getErrorMessage(err, '上传失败')))
          .finally(() => {
            this.merchantImageUploading = false;
            if (event && event.target) {
              event.target.value = '';
            }
          });
      },
      removeUpdateImage(type, image) {
        const key = type === 'proof' ? 'proofImages' : 'images';
        const list = this.splitImages(this.updateForm[key]).filter(item => item !== image);
        this.updateForm[key] = util.imageListToCsv(list);
      },
      removeDealImage(image) {
        const list = this.splitImages(this.groupDealForm.images).filter(item => item !== image);
        this.groupDealForm.images = util.imageListToCsv(list);
      },
      priceToYuan(value) {
        const price = util.formatPrice(value);
        return price == null ? '0.00' : price;
      },
      yuanToFen(value) {
        if (value === null || value === undefined || value === '') return null;
        const num = Number(value);
        if (!isFinite(num) || num <= 0) return null;
        return Math.round(num * 100);
      },
      formatDateTime(value) {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        const h = String(date.getHours()).padStart(2, '0');
        const min = String(date.getMinutes()).padStart(2, '0');
        return `${y}-${m}-${d} ${h}:${min}`;
      },
      formatDishStatus(status) {
        const map = { 0: '草稿', 1: '已上架', 2: '已下架' };
        return map[status] || '未知';
      },
      formatDealStatus(status) {
        const map = { 0: '草稿', 1: '已上架', 2: '已下架', 3: '已结束' };
        return map[status] || '未知';
      },
      toggleAllVouchers() {
        this.showAllVouchers = !this.showAllVouchers;
      },
      queryShopById(shopId) {
        axios.get("/shop/" + shopId)
        .then(({data}) => {
          data.images = (data.images ? data.images.split(",") : []).map(img => this.resolveImageUrl(img))
          this.shop = data
          const profile = data.cityCode
            ? (util.findCityProfile({ cityCode: data.cityCode, city: data.city, province: data.province })
              || util.createGenericCityProfile({ cityName: data.city || data.province || '当前位置', province: data.province }))
            : util.readCurrentCityProfile();
          this.cityProfile = util.normalizeCityProfile(profile);
          util.applyCityTheme(this.cityProfile);
        })
        .catch(err => this.$message.error(util.getErrorMessage(err)))
      },
      queryVoucher(shopId) {
        axios.get("/voucher/list/" + shopId)
        .then(({data}) => {
          this.vouchers = data;
        })
        .catch(err => this.$message.error(util.getErrorMessage(err)))
      },
      checkFavorite(shopId) {
        if (!this.hasToken()) return;
        axios.get(`/favorites/is/1/${shopId}`)
        .then(({data}) => {
          this.isFavorite = data;
        })
        .catch(() => {
          this.isFavorite = false;
        })
      },
      toggleFavorite() {
        if (!this.hasToken()) {
          this.$message.error("请先登录")
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        if (!this.shop.id) return;
        if (this.isFavorite) {
          axios.delete(`/favorites/1/${this.shop.id}`)
          .then(() => {
            this.isFavorite = false;
            this.showToast("success", "已取消收藏");
          })
          .catch(err => this.$message.error(util.getErrorMessage(err)))
        } else {
          axios.post(`/favorites/1/${this.shop.id}`)
          .then(() => {
            this.isFavorite = true;
            this.showToast("success", "收藏成功");
          })
          .catch(err => this.$message.error(util.getErrorMessage(err)))
        }
      },
      queryComments(shopId) {
        axios.get(`/shop-comment/list/${shopId}`, {
          params: { current: 1, size: 10 }
        })
        .then(({data, total}) => {
          this.comments = Array.isArray(data) ? data.map(this.normalizeComment) : [];
          this.commentTotal = Number.isFinite(Number(total)) ? Number(total) : this.comments.length;
          this.commentGoodRate = this.calculateCommentGoodRate(this.comments);
          this.commentTagOptions = this.buildCommentTags(this.comments, this.commentTotal);
          if (!this.commentTagOptions.some(item => item.key === this.activeCommentFilter)) {
            this.activeCommentFilter = 'all';
          }
        })
        .catch(() => {
          this.comments = [];
          this.commentTotal = 0;
          this.commentGoodRate = 0;
          this.commentTagOptions = this.buildCommentTags([], 0);
        });
      },
      handleCommentImageUpload(event) {
        const file = event && event.target && event.target.files ? event.target.files[0] : null;
        if (!file) return;
        this.commentImageUploading = true;
        util.uploadImageFile(file).then((path) => {
          if (path) {
            this.commentForm.images.push(path);
          }
          this.$message.success('图片已添加');
        }).catch(err => this.$message.error(util.getErrorMessage(err, '上传失败')))
          .finally(() => {
            this.commentImageUploading = false;
            if (event && event.target) event.target.value = '';
          });
      },
      removeCommentImage(image) {
        this.commentForm.images = this.commentForm.images.filter(item => item !== image);
      },
      submitShopComment() {
        if (!this.hasToken()) {
          this.$message.error('请先登录');
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        if (!this.commentForm.content) {
          this.$message.warning('请先写下评价内容');
          return;
        }
        this.commentSubmitting = true;
        const score = Math.max(10, Number(this.commentForm.score || 5) * 10);
        axios.post('/shop-comment', {
          shopId: this.shopId,
          score,
          tasteScore: score,
          envScore: score,
          serviceScore: score,
          content: this.commentForm.content,
          images: util.imageListToCsv(this.commentForm.images)
        }).then(() => {
          this.$message.success('评价已发布');
          this.commentForm = { score: 5, content: '', images: [] };
          this.queryComments(this.shopId);
        }).catch(err => this.$message.error(util.getErrorMessage(err, '发布评价失败')))
          .finally(() => { this.commentSubmitting = false; });
      },
      normalizeComment(comment) {
        const imageList = this.parseCommentImages(comment.images);
        const tags = [];
        if ((comment.score || 0) >= 40) tags.push('highScore');
        if ((comment.tasteScore || 0) >= 45) tags.push('taste');
        if ((comment.envScore || 0) >= 45) tags.push('environment');
        if ((comment.serviceScore || 0) >= 45) tags.push('service');
        if (imageList.length) tags.push('images');
        return Object.assign({}, comment, { imageList, tags });
      },
      parseCommentImages(images) {
        if (!images) return [];
        if (Array.isArray(images)) return images.filter(Boolean);
        return String(images).split(',').map(item => item && item.trim()).filter(Boolean);
      },
      buildCommentTags(comments, total) {
        const groups = [
          { key: 'all', label: '全部', count: total || 0 },
          { key: 'highScore', label: '高分评价', count: comments.filter(item => item.tags.includes('highScore')).length },
          { key: 'images', label: '有图', count: comments.filter(item => item.tags.includes('images')).length },
          { key: 'taste', label: '口味赞', count: comments.filter(item => item.tags.includes('taste')).length },
          { key: 'environment', label: '环境好', count: comments.filter(item => item.tags.includes('environment')).length },
          { key: 'service', label: '服务好', count: comments.filter(item => item.tags.includes('service')).length }
        ];
        return groups.filter((item, index) => index === 0 || item.count > 0);
      },
      calculateCommentGoodRate(comments) {
        if (!comments.length) return 0;
        const goodCount = comments.filter(item => Number(item.score || 0) >= 40).length;
        return Math.round(goodCount * 100 / comments.length);
      },
      formatCommentDate(value) {
        if (!value) return '刚刚';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
      },
      showToast(type, message) {
        if (this.toastTimer) {
          clearTimeout(this.toastTimer);
          this.toastTimer = null;
        }
        this.$message.closeAll();
        if (this.toastInstance && this.toastInstance.close) {
          this.toastInstance.close();
        }
        this.toastInstance = this.$message({
          type,
          message,
          duration: 1200,
          showClose: false,
          center: false
        });
        this.toastTimer = setTimeout(() => {
          if (this.toastInstance && this.toastInstance.close) {
            this.toastInstance.close();
          }
          this.toastInstance = null;
          this.toastTimer = null;
        }, 1300);
      },
      shareShop() {
        this.shareSheetVisible = true;
      },
      closeShareSheet() {
        this.shareSheetVisible = false;
        this.shareFriendPanelVisible = false;
      },
      shortShareText(value, maxLength) {
        const text = String(value || '').replace(/\s+/g, ' ').trim();
        if (!maxLength || text.length <= maxLength) {
          return text;
        }
        return text.slice(0, Math.max(0, maxLength - 1)) + '…';
      },
      copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          return navigator.clipboard.writeText(text);
        }
        const input = document.createElement('textarea');
        input.value = text;
        input.setAttribute('readonly', 'readonly');
        input.style.position = 'fixed';
        input.style.opacity = '0';
        document.body.appendChild(input);
        input.select();
        try {
          document.execCommand('copy');
          document.body.removeChild(input);
          return Promise.resolve();
        } catch (err) {
          document.body.removeChild(input);
          return Promise.reject(err);
        }
      },
      copyShareLink() {
        this.copyText(this.shareUrl).then(() => {
          this.$message.success('店铺链接已复制');
        }).catch(() => {
          this.$message.warning('请复制当前页面链接分享');
        });
      },
      buildSharePayload(channel, compact) {
        const payload = {
          type: 'share-shop',
          channel: channel || 'private',
          shopId: this.shopId,
          title: this.shortShareText(this.shareTitle, compact ? 48 : 90),
          summary: this.shortShareText(this.shareSummary, compact ? 64 : 120),
          cover: this.resolveImageUrl(this.shareCover),
          url: this.shareUrl,
          shopName: this.shortShareText(this.shop.name || '', 36)
        };
        if (!compact) {
          payload.appUrl = 'hmdp://shop/detail?id=' + this.shopId;
          payload.address = this.shortShareText(this.shop.address || '', 80);
          payload.target = channel || 'more';
          payload.text = payload.summary;
          payload.thumbUrl = payload.cover;
        }
        return payload;
      },
      buildShareMessage() {
        return '__HMDP_SHARE_SHOP__' + JSON.stringify(this.buildSharePayload('private', true));
      },
      openPrivateShare() {
        if (!this.hasToken()) {
          this.$message.error('请先登录');
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        this.shareFriendPanelVisible = !this.shareFriendPanelVisible;
        if (this.shareFriendPanelVisible && !this.shareFriends.length) {
          this.loadShareFriends();
        }
      },
      loadShareFriends() {
        if (this.shareFriendLoading) {
          return;
        }
        this.shareFriendLoading = true;
        const ensureUser = this.currentUser && this.currentUser.id
          ? Promise.resolve(this.currentUser)
          : axios.get('/user/me').then((res) => {
            this.currentUser = res.data || null;
            return this.currentUser;
          });
        ensureUser.then((user) => {
          if (!user || !user.id) {
            return [];
          }
          return Promise.all([
            axios.get('/follow/list/' + user.id, { params: { current: 1 } }).catch(() => ({ data: [] })),
            axios.get('/follow/followers/' + user.id, { params: { current: 1 } }).catch(() => ({ data: [] })),
            axios.get('/chat/conversations').catch(() => ({ data: [] }))
          ]).then((results) => {
            let rows = [];
            results.slice(0, 2).forEach((result) => {
              if (Array.isArray(result.data)) rows = rows.concat(result.data);
            });
            if (Array.isArray(results[2].data)) {
              rows = rows.concat(results[2].data.map((item) => ({
                id: item.otherUserId,
                nickName: item.otherUserName,
                icon: item.otherUserIcon
              })));
            }
            const seen = {};
            return rows.filter((item) => {
              const itemId = item && item.id ? Number(item.id) : 0;
              if (!itemId || itemId === Number(user.id) || seen[itemId]) {
                return false;
              }
              seen[itemId] = true;
              return true;
            });
          });
        }).then((list) => {
          this.shareFriends = Array.isArray(list) ? list : [];
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '加载好友失败'));
        }).finally(() => {
          this.shareFriendLoading = false;
        });
      },
      sendShareToFriend(user) {
        if (!user || !user.id) return;
        this.shareSendingId = user.id;
        axios.post('/chat/send', null, {
          params: {
            receiverId: user.id,
            content: this.buildShareMessage()
          }
        }).then(() => {
          this.$message.success('已私信给 ' + (user.nickName || '好友'));
        }).catch(err => this.$message.error(util.getErrorMessage(err, '私信发送失败')))
          .finally(() => { this.shareSendingId = 0; });
      },
      openShareUrl(url) {
        const opened = window.open(url, '_blank');
        if (!opened) {
          location.href = url;
        }
      },
      invokeNativeShare(channel) {
        const payload = this.buildSharePayload(channel);
        const message = JSON.stringify({ action: 'share', channel: channel || 'more', payload });
        const methodMap = {
          wechat: ['shareToWechat', 'shareWechatFriend', 'share'],
          moments: ['shareToWechatMoments', 'shareToMoments', 'share'],
          qq: ['shareToQQ', 'shareQQFriend', 'share'],
          qzone: ['shareToQZone', 'shareQZone', 'share'],
          more: ['shareSystem', 'share']
        };
        const methods = methodMap[channel] || methodMap.more;
        const bridges = [window.HmdpNative, window.HMDPNative, window.Android].filter(Boolean);
        for (let i = 0; i < bridges.length; i += 1) {
          for (let j = 0; j < methods.length; j += 1) {
            const method = bridges[i][methods[j]];
            if (typeof method === 'function') {
              try {
                const result = method.call(bridges[i], message);
                return result !== false;
              } catch (err) {}
            }
          }
        }
        if (window.webkit && window.webkit.messageHandlers) {
          const handlers = ['hmdpShare', 'HmdpNative', 'share'];
          for (let k = 0; k < handlers.length; k += 1) {
            const handler = window.webkit.messageHandlers[handlers[k]];
            if (handler && typeof handler.postMessage === 'function') {
              try {
                handler.postMessage({ action: 'share', payload });
                return true;
              } catch (err) {}
            }
          }
        }
        if (window.ReactNativeWebView && typeof window.ReactNativeWebView.postMessage === 'function') {
          window.ReactNativeWebView.postMessage(message);
          return true;
        }
        return false;
      },
      openAppScheme(scheme, fallback) {
        let hidden = false;
        const onVisibilityChange = () => {
          hidden = document.hidden;
        };
        document.addEventListener('visibilitychange', onVisibilityChange, { once: true });
        location.href = scheme;
        setTimeout(() => {
          document.removeEventListener('visibilitychange', onVisibilityChange);
          if (!hidden && fallback) {
            fallback();
          }
        }, 900);
      },
      buildQQScheme(channel) {
        const params = {
          src_type: 'web',
          version: '1',
          file_type: 'news',
          title: this.shareTitle,
          description: this.shareSummary,
          url: this.shareUrl,
          image_url: this.resolveImageUrl(this.shareCover) || ''
        };
        if (channel === 'qzone') {
          return util.buildUrl('mqqapi://qzone/publish', params);
        }
        return util.buildUrl('mqqapi://share/to_fri', params);
      },
      shareExternal(channel) {
        const payload = {
          title: this.shareTitle,
          text: this.shareSummary,
          url: this.shareUrl
        };
        if (this.invokeNativeShare(channel)) {
          this.$message.success('正在唤起分享面板');
          return;
        }
        if (channel === 'wechat' || channel === 'moments') {
          const targetName = channel === 'wechat' ? '微信好友' : '微信朋友圈';
          this.copyText(this.shareUrl).finally(() => {
            this.openAppScheme('weixin://', () => {
              this.$message.warning('当前 Web 环境已复制链接；App 原生层接入微信 SDK 后可直达' + targetName);
            });
          });
          return;
        }
        if (channel === 'qq') {
          this.openAppScheme(this.buildQQScheme('qq'), () => {
            this.openShareUrl('https://connect.qq.com/widget/shareqq/index.html?'
              + 'url=' + encodeURIComponent(this.shareUrl)
              + '&title=' + encodeURIComponent(this.shareTitle)
              + '&summary=' + encodeURIComponent(this.shareSummary)
              + '&pics=' + encodeURIComponent(this.shareCover || ''));
          });
          return;
        }
        if (channel === 'qzone') {
          this.openAppScheme(this.buildQQScheme('qzone'), () => {
            this.openShareUrl('https://sns.qzone.qq.com/cgi-bin/qzshare/cgi_qzshare_onekey?'
              + 'url=' + encodeURIComponent(this.shareUrl)
              + '&title=' + encodeURIComponent(this.shareTitle)
              + '&summary=' + encodeURIComponent(this.shareSummary)
              + '&pics=' + encodeURIComponent(this.shareCover || ''));
          });
          return;
        }
        if (navigator.share) {
          navigator.share(payload).catch(() => {});
          return;
        }
        this.copyShareLink();
      },
      generateShareImage() {
        if (this.shareImageGenerating) return;
        this.shareImageGenerating = true;
        const canvas = document.createElement('canvas');
        canvas.width = 750;
        canvas.height = 980;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#fff8f2';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = '#ff7a45';
        ctx.fillRect(0, 0, canvas.width, 18);
        ctx.fillStyle = '#1f2937';
        ctx.font = 'bold 44px "Microsoft YaHei", sans-serif';
        this.drawWrappedText(ctx, this.shareTitle, 60, 110, 630, 58, 2);
        ctx.fillStyle = '#6b7280';
        ctx.font = '28px "Microsoft YaHei", sans-serif';
        this.drawWrappedText(ctx, this.shareSummary, 60, 250, 630, 42, 3);
        ctx.fillStyle = '#ff7a45';
        ctx.font = 'bold 30px "Microsoft YaHei", sans-serif';
        ctx.fillText('打开 HMDP 查看团购、招牌菜和评价', 60, 760);
        ctx.fillStyle = '#9ca3af';
        ctx.font = '24px "Microsoft YaHei", sans-serif';
        this.drawWrappedText(ctx, this.shareUrl, 60, 820, 630, 34, 3);
        const link = document.createElement('a');
        link.download = 'shop-share-' + this.shopId + '.png';
        link.href = canvas.toDataURL('image/png');
        link.click();
        this.shareImageGenerating = false;
        this.$message.success('分享图已生成');
      },
      drawWrappedText(ctx, text, x, y, maxWidth, lineHeight, maxLines) {
        const words = String(text || '').split('');
        let line = '';
        let lineCount = 0;
        for (let i = 0; i < words.length; i += 1) {
          const test = line + words[i];
          if (ctx.measureText(test).width > maxWidth && line) {
            ctx.fillText(line, x, y);
            line = words[i];
            y += lineHeight;
            lineCount += 1;
            if (maxLines && lineCount >= maxLines) {
              return y;
            }
          } else {
            line = test;
          }
        }
        if (line && (!maxLines || lineCount < maxLines)) {
          ctx.fillText(line, x, y);
        }
        return y + lineHeight;
      },
      openUserProfile(userId) {
        if (!userId) return;
        location.href = '/pages/user/other-info.html?id=' + userId;
      },
      formatTime(v){
        let b = new Date(v.beginTime);
        let e = new Date(v.endTime);
        return b.getMonth() + 1 + "月" + b.getDate() + "日"
          + b.getHours() + ":" + this.formatMinutes(b.getMinutes())
          + " ~ "
          + e.getHours() + ":" + this.formatMinutes(e.getMinutes());
      },
      formatMinutes(m){
        if(m < 10) m = "0" + m
        return m;
      },
      isNotBegin(v){
        return new Date(v.beginTime).getTime() > new Date().getTime();
      },
      isEnd(v){
        if (!v.endTime) return false;
        return new Date(v.endTime).getTime() < new Date().getTime();
      },
      seckill(v){
        if(!this.hasToken()){
          this.$message.error("请先登录")
          util.redirectToLogin(location.pathname + location.search, 200);
          return;
        }
        if(v.type !== 1){
          axios.post("/voucher-order/receive/" + v.id)
          .then(() => {
            this.$message.success("领取成功");
            if (typeof v.stock === "number" && v.stock > 0) {
              v.stock--;
            }
          })
          .catch(err => this.$message.error(util.getErrorMessage(err)))
          return;
        }
        if(this.isNotBegin(v)){
          this.$message.error("优惠券抢购尚未开始")
          return;
        }
        if(this.isEnd(v)){
          this.$message.error("优惠券抢购已经结束")
          return;
        }
        if(v.stock < 1){
          this.$message.error("库存不足，请刷新后重试")
          return;
        }
        let id = v.id;
        axios.post("/voucher-order/seckill/" + id)
        .then(({data}) => {
          this.$message.success("抢购成功，订单id：" + data)
        })
        .catch(err => this.$message.error(util.getErrorMessage(err)))
      },
      hasToken() {
        return util.hasToken();
      },
      resolveImageUrl(path) {
        return util.resolveImageUrl(path, this.fallbackImage);
      },
      handleImageError(event) {
        util.applyImageFallback(event, this.fallbackImage);
      }
    }
  })

