# 息间官网

纯静态网站。将 `website/` 作为站点根目录部署到任意静态文件服务（例如 `offtime.archeo.cn`）；`index.html`、`styles.css`、`favicon.svg` 和 `downloads/` 需要一起发布。直接用浏览器打开 `index.html` 也可以预览。

下载按钮当前指向 `http://offtime.archeo.cn/downloads/offtime-v1.3.1.apk`；HTTPS 证书签发成功后应改为 `https://`。该文件由当前已签名的 `app/build/outputs/apk/release/app-release.apk` 复制而来。根目录 `.gitignore` 对此下载文件做了例外，以便与站点一起发布。发布新版本时，先构建正式 APK，再复制到此目录、更新页面中的版本号和链接，并检查下载文件与版本一致。不要使用 debug APK 作为正式下载。
