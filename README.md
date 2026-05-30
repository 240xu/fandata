# FanData - Legado 书源转译引擎

[English](#english) | 中文

FanData 是一个 LNR（Light Novel Reader）插件，将 lyc486 版 Legado 的 JSON 书源转译为 LNR 可用的数据源。

## 功能

- ✅ 导入 Legado JSON 书源，直接使用
- ✅ 搜索、探索页、书籍详情、目录、正文解析
- ✅ JavaScript 动态规则（Rhino 引擎 + 安全沙箱）
- ✅ CSS/XPath/JSONPath/正则 全规则格式支持
- ✅ data URL 路由（聚合源支持）
- ✅ 登录 UI（WebView）
- ✅ 功能开关设置页

## 构建

`ash
# 需要 Android Studio + JDK 21
.\gradlew.bat :plugin:assembleDebug

# 输出: plugin/build/outputs/apk/debug/plugin-debug.apk.lnrp
`

## 安装

1. 将 .lnrp 文件传到手机
2. 在 LNR 中通过插件管理器安装
3. 重启 LNR

## 开发文档

详见 [DEVELOPMENT_DOC.md](DEVELOPMENT_DOC.md)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证。

本项目是基于 GPL-3.0 的 [Legado（lyc486 版）](https://gitee.com/lyc486/legado) 的衍生作品。
