Vue.component("footBar", {
  template: `
    <div class="foot">
      <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
        <div class="foot-view"><i class="el-icon-s-home"></i></div>
        <div class="foot-text">首页</div>
      </div>
      <div class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)">
        <div class="foot-view"><i class="el-icon-map-location"></i></div>
        <div class="foot-text">地图</div>
      </div>
      <div class="foot-box foot-box--add" @click="toPage(0)" aria-label="发布笔记">
        <div class="add-btn"><i class="el-icon-plus"></i></div>
      </div>
      <div class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)">
        <div class="foot-view"><i class="el-icon-chat-dot-round"></i></div>
        <div class="foot-text">消息</div>
      </div>
      <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
        <div class="foot-view"><i class="el-icon-user"></i></div>
        <div class="foot-text">我的</div>
      </div>
    </div>
  `,
  props: ["activeBtn"],
  methods: {
    toPage(i) {
      const routes = {
        0: "/pages/blog/blog-edit.html",
        1: "/pages/index-new.html",
        2: "/pages/map/map.html",
        3: "/pages/misc/messages.html",
        4: "/pages/user/info.html"
      };
      if (!routes[i]) {
        return;
      }
      if (window.authHelper && [0, 3, 4].includes(i)) {
        window.authHelper.navigateWithAuth(routes[i]);
        return;
      }
      if (routes[i]) {
        location.href = routes[i];
      }
    }
  }
})
