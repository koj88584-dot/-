new Vue({
  el: '#app',
  data: {
    loading: true,
    settings: {
      showFollowing: true,
      showFollowers: true,
      showFavorites: true,
      showHistory: false,
      allowMessage: true,
      allowRecommend: true,
      showOnlineStatus: true,
      stealthMode: false,
      allowVisitNotify: true
    }
  },
  created: function() {
    this.fetchSettings();
  },
  methods: {
    fetchSettings: function() {
      axios.get('/privacy')
        .then(function(res) {
          var setting = (res.data && res.data.data) ? res.data.data : res.data;
          if (setting) {
            this.settings.showFollowing = !!setting.showFollowing;
            this.settings.showFollowers = !!setting.showFollowers;
            this.settings.showFavorites = !!setting.showFavorites;
            this.settings.showHistory = !!setting.showHistory;
            this.settings.allowMessage = !!setting.allowMessage;
            this.settings.allowRecommend = !!setting.allowRecommend;
            this.settings.showOnlineStatus = !!setting.showOnlineStatus;
            // New fields - default if not returned by backend
            this.settings.stealthMode = !!(setting.stealthMode);
            this.settings.allowVisitNotify = setting.allowVisitNotify !== undefined ? !!setting.allowVisitNotify : true;
          }
          this.loading = false;
        }.bind(this))
        .catch(function() {
          this.loading = false;
          this.$message.error('获取隐私设置失败，请先登录');
        }.bind(this));
    },
    updateSettings: function() {
      var payload = {
        showFollowing: this.settings.showFollowing ? 1 : 0,
        showFollowers: this.settings.showFollowers ? 1 : 0,
        showFavorites: this.settings.showFavorites ? 1 : 0,
        showHistory: this.settings.showHistory ? 1 : 0,
        allowMessage: this.settings.allowMessage ? 1 : 0,
        allowRecommend: this.settings.allowRecommend ? 1 : 0,
        showOnlineStatus: this.settings.showOnlineStatus ? 1 : 0,
        stealthMode: this.settings.stealthMode ? 1 : 0,
        allowVisitNotify: this.settings.allowVisitNotify ? 1 : 0
      };
      axios.put('/privacy', payload)
        .then(function() {
          this.$message.success('隐私设置已保存');
        }.bind(this))
        .catch(function() {
          this.$message.error('保存失败，请重试');
        }.bind(this));
    }
  }
});