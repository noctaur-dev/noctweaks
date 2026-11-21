# Video: Video Track (prototype)

Part of **NocTweaks** by noctaur. **Prototype: expect rough edges.**

Drop a video file on an arranger track. Its sound goes in as an ordinary audio clip, and the picture plays in
a window that follows Bitwig's transport: play, stop, scrub, jump and loop.

## What you need

Two free programs. The mod looks for them on your PATH and in the usual install places, then in the mod's
own **tools** folder:

- **mpv** shows the picture. Windows: download it from [mpv.io](https://mpv.io/installation/); macOS:
  `brew install mpv`.
- **ffmpeg** makes the WAV of the video's sound. Windows: [ffmpeg.org](https://ffmpeg.org/download.html);
  macOS: `brew install ffmpeg`. Without ffmpeg, mpv makes the WAV instead.

Not installed anywhere? Put `mpv.exe` and `ffmpeg.exe` (macOS: `mpv.app` and `ffmpeg`) in the tools folder:
arranger right-click menu > **Video** > **Open Video Tools Folder (mpv, ffmpeg)**.

## How to use it

1. Drag a video file (`.mp4`, `.mov`, `.m4v`, `.mkv`, `.webm`, `.avi`) from Explorer or Finder onto an audio
   track in the arranger, where it should start.
2. The mod saves the sound beside the video as `<name> (sound).wav`, and Bitwig adds that as the clip. The
   video window opens.
3. Press play. The picture follows Bitwig: it stops with the transport, scrubs when you click or drag the
   playhead, and shows the first frame while the playhead is before the video's start.

The arranger's right-click menu (empty space) has a **Video** submenu:

- **Show / Hide Video Window**
- **Set Video Start to Playhead**: put the playhead where the clip starts, then pick this. Use it after moving
  the clip, or when the video wasn't dropped on a lane.
- **Earlier / Later by 1 Frame**: fine-tune the picture against the sound.
- **Forget Video**

## Limits of this prototype

- One video at a time. The link is remembered after a restart (Show Video Window opens it again), but
  isn't tied to a project yet.
- The video doesn't follow the clip if you move it: use **Set Video Start to Playhead**.
- Tempo changes after the drop aren't followed (the start is kept in seconds).
- The window is mpv's own. Its keyboard shortcuts still work in it, but the picture is driven by Bitwig.

## Bitwig versions

Built against Bitwig 6.1.4, so Patchbay runs it on 6.0 to 6.1.7 on Windows, macOS and Linux. Not tested in
Bitwig yet.
