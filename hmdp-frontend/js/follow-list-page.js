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
        currentUserId: null
      },
      created() {
        // 获取URL参数
        const params = new URLSearchParams(window.location.search);
        this.targetUserId = params.get('userId');
        const tab = params.get('tab');
        if (tab) {
          this.activeTab = tab;
        }
        
        // 获取当前用户ID
        this.getCurrentUser();
      },
      methods: {
        getCurrentUser() {
          axios.get('/user/me')
            .then(res => {
              this.currentUserId = res.data.id;
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
          this.hasMore = true;
          this.loadMore();
        },
        loadMore() {
          if (this.loading || !this.hasMore) return;
          
          this.loading = true;
          const userId = this.targetUserId || this.currentUserId;
          const url = this.activeTab === 'follow' 
            ? `/follow/list/${userId}?current=${this.current}`
            : `/follow/followers/${userId}?current=${this.current}`;
          
          axios.get(url)
            .then(res => {
              const list = res.data || [];
              
              // 检查关注状态
              list.forEach(user => {
                this.checkFollowStatus(user);
              });
              
              this.userList = this.userList.concat(list);
              this.hasMore = list.length === 10;
              this.current++;
              
              // 更新数量
              if (this.activeTab === 'follow') {
                this.followCount = this.userList.length;
              } else {
                this.followerCount = this.userList.length;
              }
            })
            .catch(err => {
              this.$message.error(err || '加载失败');
            })
            .finally(() => {
              this.loading = false;
            });
        },
        checkFollowStatus(user) {
          axios.get(`/follow/or/not/${user.id}`)
            .then(res => {
              this.$set(user, 'isFollow', res.data);
            });
        },
        switchTab(tab) {
          this.activeTab = tab;
          this.loadData();
        },
        toggleFollow(user) {
          const isFollow = !user.isFollow;
          axios.put(`/follow/${user.id}/${isFollow}`)
            .then(() => {
              user.isFollow = isFollow;
              this.$message.success(isFollow ? '关注成功' : '取消关注成功');
            })
            .catch(err => {
              this.$message.error(err || '操作失败');
            });
        },
        toUserPage(userId) {
          if (userId === this.currentUserId) {
            location.href = 'info.html';
          } else {
            location.href = `other-info.html?id=${userId}`;
          }
        },
        isSelf(userId) {
          return userId === this.currentUserId;
        },
        formatPhone(phone) {
          if (!phone) return '';
          return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
        },
        goBack() {
          history.back();
        }
      }
    });
  
