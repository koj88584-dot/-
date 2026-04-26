new Vue({
  el: "#app",
  data() {
    return {
      loading: false,
      friends: [],
      fallbackAvatar: "/imgs/icons/default-icon.png"
    };
  },
  created() {
    this.loadRecommendations();
  },
  methods: {
    goBack() {
      history.back();
    },
    loadRecommendations() {
      this.loading = true;
      axios.get("/user/recommendations", { params: { current: 1 } })
        .then((result) => {
          this.friends = Array.isArray(result.data) ? result.data.map((item) => Object.assign({ following: false }, item)) : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "加载好友推荐失败"));
        })
        .finally(() => {
          this.loading = false;
        });
    },
    follow(friend) {
      if (!friend || !friend.id || friend.following) return;
      axios.put("/follow/" + friend.id + "/true")
        .then(() => {
          friend.following = true;
          this.$message.success("已关注 " + (friend.nickName || "好友"));
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "关注失败"));
        });
    },
    openProfile(userId) {
      if (!userId) return;
      location.href = "/pages/user/other-info.html?id=" + userId;
    },
    scan() {
      const bridge = window.HmdpNative || window.HMDPNative || window.Android;
      if (bridge && typeof bridge.scanQRCode === "function") {
        bridge.scanQRCode();
        return;
      }
      this.$message.info("当前 Web 环境未接入扫码，App 原生层接入后会直接打开相机");
    },
    showCard() {
      this.$alert("打开 App 后这里会展示你的个人二维码名片，可让朋友扫码关注。", "我的名片", {
        confirmButtonText: "知道了"
      });
    },
    avatarUrl(path) {
      return util.resolveImageUrl(path, this.fallbackAvatar);
    },
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackAvatar);
    }
  }
});
