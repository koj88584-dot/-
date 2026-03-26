
  const app = new Vue({
    el: "#app",
    data: {
      util,
      shop: {},
      vouchers: [],
      isFavorite: false
    },
    created() {
      let shopId = util.getUrlParam("id");
      this.queryShopById(shopId);
      this.queryVoucher(shopId);
      this.checkFavorite(shopId);
    },
    methods: {
      goBack() {
        history.back();
      },
      goToMap() {
        if (this.shop.id) {
          location.href = `../map/map.html?targetId=${this.shop.id}&targetName=${encodeURIComponent(this.shop.name)}&targetAddress=${encodeURIComponent(this.shop.address)}&targetX=${this.shop.x}&targetY=${this.shop.y}`;
        }
      },
      callShop() {
        this.$message.info('鎷ㄦ墦鐢佃瘽鍔熻兘寮€鍙戜腑...');
      },
      queryShopById(shopId) {
        axios.get("/shop/" + shopId)
        .then(({data}) => {
          data.images = (data.images ? data.images.split(",") : []).map(img => this.resolveImageUrl(img))
          this.shop = data
        })
        .catch(err => this.$message.error(this.getErrorMessage(err)))
      },
      queryVoucher(shopId) {
        axios.get("/voucher/list/" + shopId)
        .then(({data}) => {
          this.vouchers = data;
        })
        .catch(err => this.$message.error(this.getErrorMessage(err)))
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
          this.$message.error("璇峰厛鐧诲綍")
          setTimeout(() => {
            location.href = "../auth/login.html"
          }, 200);
          return;
        }
        if (!this.shop.id) return;
        if (this.isFavorite) {
          // 鍙栨秷鏀惰棌
          axios.delete(`/favorites/1/${this.shop.id}`)
          .then(() => {
            this.isFavorite = false;
            this.$message.success("已取消收藏");
          })
          .catch(err => this.$message.error(this.getErrorMessage(err)))
        } else {
          // 娣诲姞鏀惰棌
          axios.post(`/favorites/1/${this.shop.id}`)
          .then(() => {
            this.isFavorite = true;
            this.$message.success("收藏成功");
          })
          .catch(err => this.$message.error(this.getErrorMessage(err)))
        }
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
          setTimeout(() => {
            location.href = "../auth/login.html"
          }, 200);
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
          .catch(err => this.$message.error(this.getErrorMessage(err)))
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
        .catch(err => this.$message.error(this.getErrorMessage(err)))
      },
      hasToken() {
        return !!localStorage.getItem("token");
      },
      resolveImageUrl(path) {
        if (!path) {
          return "/imgs/icons/default-icon.png";
        }
        if (path.startsWith("http") || path.startsWith("data:")) {
          return path;
        }
        if (path.startsWith(util.commonURL)) {
          return path;
        }
        return util.commonURL + (path.startsWith("/") ? path : "/" + path);
      },
      getErrorMessage(err) {
        if (typeof err === "string") {
          return err;
        }
        return err?.response?.data?.errorMsg || err?.message || "鎿嶄綔澶辫触";
      }
    }
  })

