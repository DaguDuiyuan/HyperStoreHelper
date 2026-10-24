# AGENTS.md

本仓库用于开发「澎湃 OS 4 商店净化」LSPosed 模块。所有后续逆向、编码和真机验证都必须遵守本文件。

## 项目目标

- 宿主固定为小米应用商店（GetApps）：`com.xiaomi.market`。
- 在不修改、不重签、不重打包宿主 APK 的前提下，通过 LSPosed 进程内 hook 实现 `SPEC.md` 中确认的界面净化需求。
- 必须保留搜索、下载、安装、卸载、手动检查更新、登录、账户和支付等核心功能。
- 模块默认全开，每个净化项必须能够独立关闭；功能开关状态保存在宿主私有数据目录。
- 模块自身不启动常驻服务，不劫持网络，不修改系统设置。

`SPEC.md` 是功能需求的唯一来源。不得根据「净化」一词自行扩大移除范围。

## 当前基线

采集日期：2026-10-24（Asia/Shanghai）。

| 项目 | 值 |
| --- | --- |
| 真机 | Xiaomi 14 / `houji` / `23127PN0CC` |
| ROM | HyperOS `OS4.0.18.0.XNCCNXM` |
| Android | 17 / SDK 37 / 安全补丁 2026-09-01 |
| 商店包名 | `com.xiaomi.market` |
| 商店版本 | `4.129.s.00` |
| versionCode | `40007700` |
| 宿主 min/target SDK | 24 / 35 |
| 安装形态 | 单一 `base.apk`，`splits=[base]`，可更新系统应用 |
| 启动入口 | `com.xiaomi.market/.ui.DefaultLauncherIcon` |
| 测试框架 | KernelSU + Zygisk LSPosed（需在 LSPosed 管理器手动勾选作用域） |
| 真机交互 | 无法用 adb 模拟点击/滑动，必须请用户手动操作 |

本地逆向样本：

```text
artifacts/com.xiaomi.market/4.129.s.00/base.apk
size:   60672223 bytes
sha256: bb35fb49833309548a36ae1528a047794fca49680eda132f4a0d9f30a5c0b80b
signer: SHA-256 c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025
```

APK 是签名 v2/v3 的小米系统签名包。不要把 `artifacts/`、反编译产物或构建产物提交到版本库。

复核或重新拉包时使用以下流程；版本变化后必须记录新版本，不能悄悄覆盖基线结论：

```bash
adb shell pm path com.xiaomi.market
adb shell dumpsys package com.xiaomi.market | grep -E 'versionCode=|versionName='
adb pull '<pm path 输出的 apk 路径>' \
  artifacts/com.xiaomi.market/<versionName>/base.apk
shasum -a 256 artifacts/com.xiaomi.market/<versionName>/base.apk
```

如果以后出现 split APK，必须按 `pm path` 输出把全部 APK 一起拉取，并记录每个文件的作用。

## 当前功能范围

当前实现 `SPEC.md` 定义的八项功能；F1–F4、F7–F9 为 P0 且默认开启，F10 默认关闭。
八项均必须独立可控：

| ID | 功能 | 开关键 |
| --- | --- | --- |
| `F1` | 首页顶部去除「送水果」入口 | `helper_home_fruit_entry` |
| `F2` | 首页顶部 Tab 仅保留「金米奖、软件、分类」，并按规定顺序排列 | `helper_home_top_tabs` |
| `F3` | 底部导航栏去除「游戏」，并让正常启动固定进入首页 | `helper_bottom_nav_games` |
| `F4` | 「我的」页面去除「应用推荐」 | `helper_profile_app_recommendations` |
| `F7` | 去除主页面启动广告 | `helper_startup_ads` |
| `F8` | 榜单游戏榜去除广告排名 | `helper_game_rank_ads` |
| `F9` | 下载队列页去除「最近安装完成」「大家还安装了」「为你优选」 | `helper_download_recommendations` |
| `F10` | 隐藏底部导航「榜单」Tab（默认关闭） | `helper_hide_rank_tab` |

信息流广告、搜索广告、应用详情页被动开屏广告、更新弹窗和红点等仍不在当前范围。任何新增功能必须先修改 `SPEC.md` 并取得确认。

## 技术方案

参考工程：`/Volumes/disk3/test/miui_music_helper`。该目录只读，不要修改。

沿用参考工程中已验证的结构：

```text
app/src/main/java/com/dagu/hyperstorehelper/
    Main.java                 模块入口，只做 hook 装配
    Host.java                 包名、类名、方法名、资源名集中管理
    Reflect.java              空安全的反射工具
    core/                     BaseHook、Feature、HelperPrefs
    hooks/                    每个净化项一个独立 Hook
    settings/                宿主设置页入口、独立净化设置页和重启按钮
app/src/main/resources/META-INF/xposed/
    java_init.list            API 102 入口
    module.prop               minApiVersion=102 / targetApiVersion=102 / staticScope=true
    scope.list                只写 com.xiaomi.market
```

模块标识约定：

- `applicationId` / Java 根包：`com.dagu.hyperstorehelper`
- 模块名：`澎湃商店净化`
- 日志 tag：`HyperStore-AdFree`
- `scope.list`：`com.xiaomi.market`
- 不注册 launcher Activity。宿主设置页只注入一个入口，进入独立净化设置页；该页右上角提供重启图标。
- 开关文件名：`hyper_store_helper`；未写入或读取失败时一律按「开启」处理。

Hook 实现必须遵守：

1. 先判断 `param.getPackageName().equals(Host.PKG)`，模块不得在其它进程产生副作用。
2. 优先调用宿主自己的开关或路由；其次过滤/重排数据源；再次隐藏或移除 View；最后才用页面兜底跳转。
3. 每个功能放到独立的 `BaseHook` 子类中，一组 hook 安装失败只打 `WARN` 并继续安装其它组。
4. 类、方法、字段找不到时必须静默 no-op，不允许抛异常导致宿主启动失败。
5. 混淆名集中写在 `Host.java`。对关键方法准备多个候选签名，按当前版本的实际 DEX 结果取用。
6. 不按文案全局搜索 View；不要 hook 微信、QQ、系统服务或商店 SDK 提供的公共包。
7. 不阻塞全部网络请求，不篡改下载包、签名或安装校验。
8. UI 修改必须幂等，兼容 RecyclerView 复用、页面重建和深浅色切换。
9. 每个 hook 至少记录一条命中日志和一条类/方法缺失时的 `WARN` 日志，便于 `adb logcat` 定位。
10. F3 的启动首页逻辑只处理 `ACTION_MAIN + CATEGORY_LAUNCHER` 且没有 URI、`tag`、`subTag`、`extra_tab` 的正常启动；不得改变深链、通知或定向快捷入口的目标。
11. F7 只拦截 `MarketTabActivity` 主页面启动广告任务；不得影响应用详情页被动开屏广告、信息流、搜索广告和页面路由。
12. F7 不得主动删除普通启动封面、品牌画面、隐私/用户协议或首启推荐；广告任务取消后沿用宿主清理流程。
13. 设置页只提供一个净化入口，独立页面展示八项开关；重启按钮仅重新打开商店首页，不修改宿主数据、签名或账户状态。
14. F8 只处理游戏榜广告排名，按宿主 `BaseAppDataBean.isShowAdTag()` 识别并在排名前过滤；不得影响软件榜、其它榜单、下载或详情页。
15. F9 只移除下载队列页的 `installRecord` / `recForYou` 推荐分组和本地「最近安装完成」模块，不得删除真实下载任务或阻断下载/安装操作。

构建基线：

- JDK 17（标准 OpenJDK / Corretto；GraalVM 17 的 `jlink` 可能无法完成 AGP 9.4 的 Android 36 JDK image 转换）
- Gradle 9.7.1、AGP 9.4.0
- Kotlin 2.4.10（仅在确实需要 Kotlin 时启用）
- `minSdk = 26`、`compileSdk = 36`、`targetSdk = 36`
- Xposed API：`compileOnly("io.github.libxposed:api:102.0.0")`
- 需要 Compose 或其它宿主库时只能 `compileOnly`，且版本必须从当前宿主 APK 中确认；禁止把另一套 Compose/Kotlin runtime 打进宿主进程。

## 逆向与定位流程

1. 先用 `aapt2 dump badging` / `aapt2 dump xmltree` 确认包名、版本、入口和组件。
2. 用 jadx 反编译到 `build/`，搜索结果时同时看 DEX、资源名和真实 UI；商店资源名和类名可能被混淆。
3. 用 `adb shell am force-stop com.xiaomi.market` 做干净冷启动，用 `uiautomator dump`、截图和 `dumpsys activity` 记录复现路径。
4. 用 `adb logcat` 找首页入口、顶部 Tab、底部导航和「我的」页面的配置/渲染入口及数据加载日志。
5. 先写最小 hook 在真机验证单点行为，再抽成功能类；不要一次改完所有页面。
6. 每项功能都要做「有模块 / 禁用模块」的前后对照，并验证相邻正常功能没有回归。

当前 APK 有 5 个 `classes*.dex`、大量混淆资源名和 native 库，包括 `libijiami_1011`、`libkcg`、`libbytehook`、`libshadowhook`、`libxcrash`、`libpatcherV*`；manifest 还会查询 Xposed/Substrate。必须把它当作带保护和检测风险的宿主：不要假设所有代码都能反编译，也不要在没有真机日志的情况下宣称 LSPosed 不被检测。

### 真机交互协作

- 该手机拒绝 adb 输入注入，执行 `adb shell input tap` 会报 `SecurityException: INJECT_EVENTS permission`。
- 不要尝试用 KernelSU root、`su -c input`、无障碍服务或其它方式绕过该限制。
- `adb shell am start`、`uiautomator dump`、`screencap`、`logcat` 仍可用于启动页面、抓取界面和验证结果。
- 需要点击、滑动、登录、下拉刷新或切换 Tab 时，先明确告诉用户当前页面和要点击的位置/文字，等用户完成并回复后再继续抓取和验证。
- 不得代替用户猜测操作已完成；只有拿到操作后的 UI dump、截图或日志证据，才能继续下结论。

## 构建、安装与验证

```bash
export JAVA_HOME=/path/to/standard-jdk-17
./gradlew :app:assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

本机 `/usr/libexec/java_home -v 17` 可能选中 GraalVM 17，从而在 `JdkImageTransform` 阶段
因 `jlink` 失败。遇到该错误应显式改用标准 JDK 17（例如本机的 Corretto 17）或 Android Studio
自带 JBR；不得把环境问题误判为源码编译错误。

安装后需要用户在 LSPosed 管理器中启用模块并勾选 `com.xiaomi.market`，再强制停止商店。不能仅凭 APK 安装成功就宣称模块已生效。

```bash
adb logcat -c
adb shell am force-stop com.xiaomi.market
adb shell am start -n com.xiaomi.market/.ui.DefaultLauncherIcon
adb logcat -v threadtime | grep -E 'HyperStore-AdFree|AndroidRuntime|FATAL EXCEPTION'
```

至少要覆盖以下场景：

- 冷启动、热启动、退出后重进。
- 冷启动和后台重入时确认无启动广告，并确认普通启动流程、隐私协议和首启推荐不受影响。
- 首页首次加载、下拉刷新、连续滚动、切换 tab。
- 搜索、应用详情、下载、暂停/继续、安装、升级、卸载。
- 从通知或外部链接唤起商店。
- 宿主升级后的首次启动和字段缺失路径。

## 验收标准

- 模块只加载到 `com.xiaomi.market`，其它应用和 system_server 没有该模块日志或行为变化。
- `SPEC.md` 的八项功能在真机上按各自开关稳定生效，重启商店、刷新和页面重建后仍然保持。
- 搜索、下载、安装、更新、卸载和账户功能正常，无崩溃、ANR、白屏或死循环。
- 宿主版本不匹配或方法签名变化时，对应功能安全跳过，商店仍可正常使用。
- 每个开关独立生效，默认开启；关闭后恢复宿主原始行为。
- README 记录已确认功能、宿主版本、hook 签名、日志标记和已知限制；实际行为必须与 `SPEC.md` 一致。

## 变更与提交约束

- 不修改 `/Volumes/disk3/test/miui_music_helper`，只参考其结构、API 用法和容错策略。
- 不提交 `artifacts/`、`build/`、`.gradle/`、反编译目录、APK、签名文件和 `local.properties`。
- 不重打包或分发修改后的商店 APK，不伪造宿主签名。
- 不绕过商店支付、账户安全、安装校验或应用审核逻辑。
- 不实现 `SPEC.md` 之外的净化项；新增或删除功能先修改规范并确认。
- 修改前先查看工作区现有改动，不覆盖或回滚他人未提交的内容。
- 未经真机验证的功能只能标为「待验证」，不能写进 README 的「已生效功能」。
- 兼容性结论必须绑定具体版本号，例如「`4.129.s.00` 已验证」，不能只写「HyperOS 4 可用」。
