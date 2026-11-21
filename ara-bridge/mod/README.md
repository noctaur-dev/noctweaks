# ARA Bridge (prototype)

Part of **NocTweaks** by noctaur. **Prototype: expect rough edges.**

Use Melodyne (or another ARA plug-in) on a Bitwig track without transferring audio.

Bitwig has no ARA, and ARA lives in the audio engine, which mods can't change. So the bridge is a plug-in
that plays the part of the DAW for Melodyne: it tells Melodyne which audio file the clip plays and where the
clip sits on the timeline. Melodyne then reads the file directly and plays the edited clip in time with
Bitwig.

Two parts:

- **plugin/**: *NocTweaks ARA Bridge*, a CLAP effect (C++). It hosts the ARA plug-in's VST3 inside itself,
  with its editor in the bridge's window, and saves Melodyne's edits in your Bitwig project.
- **mod/**: a Patchbay mod that tells the bridge where the clip starts.

## Trying it

1. Install the plug-in. Windows: copy `NocTweaks ARA Bridge.clap` to `C:\Program Files\Common Files\CLAP`.
   macOS: build it (below) and copy it to `~/Library/Audio/Plug-Ins/CLAP`. Rescan plug-ins in Bitwig.
2. Install the mod in Patchbay (`noctweaks-ara-bridge-*.zip`) and tick it.
3. Put **one** audio clip on a track. Easiest for a first test: a WAV clip starting at bar 1, playing from the
   start of its file.
4. Add **NocTweaks ARA Bridge** to that track and open its window. It asks for the clip's audio file (WAV),
   and for Melodyne's `.vst3` if it isn't in the usual VST3 folder. Melodyne opens inside the bridge.
5. If the clip doesn't start at bar 1: put the playhead where it starts, right-click the track header >
   **ARA Bridge** > **Clip Starts at Playhead**.
6. Press play: you hear Melodyne's version of the clip (the bridge replaces the track's own playback of it).

Each track's settings are a small text file in the **ARA Bridge** folder (track header menu > ARA Bridge >
Open ARA Bridge Folder): `plugin`, `file`, `start` (seconds on the timeline), `offset` (seconds into the
file) and `length` (seconds, 0 = to the end). The bridge rereads it within half a second when it changes.

## Building the plug-in

Needs CMake 3.20+ and a C++17 compiler (Visual Studio on Windows, Xcode's command line tools on macOS). The
SDKs are downloaded on the first configure:

```sh
cd ara-bridge/plugin
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

## What's tested

Tested here, on Linux, with Celemony's ARA test plug-in, which checks every ARA call:

- it loads the plug-in and sets up the clip;
- the clip plays at the right place on the timeline, at the right level, with silence before and after;
- Melodyne's edits (the ARA archive) are saved and restored with the project;
- ARA starts and shuts down cleanly.

Not tested yet: Melodyne itself, inside Bitwig, or on Windows or macOS. The Windows build is cross-compiled
and only checked for its exports and dependencies.

## Limits of this prototype

- One clip per track, WAV files only, at a constant tempo.
- The clip's file and placement are set through the session file, not read from Bitwig yet. Next step: the
  mod reads the clip itself (file, start, offset, length) and keeps the file up to date as clips move.
- Bitwig's own fades, stretching and clip gain aren't passed to Melodyne.

## Licences

The bridge uses the [ARA SDK](https://github.com/Celemony/ARA_SDK) (Apache 2.0), the
[VST3 SDK](https://github.com/steinbergmedia/vst3sdk) (MIT), [CLAP](https://github.com/free-audio/clap)
(MIT), [dr_wav](https://github.com/mackron/dr_libs) (public domain / MIT-0) and
[tinyfiledialogs](https://sourceforge.net/projects/tinyfiledialogs/) (zlib). Melodyne is a product of
Celemony, not part of this project.
