# 442 实车修复基线（基于 P21 源码）

本仓库 = `DiPlay-H52-P21-source.zip` 的 full-source + 以下改动，用于吉利 Android 4.4.2（API19）车机。

## 相对 P21 源码的改动

1. **CarPlayMediaKeys：API<21 停用媒体键注册与焦点申请**（`legacyMediaKeysEnabled = false` 开关）。
   原因：4.x 车机的原厂音频栈把"App 注册 RCC + 抢 STREAM_MUSIC 焦点"当作音源切换，
   会向 iPhone 发送 AVRCP PAUSE（实车日志 bt-btif `op_id=0x46`，与起播同秒），
   表现为 CarPlay 音乐起播约 1 秒即被暂停。API≥21 路径不变。
2. 修复 P21 源码 zip 两处编译不一致（VERSION-HISTORY 中 P22 才修的内容）：
   - `H52WirelessDiagnostics.java`：`readHostapdChannel()` 无参调用改为传 `(observed, configured)`；
   - `H52UsbConfigurationFix.java`：跨包引用 `H52NoRootBridge` 改为全限定名。

## 构建

认证资产不入库。从作者发布包提取 `assets/offline-mfi/identity.pk8` 与 `certificate.p7b`
（例如解压 `DiPlay-H52-P21-OpusSoftwareHudGate-test.apk`），放到一个独立目录后：

```bash
export JAVA_HOME=<jdk> ANDROID_HOME=<sdk>
export DIPLAY_AUTH_ASSETS_DIR=<存放 offline-mfi 目录的父目录>
./gradlew :mobile:assembleDebug
# 产物 mobile/build/outputs/apk/debug/mobile-debug.apk
```

不带 `DIPLAY_AUTH_ASSETS_DIR` 的构建是"源码-only"包，安装后连接时会报
"无法加载 CarPlay 认证资料"。

## 已知事项（4.4.2 实车）

- 无线连接卡在"已发送 Wi-Fi 配置"：手机 Wi-Fi 关开一次即可（iOS 需要收到配置后新鲜加入热点）。
- 本构建 versionName 仍显示 `0.2.13-H52-P16`/code30（沿用 P21 归档的清单版本）。
- 与作者发布包签名不同，同包名覆盖安装需先卸载；与本机自建包之间可直接 `adb install -r`。
