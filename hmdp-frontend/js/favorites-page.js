new Vue({
    el: '#app',
    data: {
      activeType: '0',
      favorites: [],
      loading: true,
      loadingMore: false,
      current: 1,
      hasMore: true,
      fallbackImage: '/imgs/blogs/blog1.jpg'
    },
    created() {
      this.loadFavorites();
    },
    methods: {
      goBack() {
        history.back();
      },
      goToSearch() {
        location.href = '/pages/misc/search.html';
      },
      goToExplore() {
        location.href = '/pages/index-new.html';
      },
      switchTab(type) {
        if (this.activeType === type) return;
        this.activeType = type;
        this.current = 1;
        this.favorites = [];
        this.hasMore = true;
        this.loadFavorites();
      },
      loadFavorites() {
        this.loading = this.current === 1;
        const type = this.activeType === '0' ? null : Number(this.activeType);
        axios.get('/favorites/list', {
          params: { type: type, current: this.current }
        }).then(({ data }) => {
          const list = data?.data || data;
          const rows = Array.isArray(list) ? list : [];
          if (this.current === 1) {
            this.favorites = rows;
          } else {
            this.favorites = this.favorites.concat(rows);
          }
          this.hasMore = rows.length === 10;
        }).catch((err) => {
          this.$message.error(util.getErrorMessage(err, '加载收藏失败'));
        }).finally(() => {
          this.loading = false;
          this.loadingMore = false;
        });
      },
      loadMore() {
        if (this.loadingMore || !this.hasMore) return;
        this.current += 1;
        this.loadingMore = true;
        this.loadFavorites();
      },
      getThumb(item) {
        const first = item?.images ? String(item.images).split(',')[0] : '';
        return util.resolveImageUrl(first, this.fallbackImage);
      },
      handleImageError(event) {
        util.applyImageFallback(event, this.fallbackImage);
      },
      getDesc(item) {
        if (item?.address) return item.address;
        if (item?.content) {
          const content = String(item.content).replace(/<[^>]+>/g, '').trim();
          return content ? content.slice(0, 64) : '暂无内容简介';
        }
        return '暂无内容简介';
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
      cancelFavorite(item) {
        const type = item?.name ? 1 : 2;
        this.$confirm('确定取消这条收藏吗？', '提示', {
          confirmButtonText: '取消收藏',
          cancelButtonText: '再想想',
          type: 'warning'
        }).then(() => {
          return axios.delete('/favorites/' + type + '/' + item.id);
        }).then(() => {
          this.$message.success('已取消收藏');
          this.favorites = this.favorites.filter((row) => row.id !== item.id);
        }).catch((err) => {
          if (err !== 'cancel') {
            this.$message.error(util.getErrorMessage(err, '取消收藏失败'));
          }
        });
      }
    }
  });

