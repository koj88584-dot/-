new Vue({
  el: "#app",
  data() {
    return {
      user: {},
      info: {},
      blogs: [],
      blogs2: [],
      activeTab: "notes",
      followCount: 0,
      followerCount: 0,
      unreadCount: 0,
      visitorUnreadCount: 0,
      pendingReviewCount: 0,
      pageReady: false,
      params: {
        minTime: 0,
        offset: 0
      }
    };
  },
  created() {
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) {
        return;
      }
      this.pageReady = true;
      this.queryUser();
    },
    queryUser() {
      axios.get("/user/me")
        .then((result) => {
          const user = result.data;
          if (!user || !user.id) {
            window.authHelper.clearAuth();
            util.redirectToLogin(location.pathname + location.search, 0);
            return;
          }
          this.user = Object.assign({}, user);
          const savedNickName = localStorage.getItem("userNickName");
          const savedIcon = localStorage.getItem("userIcon");
          if (savedNickName) {
            this.user.nickName = savedNickName;
          }
          if (savedIcon) {
            this.user.icon = savedIcon;
          }
          this.queryUserInfo();
          this.queryBlogs();
          this.queryFollowStats(this.user.id);
        })
        .catch(() => {
          window.authHelper.clearAuth();
          util.redirectToLogin(location.pathname + location.search, 0);
        });
    },
    queryUserInfo() {
      axios.get("/user/info/" + this.user.id)
        .then((result) => {
          this.info = result.data || {};
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载用户信息失败"));
        });
    },
    queryBlogs() {
      axios.get("/blog/of/me")
        .then((result) => {
          this.blogs = Array.isArray(result.data) ? result.data : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载笔记失败"));
        });
    },
    queryBlogsOfFollow(clear) {
      if (clear) {
        this.params.offset = 0;
        this.params.minTime = new Date().getTime() + 1;
      }
      const minTime = this.params.minTime || (new Date().getTime() + 1);
      axios.get("/blog/of/follow", {
        params: {
          offset: this.params.offset,
          lastId: minTime
        }
      })
        .then((result) => {
          if (!result.data) {
            return;
          }
          const list = Array.isArray(result.data.list) ? result.data.list : [];
          const nextParams = {
            minTime: result.data.minTime || 0,
            offset: result.data.offset || 0
          };
          list.forEach((blog) => {
            blog.img = blog.images ? blog.images.split(",")[0] : "";
          });
          this.blogs2 = clear ? list : this.blogs2.concat(list);
          this.params = nextParams;
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载关注动态失败"));
        });
    },
    queryFollowStats(userId) {
      axios.get("/follow/list/" + userId, { params: { current: 1 } })
        .then((result) => {
          this.followCount = Array.isArray(result.data) ? result.data.length : 0;
        });
      axios.get("/follow/followers/" + userId, { params: { current: 1 } })
        .then((result) => {
          this.followerCount = Array.isArray(result.data) ? result.data.length : 0;
        });
      axios.get("/message/unread-count")
        .then((result) => {
          this.unreadCount = result.data || 0;
        })
        .catch(() => {});
      axios.get("/profile-visit/count")
        .then((result) => {
          this.visitorUnreadCount = result.data || 0;
        })
        .catch(() => {});
      this.queryPendingReviews();
    },
    queryPendingReviews() {
      Promise.all([
        axios.get("/group-deals/orders/my", { params: { status: 3, commented: 0, current: 1 } }).catch(() => ({ data: [] })),
        axios.get("/featured-dishes/orders/my", { params: { status: 3, commented: 0, current: 1 } }).catch(() => ({ data: [] }))
      ]).then((results) => {
        this.pendingReviewCount = results.reduce((sum, result) => sum + (Array.isArray(result.data) ? result.data.length : 0), 0);
      }).catch(() => {
        this.pendingReviewCount = 0;
      });
    },
    addLike(blog) {
      axios.put("/blog/like/" + blog.id)
        .then(() => {
          blog.isLike = !blog.isLike;
          blog.liked = (blog.liked || 0) + (blog.isLike ? 1 : -1);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "操作失败"));
        });
    },
    toFollowList(tab) {
      location.href = "../misc/follow-list.html?tab=" + tab;
    },
    toBlogDetail(blog) {
      location.href = "../blog/blog-detail.html?id=" + blog.id;
    },
    goBack() {
      history.back();
    },
    getImageUrl(path) {
      return util.resolveImageUrl(path, "/imgs/icons/default-icon.png");
    },
    goHome() {
      location.href = "../index-new.html";
    },
    goProfile() {
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
    toEdit() {
      location.href = "info-edit.html";
    },
    logout() {
      this.$confirm("确定要退出登录吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(() => {
        axios.post("/user/logout")
          .then(() => {
            window.authHelper.clearAuth();
            this.$message.success("已退出登录");
            location.href = "../index-new.html";
          })
          .catch((err) => {
            this.$message.error(util.getErrorMessage(err, "退出失败"));
          });
      }).catch(() => {});
    },
    publish() {
      location.href = "../blog/blog-edit.html";
    },
    toFavorites() {
      location.href = "../misc/favorites.html";
    },
    toHistory() {
      location.href = "../misc/history.html";
    },
    toMessages() {
      location.href = "../misc/messages.html";
    },
    toVisitors() {
      location.href = "../misc/visitors.html";
    },
    toVouchers() {
      location.href = "../misc/vouchers.html";
    },
    toOrders() {
      location.href = "../order/orders.html";
    },
    toPendingReviews() {
      location.href = "../order/orders.html?status=review";
    },
    toMap() {
      location.href = "../map/map.html";
    },
    toMerchantCenter() {
      if (!this.user || !this.user.merchantEnabled) {
        if (this.user && this.user.merchantApplicationStatus === "PENDING") {
          location.href = "../merchant/progress.html";
          return;
        }
        if (this.user && this.user.merchantApplicationStatus === "REJECTED") {
          location.href = "../merchant/progress.html";
          return;
        }
        location.href = "../merchant/apply.html";
        return;
      }
      location.href = "../merchant/vouchers.html";
    },
    toMerchantApply() {
      location.href = "../merchant/apply.html";
    },
    toMerchantProgress() {
      location.href = "../merchant/progress.html";
    },
    toAdminReview() {
      if (!this.user || !this.user.admin) {
        this.$message.warning("当前账号暂无审核权限");
        return;
      }
      location.href = "../admin/merchant-reviews.html";
    },
    toPrivacy() {
      location.href = "../misc/privacy.html";
    }
  }
});
