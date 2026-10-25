# 澎湃商店净化

面向 HyperOS 4 小米应用商店（`com.xiaomi.market`）的 LSPosed 模块，实现 `SPEC.md`
中已确认的十一项界面净化与交互开关；其中十项默认开启，「隐藏榜单导航」默认关闭。
另包含金米奖页面 H5 数据缓存优化，以及固定生效、不提供开关的状态栏图标明暗修正。

## 目标版本

- 宿主：`com.xiaomi.market` `4.129.s.00`（versionCode `40007700`）
- 基线设备：Xiaomi 14，HyperOS `OS4.0.18.0.XNCCNXM`
- 模块 ID：`com.dagu.hyperstorehelper`
- 模块版本：`1.13.2`（versionCode `17`）
- 日志 tag：`HyperStore-AdFree`
- Xposed API：102

## 功能列表

- 首页顶部及应用详情页右上角移除「送水果 / 种树」入口（共用同一个开关）
- 首页顶部仅保留「金米奖、软件、分类」（默认开启）
- 底部移除「游戏」导航并固定正常启动进入首页（默认开启）
- 「我的」页面移除「应用推荐」（默认开启）
- 去除主页面启动广告（默认开启）
- 榜单游戏榜去除广告排名（默认开启）
- 下载队列页及升级记录页去除推荐区块（默认开启，共用同一个开关）
- 隐藏底部「榜单」导航（默认关闭）
- 应用升级页去除「精选推荐」「热门下载」「大家还安装了」（默认开启）
- 自动展开应用升级列表（默认开启）
- 搜索列表去除广告排名（默认开启；不含无结果填充广告）
- 金米奖页面 H5 数据缓存（默认生效，不提供独立开关）
- 修正深色/浅色模式下的状态栏图标明暗（固定生效，不提供独立开关）

「我的 → 设置」中的独立设置页展示全部十一项开关，右上角提供重启应用按钮。

## 构建

```bash
export JAVA_HOME=/path/to/standard-jdk-17
./gradlew :app:assembleDebug
```

## 当前验证状态

- 已按 APK `4.129.s.00`（versionCode `40007700`）的 DEX 结果实现十一项功能 Hook。
- 已通过 `:app:assembleDebug`，并已安装到真机；设置入口、独立设置页、右上角重启图标
  和重启动作已在 `4.129.s.00` 上确认。
- F1–F4、F7–F9 的关键命中日志已抓取；F8、F9 已由用户在 `4.129.s.00` 上确认效果。
- 榜单首次布局的性能实验已按用户要求撤回；F10 默认关闭，并已由用户在 `4.129.s.00`
  真机确认开关打开/关闭、重启生效和首页回退。
- `1.9.0` 已实现「我的」页预加载和底部导航动画修正；底部导航动画仍需用户手动确认。
- `1.10.0` 已实现应用升级页推荐区块移除和升级列表自动展开，并已抓取 F11/F12 命中日志。
- `1.11.0` 已实现金米奖 `goldMiV2` H5 缓存，命中日志确认缓存响应约 12ms 返回。
- `1.12.0` 已加回状态栏图标修正：在 `DoubleTabProxyActivityWrapper` 的恢复、重入和
  Tab 切换路径中读取 `Client.isEnableDarkMode(Context)`，并调用宿主
  `UIUtils.setStatusBarDarkModeV2(Activity, boolean)`（缺失时回退到旧方法）。
  该修正固定启用，不提供开关，仍待真机确认。
- `1.12.1` 修复金米奖 H5 缓存：仅匹配 `/apm/zone/goldMiV2`，并将日期、URL 和完整业务
  参数纳入缓存 key，避免首次打开黑屏以及不同请求/分页读到同一份缓存。待真机确认。
- `1.13.0` 新增搜索列表广告清理：在 `NativeSearchResultFragment.parseResponseData(...)`
  返回前移除宿主显式搜索广告组件及 `BaseAppDataBean.isShowAdTag()` 标记的广告条目。
  该功能默认开启，待真机确认。
- `1.13.1` 将应用详情页右上角种树入口纳入 `helper_home_fruit_entry`：通过
  `DetailOrchardIconController.shouldShowEntry(...)` 与首页入口同步隐藏。待真机确认。
- `1.13.2` 扩展 `helper_download_recommendations`：升级记录页的「精选推荐」「热门下载」
  「大家还安装了」与下载队列推荐区块共用同一个开关。待真机确认。
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
