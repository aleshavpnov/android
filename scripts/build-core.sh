#!/usr/bin/env bash
# Собирает libXray.aar (gomobile-обёртка Xray-core) из пинованного тега и кладёт в app/libs.
# Артефакт ~40 МБ, в git его нет — каждый клон и CI собирают сами; кэш CI — по LIBXRAY_TAG.
#
# Нужны: go, gomobile (go install golang.org/x/mobile/cmd/gomobile@latest; gomobile init),
# python3, ANDROID_HOME и ANDROID_NDK_HOME.
set -euo pipefail

LIBXRAY_TAG="${LIBXRAY_TAG:-v26.7.28}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_DIR="$ROOT/.core/libXray"
OUT_DIR="$ROOT/app/libs"

: "${ANDROID_HOME:?ANDROID_HOME не задан}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME не задан}"
command -v gomobile >/dev/null || { echo "gomobile не найден в PATH" >&2; exit 1; }

if [ -d "$CORE_DIR/.git" ]; then
	git -C "$CORE_DIR" fetch -q --depth 1 origin "refs/tags/$LIBXRAY_TAG:refs/tags/$LIBXRAY_TAG"
	git -C "$CORE_DIR" checkout -q "$LIBXRAY_TAG"
else
	mkdir -p "$(dirname "$CORE_DIR")"
	git clone -q --depth 1 --branch "$LIBXRAY_TAG" https://github.com/XTLS/libXray "$CORE_DIR"
fi

echo "libXray $LIBXRAY_TAG → сборка android"
(cd "$CORE_DIR" && python3 build/main.py android)

mkdir -p "$OUT_DIR"
cp "$CORE_DIR/libXray.aar" "$OUT_DIR/libXray.aar"
echo "готово: $OUT_DIR/libXray.aar ($(du -h "$OUT_DIR/libXray.aar" | cut -f1))"
