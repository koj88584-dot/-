
  new Vue({
    el: '#app',
    data: {
      activeType: '0',
      favorites: [],
      loading: true,
      loadingMore: false,
      current: 1,
      hasMore: true
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
        this.activeType = type;
        this.current = 1;
        this.favorites = [];
        this.hasMore = true;
        this.loadFavorites();
      },
      loadFavorites() {
        this.loading = true;
        const type = this.activeType === '0' ? null : parseInt(this.activeType);
        
        axios.get('/favorites/list', {
          params: { type: type, current: this.current }
        })
        .then(({ data }) => {
          const list = data?.data || data;
          if (this.current === 1) {
            this.favorites = Array.isArray(list) ? list : [];
          } else {
            this.favorites = this.favorites.concat(Array.isArray(list) ? list : []);
          }
          this.hasMore = Array.isArray(list) && list.length === 10;
          this.loading = false;
          this.loadingMore = false;
        })
        .catch(err => {
          this.$message.error('鍔犺浇鏀惰棌澶辫触');
          this.loading = false;
          this.loadingMore = false;
        });
      },
      loadMore() {
        this.current++;
        this.loadingMore = true;
        this.loadFavorites();
      },
      getThumb(item) {
        if (item.images) {
          return this.resolveImageUrl(item.images.split(',')[0]);
        }
        return '/imgs/icons/default-icon.png';
      },
      getDesc(item) {
        if (item.address) return item.address;
        if (item.content) return item.content.substring(0, 60) + '...';
        return '';
      },
      openDetail(item) {
        if (item.name) {
          location.href = '/pages/shop/shop-detail.html?id=' + item.id;
        } else if (item.title) {
          location.href = '/pages/blog/blog-detail.html?id=' + item.id;
        }
      },
      cancelFavorite(item) {
        const type = item.name ? 1 : 2;
        this.$confirm('纭畾鍙栨秷鏀惰棌鍚楋紵', '鎻愮ず', {
          confirmButtonText: '纭畾',
          cancelButtonText: '鍙栨秷',
          type: 'warning'
        }).then(() => {
          axios.delete(`/favorites/${type}/${item.id}`)
            .then(() => {
              this.$message.success('已取消收藏');
              this.favorites = this.favorites.filter(f => f.id !== item.id);
            })
            .catch(() => {
              this.$message.error('鎿嶄綔澶辫触');
            });
        });
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
      }
    }
  });

