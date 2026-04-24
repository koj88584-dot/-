new Vue({
  el: '#app',
  data() {
    return {
      util,
      loading: false,
      profile: null,
      proofImageList: [],
      form: {
        contactName: '',
        contactPhone: '',
        companyName: '',
        description: '',
        proofImages: ''
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
      this.loadProfile();
    },
    goBack() {
      history.back();
    },
    goProgress() {
      location.href = './progress.html';
    },
    loadProfile() {
      this.loading = true;
      axios.get('/merchant/application/me').then(({ data }) => {
        this.profile = data || {};
        if (this.profile.merchantEnabled) {
          location.replace('./vouchers.html');
        }
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载申请状态失败'));
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
      if (!this.form.contactName) {
        this.$message.warning('请填写联系人');
        return;
      }
      if (!this.form.contactPhone) {
        this.$message.warning('请填写联系电话');
        return;
      }
      if (!this.form.proofImages) {
        this.$message.warning('请至少上传一张证明图片');
        return;
      }
      this.loading = true;
      axios.post('/merchant/application', Object.assign({}, this.form)).then(() => {
        this.$message.success('商家申请已提交');
        setTimeout(() => {
          location.href = './progress.html';
        }, 400);
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '提交申请失败'));
      }).finally(() => {
        this.loading = false;
      });
    }
  }
});
