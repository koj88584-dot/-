const app = new Vue({
    el: "#app",
    data: {
      util,
      shop: {},
      cityProfile: util.readCurrentCityProfile(),
      vouchers: [],
      isFavorite: false,
      fallbackImage: '/imgs/blogs/blog1.jpg',
      comments: [],
      commentTotal: 0,
      commentGoodRate: 0,
      commentTagOptions: [],
      activeCommentFilter: 'all',
      showAllVouchers: false,
      toastInstance: null,
      toastTimer: null
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
      }
    },
    created() {
      let shopId = util.getUrlParam("id");
      this.queryShopById(shopId);
      this.queryVoucher(shopId);
      this.checkFavorite(shopId);
      this.queryComments(shopId);
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
        this.$message.info('拨打电话功能开发中...');
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

