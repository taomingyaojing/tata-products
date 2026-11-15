# 工卡守护（Gongka Guardian）—— 忘带工卡防呆拦截 App

针对上班族「忘带工卡」的软硬件结合方案。硬件端是放在工卡套里的超薄 iBeacon 蓝牙卡片，
App 端死磕「只在出门瞬间精准拦截」，解决同类产品「后台易被杀、频繁误报、费电」三大痛点。

## 架构总览

```
                    ┌─────────────────────────────────────────────┐
                    │        GkaForegroundService（大脑/常驻）        │
                    │  状态机：触发 → 15s BLE 裁决 → 静默/报警          │
                    └───▲──────────▲──────────────────▲───────────┘
                        │          │                  │
   第一道防线          │          │                  │       第二道防线
 ActivityDetector      │    WiFi 断开监听           │      GeofenceManager
 (加速度传感器)  ──静止→移动──┘   WifiDisconnectDetector │   (高德/百度围栏)
 [工作日 07:00-10:00]      (家 WiFi 断=出门·零耗电)      └──离开家50米(EXIT)──
                                                      [任意时间兜底]
                        │
                        ▼
                 ┌──────────────────────┐
                 │   GkaBleScanner       │  15 秒高频扫描，ScanFilter 锁 iBeacon
                 │   (裁决：搜卡比对)      │
                 └──────┬───────────────┘
                        │
            搜到工卡 ────┤──── 没搜到
              │          │
              ▼          ▼
        绝对静默      AlertManager（第三道防线）
      [标记已带卡]     强音量 + 强震动 + 全屏 AlarmActivity
```

## 触发源（三道触发 → 一个裁决入口）

| 触发源 | 触发条件 | 说明 |
|--------|---------|------|
| ① 出门动作 | 工作日 07:00-10:00，加速度传感器「静止→移动」 | 传感器被动回调，零轮询 |
| ② WiFi 断开 | 家 WiFi 断连（下降沿）+ **WiFi 指纹二次确认** | 系统广播零耗电；指纹防信号波动误报 |
| ③ 地理围栏 | 离开家 50 米（GEOFENCE_TRANSITION_EXIT）+ **WiFi 指纹二次确认** | 系统后台唤醒硬兜底；指纹防 GPS 漂移误报 |

三者任一一触发 → 汇入同一个 `confirmAwayThenTrigger()`（WiFi 指纹二次确认）→ 确认离家后走 15 秒 BLE 裁决 → 静默 / 强力报警

## 裁决逻辑（核心）

- 两个触发点汇入同一个 `triggerScan()`，有「扫描互斥 + 60 秒冷却」防抖
- 15 秒内搜到工卡 UUID → 静默，标记「今日已带卡」，当天不再打扰
- 15 秒没搜到 → 强力报警
- 「今日请假」→ 当天完全豁免

## 保活方案（国产机）

1. 前台服务 + 常驻通知（`startForeground` + `START_STICKY`）
2. 开机自启（BootReceiver）
3. 引导加入「电池优化白名单」（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
4. 引导加入厂商「自启动管理」白名单（华为/小米/OV 需手动，见 MainActivity 注释）

## 目录结构

```
app/src/main/
├── AndroidManifest.xml
└── java/com/gongka/guardian/
    ├── GkaApplication.kt              # 入口：通知渠道 + 拉起服务
    ├── MainActivity.kt                # 权限引导 + 家坐标 + 白名单
    ├── core/
    │   ├── Const.kt                   # 常量集中地（UUID/窗口/阈值/超时）
    │   └── Prefs.kt                   # 持久化（今日带卡/请假/家坐标）
    ├── service/
    │   └── GkaForegroundService.kt    # 核心大脑（三道防线联动）
    ├── detect/
    │   ├── ActivityDetector.kt        # 触发源①：传感器状态机
    │   ├── WifiDisconnectDetector.kt  # 触发源②：家 WiFi 断连监听
    │   ├── WifiFingerprintHelper.kt   # 触发源②辅助：WiFi 指纹采集/比对
    │   └── GeofenceManager.kt         # 触发源③：地理围栏
    ├── ble/
    │   └── GkaBleScanner.kt           # 裁决：iBeacon 扫描比对
    ├── alert/
    │   ├── AlertManager.kt            # 第三道防线：强音量/震动/唤醒锁
    │   └── AlarmActivity.kt           # 全屏报警界面
    └── receiver/
        ├── BootReceiver.kt            # 开机自启
        └── GeofenceReceiver.kt        # 地理围栏广播

```

## 待接入项（TODO）

1. **工卡 UUID**：`Const.WORK_CARD_UUID` 现为占位值，等商家确认真实 UUID 后替换
2. **报警音资源**：`res/raw/alarm.mp3`（或改 `AlertManager` 用系统默认音）
3. **高德地图 API key**：老大申请中，拿到后把 `GeofenceManager` 换成高德 GeoFenceClient
4. **家坐标**：接入真实定位（高德/百度 SDK）
5. **家 WiFi 绑定**：MainActivity 已提供"绑定当前 WiFi"按钮，用户首次需手动绑定
6. **正式 UI**：替换 MainActivity / AlarmActivity 的动态布局
