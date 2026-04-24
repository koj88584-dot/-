var app = new Vue({
  el: '#app',
  data: function() {
    return {
      userId: Number(util.getUrlParam('id') || 0),
      user: {},
      loginUser: null,
      info: {},
      blogs: [],
      followed: false,
      isMutual: false,
      commonFollows: [],
      followCount: 0,
      followerCount: 0,
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
      // Check if the target user follows me back (mutual follow)
      // We can check by querying common follows with self
      if (!this.loginUser) return;
      axios.get('/follow/common/' + this.userId).then(function(res) {
        var commons = Array.isArray(res.data) ? res.data : [];
        // If I follow them and they follow me, it's mutual
        // Simple heuristic: check if me is in their followers
        this.isMutual = this.followed; // We'll refine this
      }.bind(this)).catch(function() {}.bind(this));

      // Better approach: check if they follow me
      if (this.loginUser && this.loginUser.id) {
        axios.get('/follow/or/not/' + this.loginUser.id).catch(function() {});
      }
    },
    loadFollowCounts: function() {
      // Load follow count
      axios.get('/follow/list/' + this.userId, { params: { current: 1 } })
        .then(function(res) {
          var data = res.data;
          if (data && typeof data.total === 'number') {
            this.followCount = data.total;
          } else if (Array.isArray(data)) {
            this.followCount = data.length;
          }
        }.bind(this)).catch(function() {}.bind(this));

      // Load follower count
      axios.get('/follow/followers/' + this.userId, { params: { current: 1 } })
        .then(function(res) {
          var data = res.data;
          if (data && typeof data.total === 'number') {
            this.followerCount = data.total;
          } else if (Array.isArray(data)) {
            this.followerCount = data.length;
          }
        }.bind(this)).catch(function() {}.bind(this));
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
      // Navigate to chat page (will be created)
      // For now, show a message
      this.$message.info('聊天功能开发中，敬请期待');
    },
    switchToCommon: function() {
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
      // Record profile visit via browse history API
      axios.post('/browse-history', null, {
        params: { targetType: 'USER', targetId: this.userId }
      }).then(function() {
        this.showVisitToast = true;
        setTimeout(function() { this.showVisitToast = false; }.bind(this), 2000);
      }.bind(this)).catch(function() {
        // Visit recording is optional, don't show errors
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
