# 单词九宫格（Android 版）

离线 WebView 封装，页面与素材位于 app/src/main/assets/。

- 云端编译：push 后由 GitHub Actions 自动构建 APK（见 .github/workflows/build.yml）
- 声音：调用系统 TTS（AndroidTTS bridge），无网络也可朗读
- 单词导入：页面内「设置 → 导入单词」，支持 txt/csv/json
