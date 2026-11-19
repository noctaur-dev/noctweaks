package io.github.noctaur.expression;

import bitwig.obf.*;
import com.bitwig.flt.widget.core.timeline.interaction.MoveValueEventsDragAction;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * NocTweaks: Ctrl+drag copies note expression points, like notes and clips.
 *
 * With Ctrl held when a drag of expression points starts, Bitwig doesn't move the points: each mouse move,
 * NocTweaks redraws every touched note's whole curve as it will be after the drop, the original curve with
 * the copy (the dragged points, shifted by the drag) pasted over the stretch it lands on. So the original
 * always shows, the copy shows where it will land, and nothing changes until the drag says so. The
 * curve eases into and out of the copy (a short S-curve each side) so the paste has no hard corners.
 * Esc puts the original back; the whole drag is one undo step.
 *
 * Hooks (inserted by the build-time patcher), on MoveValueEventsDragAction:
 *   beginEventEditing(dJx, oPC, List)     -> begin(action, event, events)
 *   updateEditEvents(double, double, eoV) -> update(action, dt, dv)   true: Bitwig doesn't move the points
 *   finishEventEditing()                  -> finish(action)           true: Bitwig's move is cancelled instead
 *   cancelEventEditing()                  -> cancel(action)
 */
public final class NocCopyDrag {
   private NocCopyDrag() {
   }

   /** One note's part of the drag: its whole curve before the drag and which of its points are dragged. */
   private static final class Part {
      final gum note;
      final AGf clip;
      final List<oq1> dragged = new ArrayList<>();
      double[] bt;   // the note's curve before the drag: times, normalized values, curvatures
      double[] bv;
      double[] bc;
      double[] ct;   // the dragged points (the copy, before shifting)
      double[] cv;
      double[] cc;

      Part(gum note, AGf clip) {
         this.note = note;
         this.clip = clip;
      }
   }

   private static final class Drag {
      final sre parameter;
      final List<Part> parts;
      final Object key = new Object();   // the drag's undo step
      final rBG selection;
      final AGf fallbackClip;
      volatile double dt;
      volatile double dv;
      volatile boolean pending;
      long lastWrite;
      boolean written;

      Drag(sre parameter, List<Part> parts, rBG selection, AGf fallbackClip) {
         this.parameter = parameter;
         this.parts = parts;
         this.selection = selection;
         this.fallbackClip = fallbackClip;
      }
   }

   private static final Map<Object, Drag> DRAGS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
   private static volatile boolean disabled;
   private static volatile boolean loggedScale;
   private static final ThreadLocal<Boolean> ENDING = new ThreadLocal<>();

   public static void begin(Object action, Object event, List<?> events) {
      try {
         if (disabled || events == null || events.isEmpty() || !(event instanceof dLc e) || e.ymo() == null || !e.ymo().sCA()) {
            return;
         }
         lFl expression = NocShapePanel.expression();
         sre parameter = expression != null ? cX.beI(expression) : null;
         if (parameter == null) {
            return;
         }
         double[] range = NocShapes.mapping(parameter);
         Map<gum, Part> byNote = new IdentityHashMap<>();
         List<Part> parts = new ArrayList<>();
         for (Object o : NocShapes.unwrap(new ArrayList<>(events))) {
            if (!(o instanceof oq1 c)) {
               continue;
            }
            gum note = NocShapes.noteOf(c);
            if (note == null) {
               continue;   // automation, not a note expression
            }
            Part p = byNote.get(note);
            if (p == null) {
               p = new Part(note, NocShapes.clipOf(note));
               byNote.put(note, p);
               parts.add(p);
            }
            if (!p.dragged.contains(c)) {
               p.dragged.add(c);
            }
         }
         if (parts.isEmpty()) {
            return;
         }
         for (Part p : parts) {
            snapshot(p, range);
         }
         if (!loggedScale) {
            loggedScale = true;
            oq1 c = parts.get(0).dragged.get(0);
            Expr.log("Ctrl+drag copy: value scale check, Bitwig " + c.SQL() + " vs NocTweaks " + NocShapes.normalized(c.mEL(), range), null);
         }
         DRAGS.put(action, new Drag(parameter, parts, selection(action), clip(action)));
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Ctrl+drag copy of expression points disabled", t);
      }
   }

   /** The note's whole curve (same-time points included), and the dragged points in curve order. */
   private static void snapshot(Part p, double[] range) {
      List<LCN> chain = NocShapes.curveOf(p.dragged.get(0));
      Map<LCN, Integer> index = new IdentityHashMap<>();
      for (int k = 0; k < chain.size(); k++) {
         index.put(chain.get(k), k);
      }
      List<double[]> pts = new ArrayList<>();
      for (LCN q : chain) {
         pts.add(new double[]{NocShapes.time(q), NocShapes.normalized(q.mEL(), range), curve(q)});
      }
      p.bt = column(pts, 0);
      p.bv = column(pts, 1);
      p.bc = column(pts, 2);
      List<oq1> dragged = new ArrayList<>();
      for (oq1 c : p.dragged) {
         if (index.containsKey(c) && !dragged.contains(c)) {
            dragged.add(c);
         }
      }
      if (dragged.isEmpty()) {
         dragged.addAll(p.dragged);
      }
      dragged.sort(java.util.Comparator.comparingInt(c -> index.getOrDefault(c, 0)));
      List<double[]> copy = new ArrayList<>();
      for (oq1 c : dragged) {
         copy.add(new double[]{NocShapes.time(c), NocShapes.normalized(c.mEL(), range), curve(c)});
      }
      p.ct = column(copy, 0);
      p.cv = column(copy, 1);
      p.cc = column(copy, 2);
      Expr.log("Ctrl+drag copy: note curve of " + chain.size() + " point(s), copying " + copy.size()
         + " (" + p.ct[0] + ".." + p.ct[p.ct.length - 1] + ")", null);
   }

   private static double[] column(List<double[]> rows, int i) {
      double[] out = new double[rows.size()];
      for (int k = 0; k < out.length; k++) {
         out[k] = rows.get(k)[i];
      }
      return out;
   }

   private static double curve(Object point) {
      try {
         return ((oq1)point).dT_();
      } catch (Throwable t) {
         return 0;
      }
   }

   /** Each mouse move: redraw with the copy at the drag's offset. True: Bitwig leaves the points alone. */
   public static boolean update(Object action, double dt, double dv) {
      Drag d = DRAGS.get(action);
      if (d == null || disabled) {
         return false;
      }
      d.dt = dt;
      d.dv = dv;
      if (!d.pending) {
         d.pending = true;
         long wait = Math.max(0, 40 - (System.currentTimeMillis() - d.lastWrite));   // at most ~25 redraws a second
         Runnable run = () -> {
            d.pending = false;
            if (DRAGS.get(action) == d) {
               draw(d, false);
            }
         };
         if (wait == 0) {
            Fx.beI(run);
         } else {
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
               @Override
               public void run() {
                  Fx.beI(run);
               }
            }, wait);
         }
      }
      return true;
   }

   /** On release: the final redraw. True: Bitwig's own (unused) move is cancelled instead of committed. */
   public static boolean finish(Object action) {
      Drag d = DRAGS.remove(action);
      if (d == null) {
         return false;
      }
      try {
         ENDING.set(Boolean.TRUE);
         if (action instanceof MoveValueEventsDragAction m) {
            m.cancelEventEditing();
         }
      } catch (Throwable t) {
         Expr.log("Ctrl+drag copy: could not end Bitwig's move", t);
      } finally {
         ENDING.remove();
      }
      Fx.beI(() -> draw(d, true));
      return true;
   }

   public static void cancel(Object action) {
      if (ENDING.get() != null) {
         return;
      }
      Drag d = DRAGS.remove(action);
      if (d != null && d.written) {
         d.dt = 0;
         d.dv = 0;
         Fx.beI(() -> restore(d));
      }
   }

   private static void restore(Drag d) {
      try {
         NocShapes.undoable(d.parts.get(0).note, "Copy Expression Points", d.key, () -> {
            for (Part p : d.parts) {
               write(d, p, p.bt, p.bv, p.bc, null);
            }
         });
      } catch (Throwable t) {
         Expr.log("Ctrl+drag copy: restoring the original failed", t);
      }
   }

   /** Writes each note's curve with the copy pasted at the current offset. */
   private static void draw(Drag d, boolean last) {
      try {
         d.lastWrite = System.currentTimeMillis();
         double dt = d.dt;
         double dv = d.dv;
         if (Math.abs(dt) < 1e-6 && Math.abs(dv) < 1e-6) {
            if (d.written) {
               restore(d);   // back where it started: just the original
            }
            return;
         }
         int[] count = {0};
         NocShapes.undoable(d.parts.get(0).note, "Copy Expression Points", d.key, () -> {
            for (Part p : d.parts) {
               double[][] c = pasted(p, dt, dv);
               write(d, p, c[0], c[1], c[2], null);
               count[0] += p.ct.length;
            }
         });
         d.written = true;
         if (last) {
            Expr.log("Ctrl+drag copy: " + count[0] + " expression point(s) copied", null);
         }
      } catch (Throwable t) {
         Expr.log("Ctrl+drag copy failed", t);
      }
   }

   /**
    * The note's curve after the paste: the original outside the copy's stretch, the copy inside it, and a
    * short S-curve easing from the original into the copy and back out (no hard corners at the seams).
    */
   static double[][] pasted(Part p, double dt, double dv) {
      int n = p.ct.length;
      double a = p.ct[0] + dt;
      double b = p.ct[n - 1] + dt;
      double len = Math.max(b - a, 1.0 / 64);
      double blend = Math.max(1.0 / 64, Math.min(1.0 / 8, len * 0.25));
      List<double[]> out = new ArrayList<>();
      boolean before = p.bt.length > 0 && p.bt[0] < a - 1e-9;
      boolean after = p.bt.length > 0 && p.bt[p.bt.length - 1] > b + 1e-9;
      double ea = before ? Math.max(p.bt[0], a - blend) : a;   // where the ease into the copy starts
      double eb = after ? Math.min(p.bt[p.bt.length - 1], b + blend) : b;
      for (int i = 0; i < p.bt.length; i++) {
         if (p.bt[i] < ea - 1e-9) {
            out.add(new double[]{p.bt[i], p.bv[i], p.bc[i]});
         }
      }
      double v0 = clamp(p.cv[0] + dv);
      double v1 = clamp(p.cv[n - 1] + dv);
      if (before && ea < a - 1e-6) {
         out.add(new double[]{ea, valueAt(p, ea), 0});
         ease(out, ea, a, valueAt(p, ea), v0);
      }
      for (int i = 0; i < n; i++) {
         out.add(new double[]{p.ct[i] + dt, clamp(p.cv[i] + dv), p.cc[i]});
      }
      if (after && eb > b + 1e-6) {
         ease(out, b, eb, v1, valueAt(p, eb));
         out.add(new double[]{eb, valueAt(p, eb), curveAt(p, eb)});   // the original continues from there
      }
      for (int i = 0; i < p.bt.length; i++) {
         if (p.bt[i] > eb + 1e-9) {
            out.add(new double[]{p.bt[i], p.bv[i], p.bc[i]});
         }
      }
      // Keep the points in order and apart (very close points upset the engine); points at exactly the
      // same time (a vertical jump) stay.
      List<double[]> clean = new ArrayList<>();
      for (double[] q : out) {
         if (clean.isEmpty()) {
            clean.add(q);
            continue;
         }
         double gap = q[0] - clean.get(clean.size() - 1)[0];
         if (gap == 0 || gap > 1.0 / 1024) {
            clean.add(q);
         }
      }
      return new double[][]{column(clean, 0), column(clean, 1), column(clean, 2)};
   }

   /**
    * An S-curve from the last point in out (at t0, v0) to (t1, v1): that point's segment eases out, a point
    * in the middle eases back in (the caller adds the point at t1).
    */
   private static void ease(List<double[]> out, double t0, double t1, double v0, double v1) {
      double s = 0.6;   // Bitwig curvature on each half (the same S as Melt)
      out.get(out.size() - 1)[2] = -s;
      out.add(new double[]{(t0 + t1) / 2, (v0 + v1) / 2, s});
   }

   /** The original curve's value at time t. */
   private static double valueAt(Part p, double t) {
      double[] bt = p.bt;
      if (bt.length == 0) {
         return 0.5;
      }
      if (t <= bt[0]) {
         return p.bv[0];
      }
      for (int i = 0; i + 1 < bt.length; i++) {
         if (t <= bt[i + 1]) {
            return NocShapes.Span.segment(t, bt[i], bt[i + 1], p.bv[i], p.bv[i + 1], p.bc[i]);
         }
      }
      return p.bv[bt.length - 1];
   }

   private static double curveAt(Part p, double t) {
      for (int i = 0; i + 1 < p.bt.length; i++) {
         if (t < p.bt[i + 1]) {
            return p.bc[i];
         }
      }
      return 0;
   }

   private static double clamp(double v) {
      return Math.max(0, Math.min(1, v));
   }

   private static void write(Drag d, Part p, double[] times, double[] values, double[] curves, rBG selection) {
      if (times.length == 0) {
         return;
      }
      AGf clip = p.clip != null ? p.clip : d.fallbackClip;
      try {
         p.note.beI(d.parameter, times, values, r1.Gef, curves, clip, false, true, 0.0, selection);
      } catch (NullPointerException npe) {
         if (selection != null || d.selection == null) {
            throw npe;
         }
         p.note.beI(d.parameter, times, values, r1.Gef, curves, clip, false, true, 0.0, d.selection);
      }
   }

   private static AGf clip(Object action) {
      try {
         return action instanceof MoveValueEventsDragAction m && m.getParentClip() instanceof AGf c ? c : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static rBG selection(Object action) {
      try {
         return action instanceof MoveValueEventsDragAction m ? m.getEditorSelection().mS_() : null;
      } catch (Throwable t) {
         return null;
      }
   }
}
