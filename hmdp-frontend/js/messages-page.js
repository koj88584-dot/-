new Vue({
    el: '#app',
    data: {
      messages: [],
      loading: true,
      loadingMore: false,
      current: 1,
      hasMore: true,
      unreadCount: 0,
      filterType: 'all'
    },
    computed: {
      filteredMessages() {
        if (this.filterType === 'unread') {
          return this.messages.filter((item) => item.isRead === 0);
        }
        if (this.filterType === 'read') {
          return this.messages.filter((item) => item.isRead === 1);
        }
        return this.messages;
      }
    },
    created() {
      this.loadMessages();
      this.loadUnreadCount();
    },
    methods: {
      goBack() {
        history.back();
      },
      loadMessages() {
        this.loading = this.current === 1;
        axios.get('/message/list', { params: { current: this.current } }).then(({ data }) => {
          const list = data?.data || data;
          const rows = Array.isArray(list) ? list : [];
          if (this.current === 1) {
            this.messages = rows;
          } else {
            this.messages = this.messages.concat(rows);
          }
          this.hasMore = rows.length === 10;
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '加载消息失败'));
        }).finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
      },
      loadUnreadCount() {
        axios.get('/message/unread-count').then(({ data }) => {
          this.unreadCount = data?.data || data || 0;
        }).catch(() => {});
      },
      loadMore() {
        if (this.loadingMore || !this.hasMore) return;
        this.current += 1;
        this.loadingMore = true;
        this.loadMessages();
      },
      goAssistant() {
        location.href = '/pages/misc/assistant.html?scene=' + encodeURIComponent('娑堟伅涓績');
      },
      goAssistantWithPrompt(prompt) {
        const query = 'scene=' + encodeURIComponent('娑堟伅涓績') + '&prompt=' + encodeURIComponent(prompt);
        location.href = '/pages/misc/assistant.html?' + query;
      },
      openMessage(msg) {
        if (msg.isRead === 0) {
          axios.post('/message/read', null, { params: { messageId: msg.id } }).then(() => {
            msg.isRead = 1;
            this.unreadCount = Math.max(0, this.unreadCount - 1);
          }).catch(() => {});
        }
        if (msg.shopId) {
          location.href = '/pages/shop/shop-detail.html?id=' + msg.shopId;
        }
      },
      markAllAsRead() {
        axios.post('/message/read').then(() => {
          this.messages.forEach((item) => { item.isRead = 1; });
          this.unreadCount = 0;
          this.$message.success('宸插叏閮ㄦ爣璁颁负宸茶');
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '操作失败'));
        });
      },
      deleteMessage(id) {
        this.$confirm('确定删除这条消息吗？', '提示', {
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          return axios.delete('/message/' + id);
        }).then(() => {
          const target = this.messages.find((item) => item.id === id);
          if (target && target.isRead === 0) {
            this.unreadCount = Math.max(0, this.unreadCount - 1);
          }
          this.messages = this.messages.filter((item) => item.id !== id);
          this.$message.success('删除成功');
        }).catch((err) => {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '删除失败'));
          }
        });
      },
      clearAll() {
        this.$confirm('确定清空所有消息吗？', '提示', {
          confirmButtonText: '确定清空',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          return axios.delete('/message/clear');
        }).then(() => {
          this.messages = [];
          this.unreadCount = 0;
          this.$message.success('已清空消息');
        }).catch((err) => {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '清空失败'));
          }
        });
      },
      getIcon(type) {
        const icons = {
          1: 'el-icon-present',
          2: 'el-icon-goods',
          3: 'el-icon-info',
          4: 'el-icon-price-tag'
        };
        return icons[type] || 'el-icon-bell';
      },
      formatTime(timeStr) {
        if (!timeStr) return '';
        const date = new Date(timeStr);
        const now = new Date();
        const diff = now - date;
        if (diff < 60000) return '刚刚';
        if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
        if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
        if (diff < 604800000) return Math.floor(diff / 86400000) + '天前';
        return (date.getMonth() + 1) + '月' + date.getDate() + '日';
      }
    }
  });

