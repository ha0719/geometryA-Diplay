# H52.10500 ANW SPP UUID 字节序证据

## 结论范围

现有静态证据支持：`SPPConnect(byte[])` 的 16 字节数据会按原字节序传入 ANW 连接层；H52 BT SDP helper 把一个内存中的 UUID 按 `uint32 + uint16 + uint16 + 8 bytes` 解释，并将前三个字段转换为 SDP/network canonical big-endian 顺序。因此，如果本次 iAP2 服务 UUID 确实沿该 UUID/SDP helper 路径查询，传入 JNI byte[] 的候选格式应是 GUID 字段小端格式。间接回调链尚未完全闭合，故这不是“已证明实际 connect 调用最终执行该 helper”的结论。

实车此前只测了 canonical big-endian bytes，ANW `initializedFlag=true`，但连接未在超时前确认。GUID 字段小端候选尚未做实车验证；本文不建议据静态推断直接自动切换或自动重连。

## 固件与方法证据

以下地址是 H52.10500 ARM ELF 的 ELF VA；Thumb 函数地址按符号表报告，反汇编工具可能显示入口指令地址为 `VA & ~1`。表中标明的 `.rodata` 地址在这些镜像中与文件偏移相同。路径均为原固件解包树中的绝对路径。

| 库 | 绝对路径 | 符号/位置 | 观察到的行为 |
|---|---|---|---|
| `libAnwPLS.so` | `E:\BaiduNetdiskDownload\kc_update_01.03.10500.H52.00030\system\lib\libAnwPLS.so` | `Java_com_anwsdk_service_SPPConnect`：VA `0xFEF5`，size `0x154` (340)；PLT `ANW_SPPConnect` stub：VA `0xAA2C`；默认 UUID：`.rodata` VA/file offset `0x18558` | JNI wrapper 先以默认 16-byte SPP 常量初始化，再从 Java byte[] 取内容并 `memcpy` 到 UUID buffer，长度使用传入数组长度；没有观察到逐字段反转。之后以四个 ARM word 参数调用 `ANW_SPPConnect` PLT stub。请注意：超长数组边界是否由上层保证不属于本文已证明范围。 |
| `libAnwManagerS.so` | `E:\BaiduNetdiskDownload\kc_update_01.03.10500.H52.00030\system\lib\libAnwManagerS.so` | `ANW_SPPConnect`：VA `0x119931`，size `0x60`；`SPPConnect`：VA `0xEEE19`，size `0x108`；`SPPConnectProc`：VA `0xEE455`，size `0xC4`；`DeviceAPI_InitDeviceData`：VA `0x5AC49`，size `0x17C` | 导出入口把四个 word 转交 `AnwSPP_SPPConnect` 回调表函数。`SPPConnect` 将 UUID 四 word 重建/存入连接请求；`DeviceAPI_InitDeviceData` 将 request+`0x100` 的 16 字节 memcpy 到 DeviceAPI 对象+`0x260`，保留字节序。`SPPConnectProc` 后续经 DeviceAPI 间接表启动/打开设备；具体 profile callback 的最终调用点未闭合。 |
| `libanwbtif.so` | `E:\BaiduNetdiskDownload\kc_update_01.03.10500.H52.00030\system\lib\libanwbtif.so` | `uuid_transform`：VA `0xDD15`，size `0x80`（Thumb 指令入口 `0xDD14`）；`btapi_sdp_get_scn`：VA `0xE4CD`，size `0x17C`；`Btif_get_service`：VA `0xE649`，size `0x110`；`linux_Btif_get_service`：VA `0x19309`，size `0x0C` | `uuid_transform` 将内存中的 Data1/Data2/Data3 分别按 32/16/16-bit 字段读出，再逐字节写成大端，最后原样复制尾部 8 字节。`btapi_sdp_get_scn` 对 16-byte service UUID 调用该转换函数并构造 SDP 查询；`Btif_get_service` 调用该 SDP helper，`linux_Btif_get_service` 再包装调用。尚未静态证明 ANW 的特定 SPP connect 请求一定通过该调用链。 |

上述 size 为符号表函数大小，非源代码行范围。二进制反汇编与符号/section 地址来自本机 H52.10500 原固件文件；没有用现代 Android/Bluetooth API 行为替代固件证据。

## 常量和候选值

`libAnwPLS.so` 文件偏移 `0x18558`，以及 `libAnwManagerS.so` 文件偏移 `0x1506E0`、`0x1510F4` 均包含默认 SPP UUID 字节：

```text
01 11 00 00 00 00 00 10 80 00 00 80 5F 9B 34 FB
```

按 Bluetooth base UUID 字段解释，该值对应 RFCOMM/SPP UUID `00001101-0000-1000-8000-00805F9B34FB` 的 GUID 内存布局（Data1/Data2/Data3 小端，尾部不变）。它与“显示出来的 UUID 字符串按顺序直接编码为 16 bytes”不是同一种输入布局。

候选 iAP2 服务 UUID 的 canonical 字符串为：

```text
00000000-DECA-FADE-DECA-DEAFDECACAFE
```

候选 JNI byte[]（Data1/Data2/Data3 按 GUID 字段小端，余下 8 字节原样）为：

```text
00 00 00 00 CA DE DE FA DE CA DE AF DE CA CA FE
```

对照的 canonical big-endian 顺序为：

```text
00 00 00 00 DE CA FA DE DE CA DE AF DE CA CA FE
```

注意候选值是根据 `uuid_transform` 的字段处理方式推导；由于 ANW SPP 连接到 BT SDP 查询的完整间接调用链仍有缺口，它是待验证候选，不是已由实车或完整静态调用图确认的最终值。

## 已证明 / 未证明

已证明：

- JNI byte[] 内容没有在 `libAnwPLS.so` wrapper 中按 UUID 字段反转。
- ANW Manager 中间对象通过 memcpy 保留 UUID 16-byte 序列。
- H52 `libanwbtif.so` 存在明确的 GUID-字段解释与 canonical SDP UUID 生成函数；该函数被 SDP service lookup helper 使用。
- H52 默认 SPP UUID 常量是 GUID 字段小端内存形式。

未证明：

- 特定 `SPPConnect` 请求最终会经由表项/插件回调进入 `Btif_get_service` / `btapi_sdp_get_scn` / `uuid_transform`。`SPPConnectProc` 到 DeviceAPI/BT profile 的关键边连接由间接函数表决定，现有静态证据没有闭合该边。
- GUID 字段小端 iAP2 候选已经在车机上成功连接。
- iPhone 没出现 CarPlay 设备类型选择，是否由 UUID 字节序单独导致。蓝牙配对状态和 ANW initialized 状态不能替代 SPP SDP 成功、RFCOMM 建链及 iAP2 认证/会话证据。

## 实车验证状态与建议诊断

已知现场结果：此前用 canonical big-endian iAP2 bytes 发起请求，ANW 初始化标志为 true，但在超时/取消前没有收到连接确认；原厂蓝牙显示已连接。用户另报告 iPhone 中已配对的原厂车机没有 CarPlay 选项，且无法选择设备类型。此次 GUID 字段小端候选实车验证**未做**。

后续应先做单次、可观测的对照实验：记录调用前实际 16 bytes、`SPPConnect` 返回值/回调、SDP service lookup 结果与 SCN、RFCOMM socket 状态和远端地址；一次只改变 UUID 表示，不在失败后自动交换字节序重连。只有观察到 SDP 查找走到上述 helper，或动态记录 helper 收到的 UUID 与生成的 SDP 查询，才可闭合静态间接调用链并确认候选格式。