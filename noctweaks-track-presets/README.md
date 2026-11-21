# Tracks: Track Presets

Part of **NocTweaks** by noctaur. Save whole tracks as presets and add them back into any project.
**Experimental.**

▶ [Watch the full showcase](https://github.com/noctaur-dev/noctweaks/blob/main/noctweaks-track-presets/images/tp-showcase.mp4) (1 minute video)

## What it adds

- A **Tracks** button in the browser sidebar listing your track presets. Drag one onto the tracks: a preset
  with one track adds that track, not a group.

  ![Dragging a track preset from the Tracks button in the side browser](images/tp-drag-drop.gif)

- **Save Track as Track Preset** in the track header's right-click menu: the track with its devices, plugin
  states, mixer settings, colour, name and clips.

  ![Saving a track as a track preset](images/tp-save.gif)

- **Saving again under the same name** asks first: *Replace* it (the old version is kept in
  `Track Presets/.previous/`), or *Save as New Variant* with a number added.

  ![Replacing a track preset or saving a new variant](images/tp-replace-variant.gif)

- **Track presets in the new track browser:** press *+* under the tracks and the browser that opens has the
  Tracks button too. Pick a preset and press OK (or double-click it), and the preset's tracks are added in place
  of the new, empty track. Devices work there as usual. Switch it off from the browser's right-click menu
  (*Track Presets in New Track Browser*). The device popups don't get the button.

  ![Adding a track preset from the new track browser](images/tp-new-track-browser.gif)

- **Add Track Preset Below…** in the track header menu, and **Add Track Preset** in the arranger's and panels'
  background menus. Each has an **All** submenu with every preset, and a **Collections** submenu with each
  collection you've added presets to.

  ![Add Track Preset Below…](images/tp-add-below.gif)

  ![The Collections and All submenus of Add Track Preset](images/tp-collections-menu.gif)

- **Hide Tracks Button** / **Show Tracks Button** in the browser's right-click menu.

## Notes

- Presets are saved in your Bitwig library, in `My Library/Track Presets/`, one `.bwproject` file each, so you
  can back them up, share them or sort them into folders.
- With [Browser: Collections and Icons](../noctweaks-browser-improvements) installed, the Tracks button's icon
  can be changed from the browser's *Sidebar Buttons* menu. Each mod works without the other.
- Built against Bitwig 6.1.4, Patchbay's reference version, so Patchbay can run it on every Bitwig version it
  supports (6.0 to 6.1.7) on Windows, macOS and Linux. So far it has only been tested on Windows with 6.1.7.
- Not affiliated with Bitwig: an unofficial community project, not made, endorsed or supported by Bitwig GmbH.
