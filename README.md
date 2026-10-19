# 皇家骑士团（J2ME → 网页 PWA）

2008 年 J2ME 战棋《皇家骑士团》的网页版移植：原 jar 字节码零改动，经 TeaVM AOT
编译为纯静态站点，适配 iPhone 15 Pro（像素完美 4x、虚拟按键、离线 PWA、存档本地保存）。

## 在线游玩

GitHub Pages 启用后：**https://zzylydx.github.io/huangjiaqishituan/**

启用方法（一次性）：仓库 **Settings → Pages → Source 选 `Deploy from a branch`**
→ Branch: `main`，Folder: **`/docs`** → Save，1~2 分钟后生效。

iPhone 上：Safari 打开上述地址 → 分享 → **添加到主屏幕** → 从图标全屏启动、离线可玩。
验收清单见《部署与真机验收.md》。

## 仓库结构

| 路径 | 内容 |
|---|---|
| `docs/` | 成品静态站（= `workspace/dist` 的同步副本，Pages 从此发布） |
| `workspace/stubs/` | MIDP 桩层 + 宿主桥接层（Java 源码） |
| `workspace/tools/src/` | TeaVM 编译驱动 / MIDI 音频渲染器 / 图标生成 / 依赖下载 |
| `workspace/web/` | 页面壳源文件（index.html / virtualpad.js / sw.js …） |
| `workspace/dist/` | 构建产物（`docs/` 的源头） |
| `皇家骑士团.jar` | 原始游戏（未改动） |

## 重新构建

见《部署与真机验收.md》。要点：新克隆需先
`python workspace/tools/src/fetch_deps.py` 拉取 TeaVM 依赖（`tools/lib/` 不入库），
然后 `cd workspace && bash build.sh ADVANCED`（末步自动同步 `docs/`，git 推送即发布）。
