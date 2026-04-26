new Vue({
  el: '#app',
  data: {
    cityCode: util.getUrlParam('cityCode') || util.getActiveCityCode(util.readCurrentCityProfile()),
    cityName: util.getUrlParam('city') || util.readCurrentCityProfile().cityName || '本城',
    payload: {},
    hotNews: [],
    loading: false
  },
  computed: {
    sourceLabel() {
      if (this.payload && this.payload.source === 'alapi-localized') {
        return '外部热榜 + 城市新闻';
      }
      if (this.payload && this.payload.source === 'public-rss') {
        return '城市新闻直搜';
      }
      if (this.payload && this.payload.source === 'external-unavailable') {
        return '外部热点暂不可用';
      }
      return this.payload && this.payload.stale ? '最近一次外部热点' : '外部热点新闻';
    },
    sourceStatsLabel() {
      const local = this.payload && this.payload.matchedLocalCount ? this.payload.matchedLocalCount : 0;
      const real = this.payload && this.payload.realSourceCount ? this.payload.realSourceCount : 0;
      const fallback = this.payload && this.payload.fallbackCount ? this.payload.fallbackCount : 0;
      return '本地命中 ' + local + ' 条 · 外部来源 ' + real + ' 条 · 缓存条目 ' + fallback + ' 条';
    },
    citySearchHint() {
      const city = (this.payload && this.payload.searchCity) || this.cityName || '当前城市';
      return '已按定位城市“' + city + '”自动搜索外部热点新闻，只展示城市相关的真实新闻内容。';
    }
  },
  created() {
    this.loadHotNews();
  },
  methods: {
    loadHotNews() {
      this.loading = true;
      axios.get('/city/' + encodeURIComponent(this.cityCode || '430100') + '/hot-news', {
        params: { current: 1 }
      }).then(({ data }) => {
        this.payload = data || {};
        this.cityName = this.payload.cityName || this.cityName;
        this.hotNews = Array.isArray(this.payload.list) ? this.payload.list : [];
      }).catch((err) => {
        this.$message.warning(util.getErrorMessage(err, '热点加载失败'));
        this.hotNews = [];
      }).finally(() => {
        this.loading = false;
      });
    },
    refresh() {
      this.loadHotNews();
    },
    hasImage(item) {
      return Boolean(util.normalizeAssetPath(item && item.image));
    },
    resolveImage(path) {
      const normalized = util.normalizeAssetPath(path);
      if (!normalized) {
        return '';
      }
      return util.resolveImageUrl(normalized, '');
    },
    handleImageError(event, item) {
      if (item) {
        this.$set(item, 'image', '');
      }
      if (event && event.target) {
        event.target.removeAttribute('src');
      }
    },
    fallbackBadge(item) {
      const source = ((item && item.source) || this.cityName || '热点').trim();
      return source.length <= 4 ? source : source.slice(0, 4);
    },
    displayTitle(item) {
      const source = ((item && item.source) || '').trim();
      let title = ((item && item.title) || '').trim();
      if (!title) {
        return '本城热点';
      }
      if (source) {
        title = title
          .replace(new RegExp('\\s*[-_–—]\\s*' + source.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$'), '')
          .trim();
      }
      return title;
    },
    previewSummary(item) {
      const summary = ((item && item.summary) || '').trim();
      if (!summary) {
        return '点击查看这条热点新闻的详细内容。';
      }
      const source = ((item && item.source) || '').trim();
      const title = this.displayTitle(item);
      let cleaned = summary
        .replace(/\u00a0/g, ' ')
        .replace(/&nbsp;/gi, ' ')
        .replace(/&ensp;/gi, ' ')
        .replace(/&emsp;/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      if (source) {
        cleaned = cleaned
          .replace(new RegExp(source.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$'), '')
          .trim();
      }
      if (title && cleaned === title) {
        cleaned = '';
      }
      return cleaned || '点击查看这条热点新闻的详细内容。';
    },
    formatDate(value) {
      if (!value) return '刚刚';
      const date = new Date(value);
      if (isNaN(date.getTime())) return '刚刚';
      const diff = Date.now() - date.getTime();
      if (diff < 3600000) return Math.max(1, Math.floor(diff / 60000)) + '分钟前';
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
      return (date.getMonth() + 1) + '月' + date.getDate() + '日';
    },
    openHot(item) {
      if (!item) return;
      if (item.sourceUrl) {
        location.href = item.sourceUrl;
        return;
      }
      const keyword = ((this.cityName || '') + ' ' + (item.title || '')).trim();
      location.href = 'https://news.google.com/search?q=' + encodeURIComponent(keyword) + '&hl=zh-CN&gl=CN&ceid=CN:zh-Hans';
    },
    openMap() {
      location.href = util.buildUrl('/pages/map/map.html', {
        cityCode: this.cityCode,
        city: this.cityName
      });
    },
    goHome() {
      location.href = '/pages/index-new.html';
    },
    goBack() {
      history.back();
    }
  }
});
