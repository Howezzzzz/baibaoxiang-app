# 百宝箱 · 安卓壳

把 `https://m.tbox.cn/agents/home`（百宝箱）封装成独立 Android App 的 WebView 壳。

## 说明

- **非官方客户端**，仅供本人自用；**不收集任何数据**，**不含任何内容**（页面内容与帐号均由 m.tbox.cn 提供）。
- **站内跳转留在壳内**（`tbox.cn` 及其子域）；外链（其它站点）交给系统浏览器。
- 登录在壳内完成，Cookie / 本地存储由 WebView 持久化（不接管帐号体系）。
- 安全：只走 https（明文一律拒绝），外部 scheme（tel/mailto/alipay/weixin 等）交系统处理。

## 功能

- 返回键：网页可后退则后退，到根页「再按一次退出」
- 下拉刷新、顶部加载进度、无网/加载失败错误页 + 重试
- 文件上传（相册/相机/文件）与下载
- 权限：相机 / 麦克风 / 媒体（按需申请，拒绝不崩）
- 深色模式跟随系统；字体缩放钳制（避免超大字号破版）
- 长按链接菜单（复制链接 / 在浏览器打开）
- 清除缓存（含登录态，二次确认）
- 检查更新（比对 GitHub Releases）

## 构建

- 包名：`cn.tbox.baibaoxiang`；应用名：百宝箱
- CI：push 到 `main` 自动出包 —— `.github/workflows/build-apk.yml`（analyze + test + `flutter build apk --release --split-per-abi`，产物 `baibaoxiang-<abi>.apk`）
- 本地（可选）：`flutter build apk --release --split-per-abi`

## 免责

本项目仅为个人使用的网页封装壳，与百宝箱/相关平台的官方无关；请勿分发。
