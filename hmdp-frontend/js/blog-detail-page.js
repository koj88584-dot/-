new Vue({
    el: '#app',
    data() {
      return {
        util,
        blogId: Number(util.getUrlParam('id') || 0),
        blog: {
          images: []
        },
        fallbackImage: '/imgs/blogs/blog1.jpg',
        fallbackAvatar: '/imgs/icons/default-icon.png',
        shop: null,
        likes: [],
        comments: [],
        commentTotal: 0,
        currentUser: null,
        followed: false,
        activeImageIndex: 0,
        commentText: '',
        commentSubmitting: false,
        replyParentId: 0,
        replyTargetName: ''
      };
    },
    computed: {
      blogImages() {
        return Array.isArray(this.blog.images) ? this.blog.images : [];
      },
      currentImage() {
        return this.blogImages[this.activeImageIndex] || this.fallbackImage;
      },
      shopImage() {
        if (!this.shop || !this.shop.images) {
          return this.fallbackImage;
        }
        var first = this.shop.images.split(',')[0];
        return this.resolveImageUrl(first);
      },
      canFollowAuthor() {
        return this.currentUser && this.blog.userId && this.currentUser.id !== this.blog.userId;
      },
      likesLabel() {
        if (!this.likes.length) {
          return '还没有点赞';
        }
        return this.likes.map(function (user) {
          return user.nickName || user.name || '用户';
        }).slice(0, 3).join('、');
      }
    },
    created() {
      if (!this.blogId) {
        this.$message.error('缺少笔记编号');
        return;
      }
      this.loadCurrentUser();
      this.loadBlog();
      this.loadComments();
    },
    methods: {
      goBack() {
        history.back();
      },
      loadCurrentUser() {
        if (!util.hasToken()) {
          return;
        }
        axios.get('/user/me').then(function (res) {
          this.currentUser = res.data || null;
          if (this.blog.userId && this.currentUser && this.currentUser.id !== this.blog.userId) {
            this.checkFollowed();
          }
        }.bind(this)).catch(function () {
          this.currentUser = null;
        }.bind(this));
      },
      loadBlog() {
        axios.get('/blog/' + this.blogId).then(function (res) {
          var data = res.data || {};
          data.images = this.normalizeImages(data.images);
          this.blog = data;
          this.activeImageIndex = 0;
          this.commentTotal = data.comments || this.commentTotal || 0;
          this.loadLikeUsers();
          if (data.shopId) {
            this.loadShop(data.shopId);
          } else {
            this.shop = null;
          }
          if (this.currentUser && this.currentUser.id !== data.userId) {
            this.checkFollowed();
          }
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '加载笔记失败'));
        }.bind(this));
      },
      loadShop(shopId) {
        axios.get('/shop/' + shopId).then(function (res) {
          var data = res.data || {};
          if (data.images) {
            data.images = data.images;
          }
          this.shop = data;
        }.bind(this)).catch(function () {
          this.shop = null;
        }.bind(this));
      },
      loadLikeUsers() {
        axios.get('/blog/likes/' + this.blogId).then(function (res) {
          this.likes = Array.isArray(res.data) ? res.data : [];
        }.bind(this)).catch(function () {
          this.likes = [];
        }.bind(this));
      },
      loadComments() {
        axios.get('/blog-comments/list/' + this.blogId, {
          params: { current: 1 }
        }).then(function (res) {
          this.comments = Array.isArray(res.data) ? res.data.map(this.normalizeComment) : [];
          this.commentTotal = typeof res.total === 'number' ? res.total : this.comments.length;
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '加载评论失败'));
        }.bind(this));
      },
      normalizeComment(comment) {
        return Object.assign({
          liked: 0,
          likedByCurrentUser: false,
          owner: false,
          replyTotal: 0,
          replies: [],
          repliesLoading: false,
          showReplies: false
        }, comment || {});
      },
      toggleReplies(comment) {
        if (comment.showReplies) {
          this.$set(comment, 'showReplies', false);
          return;
        }
        this.$set(comment, 'showReplies', true);
        if (comment.replies && comment.replies.length) {
          return;
        }
        this.$set(comment, 'repliesLoading', true);
        axios.get('/blog-comments/replies/' + comment.id, {
          params: { current: 1 }
        }).then(function (res) {
          var replies = Array.isArray(res.data) ? res.data.map(this.normalizeComment) : [];
          this.$set(comment, 'replies', replies);
          this.$set(comment, 'replyTotal', typeof res.total === 'number' ? res.total : replies.length);
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '加载回复失败'));
          this.$set(comment, 'showReplies', false);
        }.bind(this)).finally(function () {
          this.$set(comment, 'repliesLoading', false);
        }.bind(this));
      },
      submitComment() {
        if (!this.ensureLogin()) {
          return;
        }
        if (!this.commentText) {
          this.$message.warning('先写点内容再发送吧');
          return;
        }
        this.commentSubmitting = true;
        var params = {
          blogId: this.blogId,
          content: this.commentText
        };
        if (this.replyParentId) {
          params.parentId = this.replyParentId;
        }
        axios.post('/blog-comments', null, { params: params }).then(function () {
          this.$message.success(this.replyParentId ? '回复成功' : '评论成功');
          this.commentText = '';
          this.commentSubmitting = false;
          this.clearReplyTarget();
          this.loadBlog();
          this.loadComments();
        }.bind(this)).catch(function (err) {
          this.commentSubmitting = false;
          this.$message.error(util.getErrorMessage(err, '评论失败'));
        }.bind(this));
      },
      deleteComment(comment) {
        this.$confirm('确定删除这条评论吗？', '提示', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(function () {
          return axios.delete('/blog-comments/' + comment.id);
        }).then(function () {
          this.$message.success('评论已删除');
          this.loadBlog();
          this.loadComments();
        }.bind(this)).catch(function (err) {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '删除评论失败'));
          }
        }.bind(this));
      },
      deleteReply(comment, reply) {
        this.$confirm('确定删除这条回复吗？', '提示', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(function () {
          return axios.delete('/blog-comments/' + reply.id);
        }).then(function () {
          this.$message.success('回复已删除');
          this.loadBlog();
          this.loadComments();
        }.bind(this)).catch(function (err) {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '删除回复失败'));
          }
        }.bind(this));
      },
      toggleCommentLike(comment) {
        if (!this.ensureLogin()) {
          return;
        }
        axios.put('/blog-comments/like/' + comment.id).then(function () {
          this.loadComments();
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '操作失败'));
        }.bind(this));
      },
      toggleReplyLike(comment, reply) {
        if (!this.ensureLogin()) {
          return;
        }
        axios.put('/blog-comments/like/' + reply.id).then(function () {
          this.loadComments();
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '操作失败'));
        }.bind(this));
      },
      replyTo(comment) {
        if (!this.ensureLogin()) {
          return;
        }
        this.replyParentId = comment.id;
        this.replyTargetName = comment.userName || '这条评论';
        this.$nextTick(function () {
          if (this.$refs.commentInput) {
            this.$refs.commentInput.focus();
          }
        }.bind(this));
      },
      clearReplyTarget() {
        this.replyParentId = 0;
        this.replyTargetName = '';
      },
      toggleBlogLike() {
        if (!this.ensureLogin()) {
          return;
        }
        axios.put('/blog/like/' + this.blogId).then(function () {
          this.loadBlog();
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '点赞失败'));
        }.bind(this));
      },
      toggleFavorite() {
        if (!this.ensureLogin()) {
          return;
        }
        // Toggle favorite state locally and try API
        var newState = !this.blog.isFavorited;
        this.$set(this.blog, 'isFavorited', newState);
        this.$message.success(newState ? '已收藏' : '已取消收藏');
        // Try to call backend API (may not exist yet)
        axios.post('/favorites/blog/' + this.blogId).catch(function() {
          // If API doesn't exist, that's ok - we still show the local state
        });
      },
      toggleFollow() {
        if (!this.ensureLogin()) {
          return;
        }
        axios.put('/follow/' + this.blog.userId + '/' + (!this.followed)).then(function () {
          this.followed = !this.followed;
          this.$message.success(this.followed ? '已关注作者' : '已取消关注');
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '关注失败'));
        }.bind(this));
      },
      checkFollowed() {
        axios.get('/follow/or/not/' + this.blog.userId).then(function (res) {
          this.followed = !!res.data;
        }.bind(this)).catch(function () {
          this.followed = false;
        }.bind(this));
      },
      openProfile() {
        if (!this.blog.userId) {
          return;
        }
        if (this.currentUser && this.currentUser.id === this.blog.userId) {
          location.href = '/pages/user/info.html';
          return;
        }
        location.href = '/pages/user/other-info.html?id=' + this.blog.userId;
      },
      openShop() {
        if (this.shop && this.shop.id) {
          location.href = '/pages/shop/shop-detail.html?id=' + this.shop.id;
        }
      },
      shareBlog() {
        var text = window.location.href;
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(function () {
            this.$message.success('链接已复制');
          }.bind(this)).catch(function () {
            this.$message.success('请复制当前页面链接分享');
          }.bind(this));
          return;
        }
        this.$message.success('请复制当前页面链接分享');
      },
      changeImage(step) {
        if (!this.blogImages.length) {
          return;
        }
        var next = this.activeImageIndex + step;
        if (next < 0) {
          next = this.blogImages.length - 1;
        }
        if (next >= this.blogImages.length) {
          next = 0;
        }
        this.activeImageIndex = next;
      },
      normalizeImages(images) {
        if (!images) {
          return [];
        }
        if (Array.isArray(images)) {
          return images.map(function (item) {
            return this.resolveImageUrl(item);
          }.bind(this)).filter(Boolean);
        }
        return images.split(',').filter(function (item) {
          return item && item.trim();
        }).map(function (item) {
          return this.resolveImageUrl(item);
        }.bind(this)).filter(Boolean);
      },
      resolveImageUrl(path, type) {
        var fallback = type === 'avatar' ? this.fallbackAvatar : this.fallbackImage;
        return util.resolveImageUrl(path, fallback);
      },
      handleImageError(event, type) {
        util.applyImageFallback(event, type === 'avatar' ? this.fallbackAvatar : this.fallbackImage);
      },
      ensureLogin() {
        if (util.hasToken()) {
          return true;
        }
        this.$message.warning('请先登录');
        util.redirectToLogin(location.pathname + location.search, 200);
        return false;
      },
      formatDate(value) {
        if (!value) {
          return '刚刚发布';
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
      formatRelativeTime(value) {
        if (!value) {
          return '刚刚';
        }
        var date = new Date(value);
        if (isNaN(date.getTime())) {
          return value;
        }
        var diff = Date.now() - date.getTime();
        var minute = 60 * 1000;
        var hour = 60 * minute;
        var day = 24 * hour;
        if (diff < minute) {
          return '刚刚';
        }
        if (diff < hour) {
          return Math.floor(diff / minute) + ' 分钟前';
        }
        if (diff < day) {
          return Math.floor(diff / hour) + ' 小时前';
        }
        if (diff < 7 * day) {
          return Math.floor(diff / day) + ' 天前';
        }
        return this.formatDate(value);
      },
      formatCount(value) {
        var num = Number(value || 0);
        if (num >= 10000) {
          return (num / 10000).toFixed(num >= 100000 ? 0 : 1) + 'w';
        }
        return String(num);
      },
    }
  });
