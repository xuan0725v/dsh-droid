#!/usr/bin/env bash
# 修复上游 dsh-runtime.tgz 的平台原生件：上游 release 在 Windows 上打包，
# node_modules 里只有 win32-x64 的原生模块；本脚本注入 linux-arm64 对应件：
#   1) sharp / koffi / ripgrep / node-addon-require-builtin —— 版本号从 payload
#      自身的 package.json optionalDependencies 动态读取，直接从 npm registry
#      下载同版本 linux-arm64 平台包（上游升级版本时自动跟进）
#   2) node-pty —— npm 不带 linux 预编译，注入仓库内置的 N-API 构建
#      （third_party/node-pty/linux-arm64/pty.node，node 22/24 实测可用，
#       N-API 跨 node 大版本 ABI 稳定）
#   3) 删除 win32 平台件（减小 APK 体积；linux 下永远不会被解析）
# 用法: fix-runtime-native.sh <dsh-runtime.tgz 路径>
set -euo pipefail

TGZ="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

tar xzf "$TGZ" -C "$W"

# 从 payload 自身 package.json 读平台件版本（上游升级自动跟进）
jget() { python3 -c "import json;d=json.load(open('$W/$1/package.json'));print(d['optionalDependencies']['$2'])"; }

V_SHARP=$(jget sharp @img/sharp-linux-arm64)
V_VIPS=$(jget sharp @img/sharp-libvips-linux-arm64)
V_KOFFI=$(jget koffi @koromix/koffi-linux-arm64)
V_RG=$(jget @vscode/ripgrep @vscode/ripgrep-linux-arm64)
V_NARBI=$(jget node-addon-require-builtin node-addon-require-builtin-linux-arm64-gnu)
echo "  平台件版本: sharp=$V_SHARP libvips=$V_VIPS koffi=$V_KOFFI ripgrep=$V_RG require-builtin=$V_NARBI"

fetch_pkg() { # fetch_pkg <registry 包名> <版本> <解压目标(相对 node_modules)>
  local name="$1" v="$2" dest="$W/$3" fname url
  fname="$(basename "$name")-$v.tgz"
  url="https://registry.npmjs.org/$name/-/$fname"
  mkdir -p "$dest"
  curl -fsSL --retry 3 --retry-delay 2 "$url" | tar xz -C "$dest" --strip-components=1
  echo "  ✚ $name@$v"
}

fetch_pkg "@img/sharp-linux-arm64"                    "$V_SHARP" "@img/sharp-linux-arm64"
fetch_pkg "@img/sharp-libvips-linux-arm64"            "$V_VIPS"  "@img/sharp-libvips-linux-arm64"
fetch_pkg "@koromix/koffi-linux-arm64"                "$V_KOFFI" "@koromix/koffi-linux-arm64"
fetch_pkg "@vscode/ripgrep-linux-arm64"               "$V_RG"    "@vscode/ripgrep-linux-arm64"
fetch_pkg "node-addon-require-builtin-linux-arm64-gnu" "$V_NARBI" "node-addon-require-builtin-linux-arm64-gnu"

# node-pty：npm 上不发布 linux 预编译 → 仓库内置（N-API，node 22/24 均实测可加载）
mkdir -p "$W/node-pty/prebuilds/linux-arm64"
cp "$REPO/third_party/node-pty/linux-arm64/pty.node" "$W/node-pty/prebuilds/linux-arm64/"
echo "  ✚ node-pty prebuilds/linux-arm64/pty.node (内置)"

# 校验关键二进制落位（缺一个都视为失败，让 CI 立即报错而不是产出坏包）
need_file() { [ -f "$1" ] || { echo "❌ 缺少 $1" >&2; exit 1; }; }
need_glob() { ls $1 >/dev/null 2>&1 || { echo "❌ 缺少匹配 $1" >&2; exit 1; }; }
need_glob "$W/@img/sharp-linux-arm64/lib/*.node"
need_glob "$W/@img/sharp-libvips-linux-arm64/lib/libvips-cpp.so*"
need_file "$W/@koromix/koffi-linux-arm64/linux_arm64/koffi.node"
need_file "$W/@vscode/ripgrep-linux-arm64/bin/rg"
need_glob "$W/node-addon-require-builtin-linux-arm64-gnu/prebuilt/*.node"
need_file "$W/node-pty/prebuilds/linux-arm64/pty.node"
echo "  ✅ 关键二进制全部落位"

# 删除 win32 平台件（linux 下永远不会被解析，纯减体积）
rm -rf "$W/@img/"*win32* "$W/@koromix/"*win32* "$W/@vscode/"*win32* \
       "$W/"node-addon-require-builtin-*win32* \
       "$W/node-pty/prebuilds/"win32-* 2>/dev/null || true

# 重新打包（保持原 tarball 的 ./ 前缀风格，App 端 TarArchiveInputStream 直接可读）
tar czf "$TGZ" -C "$W" .
echo "  ✅ 已重打包: $TGZ ($(du -h "$TGZ" | cut -f1))"
