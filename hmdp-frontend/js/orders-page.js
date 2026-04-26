new Vue({
    el: '#app',
    data() {
      return {
        util,
        activeStatus: '0',
        orders: [],
        rawOrders: [],
        loading: true,
        loadingMore: false,
        current: 1,
        hasMore: true
      };
    },
    computed: {
      summaryCards() {
        return [
          { key: 'all', label: '当前筛选', value: this.orders.length },
          { key: 'paying', label: '待支付', value: this.countByStatus(1) },
          { key: 'paid', label: '已支付', value: this.countByStatus(2) },
          { key: 'done', label: '已完成', value: this.countByStatus(3) }
        ];
      }
    },
    created() {
      this.ensureLogin();
      this.handleAssistantEntry();
      const statusParam = util.getUrlParam('status');
      if (statusParam === 'review') {
        this.activeStatus = 'review';
      }
      this.loadOrders();
    },
    methods: {
      goBack() {
        history.back();
      },
      ensureLogin() {
        if (util.hasToken()) {
          return true;
        }
        this.$message.warning('请先登录');
        util.redirectToLogin(location.pathname + location.search, 200);
        return false;
      },
      handleAssistantEntry() {
        const source = util.getUrlParam('source');
        const status = util.getUrlParam('status');
        if (source !== 'assistant') {
          return;
        }
        if (status !== '') {
          this.activeStatus = String(status);
        }
      },
      loadOrders() {
        if (!this.ensureLogin()) {
          return;
        }
        this.loading = this.current === 1 && !this.loadingMore;
        var status = this.activeStatus === '0' || this.activeStatus === 'review' ? null : Number(this.activeStatus);
        var reviewMode = this.activeStatus === 'review';
        var requests = [];
        if (!reviewMode) {
          requests.push(axios.get('/voucher-order/list', {
            params: { current: this.current, status: status }
          }).then(function (res) {
            return (Array.isArray(res.data) ? res.data : []).map(this.normalizeVoucherOrder);
          }.bind(this)).catch(function () { return []; }));
        }
        requests.push(axios.get('/group-deals/orders/my', {
          params: { current: this.current, status: reviewMode ? 3 : status, commented: reviewMode ? 0 : null }
        }).then(function (res) {
          return (Array.isArray(res.data) ? res.data : []).map(this.normalizeGroupDealOrder);
        }.bind(this)).catch(function () { return []; }));
        requests.push(axios.get('/featured-dishes/orders/my', {
          params: { current: this.current, status: reviewMode ? 3 : status, commented: reviewMode ? 0 : null }
        }).then(function (res) {
          return (Array.isArray(res.data) ? res.data : []).map(this.normalizeDishOrder);
        }.bind(this)).catch(function () { return []; }));
        Promise.all(requests).then(function (results) {
          var list = results.reduce(function (all, rows) {
            return all.concat(rows || []);
          }, []);
          list.sort(function (a, b) {
            return new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime();
          });
          if (this.current === 1) {
            this.rawOrders = list;
            this.orders = list;
          } else {
            this.rawOrders = this.rawOrders.concat(list);
            this.orders = this.orders.concat(list);
          }
          this.hasMore = list.length >= 20;
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '加载订单失败'));
        }.bind(this)).finally(function () {
          this.loading = false;
          this.loadingMore = false;
        }.bind(this));
      },
      normalizeVoucherOrder(order) {
        return Object.assign({
          productType: 'voucher',
          productLabel: order && order.voucherType === 1 ? '秒杀券' : '优惠券',
          canReview: false
        }, order || {});
      },
      normalizeGroupDealOrder(order) {
        order = order || {};
        return Object.assign({}, order, {
          productType: 'group',
          productLabel: '团购',
          voucherTitle: order.dealTitle || '团购套餐',
          voucherSubTitle: '到店核销后可评价',
          voucherImages: order.dealImages,
          actualValue: order.payValue,
          payValue: order.payValue,
          voucherType: 3,
          canReview: order.status === 3 && Number(order.commented || 0) === 0
        });
      },
      normalizeDishOrder(order) {
        order = order || {};
        return Object.assign({}, order, {
          productType: 'dish',
          productLabel: '招牌菜',
          voucherTitle: order.dishName || '招牌菜',
          voucherSubTitle: order.dishDescription || '到店核销后可评价',
          voucherImages: order.dishImage,
          actualValue: order.payValue,
          payValue: order.payValue,
          voucherType: 4,
          canReview: order.status === 3 && Number(order.commented || 0) === 0
        });
      },
      handleStatusChange() {
        this.current = 1;
        this.hasMore = true;
        this.loadOrders();
      },
      loadMore() {
        this.current += 1;
        this.loadingMore = true;
        this.loadOrders();
      },
      payOrder(id) {
        this.confirmThen('确认支付这笔订单吗？', '/voucher-order/pay/' + id, '支付成功');
      },
      useOrder(id) {
        this.confirmThen('确认核销后将无法再退款，是否继续？', '/voucher-order/verify/' + id, '核销成功');
      },
      cancelOrder(id) {
        this.confirmThen('取消后会释放本次订单名额，确定要取消吗？', '/voucher-order/cancel/' + id, '订单已取消');
      },
      refundOrder(id) {
        this.confirmThen('确认申请退款吗？退款成功后会恢复券库存。', '/voucher-order/refund/' + id, '退款成功');
      },
      showVerifyCode(order) {
        this.$alert('到店向商家出示券码：' + (order.verifyCode || '暂无券码'), order.productLabel + '待核销', {
          confirmButtonText: '知道了'
        });
      },
      reviewOrder(order) {
        if (!order || !order.shopId) return;
        location.href = '/pages/shop/shop-detail.html?id=' + order.shopId + '&comment=1';
      },
      goShop(order) {
        if (!order || !order.shopId) return;
        location.href = '/pages/shop/shop-detail.html?id=' + order.shopId;
      },
      confirmThen(message, url, successText) {
        this.$confirm(message, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(function () {
          return axios.post(url);
        }).then(function () {
          this.$message.success(successText);
          this.current = 1;
          this.hasMore = true;
          this.loadOrders();
        }.bind(this)).catch(function (err) {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '操作失败'));
          }
        }.bind(this));
      },
      countByStatus(status) {
        return this.orders.filter(function (order) {
          return order.status === status;
        }).length;
      },
      getStatusText(status) {
        var map = {
          1: '待支付',
          2: '已支付',
          3: '已核销',
          4: '已取消',
          5: '退款中',
          6: '已退款'
        };
        return map[status] || '未知状态';
      },
      getStatusHint(order) {
        if (order.status === 1) {
          return '下单后仍未支付，可直接取消或完成支付。';
        }
        if (order.status === 2) {
          return '订单已支付，可到店核销或在未使用前申请退款。';
        }
        if (order.status === 3) {
          return '订单已核销完成，消费已闭环。';
        }
        if (order.status === 4) {
          return '订单已取消，库存已经退回。';
        }
        if (order.status === 6) {
          return '订单已退款，费用与库存已恢复。';
        }
        return '订单状态处理中。';
      },
      formatDate(value) {
        if (!value) {
          return '--';
        }
        var date = new Date(value);
        if (isNaN(date.getTime())) {
          return value;
        }
        var pad = function (num) {
          return String(num).padStart(2, '0');
        };
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
      },
      toYuan(value) {
        return ((Number(value) || 0) / 100).toFixed((Number(value) || 0) % 100 === 0 ? 0 : 2);
      },
      resolveImageUrl(path) {
        return util.resolveImageUrl(path, '/imgs/icons/default-icon.png');
      },
      goToVouchers() {
        location.href = '/pages/misc/vouchers.html';
      }
    }
  });

