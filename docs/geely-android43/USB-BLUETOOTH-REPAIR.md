# H52 实车 USB / 蓝牙故障与本地修复（2026-10-06）

## 实车证据

用户照片 IMG_20261006_164027.jpg：VID0x05ac/PID0x12a8，USB权限已授权，descriptor configuration=2、NCM control=2/0、data=3/0、bulk IN0x87、OUT0x05。失败发生在 claim NCM data interface3，不能从此认定供电不足。MfiReady 表示本地认证提供者可用，不代表 iPhone 已通过 MFi 认证。

无线照片：Manual hotspot configReadable=true、wlan0/IPv4，热点与 AirPlay listener/Bonjour启动完成；蓝牙backend=H52-ANW、initializedFlag=true，连接确认超时。原厂蓝牙显示已连接，但照片没有证明 ANW SPP/iAP2 已接通。用户补充 iPhone 配对项目不显示 CarPlay 选项且设备类型不可选，作为现象记录，不能仅凭它判定 iPhone 不支持 CarPlay。

## USB 代码修正

`shared/.../compat/LegacyUsbNative.kt`、`shared/src/main/jni/legacy_usb_jni.c`：在 Android 已授权并打开的 USB fd 上执行 USBDEVFS_SETCONFIGURATION / USBDEVFS_SETINTERFACE，不自行打开设备，不关闭调用方fd。每次 syscall 返回0或负errno；无root、无shell、无外部特权。

`UsbCompat.setConfigurationLegacy`：替换此前 raw controlTransfer SET_CONFIGURATION。原方式只发送设备请求，不能作为内核配置/接口状态已同步的证明；使用与平台 libusbhost 对应的内核操作。`setInterface` 旧平台分支同样改用 USBDEVFS_SETINTERFACE。现代已存在的 typed setInterface 分支保留。

`CarPlayController.openNcm`：USBMUX已在另一条连接占用接口后，不再重新SET_CONFIGURATION。读取 active configuration，与已解析目标一致才继续，否则失败并提示拔插。`NcmConfigurationGuard` 测试确保未知/不一致配置不被当作ready，也不重新切换已打开的USBMUX配置。

`UsbCompat.claimInterface`：先保留Android force claim；失败后仅对同一个目标接口再尝试内核claim以取得明确errno，并把结果返回。不会额外遍历、拆除其他接口。`NcmUsbBridge.open` 使用该封装并把NCM claim的 errno trace送入当前连接日志。errno16表示忙，errno19设备不可用，errno22参数/接口配置无效，errno13权限拒绝；这些错误不单独证明具体原厂应用是占用者。

已修正代码中的USB配置处理问题，但当前实车缺少errno，不能保证它就是照片中claim失败的唯一原因。USB物理读写/NCM链路仍需车测。

平台参考：
https://android.googlesource.com/platform/system/core/+/79677f8b4928e1cda39b8f561001769b955fa2a0/libusbhost/usbhost.c
https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection

## 蓝牙诊断

`AnwBluetoothBackend.connect` 保留既有单次请求及15秒确认边界，增加每秒当前owned slot的result/state/addressPresent/addressMatches日志。不记录完整设备名、地址或数据载荷，不自动换UUID字节序、不将result1/slot分配当作连接成功。

固件再次确认 IAnwSPPDataCallBack 仅为数据指示回调，不通知连接成功。ANW SPPConnect返回请求结果/slot；deviceState返回槽位状态和地址。UUID输入格式、连接状态枚举仍存在未验证边界，不从普通HFP/A2DP连接或UI上的蓝牙标志推断SPP可用。

## 可读界面与直接导出

`ConnectionKeyDiagnostics` 保留最近ANW请求result/index、槽位result/state/地址匹配标志和USB claim errno。后续滚动日志不会抹去这些数字；新请求清除旧slot状态，避免把旧状态当成当前。该区域只展示精确字段，不参与连接成功判定。单元测试覆盖状态保留、新请求重置和不带出密码。

`CarPlayHostActivity` 左侧关键结果区显示这些字段，USB claim失败标题汉化。右侧日志顶部常驻“保存诊断报告”；窄屏也提供该按钮。保存报告不需要返回设置，也不依赖文件选择器。API18通过 DiagnosticExportStore.saveLegacy 保存至共享用户存储根目录，文件名 `DiPlay-诊断报告-yyyyMMdd-HHmmss-SSS.txt`。后台读取当前可见日志与所有SessionLogFile归档，逐行脱敏；历史日志包含完整连接结果，不需要靠拍照捕捉一行。

## 发布状态

按用户2026-10-06要求，本次仅本地交付；不推送GitHub，也不创建Release。等用户实车测试通过再发布。保留APK认证私钥/证书。


## 最终本地验证与边界（2026-10-06）

- shared 456、common 161，共 617 项单元测试通过，失败/错误/跳过均为 0。
- Android 4.3/API18 x86 模拟器：新增 USB native 库加载及无效 FD/参数保护通过；连接页保存按钮可将完整报告写至用户存储根目录。
- APK 四种 ABI 的 USB 库及本地 MFi 私钥/证书已确认保留。
- APK SHA-256：`2e71e66f34ef8e316e53fca0b9ee679689c70a84dfdb1771a6d8bc9b9a4b57cd`。
- USB、H52 ANW 蓝牙真实 iPhone 连接未通过实车验证；不代表已修好。UUID 字节序候选仍未切换，静态证据见 ANW-UUID-BYTE-ORDER.md。
- 仅本地整理，未推送 GitHub。
