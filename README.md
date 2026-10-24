# 澎湃商店净化

面向 HyperOS 4 小米应用商店（`com.xiaomi.market`）的 LSPosed 模块，实现 `SPEC.md`
中已确认的十项界面净化与交互开关；其中九项默认开启，「隐藏榜单导航」默认关闭。

## 目标版本

- 宿主：`com.xiaomi.market` `4.129.s.00`（versionCode `40007700`）
- 基线设备：Xiaomi 14，HyperOS `OS4.0.18.0.XNCCNXM`
- 模块 ID：`com.dagu.hyperstorehelper`
- 模块版本：`1.10.0`（versionCode `11`）
- 日志 tag：`HyperStore-AdFree`
- Xposed API：102

## 功能列表

- 首页移除「送水果 / 种果树」入口（默认开启）
- 首页顶部仅保留「金米奖、软件、分类」（默认开启）
- 底部移除「游戏」导航并固定正常启动进入首页（默认开启）
- 「我的」页面移除「应用推荐」（默认开启）
- 去除主页面启动广告（默认开启）
- 榜单游戏榜去除广告排名（默认开启）
- 下载队列页去除「最近安装完成」「大家还安装了」「为你优选」（默认开启）
- 隐藏底部「榜单」导航（默认关闭）
- 应用升级页去除「精选推荐」「热门下载」「大家还安装了」（默认开启）
- 自动展开应用升级列表（默认开启）

「我的 → 设置」中的独立设置页展示全部十项开关，右上角提供重启应用按钮。

## 构建

```bash
export JAVA_HOME=/path/to/standard-jdk-17
./gradlew :app:assembleDebug
```

需要标准 OpenJDK / Corretto 17。部分 GraalVM 17 自带的 `jlink` 无法完成 AGP 9.4 的
Android 36 JDK image 转换，会表现为 `JdkImageTransform` 失败；这不是源码错误。

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 当前验证状态

- 已按 APK `4.129.s.00`（versionCode `40007700`）的 DEX 结果实现十项功能 Hook。
- 已通过 `:app:assembleDebug`，并已安装到真机；设置入口、独立设置页、右上角重启图标
  和重启动作已在 `4.129.s.00` 上确认。
- F1–F4、F7–F9 的关键命中日志已抓取；F8、F9 已由用户在 `4.129.s.00` 上确认效果。
- 榜单首次布局的性能实验已按用户要求撤回；F10 默认关闭，并已由用户在 `4.129.s.00`
  真机确认开关打开/关闭、重启生效和首页回退。
- `1.9.0` 已实现「我的」页预加载和底部导航动画修正；底部导航动画仍需用户手动确认。
- `1.10.0` 已实现应用升级页推荐区块移除和升级列表自动展开，并已抓取 F11/F12 命中日志。
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
