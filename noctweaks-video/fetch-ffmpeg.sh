#!/bin/sh
# Puts ffmpeg (a static build: one file) in package/tools for one platform, before building that platform's
# ffmpeg zip. The builds are FFmpeg 6.0 as packaged by ffmpeg-static (github.com/eugeneware/ffmpeg-static):
# John Van Sickle's for Linux, gyan.dev's for Windows, and the macOS builds it lists.
#   sh fetch-ffmpeg.sh windows | macos-arm | macos-intel | linux | none
set -e
cd "$(dirname "$0")/package/tools"
rm -rf ffmpeg ffmpeg.gz ffmpeg-licence
URL=https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0
case "$1" in
  windows) P=win32-x64 ;;
  macos-arm) P=darwin-arm64 ;;
  macos-intel) P=darwin-x64 ;;
  linux) P=linux-x64 ;;
  none) exit 0 ;;
  *) echo "usage: fetch-ffmpeg.sh windows|macos-arm|macos-intel|linux|none"; exit 1 ;;
esac
curl -sSfL -o ffmpeg.gz "$URL/ffmpeg-$P.gz"
mkdir ffmpeg-licence
curl -sSfL -o ffmpeg-licence/LICENSE "$URL/$P.LICENSE"
curl -sSfL -o ffmpeg-licence/README "$URL/$P.README"
cat > ffmpeg-licence/SOURCE.txt <<TXT
This mod's zip includes ffmpeg 6.0, unchanged, as a static build for $P from
$URL/ffmpeg-$P.gz
(the ffmpeg-static project, github.com/eugeneware/ffmpeg-static). FFmpeg is free software under the GNU GPL
(see LICENSE here, and README for how this build was made). Its source code is attached to the mod's release
on GitHub (ffmpeg-6.0-source.tar.gz), and at https://github.com/FFmpeg/FFmpeg/tree/n6.0.
TXT
