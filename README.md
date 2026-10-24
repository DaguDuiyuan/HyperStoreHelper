# 澎湃商店净化

面向 HyperOS 4 小米应用商店（`com.xiaomi.market`）的 LSPosed 模块，实现
`SPEC.md` 中已确认的七项默认净化，以及一个默认关闭的「隐藏榜单导航」开关。当前只移除
主页面启动广告，不处理信息流、搜索、
应用详情页广告、更新弹窗、红点或其它入口。

## 目标版本

- 宿主：`com.xiaomi.market` `4.129.s.00`（versionCode `40007700`）
- 基线设备：Xiaomi 14，HyperOS `OS4.0.18.0.XNCCNXM`
- 模块 ID：`com.dagu.hyperstorehelper`
- 模块版本：`1.9.0`（versionCode `10`）
- 日志 tag：`HyperStore-AdFree`
- Xposed API：102

## 功能与 Hook

| 功能 | Hook | 默认 |
| --- | --- | --- |
| 首页移除送水果 / 种果树入口 | `com.xiaomi.market.ui.entrance.EntranceView.bindView(Context, String)` | 开启 |
| 首页顶部仅保留金米奖、软件、分类 | `com.xiaomi.market.ui.PagerTabsInfo.fromTabInfo(TabInfo)` | 开启 |
| 底部移除游戏导航并固定启动首页 | `PageConfig.updateTabs(boolean)` / `DoubleTabProxyActivityWrapper.initTab()` / `onNewIntent(Intent)` | 开启 |
| 隐藏底部榜单导航 | `PageConfig.updateTabs(boolean)` | 关闭 |
| 「我的」移除应用推荐 | `com.xiaomi.market.business_ui.main.mine.NativeMinePagerFragment.bindAdFragmentView(int)` | 开启 |
| 去除启动广告 | `SplashManager.tryAdSplash(Activity, String)` / `SplashManager$SplashTask.run()` | 开启 |
| 游戏榜去除广告排名 | `RankTabFragment.tryAddRank(int, List, boolean)` / `tryFilter(List)` | 开启 |
| 下载队列页去除推荐区块 | `DownloadListFragment.setRecommendGroup(List)` / `addUnActiveApp(String)` | 开启 |

顶部 Tab 使用服务端真实 tag 白名单排序：`GOLDEN MI AWARD`、`software_sub5`、
`Classification`。Hook 会同步过滤 `tags`、`titles`、`urls`、`abNormals` 和
`tabInfos`，并在原选中项被移除时回退到首个可用 Tab。底部导航只替换
`PageConfig.tabs`，不会修改 `originTabs`，也不会阻断从其它入口打开游戏页面。

底部游戏导航和启动首页由同一个开关 `helper_bottom_nav_games` 控制。启动首页逻辑只对
`ACTION_MAIN + CATEGORY_LAUNCHER`、无 URI 且不含路由参数的正常启动生效；它在页面初始化和
Launcher 重入时改为选择 `native_market_home`，并覆盖宿主从 `selected_tab_index` 恢复旧
Tab 的路径；深链、通知和带 `tag` 的入口保持原目标。

F7 在主页面 `MarketTabActivity` 的 `SplashManager.tryAdSplash` 入口直接取消启动广告，
不创建 `SplashTask`、不发起广告请求，也不等待广告超时；随后在首帧后用宿主
`hideSplashCover(Context, boolean)` 清理广告临时封面，避免冷启动时封面等待广告回调。
应用详情页被动开屏广告、信息流和搜索广告不经过该主页面闸门，因此保持宿主行为；
普通启动封面、隐私/用户协议、首启推荐和页面路由不被主动删除或改写。

F8 只在「榜单 → 游戏榜」生效：按宿主 `BaseAppDataBean.isShowAdTag()` 识别广告条目，在排名编号前
过滤并重新连续编号；软件榜和其它榜单页面保持宿主行为。

F9 只在下载队列页生效：过滤 `recPosition=installRecord` / `recForYou` 推荐分组，并抑制本地
「最近安装完成」模块，保留真实下载任务及其暂停、继续、删除和安装操作。

交互性能优化不增加开关：

- 底部导航点击：`BottomTabLayoutMiux4.performTabSwitch(int)` 标记用户点击作用域，
  `BottomNavigator.v0(int, boolean)` 在该作用域内把 `false` 恢复为 `true`，避免
  `OverlayView.setTo()` 截断按下时启动的弹簧动画。
- 「我的」页首次进入：`BottomTabLayoutMiux4.addFragmentTab(TabInfo, FragmentInfo)`
  注册 `native_market_mine` 时设置 `TabInfo.needPreload=true`，交给宿主原有的
  IdleHandler 预加载路径提前创建页面。

F10 默认关闭。打开后只在 `PageConfig.tabs` 中移除 `native_market_rank`，不会删除榜单页面、
业务路由或其它入口；若宿主恢复的选中项正好是榜单，会回退到首页。

JADX 因类冲突会把部分包名显示为 `p108ui`、`p103ad`、`p104v2`；这些不是运行时类名。
本模块使用的都是 APK DEX 中实际存在的 `com.xiaomi.market.ui.*` 等名称。

「我的 → 设置」页只注入一个「澎湃商店净化」入口，点击后进入独立的净化设置页。该页展示
八项独立开关，标题为「澎湃商店净化」，右上角提供重启应用图标按钮。开关保存在宿主私有
数据目录的 `hyper_store_helper` SharedPreferences 中；每项可独立关闭，修改后点击重启
图标重新打开商店首页。状态栏图标修正功能已按用户确认移除。

## 构建

```bash
export JAVA_HOME=/path/to/standard-jdk-17
./gradlew :app:assembleDebug
```

需要标准 OpenJDK / Corretto 17。部分 GraalVM 17 自带的 `jlink` 无法完成 AGP 9.4 的
Android 36 JDK image 转换，会表现为 `JdkImageTransform` 失败；这不是源码错误。

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 当前验证状态

- 已按 APK `4.129.s.00`（versionCode `40007700`）的 DEX 结果实现八项功能 Hook。
- 已通过 `:app:assembleDebug`，并已安装到真机；设置入口、独立设置页、右上角重启图标
  和重启动作已在 `4.129.s.00` 上确认。
- F1–F4、F7–F9 的关键命中日志已抓取；F8、F9 已由用户在 `4.129.s.00` 上确认效果。
- 榜单首次布局的性能实验已按用户要求撤回；F10 默认关闭，并已由用户在 `4.129.s.00`
  真机确认开关打开/关闭、重启生效和首页回退。
- `1.9.0` 已实现「我的」页预加载和底部导航动画修正；底部导航动画仍需用户手动确认。
- 搜索、下载、安装、更新、卸载、重启按钮和完整前后台回归仍需继续验收。
- 在完成完整真机验收前，不应把任何功能标记为「已验证生效」。

本机拒绝 ADB 模拟点击/滑动（`INJECT_EVENTS permission`）。验证过程中需要切换页面、展开设置
或点击开关时，必须由用户手动操作；ADB 只用于启动页面、抓取 UI 层级、截图和日志。

安装后需由用户在 LSPosed 管理器中启用「澎湃商店净化」并勾选 `com.xiaomi.market`，然后重启
应用商店。独立设置页中的开关变更需点击右上角重启图标，或手动强制停止商店后重新打开。

日志过滤：

```bash
adb logcat -v threadtime | grep -E 'HyperStore-AdFree|AndroidRuntime|FATAL EXCEPTION'
```
