# CamRelay — 把 Nikon Z30 拍的照片自动传到 NAS 的小盒子

> 一个小盒子(Raspberry Pi Zero 2 W 等 Linux SBC),常驻内存,
> 定时从相机拉新照片,推到你指定的 NAS。
> 不需要手机,不需要电脑,7×24 无人值守。

---

## 为什么不用手机

| | 手机 | 小盒子 |
|---|---|---|
| 成本 | $500+ | **$15-30** |
| 电耗 | 5-15W | **0.5-1W** (一年几毛电费) |
| 常开 | 烧电池 | **设计就是 7×24** |
| WiFi 切换 | 切相机 AP 就没外网 | 盒子可以双 WiFi (连相机 + 家里) |
| 远程管理 | 复杂 | **Web UI** 直接看 |
| 自动恢复 | 杀后台就停 | **systemd 死了自动拉起** |

---

## 你需要什么

### 硬件(任选一)

| 设备 | 价格 | 推荐度 | 备注 |
|---|---|---|---|
| **Raspberry Pi Zero 2 W** | $15 | ⭐⭐⭐ | 全套功能,USB 麻烦 |
| Raspberry Pi 4 | $35-55 | ⭐⭐⭐ | USB 3 多个,适合长时间 |
| Orange Pi Zero 2 | $20 | ⭐⭐ | 性价比不错 |
| 任何 Linux 盒子 | - | ⭐ | 只要有 gphoto2 跑得动 |

外加:
- 5V USB-C 电源(旧手机充电器就行)
- 16GB microSD 卡
- 装相机的 SD 卡适配器(只有 USB 方案需要)

### 软件

- Raspberry Pi OS Lite (Debian 12 bookworm)
- gphoto2 (`apt install gphoto2`)
- rclone (`apt install rclone`) — 用来推 NAS,什么协议都支持
- Python 3.11+

### NAS

任何 rclone 支持的协议都行: SMB / SFTP / FTP / WebDAV / Google Drive / S3 / OneDrive / ...

---

## 安装 (5 分钟)

```bash
# 1. 烧好 Pi OS Lite,SSH 进去
ssh pi@<pi-ip>

# 2. 装系统包
sudo apt update
sudo apt install -y git gphoto2 rclone python3 python3-pip python3-yaml python3-flask cifs-utils

# 3. 拉代码
git clone https://github.com/yourname/camrelay.git
cd camrelay

# 4. 跑安装脚本
sudo bash scripts/install.sh
```

### 配 rclone (推 NAS 用)

```bash
rclone config
# 选 n (new remote), 名字填 nas,类型按你的 NAS 选
# 例:SMB: 填 NAS IP, share 名, 用户名密码
# 测一下: rclone ls nas:/Photos
```

### 配相机 WiFi

```bash
sudo bash scripts/connect-camera.sh "NIKON_Z30_XXXXXX" "WiFi密码"
# 这一步是让盒子连到相机的 AP,不是反过来
```

### 配主配置

```bash
sudo nano /etc/camrelay/config.yaml
# 改:
#   nas.rclone_remote = "nas:Photos"
#   camera.poll_interval_sec = 5
```

### 启动

```bash
sudo systemctl enable --now camrelay
sudo journalctl -u camrelay -f
```

打开浏览器 `http://<pi-ip>:8080` 看状态。

---

## 日常使用

### 拍照流程

1. 相机开 WiFi (`MENU → 连接至智能设备`)
2. 正常拍照
3. 盒子每 5 秒轮询一次,新照片自动传到 NAS
4. NAS 上是 `Photos/CamRelay/2025-10-15/DSC_0001.JPG` 这样的目录结构

### 一次拍摄 session 结束

- 关闭相机的 WiFi (相机自动省电,不影响)
- 盒子继续在后台运行,等下次相机开 WiFi 就会继续传

### 不想用 USB,但想常开

把相机电源插上,WiFi 保持开,盒子一直传。

---

## 工具命令

```bash
# 健康检查
python3 -m relay.cli doctor

# 列出相机上的所有文件
python3 -m relay.cli list

# 手动下载某个文件
python3 -m relay.cli pull DSC_0001.JPG

# 交互式 Python (调试)
python3 -m relay.cli shell
```

---

## 故障排查

### Web UI 上 "no camera detected"

```bash
# 1. 看 gphoto2 自己认不认
gphoto2 --auto-detect
# 应该输出类似:
#   "usb:001,004-04b0:0401"  Nikon Corporation ...

# 2. 试 PTP/IP (相机在 AP 模式时)
#    先看 WiFi 是不是连上了
iwconfig wlan0 | grep ESSID
#    应该是 "NIKON_Z30_xxxxxx"

# 3. 测 PTP 端口
nc -zv 192.168.1.1 15740
```

### 报错 "permission denied" 接 USB

```bash
# 用 udev 规则
sudo cp scripts/99-camrelay.rules /etc/udev/rules.d/
sudo udevadm control --reload
# 重新插 USB
```

### 看详细日志

```bash
sudo journalctl -u camrelay -f
# 调试级别
sudo systemctl edit camrelay
# 加: [Service] Environment="PYTHONUNBUFFERED=1"
# 然后 sudo systemctl restart camrelay
```

---

## 文件结构

```
camrelay/
├── README.md
├── requirements.txt
├── relay/
│   ├── __init__.py
│   ├── main.py        # 入口
│   ├── cli.py         # doctor / list / pull
│   ├── config.py      # YAML 配置
│   ├── camera.py      # gphoto2 包装
│   ├── nas.py         # rclone / 直拷
│   ├── sync.py        # 主循环
│   ├── state.py       # 持久化 (上传过什么)
│   └── web.py         # Flask UI
├── web/
│   ├── templates/index.html
│   └── static/style.css
├── config/
│   └── config.example.yaml
├── scripts/
│   ├── install.sh
│   ├── connect-camera.sh
│   └── camrelay.service
└── logs/
```

---

## 扩展方向

- [ ] **触发拍照**: 用 gphoto2 --trigger-capture 远程按快门
- [ ] **延时摄影**: crontab + 自动拍
- [ ] **多机位**: 一个盒子轮询多台相机
- [ ] **云端备份**: rclone 已经支持 S3/Google Drive,加一条规则就行
- [ ] **AI 分类**: 在盒子上跑 ONNX 模型自动打标签
- [ ] **Telegram 通知**: 拍完推 Telegram 频道

---

## License

MIT
