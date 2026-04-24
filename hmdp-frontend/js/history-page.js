new Vue({
    el: '#app',
    data: {
      history: [],
      fallbackImage: '/imgs/blogs/blog1.jpg'
    },
    created() {
      this.loadHistory();
    },
    methods: {
      goBack() {
        history.back();
      },
      loadHistory() {
        axios.get('/history/list').then(({ data }) => {
          this.history = Array.isArray(data) ? data : [];
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '加载浏览历史失败'));
        });
      },
      openDetail(item) {
        if (item?.name) {
          location.href = '/pages/shop/shop-detail.html?id=' + item.id;
          return;
        }
        if (item?.title) {
          location.href = '/pages/blog/blog-detail.html?id=' + item.id;
        }
      },
      deleteHistory(id) {
        if (!id) {
          this.$message.error('这条历史记录不存在');
          return;
        }
        axios.delete('/history/' + id).then(() => {
          this.$message.success('删除成功');
          this.loadHistory();
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '删除失败'));
        });
      },
      clearHistory() {
        this.$confirm('确定清空全部浏览历史吗？', '提示', {
          confirmButtonText: '确定清空',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          return axios.delete('/history/clear');
        }).then(() => {
          this.$message.success('已清空浏览历史');
          this.history = [];
        }).catch((err) => {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '清空失败'));
          }
        });
      },
      getThumb(item) {
        const first = item?.images ? String(item.images).split(',')[0] : '';
        return util.resolveImageUrl(first, this.fallbackImage);
      },
      handleImageError(event) {
        util.applyImageFallback(event, this.fallbackImage);
      },
      formatTime(value) {
        if (!value) return '';
        return String(value).replace('T', ' ').replace(/\.\d+$/, '');
      }
    }
  });

