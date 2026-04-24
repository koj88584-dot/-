new Vue({
  el: '#app',
  data() {
    return {
      util,
      loading: false,
      keyword: '',
      shops: [],
      selectedShopId: null,
      proofImageList: [],
      form: {
        proofImages: '',
        message: ''
      }
    };
  },
  created() {
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) return;
      axios.get('/merchant/application/me').then(({ data }) => {
        if (!data || !data.merchantEnabled) {
          this.$message.warning('请先完成商家资格审核');
          setTimeout(() => {
            location.replace('./apply.html');
          }, 400);
          return;
        }
        this.searchShops();
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载商家状态失败'));
      });
    },
    goBack() {
      history.back();
    },
    goProgress() {
      location.href = './progress.html';
    },
    searchShops() {
      this.loading = true;
      axios.get('/merchant/application/shops', {
        params: { keyword: this.keyword || undefined }
      }).then(({ data }) => {
        this.shops = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '搜索店铺失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    triggerProofPicker() {
      this.$refs.proofInput.click();
    },
    async handleProofChange(event) {
      const files = Array.from((event.target && event.target.files) || []);
      event.target.value = '';
      if (!files.length) {
        return;
      }
      const remain = 3 - this.proofImageList.length;
      if (remain <= 0) {
        this.$message.warning('最多上传 3 张证明图片');
        return;
      }
      if (files.length > remain) {
        this.$message.warning('最多还可上传 ' + remain + ' 张图片');
      }
      this.loading = true;
      try {
        for (const file of files.slice(0, remain)) {
          const imagePath = await util.uploadImageFile(file);
          this.proofImageList.push(imagePath);
        }
        this.syncProofImages();
      } catch (err) {
        this.$message.error(util.getErrorMessage(err, '上传图片失败'));
      } finally {
        this.loading = false;
      }
    },
    removeProofImage(index) {
      const imagePath = this.proofImageList[index];
      util.deleteUploadedImage(imagePath)
        .catch(() => null)
        .finally(() => {
          this.proofImageList.splice(index, 1);
          this.syncProofImages();
        });
    },
    syncProofImages() {
      this.form.proofImages = util.imageListToCsv(this.proofImageList);
    },
    resolveImage(path) {
      return util.resolveImageUrl(path);
    },
    submit() {
      this.syncProofImages();
      if (!this.selectedShopId) {
        this.$message.warning('请先选择一个店铺');
        return;
      }
      if (!this.form.proofImages) {
        this.$message.warning('请上传证明图片');
        return;
      }
      this.loading = true;
      axios.post('/merchant/application/claim-shop', {
        shopId: this.selectedShopId,
        proofImages: this.form.proofImages,
        message: this.form.message
      }).then(() => {
        this.$message.success('认领申请已提交');
        setTimeout(() => {
          location.href = './progress.html';
        }, 400);
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '提交认领申请失败'));
      }).finally(() => {
        this.loading = false;
      });
    }
  }
});
