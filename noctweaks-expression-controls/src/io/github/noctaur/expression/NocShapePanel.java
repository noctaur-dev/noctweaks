package io.github.noctaur.expression;

import bitwig.obf.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;

/**
 * NocTweaks: the "Expression Shape" section of the Points Inspector (note
 * expression points). Shapes run node to node on each selected note, riding on the curve already there.
 *
 * Shape icons plus Bitwig's own number fields (Cycles, Amount, Skew, Grow/Fade). Every change is
 * written straight away to the selected notes (or between selected points), on the note expression
 * the editor is showing, so you can drag a value and watch the curve follow. Tweaks on the same notes
 * merge into one undo step. It sits after Bitwig's own sections.
 *
 * Hooks:
 *   aLg.b_(dWe)               -> inspector(panel, box)   (the note Inspector is built; patcher)
 *   NocModes.drawn(...)     -> seen(button)            (the editor's edit mode button repaints)
 *   NocShapes.menu(...)     -> remember(editor)        (the note editor's right-click menu)
 */
public final class NocShapePanel {
   private NocShapePanel() {
   }

   private static volatile WeakReference<dDG> button = new WeakReference<>(null);
   private static volatile WeakReference<aOp> editor = new WeakReference<>(null);
   private static volatile Object lastExpression;
   private static volatile boolean disabled;
   private static volatile boolean loggedSearch;
   private static volatile long lastWrite;

   static volatile NocShapes.Shape shape;   // null until a shape is picked for this selection
   static volatile int cycles = 4;

   private static final NocShapes.Shape[] FIRST = {NocShapes.Shape.SINE, NocShapes.Shape.TRIANGLE,
      NocShapes.Shape.SAW_UP, NocShapes.Shape.SAW_DOWN, NocShapes.Shape.SQUARE};
   private static final NocShapes.Shape[] SECOND = {NocShapes.Shape.RAMP_UP, NocShapes.Shape.RAMP_DOWN,
      NocShapes.Shape.RANDOM, NocShapes.Shape.FLAT};

   private static final java.util.Set<NocText> LIVE_TEXT = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
   private static volatile String status = "Select notes, then pick a shape";

   /** The Amount field of the panel on screen (its range follows the expression). */
   private static volatile WeakReference<NocNumber> amountValue = new WeakReference<>(null);
   private static volatile WeakReference<dPc> amountField = new WeakReference<>(null);

   // ------------------------------------------------------------------ editor tracking

   /** The note editor's edit mode button repainted: remember that editor and its expression. */
   static void seen(dDG n) {
      try {
         if (!(n.vYz() instanceof lFl now)) {
            return;   // the audio editor's modes
         }
         if (!now.equals(lastExpression)) {
            lastExpression = now;
            expressionChanged(now);
         }
         if (button.get() == n && editor.get() != null) {
            return;
         }
         dDG current = button.get();
         if (current != null && current != n && current.mm2() && !n.mm2()) {
            return;   // keep the editor whose expression editing is switched on
         }
         aOp found = find(n);
         button = new WeakReference<>(n);
         if (found != null) {
            editor = new WeakReference<>(found);
         }
      } catch (Throwable t) {
         Expr.log("Shape panel: could not track the note editor", t);
      }
   }

   /** The note editor this button belongs to: up its parents, then down each one looking for the editor. */
   private static aOp find(dDG n) {
      StringBuilder chain = new StringBuilder();
      Object w = n;
      for (int i = 0; i < 40 && w != null; i++) {
         if (w instanceof aOp a) {
            return a;
         }
         if (w instanceof dwF d) {
            try {
               List<?> below = d.ymo(aOp.class);
               if (!below.isEmpty()) {
                  return (aOp)below.get(0);
               }
            } catch (Throwable ignored) {
            }
         }
         chain.append(w.getClass().getName()).append(" > ");
         Object up;
         try {
            up = ((dxu)w).oRH();
         } catch (Throwable t) {
            up = null;
         }
         w = up == w ? null : up;
      }
      if (!loggedSearch) {
         loggedSearch = true;
         Expr.log("Shape panel: note editor not found from the mode button: " + chain, null);
      }
      return null;
   }

   /** The note editor's right-click menu opened (also a sure way to know the editor). */
   static void remember(aOp a) {
      if (a != null) {
         editor = new WeakReference<>(a);
      }
   }

   static void status(String s) {
      status = s;
      List<NocText> texts;
      synchronized (LIVE_TEXT) {
         texts = new java.util.ArrayList<>(LIVE_TEXT);
      }
      for (NocText t : texts) {
         t.changed();
      }
   }

   private static void expressionChanged(lFl e) {
      try {
         NocNumber v = amountValue.get();
         if (v != null) {
            double max = amountMax(e);
            v.range(0, max);
            dPc f = amountField.get();
            if (f != null) {
               f.IvP(max);
            }
            v.show(NocShapes.amount(e));
         }
      } catch (Throwable t) {
         Expr.log("Shape panel: could not follow the expression", t);
      }
   }

   /** The expression the editor is showing (or was last showing). */
   static lFl expression() {
      try {
         dDG n = button.get();
         if (n != null && n.vYz() instanceof lFl e) {
            return e;
         }
         aOp a = editor.get();
         if (a != null) {
            nNL overlay = a.QDQ().UnB().tWW();
            if (overlay != null && overlay.iFu() != null && overlay.iFu().getValue() instanceof lFl e) {
               return e;
            }
         }
      } catch (Throwable ignored) {
      }
      return lastExpression instanceof lFl e ? e : null;
   }

   // ------------------------------------------------------------------ inspector

   /** End of aLg.b_(dWe): add the section to the note Inspector. */
   public static void inspector(Object panel, Object box) {
      try {
         if (disabled || !(panel instanceof aMo g) || !(box instanceof dXL b)) {
            return;
         }
         try {
            if (g.UIe().BBg()) {
               return;   // audio events
            }
         } catch (Throwable ignored) {
         }
         // Shapes run from expression points now: the section is in the Points Inspector, not here.
         panelPoints = null;
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Shape panel disabled", t);
      }
   }

   /** The expression points the Points Inspector is showing (null while the note Inspector is up). */
   private static volatile List<Object> panelPoints;

   /**
    * End of aGB.IYM(dWe): the Points Inspector. Only for note expression points (aKm), not automation:
    * the shape then runs from the selected point to the next point, or to the end of its note.
    */
   public static void points(Object panel, Object box) {
      try {
         if (disabled || !(box instanceof dXL b)) {
            return;
         }
         if (!(panel instanceof aLw m)) {
            if (panel instanceof aHH h) {
               automationPoints(h, b);   // automation points: the paste section
            }
            return;
         }
         List<Object> pts = new java.util.ArrayList<>();
         try {
            pts.addAll(NocShapes.unwrap(new java.util.ArrayList<Object>(m.frp().bK_())));
         } catch (Throwable t) {
            Expr.log("Shape panel: could not read the selected points", t);
         }
         if (pointsLogged++ < 10 || pts.size() > 2 && pointsLogged % 20 == 0) {
            StringBuilder kinds = new StringBuilder();
            for (Object o : pts) {
               kinds.append(o == null ? "null" : o.getClass().getName()).append(' ');
            }
            Expr.log("Shape panel (points): " + pts.size() + " point(s): " + kinds, null);
         }
         panelPoints = pts;
         add(m, b);
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Shape panel (points) disabled", t);
      }
   }

   private static int pointsLogged;

   /**
    * Every selected expression point: the Inspector's own list holds only the point it shows (and the one after
    * it), so the editor's whole selection is added.
    */
   static List<Object> selectedPoints() {
      return selectedPoints(null);
   }

   /** As above, reading this Points Inspector's selection now (the count in its title), not when it was built. */
   static List<Object> selectedPoints(Object panel) {
      java.util.Set<Object> out = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      int live = -1;
      if (panel instanceof aLw m) {
         try {
            List<Object> now = new java.util.ArrayList<Object>(m.frp().bK_());
            live = now.size();
            out.addAll(NocShapes.unwrap(now));
         } catch (Throwable t) {
            Expr.log("Copy: could not read the Inspector's points", t);
         }
      }
      List<Object> pts = panelPoints;
      if (pts != null) {
         out.addAll(NocShapes.unwrap(new java.util.ArrayList<>(pts)));
      }
      try {
         aOp a = editor.get();
         if (a != null) {
            out.addAll(NocShapes.unwrap(NocShapes.selectionItems(a.sM3().GwM().mS_()))); // the editor's selection
         }
      } catch (Throwable t) {
         Expr.log("Copy: could not read the editor's selection", t);
      }
      Expr.log("Copy: " + out.size() + " selected item(s) (Inspector now: " + live + ", when built: " + (pts == null ? -1 : pts.size()) + ")", null);
      return new java.util.ArrayList<>(out);
   }

   /** The paste depth: 0 = as copied, else the swing as a share of the range (percent). */
   static volatile int pasteDepth = 0;

   /** The Points Inspector for automation points: "Paste Expression Shape", with its depths. */
   private static void automationPoints(aHH panel, dXL box) {
      try {
         List<Object> items = new java.util.ArrayList<>();
         try {
            items.addAll(new java.util.ArrayList<Object>(panel.Gak().bK_())); // the selected points
         } catch (Throwable t) {
            Expr.log("Paste: could not read the selected automation points", t);
         }
         List<oq1> pts = NocPaste.automationPoints(items);
         if (pts.isEmpty()) {
            return;
         }
         aMZ g = (aMZ) (Object) panel;
         Consumer<dXL> body = d -> {
            d.IvP(dvR.beI((JW) new NocText(NocPaste::describe, false)));
            if (NocPaste.has()) {
               NocNumber depth = new NocNumber(pasteDepth, 0, 100, true, v -> pasteDepth = (int) v);
               d.IvP(aMZ.beI("Depth", "How far the pasted shape moves: As Copied, or so its swing fills this much of the range",
                  field(g, depth, 0, 100, 5, v -> v < 0.5 ? "As Copied" : Math.round(v) + " %")));
               d.IvP(aMZ.beI("Paste", "Paste the copied shape from the selected point, at this depth",
                  NocChoice.create(new Object[]{"Paste Shape"}, o -> (String) o, null, o -> NocPaste.paste(pts, pasteDepth))));
            } else {
               d.IvP(dvR.beI((JW) new NocText(() -> "Right-click selected expression points › Copy Expression Shape", false)));
            }
            NocText line = new NocText(() -> status, true);
            LIVE_TEXT.add(line);
            d.IvP(dvR.beI((JW) line));
         };
         box.IvP(g.beI((JW) new ATz("Paste Expression Shape"), body, null));
      } catch (Throwable t) {
         Expr.log("Paste: the Inspector section could not be added", t);
      }
   }

   private static void add(aMZ g, dXL b) {
      Consumer<dXL> body = d -> build(g, d);
      b.IvP(g.beI((JW)new ATz("Expression Shape"), body, null));
   }

   private static void build(aMZ g, dXL body) {
      try {
         // The panel got rebuilt for a new selection: start fresh, with every value back at its default and
         // nothing carried over from the points edited before. (A rebuild right after our own write is just
         // the Inspector refreshing; that keeps the same points and values.)
         if (System.currentTimeMillis() - lastWrite > 1000) {
            freshStart();
         }

         NocChoice[] rows = new NocChoice[2];
         rows[0] = NocChoice.create(FIRST, o -> ((NocShapes.Shape)o).label, o -> icon((NocShapes.Shape)o),
            has(FIRST, shape) ? shape : null, o -> {
               shape = (NocShapes.Shape)o;
               rows[1].af((Object)null);
               apply();
            }, null);
         rows[1] = NocChoice.create(SECOND, o -> ((NocShapes.Shape)o).label, o -> icon((NocShapes.Shape)o),
            has(SECOND, shape) ? shape : null, o -> {
               shape = (NocShapes.Shape)o;
               rows[0].af((Object)null);
               apply();
            }, null);
         body.IvP(rows[0]);
         body.IvP(rows[1]);

         NocNumber cyc = new NocNumber(cycles, 1, 32, true, v -> {
            cycles = (int)v;
            apply();
         });
         body.IvP(aMZ.beI("Cycles", "How many times the shape repeats over the note",
            field(g, cyc, 1, 32, v -> String.valueOf((int)Math.rint(v)))));

         lFl e = expression();
         double max = amountMax(e);
         NocNumber amt = new NocNumber(e == null ? 25 : NocShapes.amount(e), 0, max, false, v -> {
            lFl now = expression();
            if (now != null) {
               NocShapes.AMOUNT.put(now, v);
            }
            apply();
         });
         dPc amountBox = field(g, amt, 0, max, max <= 24 ? 0.1 : 1, NocShapePanel::amountText);
         amountValue = new WeakReference<>(amt);
         amountField = new WeakReference<>(amountBox);
         body.IvP(aMZ.beI("Amount", "Semitones for Pitch; for Gain how far it dips (100 % = down to -inf dB); percent otherwise",
            amountBox));

         NocNumber skew = new NocNumber(NocShapes.skew * 100, -100, 100, false, v -> {
            NocShapes.skew = v / 100.0;
            apply();
         });
         body.IvP(aMZ.beI("Skew", "Squeeze the cycles toward the start (negative) or the end (positive) of the note",
            field(g, skew, -100, 100, v -> Math.abs(v) < 0.5 ? "Even" : (v < 0 ? "Start " : "End ") + Math.round(Math.abs(v)) + " %")));

         NocNumber grow = new NocNumber(NocShapes.growth * 100, -100, 100, false, v -> {
            NocShapes.growth = v / 100.0;
            apply();
         });
         body.IvP(aMZ.beI("Grow", "Grow into the shape over the note (positive) or fade it out (negative)",
            field(g, grow, -100, 100, v -> Math.abs(v) < 0.5 ? "Steady" : (v < 0 ? "Fade " : "Grow ") + Math.round(Math.abs(v)) + " %")));

         NocNumber edges = new NocNumber(NocShapes.edges * 100, 0, 50, false, v -> {
            NocShapes.edges = v / 100.0;
            apply();
         });
         body.IvP(aMZ.beI("Fade", "Fade the shape in at the start and out at the end (how much of it each fade takes)",
            field(g, edges, 0, 50, v -> v < 0.5 ? "Off" : "In/Out " + Math.round(v) + " %")));

         // Melt is live: drag it and the lines between the selected points soften as you go (0 = straight).
         NocNumber melt = new NocNumber(NocShapes.meltAmount * 100, 0, 95, false, v -> {
            NocShapes.meltAmount = v / 100.0;
            throttled(NocShapePanel::meltNow);
         });
         body.IvP(aMZ.beI("Melt", "Soft S-curves between the selected points (like auto-fades); 0 = straight lines",
            field(g, melt, 0, 95, v -> v < 0.5 ? "Off" : Math.round(v) + " %")));

         // Simplify is live too: redraws the selected stretch with as few points as still follow it.
         NocNumber simplify = new NocNumber(NocShapes.simplifyAmount * 100, 0, 100, false, v -> {
            NocShapes.simplifyAmount = v / 100.0;
            throttled(NocShapePanel::meltNow);
         });
         body.IvP(aMZ.beI("Simplify", "Redraw the selected stretch with as few points as still follow it (higher = fewer points, looser fit); 0 = off",
            field(g, simplify, 0, 100, v -> v < 0.5 ? "Off" : Math.round(v) + " %")));

         body.IvP(aMZ.beI("Copy", "Copy this stretch of the expression, to paste onto automation (select an automation point, then Paste Expression Shape)",
            NocChoice.create(new Object[]{"Copy Shape"}, o -> (String) o, null, o -> {
               lFl ex = expression();
               if (ex != null) {
                  NocPaste.copy(selectedPoints(g), ex, cX.beI(ex));
               }
            })));

         NocText line = new NocText(() -> status, true);
         LIVE_TEXT.add(line);
         body.IvP(dvR.beI((JW)line));
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Shape panel could not be built", t);
      }
   }

   /** Every value back at its default, and the points edited before let go. */
   private static void freshStart() {
      NocShapes.endSession();
      shape = null;
      cycles = 4;
      NocShapes.skew = 0;
      NocShapes.growth = 0;
      NocShapes.edges = 0;
      NocShapes.meltAmount = 0;
      NocShapes.simplifyAmount = 0;
      NocShapes.AMOUNT.clear();
      status = "Pick a shape, or use Melt or Simplify";
   }

   /**
    * One of Bitwig's own Inspector number fields, showing a NocNumber as one plain value ("4", "35 %",
    * "Grow 40 %"): drag it up or down, or double-click to type a number.
    */
   private static dPc field(aMZ g, NocNumber value, double min, double max, java.util.function.DoubleFunction<String> text) {
      return field(g, value, min, max, 1, text);
   }

   private static dPc field(aMZ g, NocNumber value, double min, double max, double step, java.util.function.DoubleFunction<String> text) {
      dPc f = g.beI((Clo)value, (NVD)null, (NVD)null);
      String widest = "";
      for (double v : new double[]{min, max, (min + max) / 2}) {
         String t = text.apply(v);
         if (t.length() > widest.length()) {
            widest = t;
         }
      }
      f.beI((dPg)new NocLayout(text, step, widest)); // one value, not Bitwig's minutes:seconds parts
      f.beI((dPX)new NocFormat(text));
      f.nhI(min);
      f.IvP(max);
      return f;
   }

   private static final java.util.Timer TIMER = new java.util.Timer("noctweaks-shapes", true);
   private static volatile long lastApply;
   private static volatile boolean pending;

   /**
    * Writes the current settings, at most about 25 times a second: dragging a value sends many changes,
    * and rewriting the curve for every one of them floods the audio engine. The last change always lands.
    */
   static void apply() {
      throttled(NocShapePanel::applyNow);
   }

   private static volatile Runnable pendingWrite;

   private static void throttled(Runnable write) {
      long now = System.currentTimeMillis();
      if (now - lastApply >= 40 && !pending) {
         lastApply = now;
         write.run();
         return;
      }
      pendingWrite = write;   // the scheduled write runs the latest one
      if (pending) {
         return;
      }
      pending = true;
      TIMER.schedule(new java.util.TimerTask() {
         @Override
         public void run() {
            Fx.beI(() -> {
               pending = false;
               lastApply = System.currentTimeMillis();
               Runnable w = pendingWrite;
               pendingWrite = null;
               if (w != null) {
                  w.run();
               }
            });
         }
      }, Math.max(10, 40 - (now - lastApply)));
   }

   /** Writes the current settings over the selected points' stretches. */
   static void applyNow() {
      try {
         aOp a = editor.get();
         if (a == null) {
            status("Right-click in the note editor once, then try again");
            return;
         }
         if (shape == null) {
            status("Pick a shape to draw it with these values");
            return;
         }
         lFl e = expression();
         if (e == null) {
            status("Pick a note expression in the editor");
            return;
         }
         sre parameter;
         try {
            parameter = cX.beI(e);
         } catch (Throwable t) {
            parameter = null;
         }
         if (parameter == null) {
            status("This expression has no curve");
            return;
         }
         bpn notes = a.sM3().GwM();
         lastWrite = System.currentTimeMillis();
         List<Object> pts = panelPoints;
         if (pts != null) {
            NocShapes.applyPoints(notes, e, parameter, shape, shape.repeats ? cycles : 1, pts);
         } else {
            NocShapes.applyNow(notes, e, parameter, shape, shape.repeats ? cycles : 1);
         }
         // Show the expression being written, as picking it from the hold button does.
         dDG n = button.get();
         if (n != null && !n.mm2()) {
            try {
               n.y3f(true);
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable t) {
         Expr.log("Shape panel: apply failed", t);
      }
   }

   /** Melt: soft S-curves between the selected points (see NocShapes.melt). */
   static void meltNow() {
      try {
         aOp a = editor.get();
         lFl e = expression();
         if (a == null || e == null) {
            status("Pick a note expression in the editor");
            return;
         }
         sre parameter = cX.beI(e);
         bpn notes = a.sM3().GwM();
         List<Object> items = new java.util.ArrayList<>(NocShapes.selectionItems(notes.mS_()));
         List<Object> pts = panelPoints;
         if (pts != null) {
            items.addAll(pts);
         }
         lastWrite = System.currentTimeMillis();
         NocShapes.melt(notes, e, parameter, items, true);
      } catch (Throwable t) {
         Expr.log("Shape panel: melt failed", t);
      }
   }

   // ------------------------------------------------------------------ labels

   private static boolean has(Object[] options, Object o) {
      for (Object x : options) {
         if (x == o) {
            return true;
         }
      }
      return false;
   }

   private static String icon(NocShapes.Shape s) {
      String name = switch (s) {
         case SINE -> "sine";
         case TRIANGLE -> "triangle";
         case SAW_UP -> "saw-up";
         case SAW_DOWN -> "saw-down";
         case SQUARE -> "square";
         case RAMP_UP -> "ramp-up";
         case RAMP_DOWN -> "ramp-down";
         case RANDOM -> "random";
         case FLAT -> "flat";
      };
      return Icons.name("shape/" + name + ".svg");
   }

   private static double amountMax(lFl e) {
      return e != null && NocShapes.isTranspose(e) ? 24 : 100;
   }

   private static String amountText(double v) {
      lFl e = expression();
      if (e != null && NocShapes.isTranspose(e)) {
         return (v < 10 ? String.format(java.util.Locale.ROOT, "%.2f", v) : String.format(java.util.Locale.ROOT, "%.1f", v)) + " st";
      }
      if (e != null && NocShapes.isGain(e) && v >= 99.95) {
         return "-inf dB";
      }
      return Math.round(v) + " %";
   }
}
