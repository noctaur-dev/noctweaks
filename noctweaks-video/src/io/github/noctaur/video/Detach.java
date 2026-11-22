package io.github.noctaur.video;

import bitwig.obf.dAd;   // a button
import bitwig.obf.dWb;   // a row or column of widgets
import bitwig.obf.dns;   // a widget, as Bitwig types it
import bitwig.obf.dve;   // a widget
import bitwig.obf.dwh;   // a window
import com.bitwig.base.geom.IBe;   // an orientation (cfM: horizontal, MRY: vertical)
import com.bitwig.base.geom.tfK;   // a widget size

import java.util.ArrayList;
import java.util.List;

/**
 * The detached video: a Bitwig window of its own (as Bitwig's controller display windows are: dwh.XBs(),
 * FzB(content), UNj(title), t_(shown)) showing the same picture as the panel, to put anywhere on screen. Pinned,
 * it belongs to Bitwig's main window (MRY(owner)), so it stays in front of it while you work elsewhere in
 * Bitwig; unpinned it's an ordinary window. The panel and the window share their decoder.
 */
final class Detach {
   private Detach() {
   }

   private static dwh window;
   private static dwh owner;
   private static boolean pinned = true;
   private static final List<dAd> detachButtons = new ArrayList<>();
   private static dAd pinButton;

   /** The panel's content: a thin bar with the Detach button, then the picture. */
   static dve panelContent() {
      dAd detach = button(label(), () -> toggle(null));
      detachButtons.add(detach);
      VideoPanel picture = VideoPanel.create();
      picture.onTick(Detach::syncLabels);
      return column(bar(detach), picture);
   }

   static boolean isOpen() {
      try {
         return window != null && window.uev();
      } catch (Throwable t) {
         return false;
      }
   }

   private static String label() {
      return isOpen() ? "Attach" : "Detach";
   }

   static void toggle(dve from) {
      try {
         if (isOpen()) {
            window.t_(false);
         } else {
            open(from);
         }
      } catch (Throwable t) {
         Video.log("Video: detaching", t);
      }
      syncLabels();
   }

   private static void open(dve from) {
      if (owner == null) {
         for (dAd b : detachButtons) {   // Bitwig's main window: the one the panel is in
            Object win = b.ZTl() ? b.jg_() : null;
            if (win instanceof dwh w) {
               owner = w;
               break;
            }
            if (win != null) {
               Video.log("Video: the panel's window is a " + win.getClass().getName() + ", not a dwh", null);
            }
         }
      }
      if (window != null) {
         try {   // shown again, as it was left
            window.t_(true);
            return;
         } catch (Throwable t) {
            window = null;   // gone (closed by the window's own button): a new one
         }
      }
      dwh w = dwh.XBs();
      pinButton = button(pinLabel(), Detach::togglePin);
      VideoPanel picture = VideoPanel.create();
      w.FzB(column(bar(pinButton), picture));
      w.UNj("Video");
      if (pinned && owner != null) {
         w.MRY(owner);
      }
      w.t_(true);
      window = w;
      Video.log("Video: detached window opened (" + (owner == null ? "no main window found, so it can't be pinned"
         : pinned ? "pinned to " + owner.getClass().getSimpleName() : "not pinned") + ")", null);
   }

   private static String pinLabel() {
      return pinned ? "Unpin" : "Pin on Top";
   }

   private static void togglePin() {
      pinned = !pinned;
      try {
         if (window != null) {
            boolean shown = isOpen();
            window.t_(false);
            window.MRY(pinned ? owner : null);   // a window owned by Bitwig's stays in front of it
            if (shown) {
               window.t_(true);
            }
         }
         if (pinButton != null) {
            pinButton.IwV(pinLabel());
         }
      } catch (Throwable t) {
         Video.log("Video: pinning", t);
      }
   }

   /** The panel's button says Attach while the window is open (closing it with its own button counts too). */
   private static String shownLabel;

   static void syncLabels() {
      String l = label();
      if (l.equals(shownLabel)) {
         return;
      }
      shownLabel = l;
      for (dAd b : detachButtons) {
         try {
            b.IwV(l);
         } catch (Throwable t) {
            Video.log("Video: detach button", t);
         }
      }
   }

   // ---------- widgets ----------

   private static dAd button(String text, Runnable click) {
      dAd b = dAd.gG();
      b.IwV(text);
      b.Hnt(() -> {
         try {
            click.run();
         } catch (Throwable t) {
            Video.log("Video: button", t);
         }
      });
      return b;
   }

   /** A bar with the buttons at its right end. */
   private static dve bar(dve... buttons) {
      dWb row = dWb.Hnt(IBe.cfM);
      row.cfM(Space.create(), 1f);
      for (dve b : buttons) {
         row.HL(b);
      }
      return row;
   }

   /** The bar above the picture, which takes the rest of the space. */
   private static dve column(dve bar, dve picture) {
      dWb col = dWb.Hnt(IBe.MRY);
      col.HL(bar);
      col.cfM(picture, 1f);
      return col;
   }

   /** Empty space that stretches. */
   static final class Space extends dve {
      static Space create() {
         Space s = new Space();
         s.H_();
         return s;
      }

      @Override
      public tfK mL_() {
         return tfK.cfM();
      }

      @Override
      public tfK iH_() {
         return tfK.cfM();
      }

      @Override
      public tfK nj_() {
         return tfK.MRY();
      }

      @Override
      public dve MJ() {
         return this;
      }

      @Override
      public dns UZr() {
         return this;
      }
   }
}
