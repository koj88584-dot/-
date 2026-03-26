
  new Vue({
    el: '#app',
    data: {
      history: []
    },
    created() {
      this.loadHistory();
    },
    methods: {
      loadHistory() {
        axios.get('/history/list')
          .then(({ data }) => {
            this.history = Array.isArray(data) ? data : [];
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err));
          });
      },
      openDetail(item) {
        if (item.name) {
          location.href = '/pages/shop/shop-detail.html?id=' + item.id;
        } else if (item.title) {
          location.href = '/pages/blog/blog-detail.html?id=' + item.id;
        }
      },
      deleteHistory(id) {
        if (!id) {
          this.$message.error('历史记录不存在');
          return;
        }
        axios.delete('/history/' + id)
          .then(() => {
            this.loadHistory();
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err));
          });
      },
      clearHistory(type) {
        const url = '/history/clear' + (type == null ? '' : ('?type=' + type));
        axios.delete(url)
          .then(() => {
            this.loadHistory();
          })
          .catch(err => {
            this.$message.error(this.getErrorMessage(err));
          });
      },
      getThumb(item) {
        const firstImage = item.images ? item.images.split(',')[0] : '';
        return this.resolveImageUrl(firstImage);
      },
      resolveImageUrl(path) {
        if (!path) {
          return '/imgs/icons/default-icon.png';
        }
        if (path.startsWith('http') || path.startsWith('data:')) {
          return path;
        }
        if (path.startsWith(util.commonURL)) {
          return path;
        }
        return util.commonURL + (path.startsWith('/') ? path : '/' + path);
      },
      formatTime(value) {
        if (!value) {
          return '';
        }
        return value.replace('T', ' ').replace(/\.\d+$/, '');
      },
      getErrorMessage(err) {
        if (typeof err === 'string') {
          return err;
        }
        return err?.response?.data?.errorMsg || err?.message || '操作失败';
      }
    }
  })

