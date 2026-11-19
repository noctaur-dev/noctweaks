# Piano Roll: Expression Controls

Part of **NocTweaks** by noctaur. Note expression tools for the piano roll.

## What it adds

- **Expression Shape** section in the Points Inspector (and the note expression Inspector): pick a shape
  (sine, triangle, saw up/down, square, ramps, random, flat) and draw it over the selected expression
  points, with Cycles, Amount, Skew, Grow/Fade, In/Out, live **Melt** and **Simplify**: redraws the selection with
  far fewer points that keep its shape, the way you'd draw it by hand with Alt-curved segments (a point on
  each peak and trough and one between them, each segment bent to fit). Higher amounts let go of small wiggles. Every change is one undo step.
- **Copy an expression shape onto automation:** select expression points and click *Copy Shape* in the
  Inspector (or right-click › *Copy Expression Shape*). Then select an automation point: its Points Inspector
  has a *Paste Expression Shape* section with a **Depth** (*As Copied*, or how far the shape swings, as a
  share of the target's range, in 5 % steps) and a *Paste Shape* button; its right-click menu has the same at
  fixed depths. The shape starts at that point's time and level, curved segments included.
- Shape items in the note editor's right-click menu and in the menu for selected notes and points.
- **Ctrl+drag copies expression points**, like notes: the originals stay put, a preview of the copy follows
  the mouse, and on release the copied shape is written where you drop it, smoothed into the points around it.
- The editor's **edit modes** as a strip of buttons beside the mode button, so each is one click away.
- The note editor opens on the **piano roll** instead of the drum grid on tracks with a drum machine
  (picking the drum grid by hand still works).

## Notes

- Bitwig 6.1.7 (Windows) only for now; on other versions Patchbay shows it as
  not available.
- The shape icons are the mod's own SVGs (in `icons/`).
