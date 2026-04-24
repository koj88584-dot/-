# HMDP Figma 重建检查清单

## 建文件
- 新建 Figma 文件并创建 `00 Foundations` 到 `04 Flows` 五个页面
- 在 `00 Foundations` 建立颜色、圆角、阴影、字体四组 Styles 或 Variables
- 固定默认移动端 Frame 为 `390 x 844`

## 建组件
- 先完成 `Cmp/Header/Base`
- 先完成 `Cmp/Button/Primary`
- 先完成 `Cmp/Input/Text`
- 先完成 `Cmp/Card/Shop`
- 先完成 `Cmp/Nav/Bottom`
- 再扩展 `Cmp/Card/Voucher`、`Cmp/Card/Message`、`Cmp/Card/ProfileHero`

## 建模板
- 用组件先拼 `Tpl/Home`
- 用同一套 Header 和 Button 拼 `Tpl/Auth/Login`
- 复用 `Cmp/Card/Shop` 拼 `Tpl/Shop/List`
- 复用 Tag、ActionBar、VoucherCard 拼 `Tpl/Shop/Detail`
- 复用 Hero 和 BottomNav 拼 `Tpl/User/Profile` 与 `Tpl/Misc/Messages`

## 审核标准
- 同类按钮是否只有一套主样式
- 同类卡片是否只有一套基础圆角和阴影
- 顶部栏是否没有出现多个互相冲突的高度
- 页面是否优先通过组件变体解决差异，而不是复制新图层
- 任意一个新页面是否能通过已有组件快速拼装
