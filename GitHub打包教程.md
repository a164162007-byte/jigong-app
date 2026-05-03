# 📦 GitHub Actions 自动打包使用教程（保姆级）

## 前置准备
1. 注册GitHub账号：https://github.com/（完全免费）
2. 安装Git：https://git-scm.com/downloads

---

## 第一步：创建GitHub仓库

### 1. 登录GitHub，点击右上角的 "+" → "New repository"

### 2. 填写仓库信息
- Repository name: `WorkLogger`（随便起个名，比如记工App）
- Description: 安卓记工App
- 选择 Public（公开，免费使用Actions）
- 不用勾选 "Add a README file"（我们源码里已经有了）
- 点击 "Create repository"

---

## 第二步：把代码推送到GitHub

### 推荐用GitHub Desktop（图形界面，简单！）
1. 下载 GitHub Desktop：https://desktop.github.com/
2. 安装后登录你的GitHub账号
3. File → Add Local Repository
4. 选择你解压后的源码文件夹
5. 左下角填写提交信息：比如 "初始提交"
6. 点击 "Commit to main"
7. 点击顶部的 "Publish repository"
8. 等待上传完成 ✅

---

## 第三步：触发自动打包

只要你把代码推送到GitHub，GitHub就会自动开始打包！

### 查看打包状态：
1. 打开你的GitHub仓库页面
2. 点击顶部的 "Actions" 标签
3. 就能看到正在打包的工作流
4. 黄色 = 打包中，绿色 = 成功，红色 = 失败

### 手动触发打包：
如果想手动重新打包：
1. 在 Actions 页面左边选择 "Android Build APK"
2. 点击 "Run workflow" → 再点一次 "Run workflow"
3. 等待3-5分钟就好

---

## 第四步：下载打包好的APK

### 打包成功后下载：
1. 在 Actions 页面点击已完成的绿色工作流
2. 往下滚动到最底部的 "Artifacts" 区域
3. 点击 "记工App-v1.x.x-debug" 下载ZIP包
4. 解压ZIP就能得到 `.apk` 安装文件
5. 传到手机安装即可！

### 发布正式版本（推荐！）：
打个标签就能自动发布到Release页面：
```bash
git tag v1.1.0
git push origin v1.1.0
```
打包完成后会自动出现在仓库首页的 "Releases" 区域，
任何人打开你的仓库都能直接下载APK！

---

## 常见问题

### Q: 打包失败怎么办？
A: 点击红色的失败工作流，看红色的错误信息，一般是：
- 网络问题 → 点 "Re-run jobs" 重新跑一次就行
- 代码问题 → 看错误提示改代码再提交

### Q: 打包需要付费吗？
A: 完全免费！GitHub免费版每个月有2000分钟的Actions额度，
打安卓包一次约5分钟，一个月能打400次，完全够用！

### Q: 怎么更新代码？
修改代码后重新提交并推送，GitHub会自动重新打包！

---

## ✅ 总结流程
1. 注册GitHub → 2. 建仓库 → 3. 推代码
4. 等3分钟 → 5. 下载APK → 6. 手机安装

就这么简单！以后改完代码推上去就行，不用开Android Studio了！
