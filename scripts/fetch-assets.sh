#!/usr/bin/env bash
# CI 资产组装：ubuntu rootfs + node + proot + 上游 dsh-starter 最新内容
set -euo pipefail
cd "$(dirname "$0")/.."
A=app/src/main
mkdir -p "$A/assets" "$A/jniLibs/arm64-v8a" /tmp/da
MIRROR="${GH_MIRROR:-}"   # 可选 GitHub 镜像前缀，如 https://ghfast.top/
gh() { echo "${MIRROR}$1"; }

echo "══ 1/5 ubuntu rootfs (arm64) ══"
curl -sL --retry 3 -o /tmp/rootfs.tar.gz \
  "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"
cp /tmp/rootfs.tar.gz "$A/assets/rootfs.tar.gz"

echo "══ 2/5 node (arm64, glibc) ══"
NODE_V=v22.14.0
curl -sL --retry 3 -o /tmp/node.tar.xz "https://nodejs.org/dist/${NODE_V}/node-${NODE_V}-linux-arm64.tar.xz"
tar xJf /tmp/node.tar.xz -C /tmp
cp "/tmp/node-${NODE_V}-linux-arm64/bin/node" "$A/jniLibs/arm64-v8a/libnode.so"

echo "══ 3/5 proot (官方静态 aarch64) ══"
PROOT_URL=$(curl -s "https://api.github.com/repos/proot-me/proot/releases/latest" \
  | python3 -c "import json,sys;[print(a['browser_download_url']) for a in json.load(sys.stdin)['assets'] if 'aarch64' in a['name'] and 'static' in a['name']]" | head -1)
echo "proot: $PROOT_URL"
curl -sL --retry 3 -o /tmp/proot.bin "$PROOT_URL"
file /tmp/proot.bin | grep -qi ELF
cp /tmp/proot.bin "$A/jniLibs/arm64-v8a/libproot.so"

echo "══ 4/5 上游 dsh-starter 最新 release 内容 ══"
REL_URL=$(curl -s "https://api.github.com/repos/sryimnoob123/dsh-starter/releases/latest" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['assets'][0]['browser_download_url'])")
curl -sL --retry 3 -o /tmp/setup.exe "$(gh "$REL_URL")"
7z x /tmp/setup.exe -o/tmp/nsis '$PLUGINSDIR/app-64.7z' -y > /dev/null
7z x '/tmp/nsis/$PLUGINSDIR/app-64.7z' -o/tmp/app64 'dsh-archives/*' -y > /dev/null
cp /tmp/app64/dsh-archives/dsh-runtime.tgz   "$A/assets/"
cp /tmp/app64/dsh-archives/dsh-home-seed.tgz "$A/assets/"

echo "══ 5/5 壳内置插件（asar.unpacked → 按包名归位）═══"
7z x '/tmp/nsis/$PLUGINSDIR/app-64.7z' -o/tmp/app64 'resources/app.asar.unpacked/plugins/*' -y > /dev/null
SRC=/tmp/app64/resources/app.asar.unpacked/plugins
STAGE=/tmp/host-plugins/profiles/web/node_modules
mkdir -p "$STAGE"
while IFS= read -r pj; do
  NAME=$(python3 -c "import json;print(json.load(open('$pj'))['name'])")
  DIR="$STAGE/$NAME"; mkdir -p "$DIR"
  cp -a "$(dirname "$pj")/." "$DIR/"
done < <(find "$SRC" -name package.json -maxdepth 4)
tar czf "$A/assets/host-plugins.tar.gz" -C /tmp/host-plugins profiles

echo "══ assets 清单 ══"
ls -la "$A/assets" "$A/jniLibs/arm64-v8a"
echo "✅ 资产组装完成"
