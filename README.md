# dsh-droid — 手机版 DeepSeek Harness 壳

复刻 dsh-starter 的 Android 壳：WebView + 前台服务 + proot 容器。
内容层（DSH 内核 + 全部插件种子）在每次 CI 构建时自动从上游
`github.com/sryimnoob123/dsh-starter` 最新 release 提取——他更新，你重新构建即跟进。

## 构建
GitHub → Actions → build → Run workflow → 下载 artifact (app-debug.apk) → 安装。

## 数据
- dsh-home 在 App 私有目录 files/dsh-home，覆盖安装保留
- 端口 127.0.0.1:3081（避开 DSHA 的 3080，可共存）
- 首次启动解压运行时约 1-2 分钟
