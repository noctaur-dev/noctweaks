# NocTweaks

Mods for [Bitwig Studio](https://www.bitwig.com) by noctaur, for the
[Patchbay](https://github.com/shaboogen-dev/Patchbay) mod loader.

| Mod | What it does |
| --- | --- |
| [**Global Track View**](noctweaks-global-track-view) | Edit any track in the piano roll without going back to the arranger: an *Edit Tracks* menu, layered editing on when several tracks are open, and the Track List panel in the Edit screen. |
| [**Expression Controls**](noctweaks-expression-controls) | Draw shapes over note expression points, simplify and melt them, copy an expression shape onto automation, Ctrl+drag to copy points, and the edit modes one click away. |
| [**Browser Improvements**](noctweaks-browser-improvements) | Folders and nested collections, your own icons (Lucide), collections pinned as quick sources, a Tracks button for track presets, and each plugin listed once. |

Each mod is separate: install only the ones you want.

## Install

1. Install [Patchbay](https://github.com/shaboogen-dev/Patchbay/releases) and set it up for your Bitwig.
2. Download a mod's zip from [Releases](../../releases) (each mod has its own release).
3. In Patchbay, **Add Mod…** and pick the zip, then start Bitwig through Patchbay.

To update, add the newer zip the same way.

## Bitwig versions

Bitwig **6.1.7** (Windows) only for now. The mods follow Patchbay: when Patchbay moves to a new Bitwig
version, they'll be updated to match.

## Building from source

Each folder is a Patchbay mod (`mod.json`, `build.json`, `src/`, `package/`). Put it in a Patchbay build
workspace's `Mods/` folder and run `python3 BML/tools/build_mod.py <mod id>`; see Patchbay's
[building docs](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/building.md).

## Licence

[MIT](LICENSE). The Browser mod includes [Lucide](https://lucide.dev) icons (ISC).
