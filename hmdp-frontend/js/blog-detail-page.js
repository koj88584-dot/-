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
        commentImageUploading: false,
        replyParentId: 0,
        replyTargetName: '',
        shareSheetVisible: false,
        shareFriendPanelVisible: false,
        shareFriends: [],
        shareFriendLoading: false,
        shareSendingId: 0,
        shareImageGenerating: false
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
      storyAddress() {
        if (!this.shop || !this.shop.name) {
          return '';
        }
        var detail = this.shop.area || this.shop.address || '';
        return [this.shop.name, detail].filter(Boolean).join(' · ');
      },
      currentUserAvatar() {
        if (this.currentUser && this.currentUser.icon) {
          return this.resolveImageUrl(this.currentUser.icon, 'avatar');
        }
        return this.fallbackAvatar;
      },
      pendingCommentImages() {
        return this.extractCommentImageUrls(this.commentText);
      },
      likesLabel() {
        if (!this.likes.length) {
          return '还没有点赞';
        }
        return this.likes.map(function (user) {
          return user.nickName || user.name || '用户';
        }).slice(0, 3).join('、');
      },
      shareUrl() {
        return window.location.href;
      },
      shareTitle() {
        return this.blog.title || '一篇探店笔记';
      },
      shareSummary() {
        var content = String(this.blog.content || '')
          .replace(/<[^>]+>/g, '')
          .replace(/\s+/g, ' ')
          .trim();
        return content ? content.slice(0, 80) : '发现一篇值得收藏的探店笔记';
      },
      shareCover() {
        return this.currentImage || this.shopImage || this.fallbackImage;
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
        var normalized = Object.assign({
          liked: 0,
          likedByCurrentUser: false,
          owner: false,
          replyTotal: 0,
          replies: [],
          repliesLoading: false,
          showReplies: false
        }, comment || {});
        normalized.segments = this.parseCommentContent(normalized.content);
        return normalized;
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
        if (this.commentImageUploading) {
          this.$message.warning('图片还在上传中，稍等一下再发送');
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
        this.insertCommentText('@' + this.replyTargetName + ' ');
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
      focusCommentInput() {
        this.$nextTick(function () {
          if (this.$refs.commentInput) {
            this.$refs.commentInput.focus();
          }
        }.bind(this));
      },
      mentionAuthor() {
        if (!this.blog.name) {
          return;
        }
        this.insertCommentText('@' + this.blog.name + ' ');
        this.focusCommentInput();
      },
      insertCommentText(text) {
        var current = this.commentText || '';
        var spacer = current && !/\s$/.test(current) ? ' ' : '';
        this.commentText = current + spacer + text;
      },
      openCommentImageDialog() {
        if (!this.ensureLogin()) {
          return;
        }
        if (this.commentImageUploading) {
          return;
        }
        if (this.$refs.commentImageInput) {
          this.$refs.commentImageInput.click();
        }
      },
      commentImageSelected(event) {
        var file = event && event.target && event.target.files ? event.target.files[0] : null;
        if (!file) {
          return;
        }
        this.commentImageUploading = true;
        this.uploadCommentImage(file).then(function (path) {
          if (path) {
            this.insertCommentText('\n![图片](' + path + ')\n');
            this.focusCommentInput();
          }
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '图片上传失败'));
        }.bind(this)).finally(function () {
          this.commentImageUploading = false;
          if (this.$refs.commentImageInput) {
            this.$refs.commentImageInput.value = '';
          }
        }.bind(this));
      },
      uploadCommentImage(file) {
        if (util.uploadImageFile) {
          return util.uploadImageFile(file);
        }
        var formData = new FormData();
        formData.append('file', file);
        return axios.post('/upload/blog', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        }).then(function (result) {
          var imagePath = result.data || '';
          return imagePath && imagePath.startsWith('/imgs') ? imagePath : '/imgs' + imagePath;
        });
      },
      extractCommentImageUrls(content) {
        if (!content) {
          return [];
        }
        var urls = [];
        var regex = /!\[[^\]]*]\(([^)]+)\)/g;
        var match;
        while ((match = regex.exec(content)) !== null) {
          if (match[1]) {
            urls.push(this.resolveImageUrl(match[1]));
          }
        }
        return urls;
      },
      parseCommentContent(content) {
        if (!content) {
          return [{ type: 'text', text: '' }];
        }
        var segments = [];
        var regex = /!\[[^\]]*]\(([^)]+)\)/g;
        var lastIndex = 0;
        var match;
        while ((match = regex.exec(content)) !== null) {
          if (match.index > lastIndex) {
            segments.push({ type: 'text', text: content.slice(lastIndex, match.index) });
          }
          if (match[1]) {
            segments.push({ type: 'image', src: this.resolveImageUrl(match[1]) });
          }
          lastIndex = regex.lastIndex;
        }
        if (lastIndex < content.length) {
          segments.push({ type: 'text', text: content.slice(lastIndex) });
        }
        return segments.length ? segments : [{ type: 'text', text: content }];
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
        axios.post('/favorites/blog/' + this.blogId).then(function (res) {
          var favorited = !!res.data;
          this.$set(this.blog, 'isFavorited', favorited);
          this.$message.success(favorited ? '已收藏' : '已取消收藏');
        }.bind(this)).catch(function(err) {
          this.$message.error(util.getErrorMessage(err, '收藏操作失败'));
        }.bind(this));
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
      openUserProfile(userId) {
        if (!userId) {
          return;
        }
        if (this.currentUser && Number(this.currentUser.id) === Number(userId)) {
          location.href = '/pages/user/info.html';
          return;
        }
        location.href = '/pages/user/other-info.html?id=' + userId;
      },
      openShop() {
        if (this.shop && this.shop.id) {
          location.href = '/pages/shop/shop-detail.html?id=' + this.shop.id;
        }
      },
      shareBlog() {
        this.shareSheetVisible = true;
      },
      closeShareSheet() {
        this.shareSheetVisible = false;
        this.shareFriendPanelVisible = false;
      },
      copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          return navigator.clipboard.writeText(text);
        }
        var input = document.createElement('textarea');
        input.value = text;
        input.setAttribute('readonly', 'readonly');
        input.style.position = 'fixed';
        input.style.opacity = '0';
        document.body.appendChild(input);
        input.select();
        try {
          document.execCommand('copy');
          document.body.removeChild(input);
          return Promise.resolve();
        } catch (err) {
          document.body.removeChild(input);
          return Promise.reject(err);
        }
      },
      copyShareLink() {
        this.copyText(this.shareUrl).then(function () {
          this.$message.success('链接已复制');
        }.bind(this)).catch(function () {
          this.$message.warning('请复制当前页面链接分享');
        }.bind(this));
      },
      buildShareMessage() {
        return '__HMDP_SHARE_BLOG__' + JSON.stringify(this.buildSharePayload('private', true));
      },
      shortShareText(value, maxLength) {
        var text = String(value || '').replace(/\s+/g, ' ').trim();
        if (!maxLength || text.length <= maxLength) {
          return text;
        }
        return text.slice(0, Math.max(0, maxLength - 1)) + '…';
      },
      buildSharePayload(channel, compact) {
        var title = this.shortShareText(this.shareTitle, compact ? 56 : 90);
        var summary = this.shortShareText(this.shareSummary, compact ? 72 : 120);
        var cover = this.resolveImageUrl(this.shareCover);
        var payload = {
          type: 'share-blog',
          channel: channel || 'private',
          blogId: this.blogId,
          title: title || '一篇探店笔记',
          summary: summary || '点击查看这篇笔记详情',
          cover: cover,
          url: this.shareUrl,
          shopName: this.shop && this.shop.name ? this.shortShareText(this.shop.name, 36) : ''
        };
        if (!compact) {
          payload.appUrl = 'hmdp://blog/detail?id=' + this.blogId;
          payload.authorName = this.shortShareText(this.blog.name || '', 36);
          payload.authorIcon = this.resolveImageUrl(this.blog.icon, 'avatar');
          payload.createTime = this.blog.createTime || '';
          payload.target = channel || 'more';
          payload.text = payload.summary;
          payload.thumbUrl = payload.cover;
        }
        return payload;
      },
      openPrivateShare() {
        if (!this.ensureLogin()) {
          return;
        }
        this.shareFriendPanelVisible = !this.shareFriendPanelVisible;
        if (this.shareFriendPanelVisible && !this.shareFriends.length) {
          this.loadShareFriends();
        }
      },
      loadShareFriends() {
        if (this.shareFriendLoading) {
          return;
        }
        this.shareFriendLoading = true;
        var ensureUser = this.currentUser && this.currentUser.id
          ? Promise.resolve(this.currentUser)
          : axios.get('/user/me').then(function (res) {
            this.currentUser = res.data || null;
            return this.currentUser;
          }.bind(this));

        ensureUser.then(function (user) {
          if (!user || !user.id) {
            return [];
          }
          return Promise.all([
            axios.get('/follow/list/' + user.id, { params: { current: 1 } }).catch(function () { return { data: [] }; }),
            axios.get('/follow/followers/' + user.id, { params: { current: 1 } }).catch(function () { return { data: [] }; }),
            axios.get('/chat/conversations').catch(function () { return { data: [] }; })
          ]).then(function (results) {
            var rows = [];
            results.slice(0, 2).forEach(function (result) {
              if (Array.isArray(result.data)) {
                rows = rows.concat(result.data);
              }
            });
            if (Array.isArray(results[2].data)) {
              rows = rows.concat(results[2].data.map(function (item) {
                return {
                  id: item.otherUserId,
                  nickName: item.otherUserName,
                  icon: item.otherUserIcon
                };
              }));
            }
            var seen = {};
            return rows.filter(function (item) {
              var itemId = item && item.id ? Number(item.id) : 0;
              if (!itemId || itemId === Number(user.id) || seen[itemId]) {
                return false;
              }
              seen[itemId] = true;
              return true;
            });
          });
        }.bind(this)).then(function (list) {
          this.shareFriends = Array.isArray(list) ? list : [];
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '加载好友失败'));
        }.bind(this)).finally(function () {
          this.shareFriendLoading = false;
        }.bind(this));
      },
      sendShareToFriend(user) {
        if (!user || !user.id) {
          return;
        }
        this.shareSendingId = user.id;
        axios.post('/chat/send', null, {
          params: {
            receiverId: user.id,
            content: this.buildShareMessage()
          }
        }).then(function () {
          this.$message.success('已私信给 ' + (user.nickName || '好友'));
        }.bind(this)).catch(function (err) {
          this.$message.error(util.getErrorMessage(err, '私信发送失败'));
        }.bind(this)).finally(function () {
          this.shareSendingId = 0;
        }.bind(this));
      },
      openShareUrl(url) {
        var opened = window.open(url, '_blank');
        if (!opened) {
          location.href = url;
        }
      },
      invokeNativeShare(channel) {
        var payload = this.buildSharePayload(channel);
        var message = JSON.stringify({
          action: 'share',
          channel: channel || 'more',
          payload: payload
        });
        var methodMap = {
          wechat: ['shareToWechat', 'shareWechatFriend', 'share'],
          moments: ['shareToWechatMoments', 'shareToMoments', 'share'],
          qq: ['shareToQQ', 'shareQQFriend', 'share'],
          qzone: ['shareToQZone', 'shareQZone', 'share'],
          more: ['shareSystem', 'share']
        };
        var methods = methodMap[channel] || methodMap.more;
        var bridges = [window.HmdpNative, window.HMDPNative, window.Android].filter(Boolean);
        for (var i = 0; i < bridges.length; i += 1) {
          for (var j = 0; j < methods.length; j += 1) {
            var method = bridges[i][methods[j]];
            if (typeof method === 'function') {
              try {
                var result = method.call(bridges[i], message);
                return result !== false;
              } catch (err) {}
            }
          }
        }
        if (window.webkit && window.webkit.messageHandlers) {
          var handlers = ['hmdpShare', 'HmdpNative', 'share'];
          for (var k = 0; k < handlers.length; k += 1) {
            var handler = window.webkit.messageHandlers[handlers[k]];
            if (handler && typeof handler.postMessage === 'function') {
              try {
                handler.postMessage({ action: 'share', payload: payload });
                return true;
              } catch (err) {}
            }
          }
        }
        if (window.ReactNativeWebView && typeof window.ReactNativeWebView.postMessage === 'function') {
          window.ReactNativeWebView.postMessage(message);
          return true;
        }
        return false;
      },
      openAppScheme(scheme, fallback) {
        var hidden = false;
        var onVisibilityChange = function () {
          hidden = document.hidden;
        };
        document.addEventListener('visibilitychange', onVisibilityChange, { once: true });
        location.href = scheme;
        setTimeout(function () {
          document.removeEventListener('visibilitychange', onVisibilityChange);
          if (!hidden && fallback) {
            fallback();
          }
        }, 900);
      },
      buildQQScheme(channel) {
        var params = {
          src_type: 'web',
          version: '1',
          file_type: 'news',
          title: this.shareTitle,
          description: this.shareSummary,
          url: this.shareUrl,
          image_url: this.resolveImageUrl(this.shareCover) || ''
        };
        if (channel === 'qzone') {
          return util.buildUrl('mqqapi://qzone/publish', params);
        }
        return util.buildUrl('mqqapi://share/to_fri', params);
      },
      shareExternal(channel) {
        var payload = {
          title: this.shareTitle,
          text: this.shareSummary,
          url: this.shareUrl
        };
        if (this.invokeNativeShare(channel)) {
          this.$message.success('正在唤起分享面板');
          return;
        }
        if (channel === 'wechat' || channel === 'moments') {
          var targetName = channel === 'wechat' ? '微信好友' : '微信朋友圈';
          this.copyText(this.shareUrl).then(function () {
            this.$message.success('链接已复制，正在打开' + targetName);
          }.bind(this)).catch(function () {
            this.$message.warning('当前环境未接入微信 SDK，请复制链接后在微信里分享');
          }.bind(this)).finally(function () {
            this.openAppScheme('weixin://', function () {
              this.$message.warning('未检测到微信。App 内接入微信 SDK 后可直接分享给' + targetName);
            }.bind(this));
          }.bind(this));
          return;
        }
        if (channel === 'qq') {
          this.openAppScheme(this.buildQQScheme('qq'), function () {
            this.openShareUrl('https://connect.qq.com/widget/shareqq/index.html?'
              + 'url=' + encodeURIComponent(this.shareUrl)
              + '&title=' + encodeURIComponent(this.shareTitle)
              + '&summary=' + encodeURIComponent(this.shareSummary)
              + '&pics=' + encodeURIComponent(this.shareCover || ''));
          }.bind(this));
          return;
        }
        if (channel === 'qzone') {
          this.openAppScheme(this.buildQQScheme('qzone'), function () {
            this.openShareUrl('https://sns.qzone.qq.com/cgi-bin/qzshare/cgi_qzshare_onekey?'
              + 'url=' + encodeURIComponent(this.shareUrl)
              + '&title=' + encodeURIComponent(this.shareTitle)
              + '&summary=' + encodeURIComponent(this.shareSummary)
              + '&pics=' + encodeURIComponent(this.shareCover || ''));
          }.bind(this));
          return;
        }
        if (navigator.share) {
          navigator.share(payload).catch(function () {});
          return;
        }
        var channelName = channel === 'wechat' ? '微信好友'
          : channel === 'moments' ? '微信朋友圈'
          : '更多应用';
        this.copyText(this.shareUrl).then(function () {
          this.$message.success('链接已复制，请打开' + channelName + '粘贴分享');
        }.bind(this)).catch(function () {
          this.$message.warning('请复制当前页面链接分享');
        }.bind(this));
      },
      loadCanvasImage(src) {
        return new Promise(function (resolve) {
          if (!src) {
            resolve(null);
            return;
          }
          var image = new Image();
          image.crossOrigin = 'anonymous';
          image.onload = function () { resolve(image); };
          image.onerror = function () { resolve(null); };
          image.src = src;
        });
      },
      drawWrappedText(ctx, text, x, y, maxWidth, lineHeight, maxLines) {
        var words = String(text || '').split('');
        var line = '';
        var lines = [];
        words.forEach(function (char) {
          var test = line + char;
          if (ctx.measureText(test).width > maxWidth && line) {
            lines.push(line);
            line = char;
          } else {
            line = test;
          }
        });
        if (line) {
          lines.push(line);
        }
        lines.slice(0, maxLines || lines.length).forEach(function (item, index) {
          var suffix = maxLines && index === maxLines - 1 && lines.length > maxLines ? '...' : '';
          ctx.fillText(item + suffix, x, y + index * lineHeight);
        });
        return y + Math.min(lines.length, maxLines || lines.length) * lineHeight;
      },
      generateShareImage() {
        if (this.shareImageGenerating) {
          return;
        }
        this.shareImageGenerating = true;
        this.loadCanvasImage(this.shareCover).then(function (image) {
          var canvas = document.createElement('canvas');
          canvas.width = 750;
          canvas.height = 1120;
          var ctx = canvas.getContext('2d');
          ctx.fillStyle = '#fff7f2';
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(42, 42, 666, 1036);
          if (image) {
            var boxX = 72;
            var boxY = 76;
            var boxW = 606;
            var boxH = 560;
            var ratio = Math.max(boxW / image.width, boxH / image.height);
            var drawW = image.width * ratio;
            var drawH = image.height * ratio;
            ctx.save();
            ctx.beginPath();
            if (ctx.roundRect) {
              ctx.roundRect(boxX, boxY, boxW, boxH, 36);
            } else {
              ctx.rect(boxX, boxY, boxW, boxH);
            }
            ctx.clip();
            ctx.drawImage(image, boxX + (boxW - drawW) / 2, boxY + (boxH - drawH) / 2, drawW, drawH);
            ctx.restore();
          }
          ctx.fillStyle = '#1f2937';
          ctx.font = 'bold 42px "Microsoft YaHei", sans-serif';
          var nextY = this.drawWrappedText(ctx, this.shareTitle, 72, 708, 606, 56, 2) + 18;
          ctx.fillStyle = '#6b7280';
          ctx.font = '28px "Microsoft YaHei", sans-serif';
          nextY = this.drawWrappedText(ctx, this.shareSummary, 72, nextY, 606, 42, 3) + 30;
          ctx.fillStyle = '#ff6b35';
          ctx.font = 'bold 26px "Microsoft YaHei", sans-serif';
          ctx.fillText(this.shop && this.shop.name ? '来自 ' + this.shop.name : '来自 HMDP 探店笔记', 72, nextY);
          ctx.fillStyle = '#9ca3af';
          ctx.font = '24px "Microsoft YaHei", sans-serif';
          this.drawWrappedText(ctx, this.shareUrl, 72, 1010, 606, 34, 2);
          var link = document.createElement('a');
          link.download = 'blog-share-' + this.blogId + '.png';
          link.href = canvas.toDataURL('image/png');
          link.click();
          this.$message.success('分享图已生成');
        }.bind(this)).catch(function () {
          this.$message.warning('图片生成失败，已保留复制链接分享');
        }.bind(this)).finally(function () {
          this.shareImageGenerating = false;
        }.bind(this));
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
