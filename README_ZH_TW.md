<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>精彩回錄</h1>
  <sub>Highlight Replay</sub>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3ddc84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Pia300/highlight-replay)

一款回錄式螢幕錄製應用：螢幕畫面持續錄製到記憶體，依需求儲存過去若干秒為影片。

[English](README.md) | [简体中文](README_ZH_CN.md) | 繁體中文
</div>

<div align="center">
  <img src="docs/img/screenshots.png" alt="App Screenshots" width="600" />
</div>

## 🚀 下載

🔗 [從 GitHub Releases 下載](https://github.com/Pia300/highlight-replay/releases)

## ✨ 功能

- 回錄錄製：點擊「儲存回錄」即可把剛剛過去的若干秒儲存為影片，無需提前開始錄製
- 錄製期間不寫入磁碟，僅儲存動作會產生檔案，同一段內容可重複儲存
- 錄製參數可調：解析度 720p / 1080p / 1440p / 自訂，幀率 24 / 30 / 60 fps，位元率 4 / 8 / 12 / 16 Mbps，回錄長度 30 / 60 秒
- 內部音訊：可錄製裝置內部音訊（Android 10 及以上），亦可選擇不錄製聲音
- 編碼器可選：H.264 或 H.265，硬體 / 軟體 / 自動
- 旋轉自適應：內容會旋轉以符合錄製方向，亦可強制直向或橫向
- 懸浮視窗：錄製期間的懸浮快捷控制（懸浮球），大小、透明度、位置可調
- 快速設定磁貼：開始錄製 / 儲存回錄 / 懸浮視窗開關
- 通知欄控制：錄製期間可在通知欄直接儲存或停止，並顯示影片與音訊狀態
- 回錄庫：搜尋、排序、重新命名、分享、播放、大量刪除
- 標籤：自訂標籤與配色，支援批次加上標籤
- Material 3 介面，支援深色模式與桌布取色（Android 12 及以上）
- 支援 11 種介面語言：簡體中文、繁體中文、English、日本語、한국어、Español、Português、Bahasa Indonesia、Tiếng Việt、ไทย、Русский
- 視訊串流與音訊輸入各有獨立狀態指示

## 📖 快速上手

1. 開啟應用程式，點擊「開始錄製」，並在系統對話框中允許螢幕錄製
2. 正常使用裝置即可，螢幕畫面會持續錄製到記憶體
3. 需要保留時點擊「儲存回錄」，最近 30 秒會儲存到媒體庫

> [!TIP]
> 若裝置過熱、卡頓或應用程式當機，可降低解析度與幀率。

## ✨ 技術堆疊

本專案使用 [Android Studio](https://developer.android.com/studio) 開發。

- [Kotlin](https://kotlinlang.org/)（開發語言）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（UI 框架）
- [Material 3](https://m3.material.io/)（UI 設計）
- [Coroutines](https://github.com/Kotlin/kotlinx.coroutines)（非同步與狀態串流）
- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) / [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer)（編解碼與封裝）

## ⭐ Star History

如果喜歡這個專案，請給它一顆星 ⭐

<a href="https://www.star-history.com/?type=date&repos=Pia300%2Fhighlight-replay">
 <picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&theme=dark&legend=top-left" />
  <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&legend=top-left" />
  <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&legend=top-left" />
 </picture>
</a>

## 📄 授權條款

本專案以 [GNU General Public License v3.0](LICENSE) 授權（GPL-3.0）。

隨應用程式分發的第三方元件與圖示出處，詳見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 與 [NOTICE](NOTICE)。
