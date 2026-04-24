# HMDP Figma Handoff

## 目的
这份文档把当前 `hmdp-frontend` 的页面结构、视觉模式和组件复用关系整理成一份可直接在 Figma 中执行的重建说明。目标不是把 HTML 原样搬到 Figma，而是提炼出一套可编辑、可复用、可继续美化的移动端 UI 资产。

## Figma 文件结构
建议建立一个 Figma 文件，并固定使用以下页面：

1. `00 Foundations`
2. `01 Components`
3. `02 Core Pages`
4. `03 User Pages`
5. `04 Flows`

## Frame 规范
- 默认移动端画板：`390 x 844`
- 内容左右边距：`16`
- 组件垂直间距：`12 / 16 / 20 / 24`
- 底部固定导航预留：`60`
- 顶部固定导航常规高度：`56`

## Foundations
统一使用下面这组 token 作为 Figma Variables 和 Styles 的起点，后续页面全部复用，不再按页面各自定义。

### Color
- `Primary / 500`: `#FF6B35`
- `Primary / 400`: `#FF8C61`
- `Primary / 600`: `#E55A2B`
- `Accent / 500`: `#00B894`
- `Surface / Base`: `#FFFFFF`
- `Surface / Subtle`: `#F8F9FA`
- `Text / Primary`: `#1F2937`
- `Text / Secondary`: `#667085`
- `Text / Muted`: `#98A2B3`
- `Border / Subtle`: `#F0F0F0`
- `Status / Danger`: `#FF4D4F`
- `Status / Success`: `#52C41A`
- `Status / Info`: `#1890FF`
- `Status / Warning`: `#FA8C16`

### Radius
- `Radius / S`: `8`
- `Radius / M`: `12`
- `Radius / L`: `16`
- `Radius / XL`: `24`
- `Radius / Pill`: `999`

### Shadow
- `Shadow / S`: `0 2 8 0 rgba(0,0,0,0.06)`
- `Shadow / M`: `0 4 20 0 rgba(0,0,0,0.08)`
- `Shadow / L`: `0 8 40 0 rgba(0,0,0,0.12)`
- `Shadow / Brand`: `0 12 28 0 rgba(255,102,51,0.24)`

### Typography
- 字体族：`Noto Sans SC`，后备 `PingFang SC`, `Microsoft YaHei`, `sans-serif`
- `Display / L`: `28 / 36 / 700`
- `Heading / L`: `24 / 32 / 700`
- `Heading / M`: `20 / 28 / 700`
- `Title / M`: `18 / 26 / 600`
- `Body / M`: `14 / 22 / 400`
- `Body / S`: `13 / 20 / 400`
- `Caption`: `12 / 18 / 500`

## Components
以下组件直接来源于现有页面，建议在 `01 Components` 中单独建成 Component Set。

### App Shell
- `Cmp/Header/Base`
  来源：`pages/auth/login.html`、`pages/shop/shop-list.html`、`pages/user/info.html`
  变体：`back + title`、`back + title + action`、`back + title + search`
- `Cmp/Nav/Bottom`
  来源：`css/main.css`、`pages/index-new.html`、`pages/misc/messages.html`
  变体：`default`、`active tab`

### Search
- `Cmp/Search/Primary`
  来源：`pages/index-new.html`
  内容：城市选择、搜索输入、圆形搜索按钮
- `Cmp/Search/Compact`
  来源：`pages/misc/search.html`、`pages/map/map.html`
  内容：返回、输入框、执行按钮

### Form
- `Cmp/Input/Text`
  来源：`pages/auth/login.html`、`pages/auth/register.html`
- `Cmp/Button/Primary`
  来源：`pages/auth/login.html`
- `Cmp/Button/Secondary`
  用于次操作和筛选操作
- `Cmp/Agreement/CheckboxRow`
  来源：`pages/auth/login.html`

### Card
- `Cmp/Card/Shop`
  来源：`pages/shop/shop-list.html`
  内容：封面、标题、评分、标签、价格、距离
- `Cmp/Card/Blog`
  来源：`pages/index-new.html`、`pages/user/info.html`
- `Cmp/Card/Voucher`
  来源：`pages/shop/shop-detail.html`、`pages/misc/vouchers.html`
- `Cmp/Card/Message`
  来源：`pages/misc/messages.html`
- `Cmp/Card/ProfileHero`
  来源：`pages/user/info.html`
- `Cmp/Card/AssistantHero`
  来源：`pages/index-new.html`、`pages/misc/assistant.html`

### List Item
- `Cmp/List/User`
  来源：`pages/misc/follow-list.html`
- `Cmp/List/History`
  来源：`pages/misc/history.html`
- `Cmp/List/Result`
  来源：`pages/misc/search.html`
- `Cmp/List/MapShop`
  来源：`pages/map/map.html`

### State
- `Cmp/State/Empty`
  来源：`pages/misc/messages.html`、`pages/misc/favorites.html`
- `Cmp/State/Loading`
  使用骨架块，不复刻 Element UI spinner
- `Cmp/Badge/Unread`
  来源：`pages/misc/messages.html`
- `Cmp/Chip/Tag`
  来源：`pages/shop/shop-detail.html`、`pages/index-new.html`

## Template Pages
优先重建以下模板页，每个模板页都只用上面的通用组件拼装，不再单独发明视觉语言。

### Tpl/Home
来源：`pages/index-new.html`

区块顺序：
1. 顶部导航
2. AI 助手展示卡
3. 分类宫格
4. 推荐笔记流
5. 店铺推荐卡片流
6. 底部导航

### Tpl/Auth/Login
来源：`pages/auth/login.html`

区块顺序：
1. 顶部返回栏
2. 登录主卡片
3. 手机号输入
4. 验证码输入与按钮
5. 主按钮
6. 辅助链接
7. 协议勾选

### Tpl/Auth/Register
来源：`pages/auth/register.html`

区块顺序与登录页保持一致，只替换文案和状态。

### Tpl/Shop/List
来源：`pages/shop/shop-list.html`

区块顺序：
1. 顶部栏
2. 排序栏
3. 店铺卡片列表
4. 空状态

### Tpl/Shop/Detail
来源：`pages/shop/shop-detail.html`

区块顺序：
1. 顶部栏
2. 头图轮播
3. 店铺信息卡
4. 标签与榜单块
5. 操作按钮栏
6. 优惠券卡片
7. 评论区
8. 底部操作区

### Tpl/User/Profile
来源：`pages/user/info.html`

区块顺序：
1. 顶部栏
2. 用户资料 Hero
3. 统计栏
4. 操作入口组
5. 内容 tab
6. 卡片流

### Tpl/Misc/Messages
来源：`pages/misc/messages.html`

区块顺序：
1. 顶部栏
2. 助手推广卡
3. 工具栏
4. 消息卡片列表
5. 空状态
6. 底部导航

## Figma 搭建顺序
按下面顺序执行，避免文件一开始就失控：

1. 先建立 `00 Foundations`
2. 再建立 `Cmp/Header`、`Cmp/Button`、`Cmp/Card/Shop`、`Cmp/Nav/Bottom`
3. 用组件拼出 `Tpl/Home`
4. 再拼 `Tpl/Auth/Login` 和 `Tpl/Shop/Detail`
5. 最后扩展到用户页、消息页、搜索页

## 命名规范
- 组件：`Cmp/<Group>/<Name>`
- 模板：`Tpl/<Group>/<Name>`
- 流程：`Flow/<Goal>`
- 图层：优先使用语义名，例如 `shop-title`、`price-tag`、`action-primary`

## 实施规则
- 不把整页截图直接当成最终稿，只能作为参考底图
- 每个模板页至少复用 70% 以上已有组件
- 同类页面保持同一套顶部栏、卡片、按钮尺寸
- 视觉升级优先通过 token 和组件完成，不通过页面私有样式堆砌
- 新页面先画低保真，再补高保真，不反过来
