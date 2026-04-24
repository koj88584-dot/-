# HMDP 页面盘点与 Figma 映射

## Core Pages
| 页面 | 路径 | Figma 模板 | 优先级 | 主要组件 |
| --- | --- | --- | --- | --- |
| 首页 | `pages/index-new.html` | `Tpl/Home` | P0 | Header, Search, AssistantHero, CategoryGrid, BlogCard, ShopCard, BottomNav |
| 验证码登录 | `pages/auth/login.html` | `Tpl/Auth/Login` | P0 | Header, Input, PrimaryButton, AgreementRow |
| 密码登录 | `pages/auth/login2.html` | `Tpl/Auth/LoginPassword` | P1 | Header, Input, PrimaryButton, LinkRow |
| 注册账号 | `pages/auth/register.html` | `Tpl/Auth/Register` | P1 | Header, Input, PrimaryButton, AgreementRow |
| 店铺列表 | `pages/shop/shop-list.html` | `Tpl/Shop/List` | P0 | Header, SortBar, ShopCard, EmptyState |
| 店铺详情 | `pages/shop/shop-detail.html` | `Tpl/Shop/Detail` | P0 | Header, ImageCarousel, ShopInfoCard, Tag, ActionBar, VoucherCard, CommentCard |

## User Pages
| 页面 | 路径 | Figma 模板 | 优先级 | 主要组件 |
| --- | --- | --- | --- | --- |
| 个人中心 | `pages/user/info.html` | `Tpl/User/Profile` | P1 | Header, ProfileHero, StatsBar, Tab, BlogCard |
| 编辑资料 | `pages/user/info-edit.html` | `Tpl/User/Edit` | P2 | Header, Input, Upload, PrimaryButton |
| 他人主页 | `pages/user/other-info.html` | `Tpl/User/OtherProfile` | P2 | Header, ProfileHero, StatsBar, BlogCard |

## Content Pages
| 页面 | 路径 | Figma 模板 | 优先级 | 主要组件 |
| --- | --- | --- | --- | --- |
| 笔记详情 | `pages/blog/blog-detail.html` | `Tpl/Blog/Detail` | P1 | StoryCard, CommentCard, BottomComposer |
| 发布笔记 | `pages/blog/blog-edit.html` | `Tpl/Blog/Edit` | P2 | Header, UploadGrid, SearchBar, ShopListItem |
| 我的订单 | `pages/order/orders.html` | `Tpl/Order/List` | P1 | Header, SummaryCard, Tab, OrderCard |
| 优惠券 | `pages/misc/vouchers.html` | `Tpl/Misc/Vouchers` | P1 | Header, VoucherCard, SectionTitle |

## Utility Pages
| 页面 | 路径 | Figma 模板 | 优先级 | 主要组件 |
| --- | --- | --- | --- | --- |
| 搜索 | `pages/misc/search.html` | `Tpl/Search` | P1 | CompactSearch, ResultCard, HistoryList |
| 消息中心 | `pages/misc/messages.html` | `Tpl/Misc/Messages` | P1 | Header, AssistantHero, MessageCard, EmptyState, BottomNav |
| 我的收藏 | `pages/misc/favorites.html` | `Tpl/Misc/Favorites` | P1 | Header, FavoriteCard, EmptyState |
| 浏览历史 | `pages/misc/history.html` | `Tpl/Misc/History` | P2 | Header, HistoryListItem, EmptyState |
| 关注列表 | `pages/misc/follow-list.html` | `Tpl/Misc/FollowList` | P2 | Header, UserListItem, EmptyState |
| 隐私设置 | `pages/misc/privacy.html` | `Tpl/Misc/Privacy` | P2 | Header, SettingsRow, Toggle |
| 智能助手 | `pages/misc/assistant.html` | `Tpl/Misc/Assistant` | P1 | Header, AssistantHero, SuggestionChip, ChatMessage, Composer |
| 地图找店 | `pages/map/map.html` | `Tpl/Map` | P1 | FloatingSearch, MapShopItem, BottomSheetList |

## Figma 首批重建范围
第一批只做下面 6 个模板：

1. `Tpl/Home`
2. `Tpl/Auth/Login`
3. `Tpl/Shop/List`
4. `Tpl/Shop/Detail`
5. `Tpl/User/Profile`
6. `Tpl/Misc/Messages`

这些模板覆盖了当前仓库最主要的导航、搜索、卡片、表单、列表和状态模式，足够支撑后续页面继续复用。
