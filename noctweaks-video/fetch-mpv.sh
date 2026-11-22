#!/bin/sh
# Puts mpv (the official 0.41.0 build) in package/tools for one platform, before building that platform's zip.
#   sh fetch-mpv.sh windows | macos-arm | macos-intel | none
set -e
cd "$(dirname "$0")/package/tools"
rm -rf mpv mpv.tar.gz
V=v0.41.0
URL=https://github.com/mpv-player/mpv/releases/download/$V
T=$(mktemp -d)
case "$1" in
  windows)
    curl -sSfL -o "$T/mpv.zip" "$URL/mpv-$V-x86_64-w64-mingw32.zip"
    unzip -q "$T/mpv.zip" -d "$T/outer"
    mkdir mpv
    unzip -q "$T"/outer/*.zip -d mpv
    rm -f mpv/mpv-register.bat mpv/mpv-unregister.bat
    ;;
  macos-arm|macos-intel)
    [ "$1" = macos-arm ] && F=mpv-$V-macos-14-arm.zip || F=mpv-$V-macos-15-intel.zip
    curl -sSfL -o "$T/mpv.zip" "$URL/$F"
    unzip -q "$T/mpv.zip" -d "$T/outer"
    cp "$T/outer/mpv.tar.gz" mpv.tar.gz   # kept as Apple's tar, so mpv.app keeps its signature
    ;;
  none) ;;
  *) echo "usage: fetch-mpv.sh windows|macos-arm|macos-intel|none"; exit 1 ;;
esac
rm -rf "$T"
