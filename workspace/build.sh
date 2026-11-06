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
