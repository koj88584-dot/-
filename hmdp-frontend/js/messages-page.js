new Vue({
  el: "#app",
  data() {
    return {
      activeTab: "chat",
      conversations: [],
      conversationsLoading: false,
      messages: [],
      loading: true,
      loadingMore: false,
      current: 1,
      hasMore: true,
      unreadCount: 0,
      filterType: "all",
      fallbackAvatar: "/imgs/icons/default-icon.png"
    };
  },
  computed: {
    chatUnreadCount() {
      return this.conversations.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0);
    },
    totalUnreadCount() {
      return this.unreadCount + this.chatUnreadCount;
    },
    filteredMessages() {
      if (this.filterType === "unread") {
        return this.messages.filter((item) => item.isRead === 0);
      }
      if (this.filterType === "read") {
        return this.messages.filter((item) => item.isRead === 1);
      }
      return this.messages;
    }
  },
  created() {
    this.loadConversations();
    this.loadMessages();
    this.loadUnreadCount();
  },
  methods: {
    goBack() {
      history.back();
    },
    goAddFriend() {
      location.href = "/pages/misc/add-friend.html";
    },
    switchTab(tab) {
      this.activeTab = tab;
      if (tab === "chat") {
        this.loadConversations();
      }
    },
    loadConversations() {
      this.conversationsLoading = true;
      axios.get("/chat/conversations")
        .then((result) => {
          this.conversations = Array.isArray(result.data)
            ? result.data.map((item) => Object.assign({}, item, {
              previewText: this.conversationPreview(item.lastMessage)
            }))
            : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载私信会话失败"));
        })
        .finally(() => {
          this.conversationsLoading = false;
        });
    },
    openConversation(item) {
      if (!item || !item.otherUserId) {
        return;
      }
      location.href = "/pages/misc/chat.html?userId=" + item.otherUserId;
    },
    openProfile(userId) {
      if (!userId) {
        return;
      }
      location.href = "/pages/user/other-info.html?id=" + userId;
    },
    loadMessages() {
      this.loading = this.current === 1;
      axios.get("/message/list", { params: { current: this.current } })
        .then((result) => {
          const rows = Array.isArray(result.data) ? result.data : [];
          if (this.current === 1) {
            this.messages = rows;
          } else {
            this.messages = this.messages.concat(rows);
          }
          this.hasMore = rows.length === 10;
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载系统消息失败"));
        })
        .finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
    },
    loadUnreadCount() {
      axios.get("/message/unread-count")
        .then((result) => {
          this.unreadCount = Number(result.data || 0);
        })
        .catch(() => {});
    },
    loadMore() {
      if (this.loadingMore || !this.hasMore) return;
      this.current += 1;
      this.loadingMore = true;
      this.loadMessages();
    },
    goAssistant() {
      location.href = "/pages/misc/assistant.html?scene=" + encodeURIComponent("消息中心");
    },
    goAssistantWithPrompt(prompt) {
      const query = "scene=" + encodeURIComponent("消息中心") + "&prompt=" + encodeURIComponent(prompt);
      location.href = "/pages/misc/assistant.html?" + query;
    },
    openMessage(msg) {
      if (msg.isRead === 0) {
        axios.post("/message/read", null, { params: { messageId: msg.id } })
          .then(() => {
            msg.isRead = 1;
            this.unreadCount = Math.max(0, this.unreadCount - 1);
          })
          .catch(() => {});
      }
      if (msg.shopId) {
        location.href = "/pages/shop/shop-detail.html?id=" + msg.shopId;
      }
    },
    markAllAsRead() {
      axios.post("/message/read")
        .then(() => {
          this.messages.forEach((item) => { item.isRead = 1; });
          this.unreadCount = 0;
          this.$message.success("已全部标记为已读");
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "操作失败"));
        });
    },
    deleteMessage(id) {
      this.$confirm("确定删除这条消息吗？", "提示", {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => axios.delete("/message/" + id))
        .then(() => {
          const target = this.messages.find((item) => item.id === id);
          if (target && target.isRead === 0) {
            this.unreadCount = Math.max(0, this.unreadCount - 1);
          }
          this.messages = this.messages.filter((item) => item.id !== id);
          this.$message.success("删除成功");
        })
        .catch((err) => {
          if (err !== "cancel") {
            this.$message.error(util.getErrorMessage(err, "删除失败"));
          }
        });
    },
    clearAll() {
      this.$confirm("确定清空所有系统消息吗？", "提示", {
        confirmButtonText: "确定清空",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => axios.delete("/message/clear"))
        .then(() => {
          this.messages = [];
          this.unreadCount = 0;
          this.$message.success("已清空系统消息");
        })
        .catch((err) => {
          if (err !== "cancel") {
            this.$message.error(util.getErrorMessage(err, "清空失败"));
          }
        });
    },
    getIcon(type) {
      const icons = {
        1: "el-icon-present",
        2: "el-icon-goods",
        3: "el-icon-info",
        4: "el-icon-price-tag"
      };
      return icons[type] || "el-icon-bell";
    },
    avatarUrl(path) {
      return util.resolveImageUrl(path, this.fallbackAvatar);
    },
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
    },
    parseShareContent(content) {
      if (!content || typeof content !== "string") {
        return null;
      }
      const shareMarkers = [
        { prefix: "__HMDP_SHARE_SHOP__", type: "share-shop" },
        { prefix: "__HMDP_SHARE_BLOG__", type: "share-blog" }
      ];
      for (let i = 0; i < shareMarkers.length; i += 1) {
        const marker = shareMarkers[i];
        if (content.indexOf(marker.prefix) === 0) {
          try {
            return Object.assign({ type: marker.type }, JSON.parse(content.slice(marker.prefix.length)) || {});
          } catch (e) {
            return { type: marker.type, title: "点击查看" };
          }
        }
      }
      const prefix = "__HMDP_SHARE_BLOG__";
      const hasShareMarker = content.indexOf(prefix) === 0;
      const raw = hasShareMarker ? content.slice(prefix.length) : content;
      if (raw.charAt(0) !== "{" && raw.charAt(0) !== "[") {
        return hasShareMarker ? { type: "share-blog", title: "点击查看" } : null;
      }
      try {
        const data = JSON.parse(raw);
        if (data && (data.type === "share-blog" || data.blogId)) {
          return data;
        }
      } catch (e) {
        if (hasShareMarker) {
          return { type: "share-blog", title: "点击查看" };
        }
      }
      return hasShareMarker ? { type: "share-blog", title: "点击查看" } : null;
    },
    conversationPreview(content) {
      const payload = this.parseShareContent(content);
      if (payload) {
        if (payload.type === "share-shop" || payload.shopId) {
          return "分享了一家店：" + (payload.title || "点击查看");
        }
        return "分享了一篇笔记：" + (payload.title || "点击查看");
      }
      return content || "还没有消息";
    },
    formatTime(value) {
      if (!value) return "";
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return value;
      const diff = Date.now() - date.getTime();
      if (diff < 60000) return "刚刚";
      if (diff < 3600000) return Math.floor(diff / 60000) + "分钟前";
      if (diff < 86400000) return Math.floor(diff / 3600000) + "小时前";
      if (diff < 604800000) return Math.floor(diff / 86400000) + "天前";
      return (date.getMonth() + 1) + "月" + date.getDate() + "日";
    }
  }
});
