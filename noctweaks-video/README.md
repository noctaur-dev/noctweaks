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
   track in the arranger.
2. The mod makes a WAV of the video's sound and Bitwig adds that as the clip. The clip *is* the video: the
   picture follows it wherever it goes. Move it, cut it, trim it, slip it, and the panel shows the matching part of
   the video; between clips the panel is black.
   A video with no sound gets a clip of silence as long as the video, so it works the same way.
3. Click the **Video** button in the bottom bar to show the panel, and press play.

### Detaching the picture

The panel's **Detach** button opens the picture in a window of its own, in Bitwig's style, as well as in the
panel. Drag it anywhere (a second screen, next to the piano roll) and resize it. It's **pinned** by default: it
stays in front of Bitwig's window while you work in the editor, mixer or anywhere else. **Unpin** in the window
makes it an ordinary window; **Pin on Top** pins it again. **Attach** (the panel's button, while the window is
open) or the window's own close button puts it away; Detach brings it back where it was.

### Several videos

Drop as many videos as you like, on any tracks. Every one becomes an AV track, and all their sound plays (mute
a track to silence it). The **Video panel shows the highest AV track** that has a clip under the playhead, as a
video editor does: the top track covers the ones below, and where it has a gap the next one down shows through.
**Muting an AV track hides its picture too**, so the next one down shows. Tracks inside groups count, in the
order the arranger lists them, and muting a group mutes the videos in it.

Clips of the video's sound carry a **film strip** across their top (pictures of the video at those points),
so they read as video in the arranger. Short clips get a film edge along their bottom instead. A track with a
video clip also shows a small film icon under its track icon in the header: it's an AV track.

### Where the sound copies go

By default the WAVs are kept out of the way, in the mod's own data folder (`sounds/`, one folder per video),
not next to your videos. To change that, **Settings > Locations > Video > Sound copies of videos**:

- **In the Video mod's folder** (default)
- **Next to each video** (as `<name> (sound).wav`)
- **In a folder you choose**: pick the folder with **Choose Folder for Sound Copies...** in the Video menu

The **Video** submenu (right-click a clip, or empty arranger space) also has **Open Sound Copies Folder**.

A copy can be deleted once no project uses it; dropping the video again makes it again.

### The Video menu

Right-click a video clip:

- **Video: <name>** shows which video the clip is.
- **Forget This Video**: its clips become plain audio clips again.
- **Describe This Clip**: a diagnostic (what Bitwig holds for the clip), written to the log.

Right-click empty arranger space:

- **Pop Out to a Window**: the latest video in mpv's own window (the older pop-out, with a fixed start; the
  panel's Detach button is the better way).
- **Forget All Videos**

## Limits of this prototype

- The videos are remembered after a restart, but aren't tied to a project yet.

## Bitwig versions

Built against Bitwig 6.1.4, so Patchbay runs it on 6.0 to 6.1.7 on Windows, macOS and Linux. Tested on
Bitwig 6.1.7 on Windows.
