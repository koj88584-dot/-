new Vue({
  el: "#app",
  data() {
    return {
      visitors: [],
      current: 1,
      hasMore: false,
      loading: false,
      loadingMore: false,
      unreadCount: 0,
      fallbackAvatar: "/imgs/icons/default-icon.png"
    };
  },
  async created() {
    const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
    if (!ok) {
      return;
    }
    this.loadUnreadCount();
    this.loadVisitors(true);
  },
  methods: {
    goBack() {
      history.back();
    },
    loadUnreadCount() {
      axios.get("/profile-visit/count")
        .then((result) => {
          this.unreadCount = Number(result.data || 0);
        })
        .catch(() => {});
    },
    loadVisitors(reset) {
      if (reset) {
        this.current = 1;
        this.visitors = [];
      }
      this.loading = reset;
      axios.get("/profile-visit/list", {
        params: { current: this.current }
      })
        .then((result) => {
          const rows = Array.isArray(result.data) ? result.data : [];
          this.visitors = reset ? rows : this.visitors.concat(rows);
          this.hasMore = result.total ? this.visitors.length < result.total : rows.length >= 10;
          this.unreadCount = 0;
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载访客失败"));
        })
        .finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
    },
    loadMore() {
      if (this.loadingMore || !this.hasMore) {
        return;
      }
      this.current += 1;
      this.loadingMore = true;
      this.loadVisitors(false);
    },
    openProfile(userId) {
      if (!userId) {
        return;
      }
      location.href = "/pages/user/other-info.html?id=" + userId;
    },
    avatarUrl(path) {
      return util.resolveImageUrl(path, this.fallbackAvatar);
    },
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
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
