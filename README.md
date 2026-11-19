# NocTweaks

Mods for [Bitwig Studio](https://www.bitwig.com), for the
[Patchbay](https://github.com/shaboogen-dev/Patchbay) mod loader.

| Mod | What it does |
| --- | --- |
| [**Global Track View**](noctweaks-global-track-view) | Edit any track in the piano roll without going back to the arranger: an *Edit Tracks* menu, layered editing on when several tracks are open, and the Track List panel in the Edit screen. |
| [**Expression Controls**](noctweaks-expression-controls) | Draw shapes over note expression points, simplify and melt them, copy an expression shape onto automation, Ctrl+drag to copy points, and the edit modes one click away. |
| [**Browser Improvements**](noctweaks-browser-improvements) | Folders and nested collections, your own icons (Lucide), collections pinned as quick sources, a Tracks button for track presets, and each plugin listed once. |

Each mod is separate: install only the ones you want.

> **Not affiliated with Bitwig.** NocTweaks is an unofficial, community-made mod, built via the known LLM patches and then ported over to PatchBay.
> It isn't made, endorsed or supported by Bitwig GmbH. "Bitwig" and "Bitwig Studio" are
> trademarks of Bitwig GmbH. Use the mods at your own risk, and please don't send Bitwig support requests
> about problems they cause: open an issue here instead. Using any community patch is still uncharted territory, proceed with caution. 

## Install

1. Install [Patchbay](https://github.com/shaboogen-dev/Patchbay/releases) and set it up for your Bitwig.
2. Download a mod's zip from [Releases](../../releases) (each mod has its own release).
3. In Patchbay, **Add Mod…** and pick the zip, then start Bitwig through Patchbay.

To update, add the newer zip the same way.

## Tested with

Only tested alongside these Patchbay frameworks, all enabled together with the three NocTweaks mods:

| Framework | Version |
| --- | --- |
| Actions and Menus | 0.8.0-dev.1 |
| Browser | 0.3.0-dev.1 |
| Inspector | 0.3.0-dev.2 |
| Nitro Devices | 1.3.0-dev.3 |
| Notifications | 0.3.0-dev.1 |
| Panels | 0.2.0-dev.2 |
| Piano Roll | 0.2.0-dev.1 |
| Settings | 1.0.0 |

They haven't been tested or verified with any other Patchbay mods or frameworks (Plug-in Panels and Themes
included). Most should be fine, but mods that change the same parts of Bitwig (the browser, the piano roll's
expression editing, the Inspector) could clash. If something breaks with another mod enabled, please open an
issue saying which one.

## Bitwig versions

Bitwig **6.1.7** (Windows) only for now. The mods follow Patchbay: when Patchbay moves to a new Bitwig
version, they'll be updated to match.

## Building from source

Each folder is a Patchbay mod (`mod.json`, `build.json`, `src/`, `package/`). Put it in a Patchbay build
workspace's `Mods/` folder and run `python3 BML/tools/build_mod.py <mod id>`; see Patchbay's
[building docs](https://github.com/shaboogen-dev/Patchbay/blob/main/docs/sdk/building.md).

## AI Disclosure

Obviously, most of the recent Bitwig modification scene is done with LLM assistance. I believe in being open about AI use for those that choose to abstain from engaging with it. 
I spent about a week getting these mods working with Claude, but it would not have been possible without the LLM help. 

## Support

Obviously, I expect nothing for this, but those Claude subscriptions are not cheap ;) ...so if you do want to donate, here you go
[![Support me on Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20noctaur-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/noctaur)

## Licence

[MIT](LICENSE). The Browser mod includes [Lucide](https://lucide.dev) icons (ISC).

The MIT licence covers NocTweaks' own code and files only. Bitwig Studio's source code is proprietary and
belongs to Bitwig GmbH: none of it is included here, and nothing here grants any rights to it. These mods and
other community additions aren't affiliated with or part of the real product; they're unofficial, made by
fans, and meant only as a fun resource for the Bitwig community.
