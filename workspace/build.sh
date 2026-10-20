#!/bin/bash
# 一键构建：桩层编译 -> TeaVM 编译 -> 组装 dist
set -e
cd "$(dirname "$0")"
JDK="D:/app/jdk-21/bin"
LEVEL="${1:-SIMPLE}"

echo "== 1. 编译桩层 =="
rm -rf stubs/classes
mkdir -p stubs/classes
javac --release 8 -encoding UTF-8 -Xlint:-options -nowarn \
  -cp "tools/lib/teavm-core-0.16.0.jar;tools/lib/teavm-jso-0.16.0.jar;../皇家骑士团.jar" \
  -d stubs/classes $(find stubs/src -name "*.java")
# 拷贝 services 注册文件（资源内联的关键）
cp -r stubs/src/META-INF stubs/classes/

echo "== 2. TeaVM 编译 ($LEVEL) =="
"$JDK/java" -Xmx2g -cp "tools/lib/*;stubs/classes;res;../皇家骑士团.jar" \
  tools/src/Build.java HostMain dist "$LEVEL" 2>&1 | grep -E "^\[TEAVM|successfully|with errors|with warnings" || true

# 可选：扩展包变体（res_custom/240_320.pak 存在时，双构建供前端切换）
if [ -f res_custom/240_320.pak ]; then
  echo "== 2b. TeaVM 编译扩展包变体 =="
  rm -rf res_custom_other
  mkdir -p res_custom_other
  cp res/*.png res/*.pak res/*.dat res/*.mid res/setting.dat_11 res_custom_other/ 2>/dev/null || true
  [ -d res/icons ] && cp -r res/icons res_custom_other/icons
  cp res_custom/240_320.pak res_custom_other/240_320.pak
  "$JDK/java" -Xmx2g -cp "tools/lib/*;stubs/classes;res_custom_other;../皇家骑士团.jar" \
    tools/src/Build.java HostMain dist_custom "$LEVEL" 2>&1 | grep -E "^\[TEAVM|successfully|with errors|with warnings" || true
  [ -f dist_custom/game.js ] && cp dist_custom/game.js dist/game_custom.js
  rm -rf res_custom_other
fi

echo "== 3. 组装 dist =="
cp web/index.html web/virtualpad.js web/manifest.webmanifest web/sw.js dist/
[ -f web/audio.js ] && cp web/audio.js dist/
[ -f web/hd.js ] && cp web/hd.js dist/
[ -d dist/icons ] || cp -r web/icons dist/icons

echo "== 4. 同步 GitHub Pages 目录 (docs/) =="
if [ -d ../docs ]; then
  rm -rf ../docs
  cp -r dist ../docs
fi

echo "== 完成 =="
ls -la dist/game.js
[ -f dist/game_custom.js ] && ls -la dist/game_custom.js
