new Vue({
  el: "#app",
  data() {
    return {
      util,
      fileList: [],
      params: {
        title: "",
        content: ""
      },
      showDialog: false,
      shops: [],
      shopName: "",
      selectedShop: {},
      pageReady: false,
      shopLoading: false,
      currentCity: "当前位置"
    };
  },
  async created() {
    const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
    if (!ok) {
      return;
    }
    this.pageReady = true;
    this.loadCurrentCity();
    this.handleAssistantEntry();
    this.queryShops();
  },
  methods: {
    loadCurrentCity() {
      const savedCity = localStorage.getItem("currentCity");
      if (!savedCity) {
        return;
      }
      const parsed = util.readStorageJSON("currentCity", savedCity);
      this.currentCity = parsed && parsed.name ? parsed.name : savedCity;
    },
    handleAssistantEntry() {
      if (util.getUrlParam("source") !== "assistant") {
        return;
      }
      const title = util.getUrlParam("title");
      const content = util.getUrlParam("content");
      const shopKeyword = util.getUrlParam("shopKeyword");
      if (title) {
        this.params.title = title;
      }
      if (content) {
        this.params.content = content;
      }
      if (shopKeyword) {
        this.shopName = shopKeyword;
      }
    },
    queryShops() {
      const params = {};
      const keyword = (this.shopName || "").trim();
      if (keyword) {
        params.name = keyword;
      }
      const location = this.getCachedLocation();
      if (location) {
        params.x = location.x;
        params.y = location.y;
      }

      this.shopLoading = true;
      axios.get("/shop/of/cache", { params })
        .then((result) => {
          this.shops = Array.isArray(result.data) ? result.data : [];
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err));
        })
        .finally(() => {
          this.shopLoading = false;
        });
    },
    selectShop(shop) {
      this.selectedShop = shop;
      this.showDialog = false;
    },
    submitBlog() {
      const data = {
        title: (this.params.title || "").trim(),
        content: (this.params.content || "").trim(),
        images: this.fileList.join(",")
      };
      if (!data.content) {
        this.$message.error("请先填写笔记内容");
        return;
      }
      if (this.selectedShop.id) {
        data.shopId = this.selectedShop.id;
      }

      axios.post("/blog", data)
        .then(() => {
          this.$message.success("发布成功");
          setTimeout(() => {
            location.href = "/pages/user/info.html";
          }, 200);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err));
        });
    },
    openFileDialog() {
      this.$refs.fileInput.click();
    },
    fileSelected() {
      const file = this.$refs.fileInput.files[0];
      if (!file) {
        return;
      }
      const formData = new FormData();
      formData.append("file", file);
      axios.post("/upload/blog", formData, {
        headers: { "Content-Type": "multipart/form-data" }
      })
        .then((result) => {
          const imagePath = result.data && result.data.startsWith("/imgs")
            ? result.data
            : "/imgs" + result.data;
          this.fileList.push(imagePath);
          this.$refs.fileInput.value = "";
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err));
        });
    },
    deletePic(index) {
      axios.get("/upload/blog/delete", {
        params: { name: this.fileList[index] }
      })
        .then(() => {
          this.fileList.splice(index, 1);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err));
        });
    },
    getCachedLocation() {
      const parsed = util.readStorageJSON("userLocation");
      if (parsed && parsed.x && parsed.y) {
        return parsed;
      }
      return null;
    },
    normalizeImageUrl(path) {
      return util.resolveImageUrl(path, "/imgs/icons/default-icon.png");
    },
    goBack() {
      history.back();
    }
  }
});
