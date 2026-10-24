# 澎湃商店净化

面向 HyperOS 4 小米应用商店（`com.xiaomi.market`）的 LSPosed 模块，实现
`SPEC.md` 中已确认的七项净化。当前只移除主页面启动广告，不处理信息流、搜索、
应用详情页广告、更新弹窗、红点或其它入口。

## 目标版本

- 宿主：`com.xiaomi.market` `4.129.s.00`（versionCode `40007700`）
- 基线设备：Xiaomi 14，HyperOS `OS4.0.18.0.XNCCNXM`
- 模块 ID：`com.dagu.hyperstorehelper`
- 日志 tag：`HyperStore-AdFree`
- Xposed API：102

## 功能与 Hook

| 功能 | Hook | 默认 |
| --- | --- | --- |
| 首页移除送水果 / 种果树入口 | `com.xiaomi.market.ui.entrance.EntranceView.bindView(Context, String)` | 开启 |
| 首页顶部仅保留金米奖、软件、分类 | `com.xiaomi.market.ui.PagerTabsInfo.fromTabInfo(TabInfo)` | 开启 |
| 底部移除游戏导航 | `com.xiaomi.market.model.PageConfig.updateTabs(boolean)` | 开启 |
| 「我的」移除应用推荐 | `com.xiaomi.market.business_ui.main.mine.NativeMinePagerFragment.bindAdFragmentView(int)` | 开启 |
| 正常启动固定进入首页 | `com.xiaomi.market.ui.DoubleTabProxyActivityWrapper.initTab()` / `onNewIntent(Intent)` | 开启 |
| 修正深色模式状态栏图标 | `DoubleTabProxyActivityWrapper.onResume()` / `onNewIntent(Intent)` / `onTabSelected(int, int)` | 开启 |
| 去除启动广告 | `com.xiaomi.market.ui.splash.SplashManager$SplashTask.run()` | 开启 |

顶部 Tab 使用服务端真实 tag 白名单排序：`GOLDEN MI AWARD`、`software_sub5`、
`Classification`。Hook 会同步过滤 `tags`、`titles`、`urls`、`abNormals` 和
`tabInfos`，并在原选中项被移除时回退到首个可用 Tab。底部导航只替换
`PageConfig.tabs`，不会修改 `originTabs`，也不会阻断从其它入口打开游戏页面。

F5 只对 `ACTION_MAIN + CATEGORY_LAUNCHER`、无 URI 且不含路由参数的正常启动生效。
它在页面初始化和 Launcher 重入时改为选择 `native_market_home`，并覆盖宿主从
`selected_tab_index` 恢复旧 Tab 的路径；深链、通知和带 `tag` 的入口保持原目标。

F6 在页面恢复、Launcher 重入和底部 Tab 切换后读取宿主实际深色模式，并调用商店现有
状态栏方法同步图标明暗，避免深色背景继续显示黑色状态栏图标。

F7 只取消主页面 `MarketTabActivity` 的启动广告任务，不创建广告请求，并复用宿主
`onSplashEnd` 完成临时封面与监听器清理。
应用详情页被动开屏广告、信息流和搜索广告不经过该主页面闸门，因此保持宿主行为；
普通启动封面、隐私/用户协议、首启推荐和页面路由不被主动删除或改写。

JADX 因类冲突会把部分包名显示为 `p108ui`、`p103ad`、`p104v2`；这些不是运行时类名。
本模块使用的都是 APK DEX 中实际存在的 `com.xiaomi.market.ui.*` 等名称。

设置入口注入「我的 → 设置」页中的「澎湃商店净化」分组。开关保存在宿主私有数据目录的
`hyper_store_helper` SharedPreferences 中。每项可独立关闭；为让已创建页面恢复宿主原始行为，
修改后建议重启商店。

## 构建

```bash
export JAVA_HOME=/path/to/standard-jdk-17
./gradlew :app:assembleDebug
```

需要标准 OpenJDK / Corretto 17。部分 GraalVM 17 自带的 `jlink` 无法完成 AGP 9.4 的
Android 36 JDK image 转换，会表现为 `JdkImageTransform` 失败；这不是源码错误。

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 当前验证状态

- 已按 APK `4.129.s.00`（versionCode `40007700`）的 DEX 结果实现七个功能 Hook。
- 已通过 `:app:assembleDebug` 和 `:app:lintDebug`，APK 内未打包宿主或 AndroidX 类。
- 尚待安装到真机并完成 LSPosed 作用域、前后对照、冷启动和刷新回归验证。
- 在完成真机验证前，不应把任何功能标记为「已验证生效」。

本机拒绝 ADB 模拟点击/滑动（`INJECT_EVENTS permission`）。验证过程中需要切换页面、展开设置
或点击开关时，必须由用户手动操作；ADB 只用于启动页面、抓取 UI 层级、截图和日志。

安装后需由用户在 LSPosed 管理器中启用「澎湃商店净化」并勾选 `com.xiaomi.market`，然后重启
应用商店。设置页中的开关变更同样需要重启商店后才会应用到已创建的页面。

日志过滤：

```bash
adb logcat -v threadtime | grep -E 'HyperStore-AdFree|AndroidRuntime|FATAL EXCEPTION'
```
