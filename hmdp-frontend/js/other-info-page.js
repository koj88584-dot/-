var app = new Vue({
  el: '#app',
  data: function() {
    return {
      userId: Number(util.getUrlParam('id') || 0),
      user: {},
      loginUser: null,
      info: {},
      publicPrivacy: {},
      blogs: [],
      followed: false,
      isMutual: false,
      commonFollows: [],
      followCount: 0,
      followerCount: 0,
      followCountPrivate: false,
      followerCountPrivate: false,
      activeTab: 'blogs',
      showVisitToast: false,
      fallbackAvatar: '/imgs/icons/default-icon.png',
      fallbackImage: '/imgs/blogs/blog1.jpg'
    };
  },
  computed: {
    totalLikes: function() {
      return this.blogs.reduce(function(sum, b) { return sum + (b.liked || 0); }, 0);
    }
  },
  created: function() {
    if (!this.userId) {
      this.$message.error('缺少用户ID');
      return;
    }
    this.loadLoginUser();
    this.loadUser();
  },
  methods: {
    goBack: function() {
      history.back();
    },
    loadLoginUser: function() {
      if (!util.hasToken()) return;
      axios.get('/user/me').then(function(res) {
        this.loginUser = res.data || null;
        // If viewing own profile, redirect
        if (this.loginUser && this.loginUser.id === this.userId) {
          location.href = '/pages/user/info.html';
        }
      }.bind(this)).catch(function() {
        this.loginUser = null;
      }.bind(this));
    },
    loadUser: function() {
      axios.get('/user/' + this.userId).then(function(res) {
        this.user = res.data || {};
        this.loadUserInfo();
        this.loadPublicPrivacy();
        this.loadBlogs();
        this.checkFollowed();
        this.loadFollowCounts();
        this.recordVisit();
      }.bind(this)).catch(function(err) {
        this.$message.error(util.getErrorMessage(err, '加载用户信息失败'));
      }.bind(this));
    },
    loadUserInfo: function() {
      axios.get('/user/info/' + this.userId).then(function(res) {
        if (res.data) {
          this.info = res.data;
        }
      }.bind(this)).catch(function() {});
    },
    loadPublicPrivacy: function() {
      axios.get('/privacy/public/' + this.userId).then(function(res) {
        this.publicPrivacy = res.data || {};
      }.bind(this)).catch(function() {
        this.publicPrivacy = {};
      }.bind(this));
    },
    loadBlogs: function() {
      axios.get('/blog/of/user', {
        params: { id: this.userId, current: 1 }
      }).then(function(res) {
        this.blogs = Array.isArray(res.data) ? res.data : [];
      }.bind(this)).catch(function() {
        this.blogs = [];
      }.bind(this));
    },
    checkFollowed: function() {
      if (!util.hasToken()) return;
      axios.get('/follow/or/not/' + this.userId).then(function(res) {
        this.followed = !!res.data;
        if (this.followed) {
          this.checkMutual();
        }
      }.bind(this)).catch(function() {
        this.followed = false;
      }.bind(this));
    },
    checkMutual: function() {
      if (!this.loginUser) return;
      axios.get('/follow/is-mutual/' + this.userId).then(function(res) {
        this.isMutual = !!res.data;
      }.bind(this)).catch(function() {
        this.isMutual = false;
      }.bind(this));
    },
    loadFollowCounts: function() {
      this.followCountPrivate = false;
      this.followerCountPrivate = false;
      // Load follow count
      axios.get('/follow/list/' + this.userId, { params: { current: 1 } })
        .then(function(res) {
          var data = res.data;
          if (data && typeof data.total === 'number') {
            this.followCount = data.total;
          } else if (Array.isArray(data)) {
            this.followCount = data.length;
          }
        }.bind(this)).catch(function() {
          this.followCountPrivate = true;
          this.followCount = 0;
        }.bind(this));

      // Load follower count
      axios.get('/follow/followers/' + this.userId, { params: { current: 1 } })
        .then(function(res) {
          var data = res.data;
          if (data && typeof data.total === 'number') {
            this.followerCount = data.total;
          } else if (Array.isArray(data)) {
            this.followerCount = data.length;
          }
        }.bind(this)).catch(function() {
          this.followerCountPrivate = true;
          this.followerCount = 0;
        }.bind(this));
    },
    toggleFollow: function() {
      if (!util.hasToken()) {
        this.$message.warning('请先登录');
        util.redirectToLogin(location.pathname + location.search, 200);
        return;
      }
      var newState = !this.followed;
      axios.put('/follow/' + this.userId + '/' + newState).then(function() {
        this.followed = newState;
        this.$message.success(newState ? '已关注' : '已取消关注');
        this.loadFollowCounts();
        if (newState) {
          this.checkMutual();
        } else {
          this.isMutual = false;
        }
      }.bind(this)).catch(function(err) {
        this.$message.error(util.getErrorMessage(err, '操作失败'));
      }.bind(this));
    },
    sendMessage: function() {
      if (!util.hasToken()) {
        this.$message.warning('请先登录');
        util.redirectToLogin(location.pathname + location.search, 200);
        return;
      }
      location.href = '/pages/misc/chat.html?userId=' + this.userId;
    },
    switchToCommon: function() {
      if (this.followCountPrivate) {
        this.$message.info('对方已隐藏关注列表');
        return;
      }
      this.activeTab = 'common';
      if (!this.commonFollows.length) {
        this.loadCommonFollows();
      }
    },
    loadCommonFollows: function() {
      axios.get('/follow/common/' + this.userId).then(function(res) {
        this.commonFollows = Array.isArray(res.data) ? res.data : [];
      }.bind(this)).catch(function() {
        this.commonFollows = [];
      }.bind(this));
    },
    recordVisit: function() {
      if (!util.hasToken()) return;
      axios.post('/profile-visit/record/' + this.userId).then(function(res) {
        var data = res.data || {};
        if (data.recorded) {
          this.showVisitToast = true;
          setTimeout(function() { this.showVisitToast = false; }.bind(this), 2000);
        }
      }.bind(this)).catch(function() {
      });
    },
    openBlog: function(id) {
      location.href = '/pages/blog/blog-detail.html?id=' + id;
    },
    toOtherInfo: function(id) {
      if (id === this.userId) return;
      location.href = 'other-info.html?id=' + id;
    },
    viewFollowList: function(type) {
      if (type === 'follow' && this.followCountPrivate) {
        this.$message.info('对方已隐藏关注列表');
        return;
      }
      if (type === 'follower' && this.followerCountPrivate) {
        this.$message.info('对方已隐藏粉丝列表');
        return;
      }
      location.href = '/pages/misc/follow-list.html?id=' + this.userId + '&type=' + type;
    },
    sharePage: function() {
      var text = window.location.href;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          this.$message.success('链接已复制，可分享给好友');
        }.bind(this)).catch(function() {
          this.$message.success('请复制当前页面链接分享');
        }.bind(this));
        return;
      }
      this.$message.success('请复制当前页面链接分享');
    },
    getAvatar: function(icon) {
      return util.resolveImageUrl(icon, this.fallbackAvatar);
    },
    resolveImg: function(images) {
      if (!images) return this.fallbackImage;
      var first = typeof images === 'string' ? images.split(',')[0] : images[0];
      return util.resolveImageUrl(first, this.fallbackImage);
    },
    handleAvatarError: function(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
    },
    handleImgError: function(event) {
      util.applyImageFallback(event, this.fallbackImage);
    }
  }
});
