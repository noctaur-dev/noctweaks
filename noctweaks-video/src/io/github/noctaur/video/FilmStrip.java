package io.github.noctaur.video;

import bitwig.obf.AQw;   // a value a menu row can show and set (a checkbox row)
import bitwig.obf.dKW;   // a menu
import bitwig.obf.dpN;   // Bitwig's UI event loop
import bitwig.obf.dve;   // a widget
import bitwig.obf.foz;   // a value listener

import java.lang.ref.WeakReference;

/**
 * How the film strip sits on a video clip: **separate** (the default: the waveform is moved down and the strip
 * takes the top of the clip, so neither covers the other) or **over the waveform**, at an opacity. Set from the
 * Video menus (right-click a clip or the arranger) and Settings > User Interface > Video.
 */
final class FilmStrip {
   private FilmStrip() {
   }

   static final int SEPARATE = 0, OVER = 1;
   static final String[] MODES = { "Separate from the waveform", "Over the waveform" };
   private static final int[] OPACITIES = { 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 };
   private static final int DEFAULT_OPACITY = 40;

   // ---------- settings ----------

   static int mode() {
      try {
         int m = Integer.parseInt(Video.MOD.settings().get("strip-mode", "0"));
         return m == OVER ? OVER : SEPARATE;
      } catch (Throwable t) {
         return SEPARATE;
      }
   }

   static boolean separate() {
      return mode() == SEPARATE;
   }

   static void setMode(int m) {
      save("strip-mode", Integer.toString(m == OVER ? OVER : SEPARATE));
   }

   /** The strip's opacity over the waveform, 0 to 100 (separate, it's always solid). */
   static double opacityPercent() {
      try {
         double v = Double.parseDouble(Video.MOD.settings().get("strip-opacity", Integer.toString(DEFAULT_OPACITY)));
         return Math.max(0, Math.min(100, v));
      } catch (Throwable t) {
         return DEFAULT_OPACITY;
      }
   }

   static void setOpacityPercent(double v) {
      save("strip-opacity", Long.toString(Math.round(Math.max(0, Math.min(100, v)))));
   }

   /** How opaque the strip is drawn now: solid when separate. */
   static double alpha() {
      return separate() ? 1 : opacityPercent() / 100.0;
   }

   private static void save(String key, String value) {
      try {
         if (value.equals(Video.MOD.settings().get(key, null))) {
            return;
         }
         Video.MOD.settings().put(key, value);
         Video.MOD.settings().flush();
      } catch (Throwable t) {
         Video.log("Video: could not save the film strip setting", t);
      }
      Check.changed();
      repaint();
   }

   // ---------- sizes ----------

   /**
    * Separate: how much of the top of the waveform's area the strip takes (the waveform gets the rest). Half,
    * up to a strip 60 pixels high; on short clips just a thin film edge.
    */
   static double separateBand(double h) {
      if (h < 24) {
         return h >= 10 ? 5 : 0;
      }
      return Math.min(60, Math.floor(h * 0.5));
   }

   /** Over the waveform: the whole of the clip's body, the waveform showing through at the strip's opacity. */
   static double[] overBand(double y, double h) {
      return new double[] { y, h };
   }

   // ---------- repainting ----------

   private static WeakReference<dve> arranger = new WeakReference<>(null);

   /** The widget the strips were last painted in, repainted when a setting changes. */
   static void paintedIn(dve w) {
      if (w != null && arranger.get() != w) {
         arranger = new WeakReference<>(w);
      }
   }

   static void repaint() {
      try {
         dpN.TVZ().asyncExec(() -> {
            try {
               dve w = arranger.get();
               if (w != null && w.ZTl()) {
                  w.jbi();
               }
            } catch (Throwable t) {
               Video.log("Video: repainting the film strips", t);
            }
         });
      } catch (Throwable t) {
         Video.log("Video: repainting the film strips", t);
      }
   }

   // ---------- the Video menus ----------

   /** A Film Strip submenu: where it goes, and its opacity over the waveform. */
   static void menu(dKW m) {
      dKW sub = m.Hnt("Film Strip");
      sub.cfM(MODES[SEPARATE], new Check(() -> mode() == SEPARATE, () -> setMode(SEPARATE)));
      sub.cfM(MODES[OVER], new Check(() -> mode() == OVER, () -> setMode(OVER)));
      sub.ELS();
      dKW op = sub.Hnt("Opacity Over the Waveform");
      for (int pct : OPACITIES) {
         op.cfM(pct + "%", new Check(() -> Math.round(opacityPercent()) == pct, () -> {
            setOpacityPercent(pct);
            setMode(OVER);   // picking an opacity means over the waveform
         }));
      }
   }

   /**
    * A checkbox row that shows a state and, when ticked, sets it (unticking does nothing: pick another). It has
    * to be observable: Bitwig's checkbox rows only bind to (and report clicks to) a value that tells them when it
    * changes; otherwise they just show its first state.
    */
   private static final class Check implements AQw {
      private static final java.util.Set<Check> LIVE =
         java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

      /** Every row still around shows the new state. */
      static void changed() {
         Check[] all;
         synchronized (LIVE) {
            all = LIVE.toArray(new Check[0]);
         }
         for (Check c : all) {
            c.fire();
         }
      }

      private final java.util.function.BooleanSupplier on;
      private final Runnable pick;
      private final java.util.List<foz> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

      Check(java.util.function.BooleanSupplier on, Runnable pick) {
         this.on = on;
         this.pick = pick;
      }

      @Override
      public boolean getBoolValue() {
         return on.getAsBoolean();
      }

      @Override
      public void setBoolValue(boolean b) {
         try {
            if (b) {
               pick.run();
            } else {
               fire();   // stays ticked: one of them always is
            }
         } catch (Throwable t) {
            Video.log("Video: film strip menu", t);
         }
      }

      private void fire() {
         for (foz l : listeners) {
            try {
               l.eventOccurred(this);
            } catch (Throwable t) {
               Video.log("Video: film strip menu", t);
            }
         }
      }

      @Override
      public Object getValue() {
         return getBoolValue();
      }

      @Override
      public boolean isSettable() {
         return true;
      }

      @Override
      public void setValue(Object v) {
         if (v instanceof Boolean b) {
            setBoolValue(b);
         }
      }

      @Override
      public boolean isObservable() {
         return true;
      }

      @Override
      public void addListener(foz l) {
         if (l != null) {
            listeners.add(l);
            LIVE.add(this);
         }
      }

      @Override
      public void removeListener(foz l) {
         listeners.remove(l);
         if (listeners.isEmpty()) {
            LIVE.remove(this);
         }
      }
   }
}
