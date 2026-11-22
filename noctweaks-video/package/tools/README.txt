Tools for the NocTweaks Video mod.

The mod needs mpv (https://mpv.io) or ffmpeg (https://ffmpeg.org): either one decodes the pictures for the
Video panel and the clips' film strips, and makes the WAV of a video's sound. Each zip of the mod comes with
one of them:

  mpv zips     Windows: mpv\mpv.exe (with the DLLs beside it)
               macOS:   mpv.tar.gz, unpacked to mpv.app the first time the mod needs it
               mpv 0.41.0, the official build, unchanged (licence: mpv-licence/; source: the mod's GitHub
               release, mpv-0.41.0-source.tar.gz, and https://github.com/mpv-player/mpv/tree/v0.41.0).

  ffmpeg zips  ffmpeg.gz, a static build of ffmpeg 6.0 (one file), unpacked to ffmpeg/ the first time the
               mod needs it (licence and build notes: ffmpeg-licence/; source: the mod's GitHub release,
               ffmpeg-6.0-source.tar.gz, and https://github.com/FFmpeg/FFmpeg/tree/n6.0).

The mod also uses an mpv or ffmpeg installed on the computer (on the PATH, Homebrew, /usr/bin), so you can
put another one here or install your own. With both, mpv decodes the pictures unless Settings > Locations >
Video > Decode pictures with says ffmpeg; ffmpeg makes the WAVs.
