# Video: Video Track (prototype)

Part of **NocTweaks** by noctaur. **Prototype: expect rough edges.**

Drop a video file on an arranger track. Its sound goes in as an ordinary audio clip, and the picture plays in
the **Video** panel (its button is in the bottom bar, next to Bitwig's panel buttons), following Bitwig's
transport: play, stop, scrub, jump and loop. The panel draws the frames itself; mpv decodes them in the
background.

## Downloads

The mod comes with **mpv**, the free player that shows the picture and makes the WAV of the sound, so
there's nothing else to install. Pick the zip for your computer:

- `noctweaks-video-<version>-windows.zip`
- `noctweaks-video-<version>-macos-arm.zip` (Apple silicon: M1 and later)
- `noctweaks-video-<version>-macos-intel.zip`

On a Mac, mpv is unpacked the first time you use the mod. If **ffmpeg** is installed, the mod uses it for the
sound instead (a little faster); it isn't needed.

mpv 0.41.0 is the official build from [mpv's releases](https://github.com/mpv-player/mpv/releases/tag/v0.41.0),
unchanged. mpv is free software under the GNU GPL version 2 or later: its licence is in the mod's
`tools/mpv-licence` folder, and its source code is in each release (`mpv-0.41.0-source.tar.gz`). To build
the zips, `sh fetch-mpv.sh windows` (or `macos-arm`, `macos-intel`) puts mpv in `package/tools` first.

## How to use it

1. Drag a video file (`.mp4`, `.mov`, `.m4v`, `.mkv`, `.webm`, `.avi`) from Explorer or Finder onto an audio
   track in the arranger, where it should start.
2. The mod saves the sound beside the video as `<name> (sound).wav`, and Bitwig adds that as the clip.
3. Click the **Video** button in the bottom bar to show the panel, and press play.

The arranger's right-click menu (empty space) has a **Video** submenu:

- **Set Video Start to Playhead**: put the playhead where the clip starts, then pick this (after moving the
  clip, or when the video wasn't dropped on a lane).
- **Earlier / Later by 1 Frame**: fine-tune the picture against the sound.
- **Pop Out to a Window**: the picture in a separate window too (mpv's own), for a second screen.
- **Describe Clips at Playhead**: a diagnostic for the next step (the video following clip edits).
- **Forget Video**

## Limits of this prototype

- One video at a time. The link is remembered after a restart (Show Video Window opens it again), but
  isn't tied to a project yet.
- The video doesn't follow the clip if you move, cut or slip it yet: use **Set Video Start to Playhead**.
  Next step: the picture follows whichever clip of the video's sound is playing, so edits carry over.
- Tempo changes after the drop aren't followed (the start is kept in seconds).

## Bitwig versions

Built against Bitwig 6.1.4, so Patchbay runs it on 6.0 to 6.1.7 on Windows, macOS and Linux. Not tested in
Bitwig yet.
