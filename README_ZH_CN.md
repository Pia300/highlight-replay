<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>精彩回录</h1>
  <sub>Highlight Replay</sub>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3ddc84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Pia300/highlight-replay)

一款回放式录屏应用：屏幕持续录制到内存，按需保存过去若干秒为视频。

[English](README.md) | 简体中文 | [繁體中文](README_ZH_TW.md)
</div>

<div align="center">
  <img src="docs/img/screenshots.png" alt="App Screenshots" width="600" />
</div>

## 🚀 下载

🔗 [从 GitHub Releases 下载](https://github.com/Pia300/highlight-replay/releases)

## ✨ 功能

- 回放录制：按「保存回放」即可把刚刚过去的若干秒保存为视频，无需提前开始录制
- 录制期间不写盘，仅保存动作生成文件，同一段内容可重复保存
- 录制参数可调：分辨率 720p / 1080p / 1440p / 自定义，帧率 24 / 30 / 60 fps，码率 4 / 8 / 12 / 16 Mbps，回放时长 30 / 60 秒
- 系统内录：可录制设备内部音频（Android 10 及以上），也可选择不录制声音
- 编码器可选：H.264 或 H.265，硬件 / 软件 / 自动
- 旋转自适应：采集方向与画面内容不一致时自动旋转对齐，也可固定竖屏或横屏
- 悬浮球：录制会话中的悬浮快捷控制，大小、透明度、位置可调
- 快捷设置磁贴：开始录制 / 保存回放 / 悬浮球开关
- 通知栏控制：录制期间可在通知栏直接保存或停止，并显示视频与音频状态
- 回放库：搜索、排序、重命名、分享、播放、批量删除
- 标签：自定义标签与配色，支持批量打标签
- Material 3 界面，支持深色模式与壁纸取色（Android 12 及以上）
- 支持 11 种界面语言：简体中文、繁體中文、English、日本語、한국어、Español、Português、Bahasa Indonesia、Tiếng Việt、ไทย、Русский
- 视频流与音频输入各有独立状态指示

## 📖 快速上手

1. 打开应用，按「开始录制」，在系统弹窗中同意屏幕录制
2. 正常使用设备即可，屏幕内容会持续录制到内存
3. 需要保存时按「保存回放」，最近 30 秒会保存到系统相册

> [!TIP]
> 若设备发热、卡顿或应用崩溃，可降低分辨率与帧率。

## ✨ 技术栈

本项目使用 [Android Studio](https://developer.android.com/studio) 开发。

- [Kotlin](https://kotlinlang.org/)（开发语言）
- [Jetpack Compose](https://developer.android.com/jetpack/compose)（UI 框架）
- [Material 3](https://m3.material.io/)（UI 设计）
- [Coroutines](https://github.com/Kotlin/kotlinx.coroutines)（异步与状态流）
- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) / [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer)（编解码与封装）

## ⭐ Star History

如果喜欢这个项目，请给它一颗星 ⭐

<a href="https://www.star-history.com/?type=date&repos=Pia300%2Fhighlight-replay">
 <picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&theme=dark&legend=top-left" />
  <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&legend=top-left" />
  <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Pia300/highlight-replay&type=date&legend=top-left" />
 </picture>
</a>

## 📄 许可证

本项目以 [GNU General Public License v3.0](LICENSE) 授权（GPL-3.0）。

随应用分发的第三方组件与图标出处见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [NOTICE](NOTICE)。
