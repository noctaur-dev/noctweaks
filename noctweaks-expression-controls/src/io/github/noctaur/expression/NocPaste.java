package io.github.noctaur.expression;

import bitwig.obf.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Copy an expression shape, paste it onto automation.
 *
 * Copy takes the stretch of the selected expression points (first selected point to last; one point: to the
 * next one), as its points, values and curved segments. Paste writes it onto an automation lane, starting at
 * the selected automation point's time and level. The depth sets how far it moves: as copied (the same share
 * of the range), or scaled so its swing is 10, 25, 50 or 100 % of the target's range.
 */
final class NocPaste {
   private NocPaste() {
   }

   /** The copied shape: times from its start (beats), normalized values, curvature of the segment after each. */
   private static volatile double[] ct, cv, cc;
   private static volatile String copiedFrom;

   static final Depth[] DEPTHS = Depth.values();

   enum Depth {
      AS_COPIED("As Copied", Double.NaN),
      TEN("10 %", 0.10),
      QUARTER("25 %", 0.25),
      HALF("50 %", 0.50),
      FULL("Full Range", 1.0);

      final String label;
      final double swing;

      Depth(String label, double swing) {
         this.label = label;
         this.swing = swing;
      }
   }

   static boolean has() {
      return ct != null && ct.length >= 2;
   }

   static String describe() {
      if (!has()) {
         return "Nothing copied yet";
      }
      double len = ct[ct.length - 1];
      return "Copied: " + copiedFrom + ", " + ct.length + " points over " + beats(len);
   }

   private static String beats(double b) {
      return (Math.abs(b - Math.rint(b)) < 1e-6 ? String.valueOf((long)Math.rint(b)) : String.format(java.util.Locale.ROOT, "%.2f", b))
         + (Math.abs(b - 1) < 1e-6 ? " beat" : " beats");
   }

   // ------------------------------------------------------------------ copy

   /** Copies the stretch of these expression points (the first note's, if several notes are selected). */
   static boolean copy(List<Object> items, lFl expression, sre parameter) {
      try {
         java.util.Map<gum, List<oq1>> picked = NocShapes.byNote(items);
         if (picked.isEmpty()) {
            NocShapePanel.status("Select expression points to copy");
            return false;
         }
         // Every selected point, in time order. (Grouping them by note split one curve into pieces, so they
         // are all taken; with several notes selected, the one with the most points.)
         java.util.Set<oq1> all = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
         int kinds = 0;
         for (Object o : NocShapes.unwrap(items)) {
            if (o instanceof oq1 p) {
               all.add(p);
            } else {
               kinds++;
            }
         }
         List<oq1> pts = new ArrayList<>(all);
         if (picked.size() > 1) {
            List<oq1> best = null;
            for (List<oq1> g : picked.values()) {
               if (best == null || g.size() > best.size()) {
                  best = g;
               }
            }
            if (best != null && best.size() * 2 > pts.size()) {
               pts = new ArrayList<>(best);
            }
         }
         pts.sort(java.util.Comparator.comparingDouble(NocShapes::time));
         Expr.log("Copy: " + all.size() + " point(s) selected (" + picked.size() + " note group(s), " + kinds
            + " other item(s)); copying " + pts.size(), null);
         double[] range = NocShapes.mapping(parameter);
         double[] t, v, c;
         if (pts.size() >= 2) {
            int n = pts.size();
            t = new double[n];
            v = new double[n];
            c = new double[n];
            for (int i = 0; i < n; i++) {
               oq1 p = pts.get(i);
               t[i] = NocShapes.time(p);
               v[i] = NocShapes.normalized(p.mEL(), range);  // the point's value, 0..1 of the expression's range
               c[i] = i < n - 1 ? p.dT_() : 0;               // the curvature of the segment after it
            }
         } else {
            // One point: from it to the next point (or the note's end).
            List<NocShapes.Span> spans = NocShapes.readSpans(picked, parameter);
            if (spans.isEmpty()) {
               NocShapePanel.status("Nothing after the selected point to copy");
               return false;
            }
            double[][] q = NocShapes.structure(spans.get(0), 0);
            t = q[0];
            v = q[1];
            c = q[2];
         }
         if (t.length < 2) {
            NocShapePanel.status("Select at least two points to copy");
            return false;
         }
         double t0 = t[0];
         double[] rt = new double[t.length];
         for (int i = 0; i < t.length; i++) {
            rt[i] = t[i] - t0;
         }
         ct = rt;
         cv = v.clone();
         cc = c.clone();
         copiedFrom = expression == null ? "expression" : NocShapes.titleOf(expression);
         NocShapePanel.status(describe());
         Expr.log("Paste: copied " + t.length + " points over " + rt[rt.length - 1] + " beats from " + copiedFrom, null);
         return true;
      } catch (Throwable t) {
         Expr.log("Paste: copy failed", t);
         NocShapePanel.status("Couldn't copy the shape");
         return false;
      }
   }

   // ------------------------------------------------------------------ paste

   /** The selected automation points (points not on a note). */
   static List<oq1> automationPoints(List<Object> items) {
      List<oq1> out = new ArrayList<>();
      for (Object o : NocShapes.unwrap(items)) {
         if (o instanceof oq1 p && NocShapes.noteOf(p) == null) {
            out.add(p);
         }
      }
      return out;
   }

   /** Pastes at a depth in percent (0 = as copied). */
   static void paste(List<oq1> points, int percent) {
      if (percent <= 0) {
         paste(points, Depth.AS_COPIED);
      } else {
         paste(points, percent + " %", percent / 100.0);
      }
   }

   /** Pastes the copied shape from the earliest of these automation points, at this depth. */
   static void paste(List<oq1> points, Depth depth) {
      paste(points, depth.label, depth.swing);
   }

   static void paste(List<oq1> points, String label, double depthSwing) {
      try {
         if (!has()) {
            NocShapePanel.status("Copy an expression shape first");
            return;
         }
         if (points.isEmpty()) {
            NocShapePanel.status("Select an automation point to paste from");
            return;
         }
         oq1 anchor = points.get(0);
         for (oq1 p : points) {
            if (NocShapes.time(p) < NocShapes.time(anchor)) {
               anchor = p;
            }
         }
         tvm lane = anchor.mYH();        // the automation lane (its events) the point is on
         if (lane == null) {
            NocShapePanel.status("This point isn't on an automation lane");
            return;
         }
         NSu mapping = lane.bT_();       // how the lane maps values
         double raw = anchor.mEL();      // the point's value, in the parameter's own units (Hz, dB, …)
         double level;
         try {
            level = mapping.beI((LCN) anchor);   // the same value as 0..1 of the parameter's range
         } catch (Throwable e) {
            level = Math.max(0, Math.min(1, raw));
         }
         level = Math.max(0, Math.min(1, level));
         double t0 = NocShapes.time(anchor);

         double[] st = ct, sv = cv, sc = cc;
         double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
         for (double x : sv) {
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
         }
         double swing = hi - lo;
         double scale = Double.isNaN(depthSwing) || swing < 1e-9 ? 1 : depthSwing / swing;
         int n = st.length;
         double[] t = new double[n], v = new double[n], c = sc.clone();
         for (int i = 0; i < n; i++) {
            t[i] = t0 + st[i];
            v[i] = Math.max(0, Math.min(1, level + (sv[i] - sv[0]) * scale));
         }
         final oq1 from = anchor;
         NocShapes.undoable(lane, "Paste Expression Shape", new Object(), () ->
            lane.beI(t, v, r1.Gef, c, false, true, mapping, null)); // write the points (replaces that stretch)
         NocShapePanel.status("Pasted " + n + " points (" + label + ")");
         Expr.log("Paste: " + n + " points at " + t0 + " from level " + raw + " (" + label + ", scale " + scale
            + ") normalized " + level + " onto " + lane.getClass().getName() + " via " + from.getClass().getName(), null);
      } catch (Throwable t) {
         Expr.log("Paste: paste failed", t);
         NocShapePanel.status("Couldn't paste the shape");
      }
   }

   /** "Paste Expression Shape" with its depths, in a right-click menu. */
   static void pasteMenu(dMt menu, List<oq1> points) {
      if (points.isEmpty()) {
         return;
      }
      menu.fpX();
      if (!has()) {
         menu.beI("Paste Expression Shape (copy one first)", () -> {
         });
         return;
      }
      dMt sub = menu.sCA("Paste Expression Shape");
      for (Depth d : DEPTHS) {
         sub.beI(d.label, () -> paste(points, d));
      }
   }
}
