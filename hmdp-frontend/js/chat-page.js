new Vue({
  el: "#app",
  data() {
    return {
      pageReady: false,
      loading: false,
      sending: false,
      targetUserId: Number(util.getUrlParam("userId") || 0),
      targetUser: {},
      targetPrivacy: {},
      conversations: [],
      messages: [],
      current: 1,
      hasMore: false,
      draft: "",
      canSend: true,
      sendNotice: "",
      fallbackAvatar: "/imgs/icons/default-icon.png",
      fallbackCover: "/imgs/blogs/blog1.jpg"
    };
  },
  computed: {
    chatSubtitle() {
      if (this.targetPrivacy.showOnlineStatus === false) {
        return "在线状态已隐藏";
      }
      if (this.canSend) {
        return this.sendNotice || "在线私信";
      }
      return this.sendNotice || "暂时不能继续发送";
    },
    disabledPlaceholder() {
      return this.sendNotice || "对方回复后就可以继续发送";
    }
  },
  async created() {
    const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
    if (!ok) {
      return;
    }
    this.pageReady = true;
    if (this.targetUserId) {
      this.loadTargetUser();
      this.loadTargetPrivacy();
      this.loadMessages(true);
      this.loadCanSend();
    } else {
      this.loadConversations();
    }
  },
  methods: {
    goBack() {
      history.back();
    },
    loadConversations() {
      this.loading = true;
      axios.get("/chat/conversations")
        .then((result) => {
          this.conversations = Array.isArray(result.data)
            ? result.data.map((item) => Object.assign({}, item, {
              previewText: this.conversationPreview(item.lastMessage)
            }))
            : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载会话失败"));
        })
        .finally(() => {
          this.loading = false;
        });
    },
    openConversation(userId) {
      location.href = "/pages/misc/chat.html?userId=" + userId;
    },
    loadTargetUser() {
      axios.get("/user/" + this.targetUserId)
        .then((result) => {
          this.targetUser = result.data || {};
        })
        .catch(() => {
          this.targetUser = {};
        });
    },
    loadTargetPrivacy() {
      axios.get("/privacy/public/" + this.targetUserId)
        .then((result) => {
          this.targetPrivacy = result.data || {};
        })
        .catch(() => {
          this.targetPrivacy = {};
        });
    },
    loadMessages(reset) {
      if (reset) {
        this.current = 1;
        this.messages = [];
      }
      this.loading = true;
      axios.get("/chat/messages/" + this.targetUserId, {
        params: { current: this.current }
      })
        .then((result) => {
          const rows = Array.isArray(result.data) ? result.data : [];
          this.messages = reset ? rows : rows.concat(this.messages);
          this.hasMore = result.total ? this.messages.length < result.total : rows.length >= 10;
          this.markRead();
          this.$nextTick(this.scrollToBottom);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载聊天记录失败"));
        })
        .finally(() => {
          this.loading = false;
        });
    },
    loadMore() {
      if (this.loading || !this.hasMore) {
        return;
      }
      this.current += 1;
      this.loadMessages(false);
    },
    loadCanSend() {
      axios.get("/chat/can-send/" + this.targetUserId)
        .then((result) => {
          const data = result.data || {};
          this.canSend = data.canSend !== false;
          if (data.mutual) {
            this.sendNotice = "你们已互相关注，可以自由聊天。";
          } else if (!this.canSend) {
            this.sendNotice = data.reason || "对方回复后才可以继续发送。";
          } else {
            this.sendNotice = "非互相关注时，请先发送一条简短消息，对方回复后可继续聊天。";
          }
        })
        .catch(() => {
          this.canSend = true;
          this.sendNotice = "";
        });
    },
    sendMessage() {
      if (!this.draft || this.sending || !this.canSend) {
        return;
      }
      const content = this.draft;
      this.sending = true;
      axios.post("/chat/send", null, {
        params: {
          receiverId: this.targetUserId,
          content
        }
      })
        .then((result) => {
          if (result.data) {
            this.messages.push(result.data);
          }
          this.draft = "";
          this.loadCanSend();
          this.$nextTick(this.scrollToBottom);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "发送失败"));
          this.loadCanSend();
        })
        .finally(() => {
          this.sending = false;
        });
    },
    markRead() {
      axios.post("/chat/read/" + this.targetUserId).catch(() => {});
    },
    scrollToBottom() {
      const box = this.$refs.messageList;
      if (box && this.current === 1) {
        window.scrollTo({ top: document.body.scrollHeight, behavior: "smooth" });
      }
    },
    avatarUrl(path) {
      return util.resolveImageUrl(path, this.fallbackAvatar);
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
          const rawMarked = content.slice(marker.prefix.length);
          try {
            const data = JSON.parse(rawMarked);
            return Object.assign({ type: marker.type }, data || {});
          } catch (e) {
            return { type: marker.type, title: "点击查看" };
          }
        }
      }
      const blogPrefix = "__HMDP_SHARE_BLOG__";
      const shopPrefix = "__HMDP_SHARE_SHOP__";
      const hasBlogMarker = content.indexOf(blogPrefix) === 0;
      const hasShopMarker = content.indexOf(shopPrefix) === 0;
      const hasShareMarker = hasBlogMarker || hasShopMarker;
      const fallbackType = hasShopMarker ? "share-shop" : "share-blog";
      const raw = hasBlogMarker ? content.slice(blogPrefix.length) : (hasShopMarker ? content.slice(shopPrefix.length) : content);
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
    sharePayload(message) {
      return this.parseShareContent(message && message.content) || {};
    },
    isShareMessage(message) {
      return !!this.parseShareContent(message && message.content);
    },
    shareCoverUrl(path) {
      return util.resolveImageUrl(path, this.fallbackCover);
    },
    handleShareCoverError(event) {
      util.applyImageFallback(event, this.fallbackCover);
    },
    openSharedBlog(message) {
      const payload = this.sharePayload(message);
      if (payload.shopId) {
        location.href = "/pages/shop/shop-detail.html?id=" + payload.shopId;
        return;
      }
      if (payload.blogId) {
        location.href = "/pages/blog/blog-detail.html?id=" + payload.blogId;
        return;
      }
      if (payload.url) {
        location.href = payload.url;
      }
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
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
    },
    formatClock(value) {
      if (!value) {
        return "";
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return value;
      }
      return String(date.getHours()).padStart(2, "0") + ":" + String(date.getMinutes()).padStart(2, "0");
    },
    formatRelativeTime(value) {
      if (!value) {
        return "";
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return value;
      }
      const diff = Date.now() - date.getTime();
      if (diff < 60000) return "刚刚";
      if (diff < 3600000) return Math.floor(diff / 60000) + "分钟前";
      if (diff < 86400000) return Math.floor(diff / 3600000) + "小时前";
      if (diff < 604800000) return Math.floor(diff / 86400000) + "天前";
      return (date.getMonth() + 1) + "月" + date.getDate() + "日";
    }
  }
});
