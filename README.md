# Tata Products 🦦

獭獭（Tata）系列产品合集仓库。所有自研产品统一放在这里管理。

## 产品列表

### 1. 獭獭救场（`legalcall-assistant/`）

防骚扰通话助手 Android App + 联网激活服务（Flask）。

- 来电话术自保，接听后自动播报（TTS）
- 假来电脱身（4 种人设）
- 联网激活与设备管理
- 目录结构：`app/`（Kotlin App）+ `server/`（Flask 激活服务）

### 2. 摸摸獭 MOMO Ta（`gongka-guardian/`）

工卡防忘带守护 Android App。

- iBeacon 蓝牙卡片检测，出门忘带卡强力报警
- 加速度传感器 + WiFi 指纹 + 定位距离多重确认
- 前台服务保活 + 开机自启
- 详见 `ARCHITECTURE.md`

## 目录结构

```
.
├── legalcall-assistant/   # 獭獭救场
├── gongka-guardian/       # 摸摸獭 MOMO Ta
└── README.md
```

## 安全说明

- 服务器密码、API key、管理 token 等敏感信息一律不硬编码在源码中，通过环境变量或本地文件注入。
- 部署脚本密码读取自 `SRV_PW` 环境变量或 `~/.srv_pw` 文件。
