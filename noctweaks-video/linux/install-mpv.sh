#!/bin/sh
# NocTweaks Video: installs mpv on Linux, where the Video mod looks for it.
#
# The Windows and macOS zips of the Video mod come with mpv. mpv doesn't publish a Linux build, so on Linux
# the mod uses your distribution's mpv instead (from /usr/bin or anywhere on your PATH). This script installs it
# with your package manager, plus zenity (the folder picker for "Choose Folder for Sound Copies...") if it's
# missing. ffmpeg is optional (the mod makes the sound copies a little faster with it): add --ffmpeg.
#
#   sh install-mpv.sh            mpv (and zenity if missing)
#   sh install-mpv.sh --ffmpeg   ffmpeg too
#
# It asks for your password (sudo) to install packages. Nothing else on your system is changed.

set -u

want_ffmpeg=0
for a in "$@"; do
   case "$a" in
      --ffmpeg) want_ffmpeg=1 ;;
      -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
      *) echo "Unknown option: $a (try --help)"; exit 2 ;;
   esac
done

say() { printf '%s\n' "$*"; }

have() { command -v "$1" >/dev/null 2>&1; }

packages=""
have mpv || packages="$packages mpv"
have zenity || packages="$packages zenity"
[ "$want_ffmpeg" = 1 ] && ! have ffmpeg && packages="$packages ffmpeg"

if [ -z "$packages" ]; then
   say "Everything is already installed:"
   say "  mpv: $(command -v mpv)  ($(mpv --version 2>/dev/null | head -n 1))"
   have zenity && say "  zenity: $(command -v zenity)"
   have ffmpeg && say "  ffmpeg: $(command -v ffmpeg)"
   say "The Video mod will find them. Nothing to do."
   exit 0
fi

if [ "$(id -u)" = 0 ]; then
   SUDO=""
elif have sudo; then
   SUDO="sudo"
elif have pkexec; then
   SUDO="pkexec"
else
   say "Installing needs administrator rights, and neither sudo nor pkexec was found."
   say "Install these packages yourself:$packages"
   exit 1
fi

say "Installing:$packages"

if have apt-get; then          # Debian, Ubuntu, Mint, Pop!_OS
   $SUDO apt-get update && $SUDO apt-get install -y $packages
elif have dnf; then            # Fedora, RHEL (mpv and ffmpeg need RPM Fusion on Fedora)
   if ! $SUDO dnf install -y $packages; then
      say ""
      say "On Fedora, mpv and ffmpeg come from RPM Fusion. Enable it (https://rpmfusion.org/Configuration),"
      say "then run this script again."
      exit 1
   fi
elif have pacman; then         # Arch, Manjaro, EndeavourOS
   $SUDO pacman -S --needed --noconfirm $packages
elif have zypper; then         # openSUSE
   $SUDO zypper --non-interactive install $packages
elif have xbps-install; then   # Void
   $SUDO xbps-install -Sy $packages
elif have apk; then            # Alpine
   $SUDO apk add $packages
elif have emerge; then         # Gentoo
   $SUDO emerge --noreplace $(echo "$packages" | sed 's/mpv/media-video\/mpv/; s/zenity/gnome-extra\/zenity/; s/ffmpeg/media-video\/ffmpeg/')
else
   say "No supported package manager found (apt, dnf, pacman, zypper, xbps, apk, emerge)."
   say "Install these packages yourself:$packages"
   exit 1
fi

say ""
if have mpv; then
   say "Done. mpv is at $(command -v mpv): $(mpv --version 2>/dev/null | head -n 1)"
   say "The Video mod finds it by itself; Bitwig doesn't need restarting."
else
   say "mpv still isn't on your PATH. Check the messages above."
   exit 1
fi
