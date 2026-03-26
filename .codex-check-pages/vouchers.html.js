
  new Vue({
    el: '#app',
    data: {
      activeTab: 'available',
      vouchers: [],
      myVouchers: [],
      loading: true,
      countdownTimer: null
    },
    created() {
      this.loadVouchers();
      this.startCountdowns();
    },
    beforeDestroy() {
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer);
      }
    },
    methods: {
      loadVouchers() {
        this.loading = true;
        axios.get('/voucher/list')
          .then(({ data }) => {
            this.vouchers = Array.isArray(data) ? data : [];
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err, '加载优惠券失败'));
          })
          .finally(() => {
            this.loading = false;
          });
      },
      loadMyVouchers() {
        if (!this.ensureLogin()) {
          this.loading = false;
          return;
        }
        this.loading = true;
        axios.get('/voucher-order/list')
          .then(({ data }) => {
            this.myVouchers = Array.isArray(data) ? data : [];
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err, '加载订单失败'));
          })
          .finally(() => {
            this.loading = false;
          });
      },
      handleTabChange() {
        if (this.activeTab === 'my') {
          this.loadMyVouchers();
        } else {
          this.loadVouchers();
        }
      },
      receiveVoucher(v) {
        if (!this.ensureLogin()) {
          return;
        }
        const request = v.type === 1
          ? axios.post(`/voucher-order/seckill/${v.id}`)
          : axios.post(`/voucher-order/receive/${v.id}`);

        request.then(() => {
          this.$message.success(v.type === 1 ? '秒杀成功' : '领取成功');
          if (typeof v.stock === 'number' && v.stock > 0) {
            v.stock--;
          }
          this.activeTab = 'my';
          this.loadMyVouchers();
        }).catch(err => {
          this.$message.error(this.getErrorMessage(err, v.type === 1 ? '秒杀失败' : '领取失败'));
        });
      },
      payOrder(id) {
        if (!this.ensureLogin()) {
          return;
        }
        axios.post(`/voucher-order/pay/${id}`)
          .then(() => {
            this.$message.success('支付成功');
            this.loadMyVouchers();
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err, '支付失败'));
          });
      },
      useOrder(id) {
        this.$confirm('确定要使用这张优惠券吗？', '提示', {
          confirmButtonText: '纭畾',
          cancelButtonText: '鍙栨秷',
          type: 'warning'
        }).then(() => {
          axios.post(`/voucher-order/verify/${id}`)
            .then(() => {
              this.$message.success('核销成功');
              this.loadMyVouchers();
            })
            .catch(err => {
              this.$message.error(this.getErrorMessage(err, '核销失败'));
            });
        });
      },
      cancelOrder(id) {
        this.$confirm('确定要取消这个订单吗？', '提示', {
          confirmButtonText: '纭畾',
          cancelButtonText: '鍙栨秷',
          type: 'warning'
        }).then(() => {
          axios.post(`/voucher-order/cancel/${id}`)
            .then(() => {
              this.$message.success('取消成功');
              this.loadMyVouchers();
            })
            .catch(err => {
              this.$message.error(this.getErrorMessage(err, '取消失败'));
            });
        });
      },
      formatDate(dateStr) {
        if (!dateStr) return '长期有效';
        const date = new Date(dateStr);
        return `${date.getMonth() + 1}月${date.getDate()}日`;
      },
      isSeckillActive(v) {
        if (!v.beginTime || !v.endTime) return false;
        const now = Date.now();
        const begin = new Date(v.beginTime).getTime();
        const end = new Date(v.endTime).getTime();
        return now >= begin && now <= end;
      },
      formatCountdown(v) {
        if (!v.beginTime) return '';
        const now = Date.now();
        const begin = new Date(v.beginTime).getTime();
        const end = new Date(v.endTime).getTime();
        if (now < begin) {
          const diff = begin - now;
          const hours = Math.floor(diff / 3600000);
          const mins = Math.floor((diff % 3600000) / 60000);
          return `距开始 ${hours}小时${mins}分钟`;
        }
        if (now <= end) {
          const diff = end - now;
          const hours = Math.floor(diff / 3600000);
          const mins = Math.floor((diff % 3600000) / 60000);
          return `距结束 ${hours}小时${mins}分钟`;
        }
        return '已结束';
      },
      startCountdowns() {
        this.countdownTimer = setInterval(() => {
          this.vouchers = [...this.vouchers];
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
      getOrderStyle(status) {
        if (status === 3 || status === 4 || status === 6) {
          return { opacity: 0.6 };
        }
        return {};
      },
      toYuan(value) {
        const price = util.formatPrice(value);
        return price == null ? '0.00' : price;
      },
      ensureLogin() {
        if (localStorage.getItem('token')) {
          return true;
        }
        this.$message.error('请先登录');
        setTimeout(() => {
          location.href = '/pages/auth/login.html';
        }, 200);
        return false;
      },
      getErrorMessage(err, fallback) {
        if (typeof err === 'string') {
          return err;
        }
        return err?.response?.data?.errorMsg || err?.message || fallback;
      }
    }
  });

