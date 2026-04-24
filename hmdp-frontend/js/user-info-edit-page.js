const userInfoEditApp = new Vue({
  el: "#app",
  data() {
    return {
      util,
      user: {},
      showAvatarDialog: false,
      showNickNameDialog: false,
      tempAvatar: "",
      tempNickName: "",
      selectedFile: null,
      uploading: false
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
      this.loadCurrentUser();
    },
    loadCurrentUser() {
      axios.get("/user/me")
        .then((result) => {
          this.user = result.data || {};
          this.tempNickName = this.user.nickName || "";
          this.tempAvatar = this.user.icon || "";
        })
        .catch(() => {
          this.$message.error("请先登录");
          util.redirectToLogin(location.pathname + location.search, 300);
        });
    },
    goBack() {
      history.back();
    },
    triggerFileInput() {
      this.$refs.fileInput.click();
    },
    handleFileChange(event) {
      const file = event.target.files[0];
      if (!file) {
        return;
      }
      const validateMessage = util.validateImageFile(file);
      if (validateMessage) {
        this.$message.error(validateMessage);
        return;
      }
      this.selectedFile = file;
      const reader = new FileReader();
      reader.onload = (e) => {
        this.tempAvatar = e.target.result;
      };
      reader.readAsDataURL(file);
    },
    saveAvatar() {
      if (!this.selectedFile && !this.tempAvatar) {
        this.$message.error("请选择图片");
        return;
      }
      if (!this.selectedFile) {
        this.showAvatarDialog = false;
        return;
      }

      this.uploading = true;
      util.uploadImageFile(this.selectedFile)
        .then((imagePath) => axios.post("/user/upload-icon?url=" + encodeURIComponent(imagePath)))
        .then((updateResult) => {
          this.user.icon = updateResult.data;
          this.tempAvatar = this.user.icon;
          localStorage.setItem("userIcon", this.user.icon);
          this.$message.success("头像修改成功");
          this.showAvatarDialog = false;
          this.selectedFile = null;
          this.$refs.fileInput.value = "";
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "修改失败"));
        })
        .finally(() => {
          this.uploading = false;
        });
    },
    saveNickName() {
      const nickName = (this.tempNickName || "").trim();
      if (!nickName) {
        this.$message.error("请输入昵称");
        return;
      }
      axios.put("/user/update", {
        id: this.user.id,
        nickName: nickName
      })
        .then(() => {
          this.user.nickName = nickName;
          localStorage.setItem("userNickName", nickName);
          this.$message.success("昵称修改成功");
          this.showNickNameDialog = false;
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "修改失败"));
        });
    },
    getImageUrl(path) {
      return util.resolveImageUrl(path, "/imgs/icons/default-icon.png");
    }
  }
});
