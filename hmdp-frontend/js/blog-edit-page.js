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
      uploading: false,
      submitting: false,
      currentCity: "当前位置",
      fallbackImage: "/imgs/blogs/blog1.jpg"
    };
  },
  computed: {
    canPublish() {
      return !!((this.params.title || "").trim() || (this.params.content || "").trim());
    },
    coverImage() {
      return this.fileList.length ? this.normalizeImageUrl(this.fileList[0]) : this.fallbackImage;
    },
    previewTitle() {
      return (this.params.title || "").trim() || "还没有标题";
    },
    previewContent() {
      return (this.params.content || "").trim() || "正文预览会显示在这里。写得具体一点，读者会更容易被说服。";
    },
    previewShopText() {
      if (!this.selectedShop.name) {
        return "";
      }
      return [this.selectedShop.name, this.selectedShop.area || this.selectedShop.address || ""]
        .filter(Boolean)
        .join(" · ");
    },
    previewCoverImage() {
      if (this.fileList.length) {
        return this.coverImage;
      }
      return this.resolveShopCover(this.selectedShop) || this.fallbackImage;
    }
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
      const parsed = util.readStorageJSON("currentCity", null);
      if (parsed && parsed.name) {
        this.currentCity = parsed.name;
        return;
      }
      const savedCity = localStorage.getItem("currentCity");
      if (savedCity) {
        this.currentCity = savedCity;
      }
    },
    handleAssistantEntry() {
      if (util.getUrlParam("source") !== "assistant") {
        return;
      }
      const title = util.getUrlParam("title");
      const content = util.getUrlParam("content");
      const shopKeyword = util.getUrlParam("shopKeyword");
      if (title) this.params.title = title;
      if (content) this.params.content = content;
      if (shopKeyword) this.shopName = shopKeyword;
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
          this.$message.error(util.getErrorMessage(err, "加载商户失败"));
        })
        .finally(() => {
          this.shopLoading = false;
        });
    },
    selectShop(shop) {
      this.selectedShop = shop || {};
      this.showDialog = false;
    },
    submitBlog() {
      if (!this.canPublish) {
        this.$message.warning("先写点内容再发布吧");
        return;
      }
      const data = {
        title: (this.params.title || "").trim(),
        content: (this.params.content || "").trim(),
        images: this.fileList.join(",")
      };
      if (!data.content) {
        data.content = data.title;
      }
      if (this.selectedShop.id) {
        data.shopId = this.selectedShop.id;
      }

      this.submitting = true;
      axios.post("/blog", data)
        .then(() => {
          this.$message.success("发布成功");
          setTimeout(() => {
            location.href = "/pages/user/info.html";
          }, 220);
        })
        .catch((err) => {
          this.$message.error(util.getErrorMessage(err, "发布失败"));
        })
        .finally(() => {
          this.submitting = false;
        });
    },
    openFileDialog() {
      if (this.uploading || this.fileList.length >= 9) {
        return;
      }
      this.$refs.fileInput.click();
    },
    async fileSelected() {
      const files = Array.from(this.$refs.fileInput.files || []);
      if (!files.length) {
        return;
      }
      const slots = Math.max(0, 9 - this.fileList.length);
      const selected = files.slice(0, slots);
      if (files.length > slots) {
        this.$message.warning("最多上传 9 张照片");
      }
      this.uploading = true;
      try {
        for (let i = 0; i < selected.length; i += 1) {
          const path = await this.uploadOneFile(selected[i]);
          if (path) {
            this.fileList.push(path);
          }
        }
      } catch (err) {
        this.$message.error(util.getErrorMessage(err, "图片上传失败"));
      } finally {
        this.uploading = false;
        this.$refs.fileInput.value = "";
      }
    },
    uploadOneFile(file) {
      if (util.uploadImageFile) {
        return util.uploadImageFile(file);
      }
      const formData = new FormData();
      formData.append("file", file);
      return axios.post("/upload/blog", formData, {
        headers: { "Content-Type": "multipart/form-data" }
      }).then((result) => {
        const imagePath = result.data || "";
        return imagePath && imagePath.startsWith("/imgs") ? imagePath : "/imgs" + imagePath;
      });
    },
    deletePic(index) {
      const target = this.fileList[index];
      const removeLocal = () => {
        this.fileList.splice(index, 1);
      };
      if (!target) {
        removeLocal();
        return;
      }
      const deleteTask = util.deleteUploadedImage ? util.deleteUploadedImage(target) : axios.get("/upload/blog/delete", {
        params: { name: target }
      });
      deleteTask.then(removeLocal).catch(() => {
        removeLocal();
      });
    },
    getCachedLocation() {
      const parsed = util.readStorageJSON("userLocation", null);
      if (parsed && parsed.x && parsed.y) {
        return parsed;
      }
      return null;
    },
    normalizeImageUrl(path) {
      return util.resolveImageUrl(path, this.fallbackImage);
    },
    resolveShopCover(shop) {
      if (!shop || !shop.images) {
        return "";
      }
      const first = Array.isArray(shop.images) ? shop.images[0] : String(shop.images).split(",")[0];
      return first ? this.normalizeImageUrl(first) : "";
    },
    handlePreviewImageError(event) {
      util.applyImageFallback(event, this.fallbackImage);
    },
    goBack() {
      history.back();
    }
  }
});
