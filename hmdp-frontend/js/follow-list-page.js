new Vue({
  el: '#app',
  data: {
    activeTab: 'follow',
    userList: [],
    followCount: 0,
    followerCount: 0,
    current: 1,
    loading: false,
    hasMore: true,
    targetUserId: null,
    currentUserId: null,
    emptyMessage: '',
    fallbackAvatar: '/imgs/icons/default-icon.png'
  },
  created() {
    const params = new URLSearchParams(window.location.search);
    const idParam = params.get('id') || params.get('userId');
    this.targetUserId = idParam ? Number(idParam) : null;

    const tab = params.get('tab') || params.get('type');
    if (tab === 'fans') {
      this.activeTab = 'follower';
    } else if (tab === 'follow' || tab === 'follower') {
      this.activeTab = tab;
    }

    this.getCurrentUser();
  },
  methods: {
    getCurrentUser() {
      axios.get('/user/me')
        .then(res => {
          this.currentUserId = res.data && res.data.id ? Number(res.data.id) : null;
          this.loadData();
        })
        .catch(() => {
          this.$message.error('请先登录');
          util.redirectToLogin(location.pathname + location.search, 1000);
        });
    },
    loadData() {
      this.current = 1;
      this.userList = [];
      this.emptyMessage = '';
      this.hasMore = true;
      this.loadMore();
    },
    loadMore() {
      if (this.loading || !this.hasMore) return;

      const userId = this.targetUserId || this.currentUserId;
      if (!userId) {
        this.emptyMessage = '缺少用户信息';
        this.hasMore = false;
        return;
      }

      this.loading = true;
      const url = this.activeTab === 'follow'
        ? `/follow/list/${userId}`
        : `/follow/followers/${userId}`;

      axios.get(url, { params: { current: this.current } })
        .then(res => {
          const list = Array.isArray(res.data) ? res.data : [];
          list.forEach(user => {
            this.checkFollowStatus(user);
          });

          this.userList = util.mergeUnique(this.userList, list, item => String(item.id));
          this.hasMore = list.length === 10;
          this.current++;

          if (this.activeTab === 'follow') {
            this.followCount = this.userList.length;
          } else {
            this.followerCount = this.userList.length;
          }
        })
        .catch(err => {
          this.hasMore = false;
          this.emptyMessage = util.getErrorMessage(err, '加载失败');
          this.$message.warning(this.emptyMessage);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    checkFollowStatus(user) {
      if (!user || !user.id) {
        return;
      }
      if (this.isSelf(user.id)) {
        this.$set(user, 'isFollow', true);
        return;
      }
      axios.get(`/follow/or/not/${user.id}`)
        .then(res => {
          this.$set(user, 'isFollow', !!res.data);
        })
        .catch(() => {});
    },
    switchTab(tab) {
      if (this.activeTab === tab) {
        return;
      }
      this.activeTab = tab;
      this.loadData();
    },
    toggleFollow(user) {
      if (!user || !user.id) {
        return;
      }
      const isFollow = !user.isFollow;
      axios.put(`/follow/${user.id}/${isFollow}`)
        .then(() => {
          this.$set(user, 'isFollow', isFollow);
          this.$message.success(isFollow ? '关注成功' : '取消关注成功');
        })
        .catch(err => {
          this.$message.error(util.getErrorMessage(err, '操作失败'));
        });
    },
    toUserPage(userId) {
      if (!userId) {
        return;
      }
      if (this.isSelf(userId)) {
        location.href = '/pages/user/info.html';
      } else {
        location.href = `/pages/user/other-info.html?id=${userId}`;
      }
    },
    isSelf(userId) {
      return Number(userId) === Number(this.currentUserId);
    },
    getAvatar(icon) {
      return util.resolveImageUrl(icon, this.fallbackAvatar);
    },
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
    },
    formatPhone(phone) {
      if (!phone) return '';
      return String(phone).replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
    },
    goBack() {
      history.back();
    }
  }
});
