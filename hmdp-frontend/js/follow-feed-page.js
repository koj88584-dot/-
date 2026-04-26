new Vue({
  el: '#app',
  data: {
    notes: [],
    loading: false,
    lastId: Date.now() + 1,
    offset: 0,
    hasMore: true,
    fallbackImage: '/imgs/icons/default-icon.png'
  },
  created() {
    this.bootstrap();
  },
  mounted() {
    window.addEventListener('scroll', this.handleScroll, { passive: true });
  },
  beforeDestroy() {
    window.removeEventListener('scroll', this.handleScroll);
  },
  methods: {
    async bootstrap() {
      const ok = await window.authHelper.ensureLogin(location.pathname + location.search);
      if (!ok) return;
      this.loadMore();
    },
    loadMore() {
      if (this.loading || !this.hasMore) return;
      this.loading = true;
      axios.get('/blog/of/follow', {
        params: {
          lastId: this.lastId,
          offset: this.offset
        }
      }).then((result) => {
        const payload = result.data || {};
        const list = Array.isArray(payload.list) ? payload.list : [];
        const mapped = list.map(this.normalizeNote);
        this.notes = util.mergeUnique(this.notes, mapped, item => String(item.id));
        this.lastId = payload.minTime || this.lastId;
        this.offset = payload.offset || 0;
        this.hasMore = list.length > 0;
      }).catch((err) => {
        this.hasMore = false;
        this.$message.warning(util.getErrorMessage(err, '关注动态加载失败'));
      }).finally(() => {
        this.loading = false;
      });
    },
    normalizeNote(note) {
      note = note || {};
      return Object.assign({}, note, {
        cover: util.resolveImageUrl(this.firstImage(note.images || note.img), this.fallbackImage)
      });
    },
    firstImage(images) {
      if (!images) return '';
      return String(images).split(',')[0];
    },
    resolveAvatar(icon) {
      return util.resolveImageUrl(icon, this.fallbackImage);
    },
    handleImageError(event) {
      util.applyImageFallback(event, this.fallbackImage);
    },
    handleAvatarError(event) {
      util.applyImageFallback(event, this.fallbackImage);
    },
    openNote(note) {
      if (!note || !note.id) return;
      location.href = '/pages/blog/blog-detail.html?id=' + encodeURIComponent(note.id);
    },
    formatDate(value) {
      if (!value) return '刚刚';
      const date = new Date(value);
      if (isNaN(date.getTime())) return '刚刚';
      const diff = Date.now() - date.getTime();
      if (diff < 60000) return '刚刚';
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
      return (date.getMonth() + 1) + '月' + date.getDate() + '日';
    },
    handleScroll() {
      if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 120) {
        this.loadMore();
      }
    },
    goBack() {
      history.back();
    }
  }
});
