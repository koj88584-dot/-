
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
          return this.messages.filter(m => m.isRead === 0);
        } else if (this.filterType === 'read') {
          return this.messages.filter(m => m.isRead === 1);
        }
        return this.messages;
      }
    },
    created() {
      this.loadMessages();
      this.loadUnreadCount();
    },
    methods: {
      loadMessages() {
        this.loading = true;
        axios.get('/message/list', { params: { current: this.current } })
          .then(({ data }) => {
            const list = data?.data || data;
            if (this.current === 1) {
              this.messages = Array.isArray(list) ? list : [];
            } else {
              this.messages = this.messages.concat(Array.isArray(list) ? list : []);
            }
            this.hasMore = Array.isArray(list) && list.length === 10;
            this.loading = false;
            this.loadingMore = false;
          })
          .catch(() => {
            this.loading = false;
            this.loadingMore = false;
          });
      },
      loadUnreadCount() {
        axios.get('/message/unread-count')
          .then(({ data }) => {
            this.unreadCount = data?.data || data || 0;
          })
          .catch(() => {});
      },
      handleFilterChange() {
        // 绛涢€変笉闇€瑕侀噸鏂板姞杞?
      },
      loadMore() {
        this.current++;
        this.loadingMore = true;
        this.loadMessages();
      },
      goAssistant() {
        location.href = '/pages/misc/assistant.html?scene=' + encodeURIComponent('消息中心');
      },
      goAssistantWithPrompt(prompt) {
        const query = 'scene=' + encodeURIComponent('消息中心') + '&prompt=' + encodeURIComponent(prompt);
        location.href = '/pages/misc/assistant.html?' + query;
      },
      openMessage(msg) {
        if (msg.isRead === 0) {
          axios.post('/message/read', null, { params: { messageId: msg.id } })
            .then(() => {
              msg.isRead = 1;
              this.unreadCount = Math.max(0, this.unreadCount - 1);
            });
        }
        // 濡傛灉鏈夊叧鑱斿簵閾猴紝璺宠浆鍒板簵閾鸿鎯?
        if (msg.shopId) {
          location.href = '/pages/shop/shop-detail.html?id=' + msg.shopId;
        }
      },
      markAllAsRead() {
        axios.post('/message/read')
          .then(() => {
            this.messages.forEach(m => m.isRead = 1);
            this.unreadCount = 0;
            this.$message.success('已全部标记为已读');
          })
          .catch(() => {
            this.$message.error('操作失败');
          });
      },
      deleteMessage(id) {
        this.$confirm('纭畾鍒犻櫎杩欐潯娑堟伅鍚楋紵', '鎻愮ず', {
          confirmButtonText: '纭畾',
          cancelButtonText: '鍙栨秷',
          type: 'warning'
        }).then(() => {
          axios.delete('/message/' + id)
            .then(() => {
              const msg = this.messages.find(m => m.id === id);
              if (msg && msg.isRead === 0) {
                this.unreadCount = Math.max(0, this.unreadCount - 1);
              }
              this.messages = this.messages.filter(m => m.id !== id);
              this.$message.success('已删除');
            })
            .catch(() => {
              this.$message.error('删除失败');
            });
        });
      },
      clearAll() {
        this.$confirm('确定清空所有消息吗？', '提示', {
          confirmButtonText: '纭畾',
          cancelButtonText: '鍙栨秷',
          type: 'warning'
        }).then(() => {
          axios.delete('/message/clear')
            .then(() => {
              this.messages = [];
              this.unreadCount = 0;
              this.$message.success('已清空');
            })
            .catch(() => {
              this.$message.error('清空失败');
            });
        });
      },
      getIcon(type) {
        const icons = {
          1: 'el-icon-present', // 浼樻儬娲诲姩
          2: 'el-icon-goods', // 鏂板搧涓婃灦
          3: 'el-icon-info', // 搴楅摵鍏憡
          4: 'el-icon-price-tag' // 浠锋牸鍙樺姩
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
        
        return `${date.getMonth() + 1}月${date.getDate()}日`;
      }
    }
  });

