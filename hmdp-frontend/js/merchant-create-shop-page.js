new Vue({
  el: '#app',
  data() {
    return {
      util,
      loading: false,
      types: [],
      shopImageList: [],
      proofImageList: [],
      form: {
        shopName: '',
        typeId: null,
        address: '',
        x: '',
        y: '',
        contactPhone: '',
        images: '',
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
      axios.get('/merchant/application/me').then(({ data }) => {
        if (!data || !data.merchantEnabled) {
          this.$message.warning('请先完成商家资格审核');
          setTimeout(() => {
            location.replace('./apply.html');
          }, 400);
          return;
        }
        this.loadTypes();
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
    loadTypes() {
      this.loading = true;
      axios.get('/shop-type/list').then(({ data }) => {
        this.types = Array.isArray(data) ? data : [];
      }).catch((err) => {
        this.$message.error(util.getErrorMessage(err, '加载店铺类型失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    triggerShopImagesPicker() {
      this.$refs.shopImagesInput.click();
    },
    triggerProofPicker() {
      this.$refs.proofInput.click();
    },
    async handleShopImagesChange(event) {
      await this.appendImages((event.target && event.target.files) || [], 'shopImageList', 'images', 6);
      event.target.value = '';
    },
    async handleProofChange(event) {
      await this.appendImages((event.target && event.target.files) || [], 'proofImageList', 'proofImages', 3);
      event.target.value = '';
    },
    async appendImages(fileCollection, listKey, formKey, maxCount) {
      const files = Array.from(fileCollection || []);
      if (!files.length) {
        return;
      }
      const remain = maxCount - this[listKey].length;
      if (remain <= 0) {
        this.$message.warning('最多上传 ' + maxCount + ' 张图片');
        return;
      }
      if (files.length > remain) {
        this.$message.warning('最多还可上传 ' + remain + ' 张图片');
      }
      this.loading = true;
      try {
        for (const file of files.slice(0, remain)) {
          const imagePath = await util.uploadImageFile(file);
          this[listKey].push(imagePath);
        }
        this.syncImages(formKey, listKey);
      } catch (err) {
        this.$message.error(util.getErrorMessage(err, '上传图片失败'));
      } finally {
        this.loading = false;
      }
    },
    removeShopImage(index) {
      this.removeImage('shopImageList', 'images', index);
    },
    removeProofImage(index) {
      this.removeImage('proofImageList', 'proofImages', index);
    },
    removeImage(listKey, formKey, index) {
      const imagePath = this[listKey][index];
      util.deleteUploadedImage(imagePath)
        .catch(() => null)
        .finally(() => {
          this[listKey].splice(index, 1);
          this.syncImages(formKey, listKey);
        });
    },
    syncImages(formKey, listKey) {
      this.form[formKey] = util.imageListToCsv(this[listKey]);
    },
    resolveImage(path) {
      return util.resolveImageUrl(path);
    },
    normalizeCoordinate(value, label, min, max) {
      const text = String(value == null ? '' : value).trim();
      if (!text) {
        return null;
      }
      const num = Number(text);
      if (!Number.isFinite(num) || num < min || num > max) {
        this.$message.warning(label + '格式不正确，请输入 ' + min + ' 到 ' + max + ' 之间的小数');
        return undefined;
      }
      return num;
    },
    submit() {
      this.syncImages('images', 'shopImageList');
      this.syncImages('proofImages', 'proofImageList');
      if (!this.form.shopName) {
        this.$message.warning('请填写店铺名称');
        return;
      }
      if (!this.form.typeId) {
        this.$message.warning('请选择店铺类型');
        return;
      }
      if (!this.form.address) {
        this.$message.warning('请填写店铺地址');
        return;
      }
      if (!this.form.contactPhone) {
        this.$message.warning('请填写联系电话');
        return;
      }
      if (!this.form.proofImages) {
        this.$message.warning('请上传证明图片');
        return;
      }
      const payload = Object.assign({}, this.form);
      const lng = this.normalizeCoordinate(payload.x, '经度', -180, 180);
      if (lng === undefined) {
        return;
      }
      const lat = this.normalizeCoordinate(payload.y, '纬度', -90, 90);
      if (lat === undefined) {
        return;
      }
      payload.x = lng;
      payload.y = lat;
      this.loading = true;
      axios.post('/merchant/application/create-shop', payload).then(() => {
        this.$message.success('新建店铺申请已提交');
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
