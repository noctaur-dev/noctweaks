package io.github.noctaur.expression;

import bitwig.obf.*;
import com.bitwig.ramona.core.FDA;
import com.bitwig.ramona.core.oDc;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * NocTweaks: note expression shapes. EXPERIMENTAL.
 *
 * In the note editor, while editing a note expression (Transpose, Gain, Pan, Timbre, Pressure), the
 * right-click menu gets "Insert Shape": a sine, triangle, saw, square, ramp or random shape written
 * over each selected note's length as ordinary expression points (editable and undoable like drawn
 * ones). Options: number of cycles, amount (semitones for Transpose), and whether the shape stays steady, grows or fades over
 * the note.
 *
 * Hook (inserted by the build-time patcher):
 *   aMV.IYM(dKG, GlG, ydz)  -> menu(menu, editor)   note editor right-click menu
 */
public final class NocShapes {
   private NocShapes() {
   }

   enum Shape {
      SINE("Sine", true), TRIANGLE("Triangle", true), SAW_UP("Saw Up", true), SAW_DOWN("Saw Down", true),
      SQUARE("Square", true), RAMP_UP("Ramp Up", false), RAMP_DOWN("Ramp Down", false), RANDOM("Random", true), FLAT("Flat", false);

      final String label;
      final boolean repeats;

      Shape(String label, boolean repeats) {
         this.label = label;
         this.repeats = repeats;
      }
   }

   enum Envelope {
      STEADY("Steady"), GROW("Grows Over the Note"), FADE("Fades Over the Note");

      final String label;

      Envelope(String label) {
         this.label = label;
      }
   }

   static volatile Envelope envelope = Envelope.STEADY;
   /** Grow (positive) or fade (negative) over the note, -1..1; the menu sets it to -1, 0 or 1. */
   static volatile double growth;
   /** Fade in and out at both ends of the shape: the share of the stretch each fade takes, 0..0.5. */
   static volatile double edges;
   /** Undo merge key for the next write: the Inspector panel keeps one per set of notes, so its tweaks are one undo step. */
   static volatile Object mergeKey;
   /** Slides the wobbles toward the start (negative) or the end (positive) of the note, -1..1. */
   static volatile double skew;
   /** How far a shape goes, per expression: semitones for Transpose, percent of the range otherwise. */
   static final java.util.Map<hJh, Double> AMOUNT = new java.util.concurrent.ConcurrentHashMap<>();
   private static final double[] SEMITONES = {0.1, 0.25, 0.5, 1, 2, 3, 5, 7, 12, 24};
   private static final double[] PERCENT = {5, 10, 25, 50, 75, 100};

   static boolean isTranspose(hJh e) {
      return "TRANSPOSE".equals(e.name());
   }

   static boolean isGain(hJh e) {
      return "GAIN".equals(e.name());
   }

   static double amount(hJh e) {
      return AMOUNT.getOrDefault(e, isTranspose(e) ? 1.0 : isGain(e) ? 100.0 : 25.0);
   }

   static String amountLabel(hJh e, double v) {
      if (isTranspose(e)) {
         String n = v == Math.rint(v) ? String.valueOf((int)v) : String.valueOf(v);
         return "±" + n + (v == 1 ? " semitone" : " semitones");
      }
      if (isGain(e)) {
         return v >= 100 ? "Down to -inf dB" : "Down " + (int)v + "%";
      }
      return (int)v + "%";
   }
   private static volatile boolean loggedMapping;
   private static volatile Object lastTarget;
   private static volatile OjS lastSelection;
   private static int diagnostics;

   // ------------------------------------------------------------------ menu

   private static final java.util.Set<Object> MENUS = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
   private static volatile List<Object> picked;

   /**
    * Hook at the end of bkI.IYM(dKG, duK, bhW, gC): the menu for selected notes or expression points
    * (Bitwig shows it instead of the background menu when you right-click a selection).
    */
   public static void selectionMenu(dKW menu, Object owner, Object header) {
      try {
         if (!(header instanceof big h)) {
            return;
         }
         List<Object> items = unwrap(new ArrayList<>(h.UNj().FzB()));
         pasteItems(menu, items);
         boolean editable = false;
         for (Object o : items) {
            if (o instanceof bOw || o instanceof dpB || o instanceof hMP) {
               editable = true;
               break;
            }
         }
         if (!editable) {
            if (!items.isEmpty() && diagnostics++ < 12) {
               StringBuilder b = new StringBuilder();
               for (Object o : items) {
                  b.append(describe(o)).append("; ");
               }
               Expr.log("Expression shapes: selection menu items not recognised: " + b, null);
            }
            return;
         }
         Object w = owner;
         for (int i = 0; i < 30 && w != null && !(w instanceof aNj); i++) {
            Object up = null;
            try {
               up = ((dvW)w).RbC();
            } catch (Throwable ignored) {
            }
            w = up == w ? null : up;
         }
         if (!(w instanceof aNj)) {
            Expr.log("Expression shapes: selection menu outside the note editor (" + (owner == null ? "null" : owner.getClass().getName()) + ")", null);
            return;
         }
         picked = items;
         NocShapePanel.remember((aNj)w);
         build(menu, (aNj)w);
      } catch (Throwable t) {
         Expr.log("Expression shapes: could not extend the selection menu", t);
      }
   }

   /** Selected automation points: "Paste Expression Shape" (wherever the menu is). */
   private static void pasteItems(dKW menu, List<Object> items) {
      try {
         List<hMP> auto = NocPaste.automationPoints(items);
         if (!auto.isEmpty() && PASTE_MENUS.add(menu)) {
            NocPaste.pasteMenu(menu, auto);
         }
      } catch (Throwable t) {
         Expr.log("Expression shapes: could not extend the selection menu", t);
      }
   }

   public static void menu(dKW menu, Object editorWidget, Object target, Object selectionParam) {
      picked = null;
      lastTarget = target;
      lastSelection = selectionParam instanceof OjS y ? y : null;
      if (editorWidget instanceof aNj editor) {
         NocShapePanel.remember(editor);
         build(menu, editor);
      }
   }

   private static void build(dKW menu, aNj editor) {
      try {
         if (!MENUS.add(menu)) {
            return;
         }
         try {
            pasteItems(menu, picked != null ? picked : selectionItems(editor.nfq().iCS().mR_()));
         } catch (Throwable t) {
            Expr.log("Paste: could not read the selection", t);
         }
         XtM overlay = editor.ifN().EUG().LaE();
         if (overlay == null || overlay.HL() == null || !overlay.HL().getBoolValue()) {
            return;   // not editing a note expression
         }
         Object current = overlay.MJ() == null ? null : overlay.MJ().getValue();
         if (!(current instanceof hJh expression)) {
            return;
         }
         ucd parameter;
         try {
            parameter = cM.cfM(expression);
         } catch (Throwable t) {
            return;   // velocity, chance…: not a curve
         }
         if (parameter == null) {
            return;
         }
         bob notes = editor.nfq().iCS();
         final List<Object> items = picked;
         if (!hasPoints(items != null ? items : selectionItems(notes.mR_()))) {
            return;   // shapes run from expression points; nothing to start from
         }

         menu.ELS();
         menu.cfM("Copy Expression Shape", () -> NocPaste.copy(items != null ? items : selectionItems(notes.mR_()), expression, parameter));
         menu.cfM("Melt Between Points", () -> {
            if (meltAmount < 0.005) {
               meltAmount = 0.6;   // the menu has no slider: a sensible softness when the panel's Melt is Off
            }
            melt(notes, expression, parameter, items != null ? items : selectionItems(notes.mR_()));
         });
         dKW insert = menu.Hnt("Insert Shape (" + title(expression) + ")");
         for (Shape s : Shape.values()) {
            if (s.repeats) {
               dKW sub = insert.Hnt(s.label);
               for (int cycles : new int[]{1, 2, 4, 8, 16}) {
                  sub.cfM(cycles + (cycles == 1 ? " Cycle" : " Cycles"), () -> apply(notes, expression, parameter, s, cycles, items));
               }
            } else {
               insert.cfM(s.label, () -> apply(notes, expression, parameter, s, 1, items));
            }
         }
         insert.ELS();
         dKW env = insert.Hnt("Over the Note: " + envelope.label);
         for (Envelope e : Envelope.values()) {
            env.cfM((e == envelope ? "✓ " : "    ") + e.label, () -> {
               envelope = e;
               growth = e == Envelope.GROW ? 1 : e == Envelope.FADE ? -1 : 0;
            });
         }
         double now = amount(expression);
         dKW amount = insert.Hnt("Amount: " + amountLabel(expression, now));
         for (double v : isTranspose(expression) ? SEMITONES : PERCENT) {
            amount.cfM((v == now ? "✓ " : "    ") + amountLabel(expression, v), () -> AMOUNT.put(expression, v));
         }
      } catch (Throwable t) {
         Expr.log("Expression shapes: could not build the menu", t);
      }
   }

   static String titleOf(hJh e) {
      return title(e);
   }

   /** Menus that already got "Paste Expression Shape". */
   static final Set<Object> PASTE_MENUS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

   private static String title(hJh e) {
      String n = e.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
      return Character.toUpperCase(n.charAt(0)) + n.substring(1);
   }

   // ------------------------------------------------------------------ apply

   static void apply(bob editor, hJh expression, ucd parameter, Shape shape, int cycles, List<Object> given) {
      mergeKey = null;   // a menu pick is its own undo step
      apply(editor, expression, parameter, shape, cycles, given, lastSelection != null ? lastSelection : editor.mR_(), lastTarget);
   }

   /** The inspector panel: the editor's current selection, nothing remembered from a menu. */
   static void applyNow(bob editor, hJh expression, ucd parameter, Shape shape, int cycles) {
      apply(editor, expression, parameter, shape, cycles, null, editor.mR_(), null);
   }

   /** The Points Inspector panel: its own selected points (the editor's selection holds the note, not the point). */
   static void applyPoints(bob editor, hJh expression, ucd parameter, Shape shape, int cycles, List<Object> points) {
      // All selected points from the editor (the Points Inspector itself only lists the last one clicked).
      List<Object> all = new ArrayList<>(selectionItems(editor.mR_()));
      for (Object o : points) {
         if (!all.contains(o)) {
            all.add(o);
         }
      }
      apply(editor, expression, parameter, shape, cycles, all, editor.mR_(), null);
   }

   /** Whether any expression point (of a note) is among these selection items. */
   static boolean hasPoints(List<Object> items) {
      for (Object o : unwrap(items)) {
         if (o instanceof hMP c && noteOf(c) != null) {
            return true;
         }
      }
      return false;
   }

   static void apply(bob editor, hJh expression, ucd parameter, Shape shape, int cycles, List<Object> given, OjS selection, Object target) {
      try {
         kPd clip = editor.mV_();
         List<Object> picked = unwrap(given != null ? given : selectionItems(selection));
         java.util.Map<dpB, List<hMP>> spans = byNote(picked);
         if (!spans.isEmpty()) {
            if (diagnostics < 40) {
               StringBuilder b = new StringBuilder("Expression shapes: selected points per note:");
               for (List<hMP> l : spans.values()) {
                  b.append(' ').append(l.size());
               }
               Expr.log(b.toString(), null);
            }
            if (insideHeld(spans)) {
               // Still the stretch being shaped (after a write Bitwig moves the selection to the new points):
               // keep working from the session, as the same undo step.
               mergeKey = heldKey;
               writeSpans(heldSpans, editor, selection, clip, expression, parameter, shape, cycles);
               return;
            }
            if (diagnostics++ < 6) {
               diagnose(editor, selection, target);
            }
            writeSpans(startSession(spans, parameter), editor, selection, clip, expression, parameter, shape, cycles);
            return;
         }
         if (given != null && sessionActive()) {
            // The panel is still up but Bitwig dropped the selection after the write: keep shaping that stretch.
            writeSpans(heldSpans, editor, selection, clip, expression, parameter, shape, cycles);
            return;
         }
         // Shapes go from points (node to node); a note with no selected point is left alone.
         NocShapePanel.status("Select an expression point to start from");
         Expr.log("Expression shapes: no expression points selected", null);
      } catch (Throwable t) {
         Expr.log("Expression shapes: insert failed", t);
      }
   }

   /**
    * One note's stretch in a shaping session, all captured once, from the nodes you selected:
    *   ta..tb   where writes go (just inside the end nodes, so those nodes and the selection stay put)
    *   bt..hold the curve that was there (exact points, Bitwig's curvature and hold)
    *   ht/hv    for several selected nodes, a smoothed "heartline" through it (null otherwise)
    *   at/av    the selected nodes themselves (plus the next node, or the note's end, for one node),
    *            which Melt turns into soft S-curves
    * Every later tweak in the session is worked out from this, never from what Bitwig selects after a write.
    */
   record Span(dpB note, double ta, double tb, double[] bt, double[] bv, double[] curve, boolean[] hold,
         double[] ht, double[] hv, double[] at, double[] av) {
      /** The curve to ride: the heartline if there is one, else the curve as it was. */
      double base(double t) {
         if (ht != null) {
            return lerp(ht, hv, t);
         }
         return exact(t);
      }

      /** The curve as it was, as Bitwig draws it (curvature and hold included). */
      double exact(double t) {
         if (t <= bt[0]) {
            return bv[0];
         }
         for (int i = 1; i < bt.length; i++) {
            if (t <= bt[i]) {
               if (hold[i - 1]) {
                  return bv[i - 1];
               }
               if (!(bt[i] > bt[i - 1])) {
                  return bv[i];
               }
               return segment(t, bt[i - 1], bt[i], bv[i - 1], bv[i], curve[i - 1]);
            }
         }
         return bv[bv.length - 1];
      }

      /** The melted line through the selected nodes: each stretch eases out of one and into the next. */
      double melted(double t, double m) {
         if (t <= at[0]) {
            return av[0];
         }
         for (int i = 1; i < at.length; i++) {
            if (t <= at[i]) {
               double mid = (at[i - 1] + at[i]) / 2, vm = (av[i - 1] + av[i]) / 2;
               return t <= mid ? segment(t, at[i - 1], mid, av[i - 1], vm, -m) : segment(t, mid, at[i], vm, av[i], m);
            }
         }
         return av[av.length - 1];
      }

      /** What the shape rides on: melted when Melt is on, otherwise the curve (or heartline) that was there. */
      double ride(double t, double m) {
         return m > 0.005 ? melted(t, m) : base(t);
      }

      static double lerp(double[] xs, double[] ys, double t) {
         if (t <= xs[0]) {
            return ys[0];
         }
         for (int i = 1; i < xs.length; i++) {
            if (t <= xs[i]) {
               double w = xs[i] - xs[i - 1];
               return w > 0 ? ys[i - 1] + (ys[i] - ys[i - 1]) * (t - xs[i - 1]) / w : ys[i];
            }
         }
         return ys[ys.length - 1];
      }

      static double segment(double t, double t0, double t1, double v0, double v1, double c) {
         if (!(t1 > t0)) {
            return v1;
         }
         t = Math.max(t0, Math.min(t1, t));
         try {
            return cM.cfM(t, t0, t1, v0, v1, c);   // Bitwig's own segment shape
         } catch (Throwable e) {
            return v0 + (v1 - v0) * (t - t0) / (t1 - t0);
         }
      }
   }

   /** The shaping session: its stretches, its undo key, and what's been applied so far. */
   private static volatile List<Span> heldSpans;
   private static volatile Object heldKey;
   /** A shape has been written in this session (Melt then rewrites the shape on the melted line). */
   private static volatile boolean sessionShaped;
   private static volatile Shape lastShape = Shape.SINE;
   private static volatile int lastCycles = 4;

   /** A smoothed version of the curve over ta..tb (64 samples), pinned to its two ends. */
   static double[][] heartline(Span exact, double ta, double tb) {
      int n = 64;
      double[] t = new double[n], v = new double[n], out = new double[n];
      for (int i = 0; i < n; i++) {
         t[i] = ta + (tb - ta) * i / (n - 1);
         v[i] = exact.exact(t[i]);
      }
      double sigma = 0.15 * (n - 1);
      for (int i = 0; i < n; i++) {
         double sum = 0, w = 0;
         for (int j = 0; j < n; j++) {
            double k = Math.exp(-0.5 * (i - j) * (i - j) / (sigma * sigma));
            sum += k * v[j];
            w += k;
         }
         out[i] = sum / w;
      }
      double d0 = v[0] - out[0], d1 = v[n - 1] - out[n - 1];
      for (int i = 0; i < n; i++) {
         double x = i / (double)(n - 1);
         out[i] = Math.max(0, Math.min(1, out[i] + d0 * (1 - x) + d1 * x));
      }
      return new double[][]{t, out};
   }

   /** Whether every selected point lies in a stretch already being shaped (on the same note). */
   static boolean insideHeld(java.util.Map<dpB, List<hMP>> spans) {
      List<Span> held = heldSpans;
      if (held == null || held.isEmpty()) {
         return false;
      }
      for (java.util.Map.Entry<dpB, List<hMP>> e : spans.entrySet()) {
         for (hMP c : e.getValue()) {
            double t = time(c);
            boolean in = false;
            for (Span sp : held) {
               if (sp.note() == e.getKey() && t >= sp.at()[0] - 0.02 && t <= sp.at()[sp.at().length - 1] + 0.02) {
                  in = true;
                  break;
               }
            }
            if (!in) {
               return false;
            }
         }
      }
      return true;
   }

   /** A new selection: forget the stretches the panel was working on (the next edit reads the new points). */
   static void endSession() {
      heldSpans = null;
      heldKey = null;
      mergeKey = new Object();
   }

   /** The panel's session is still going (same undo key): keep working on its stretches. */
   static boolean sessionActive() {
      return heldSpans != null && !heldSpans.isEmpty() && mergeKey != null && heldKey == mergeKey;
   }

   /** The point after this one on its curve (by time; Bitwig's own links aren't consistently forward). */
   static dfo after(dfo p) {
      double t = time(p);
      dfo best = null;
      for (dfo c : new dfo[]{p.MIO(), p.iCS()}) {
         if (c != null && c != p && time(c) > t && (best == null || time(c) < time(best))) {
            best = c;
         }
      }
      return best;
   }

   /**
    * Every point of p's curve (the note's expression), in order, including points that share a time (a
    * vertical jump is two points at the same time, which walking by time skips). The points are linked to
    * their neighbours (NFl/oP, not always the same way round), so this walks the links to one end and back.
    */
   static List<dfo> curveOf(dfo p) {
      List<dfo> out = new ArrayList<>();
      try {
         dpB note = noteOf(p);
         java.util.Set<dfo> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
         dfo prev = null;
         dfo cur = p;
         seen.add(cur);
         for (int guard = 0; guard < 100000; guard++) {
            dfo next = other(cur, prev, seen, note);
            if (next == null) {
               break;
            }
            seen.add(next);
            prev = cur;
            cur = next;
         }
         seen.clear();
         prev = null;
         out.add(cur);
         seen.add(cur);
         for (int guard = 0; guard < 100000; guard++) {
            dfo next = other(cur, prev, seen, note);
            if (next == null) {
               break;
            }
            out.add(next);
            seen.add(next);
            prev = cur;
            cur = next;
         }
         if (out.size() > 1 && time(out.get(0)) > time(out.get(out.size() - 1))) {
            java.util.Collections.reverse(out);
         }
         boolean ordered = true;
         for (int i = 1; i < out.size(); i++) {
            if (time(out.get(i)) < time(out.get(i - 1)) - 1e-9) {
               ordered = false;
               break;
            }
         }
         if (!ordered) {
            if (curveLogged++ < 3) {
               Expr.log("Expression points: the links aren't in time order; sorting " + out.size() + " point(s) by time", null);
            }
            out.sort(java.util.Comparator.comparingDouble(NocShapes::time));   // stable: same-time points keep their order
         }
      } catch (Throwable t) {
         Expr.log("Expression points: could not walk the curve", t);
         if (out.isEmpty()) {
            out.add(p);
         }
      }
      return out;
   }

   private static int curveLogged;

   private static dfo other(dfo cur, dfo prev, java.util.Set<dfo> seen, dpB note) {
      for (dfo c : new dfo[]{cur.MIO(), cur.iCS()}) {
         if (c != null && c != cur && c != prev && !seen.contains(c) && (note == null || noteOf(c) == note)) {
            return c;
         }
      }
      return null;
   }

   static dfo before(dfo p) {
      double t = time(p);
      dfo best = null;
      for (dfo c : new dfo[]{p.MIO(), p.iCS()}) {
         if (c != null && c != p && time(c) < t && (best == null || time(c) > time(best))) {
            best = c;
         }
      }
      return best;
   }

   /** The selected points, per note (by note object: notes at the same time can be "equal"). */
   static java.util.Map<dpB, List<hMP>> byNote(List<Object> items) {
      java.util.Map<dpB, List<hMP>> spans = new java.util.IdentityHashMap<>();
      for (Object o : unwrap(items)) {
         if (o instanceof hMP c) {
            dpB owner = noteOf(c);
            if (owner != null) {
               spans.computeIfAbsent(owner, k -> new ArrayList<>()).add(c);
            }
         }
      }
      return spans;
   }

   /**
    * Starts a session from the selected points, per note: one selected point runs from it to the next
    * point (or to the end of the note when there's none after it); several run from the first selected
    * to the last selected. Each note keeps its own level and slides.
    */
   static List<Span> startSession(java.util.Map<dpB, List<hMP>> spans, ucd parameter) {
      List<Span> list = readSpans(spans, parameter);
      if (mergeKey == null) {
         mergeKey = new Object();   // a fresh session: its own undo step
      }
      heldSpans = list;
      heldKey = mergeKey;
      sessionShaped = false;
      return list;
   }

   /** The selected stretches, one per note (from its first selected point to its last; one point: to the next). */
   static List<Span> readSpans(java.util.Map<dpB, List<hMP>> spans, ucd parameter) {
      List<Span> list = new ArrayList<>();
      double[] r = mapping(parameter);
      for (java.util.Map.Entry<dpB, List<hMP>> e : spans.entrySet()) {
         try {
            dpB note = e.getKey();
            List<hMP> picked = new ArrayList<>(e.getValue());
            // The whole curve in order (same-time points included); the stretch runs from the first
            // selected point to the last one (one selected: to the point after it).
            List<dfo> chain = curveOf(picked.get(0));
            java.util.Map<dfo, Integer> index = new java.util.IdentityHashMap<>();
            for (int k = 0; k < chain.size(); k++) {
               index.put(chain.get(k), k);
            }
            int ia = Integer.MAX_VALUE, ib = -1;
            for (hMP c : picked) {
               Integer k = index.get(c);
               if (k != null) {
                  ia = Math.min(ia, k);
                  ib = Math.max(ib, k);
               }
            }
            if (ib < 0) {   // not found on the walked curve: fall back to times
               picked.sort(java.util.Comparator.comparingDouble(NocShapes::time));
               chain = new ArrayList<>(picked);
               ia = 0;
               ib = chain.size() - 1;
            }
            Expr.log("Expression shapes: " + picked.size() + " selected point(s), curve of " + chain.size()
               + ", stretch " + ia + ".." + ib, null);
            hMP a = (hMP)chain.get(ia);
            dfo b = ib > ia ? chain.get(ib) : (ia + 1 < chain.size() ? chain.get(ia + 1) : null);
            final List<dfo> walk = chain;
            final int from = ia;
            List<hMP> pts = new ArrayList<>(picked);
            pts.sort(java.util.Comparator.comparingInt(c -> index.getOrDefault(c, 0)));
            for (int k = pts.size() - 1; k > 0; k--) {
               if (pts.get(k) == pts.get(k - 1)) {
                  pts.remove(k);
               }
            }
            double ta = time(a);
            double len = ((iCu)note).nfq();
            double start = note.NC();
            // Point times are either clip times or note-relative: take the note's end in the same terms.
            double end = (ta >= start - 1e-9 && ta <= start + len + 1e-9) ? start + len : len;
            double tb = b != null ? Math.min(time(b), end) : end;
            if (!(tb > ta)) {
               Expr.log("Expression shapes: skipped a span " + ta + ".." + tb + " (note " + start + "+" + len + ")", null);
               continue;
            }
            // The curve in that stretch: the points from a up to b.
            List<double[]> curve = new ArrayList<>();
            for (int k = from; k < walk.size(); k++) {
               dfo p = walk.get(k);
               double t = time(p);
               if (t > tb + 1e-9) {
                  break;
               }
               double c = 0;
               boolean h = false;
               try {
                  c = ((hMP)p).dS_();   // the point's curvature (bends the segment after it)
               } catch (Throwable ignored) {
               }
               try {
                  h = p.qoU();   // hold: the value steps at the next point
               } catch (Throwable ignored) {
               }
               curve.add(new double[]{t, normalized(p.tp2(), r), c, h ? 1 : 0});
               if (p == b) {
                  break;
               }
            }
            double vb = b != null ? normalized(b.tp2(), r) : (curve.isEmpty() ? normalized(a.tp2(), r) : curve.get(curve.size() - 1)[1]);
            // Always end on the end point itself (walking the links can stop short).
            if (curve.isEmpty() || curve.get(curve.size() - 1)[0] < tb - 1e-9) {
               curve.add(new double[]{tb, vb, 0, 0});
            }
            int n = curve.size();
            double[] bt = new double[n], bv = new double[n], cv = new double[n];
            boolean[] hv = new boolean[n];
            for (int k = 0; k < n; k++) {
               bt[k] = curve.get(k)[0];
               bv[k] = curve.get(k)[1];
               cv[k] = curve.get(k)[2];
               hv[k] = curve.get(k)[3] > 0;
            }
            // The selected nodes (Melt works between these); one node: it and the next node / note end.
            List<double[]> anchors = new ArrayList<>();
            for (hMP q : pts) {
               anchors.add(new double[]{time(q), normalized(q.tp2(), r)});
            }
            if (pts.size() == 1) {
               anchors.add(new double[]{tb, vb});
            }
            double[] at = new double[anchors.size()], av = new double[anchors.size()];
            for (int k = 0; k < at.length; k++) {
               at[k] = anchors.get(k)[0];
               av[k] = anchors.get(k)[1];
            }
            double[] ht = null, hvv = null;
            Span exact = new Span(note, ta, tb, bt, bv, cv, hv, null, null, at, av);
            if (pts.size() > 1 && bt.length > 2) {
               // Several points selected: ride the overall line through them (a smoothed "heartline").
               double[][] h = heartline(exact, ta, tb);
               ht = h[0];
               hvv = h[1];
            }
            // Leave the end nodes where they are (they stay selected and the Inspector stays up);
            // far enough away that Bitwig doesn't merge the first written point into them.
            double eps = Math.min((tb - ta) / 200.0, 1.0 / 128.0);
            double wa = ta + eps;
            double wb = b != null ? tb - eps : tb;
            Expr.log("Expression shapes: session span " + ta + ".." + tb + " over " + n + " point(s), "
               + at.length + " node(s), level " + a.tp2() + " (note " + start + "+" + len + ")", null);
            list.add(new Span(note, wa, wb, bt, bv, cv, hv, ht, hvv, at, av));
         } catch (Throwable t) {
            Expr.log("Expression shapes: a span could not be read", t);
         }
      }
      return list;
   }

   /** Writes a shape over the session's stretches (on the melted line when Melt is on). */
   static void writeSpans(List<Span> list, bob editor, OjS selection, kPd clip, hJh expression, ucd parameter, Shape shape, int cycles) {
      lastShape = shape;
      lastCycles = cycles;
      sessionShaped = true;
      render(list, editor, selection, clip, expression, parameter);
   }

   /**
    * Writes the session's stretches as they should look now: with a shape, the shape on the line
    * (melted or not); without one yet, just the melted S-curves (as a few editable points).
    */
   static void render(List<Span> list, bob editor, OjS selection, kPd clip, hJh expression, ucd parameter) {
      if (list.isEmpty()) {
         NocShapePanel.status("Nothing after the selected point");
         return;
      }
      double[] range = mapping(parameter);
      double amp = amplitude(expression, range);
      double m = Math.max(0, Math.min(0.95, meltAmount));
      boolean shaped = sessionShaped;
      Shape shape = lastShape;
      int cycles = shape.repeats ? lastCycles : 1;
      try {
         editor.fJR().hQm();
      } catch (Throwable ignored) {
      }
      int[] count = {0};
      undoable(list.get(0).note(), () -> {
         for (Span sp : list) {
            try {
               double ta = sp.ta(), tb = sp.tb();
               double[] t, v, c;
               if (shaped) {
                  double[][] p = points(shape, cycles, growth, amp, 0.0, Mode.RAW, ta, tb - ta);
                  t = p[0];
                  v = p[1];
                  for (int i = 0; i < t.length; i++) {
                     v[i] = Math.max(0, Math.min(1, sp.ride(t[i], m) + v[i]));
                  }
                  c = new double[t.length];
               } else {
                  double[][] q = structure(sp, m);
                  t = q[0];
                  v = q[1];
                  c = q[2];
               }
               if (simplifyAmount > 0.005) {
                  double[][] q = simplify(t, v, c, simplifyAmount);
                  t = q[0];
                  v = q[1];
                  c = q[2];
               }
               // The clip that owns this note: with several tracks layered in the editor it isn't the
               // editor's own clip, and writing with the wrong one leaves points stuck and uneditable.
               kPd owner = clipOf(sp.note());
               if (owner == null) {
                  owner = clip;   // the editor's clip (fine when editing a single track)
               }
               sp.note().cfM(parameter, t, v, jRs.MRY, c, owner, false, true, 0.0, selection);
               count[0]++;
            } catch (Throwable t) {
               Expr.log("Expression shapes: a span failed", t);
            }
         }
      });
      int done = count[0];
      String what = shaped ? shape.label : (m > 0.005 ? "Melt " + Math.round(m * 100) + " %" : "Curve");
      if (simplifyAmount > 0.005) {
         what += ", simplified";
      }
      NocShapePanel.status(what + " on " + done + (done == 1 ? " note" : " notes"));
      Expr.log("Expression shapes: " + what + (shaped ? " x" + cycles : "") + " on " + done + " note(s)", null);
   }

   /**
    * Without a shape: the stretch as editable points. Melt on: an S-curve per stretch between the
    * selected nodes (ease out with negative curvature, back in with positive, a point in the middle).
    * Melt off: the curve that was there.
    */
   static double[][] structure(Span sp, double m) {
      List<double[]> out = new ArrayList<>();   // {time, value, curvature of the segment after it}
      double ta = sp.ta(), tb = sp.tb();
      if (m > 0.005) {
         double[] at = sp.at(), av = sp.av();
         out.add(new double[]{ta, sp.melted(ta, m), -m});
         for (int i = 1; i < at.length; i++) {
            double mid = (at[i - 1] + at[i]) / 2;
            if (mid > ta && mid < tb) {
               out.add(new double[]{mid, (av[i - 1] + av[i]) / 2, m});
            }
            if (i < at.length - 1 && at[i] > ta && at[i] < tb) {
               out.add(new double[]{at[i], av[i], -m});
            }
         }
         out.add(new double[]{tb, sp.melted(tb, m), 0});
      } else {
         double[] bt = sp.bt(), bv = sp.bv(), cv = sp.curve();
         int seg = 0;
         while (seg + 1 < bt.length && bt[seg + 1] <= ta) {
            seg++;
         }
         out.add(new double[]{ta, sp.exact(ta), cv[Math.min(seg, cv.length - 1)]});
         for (int i = 0; i < bt.length; i++) {
            if (bt[i] > ta && bt[i] < tb) {
               out.add(new double[]{bt[i], bv[i], cv[i]});
            }
         }
         out.add(new double[]{tb, sp.exact(tb), 0});
      }
      double[] t = new double[out.size()], v = new double[out.size()], c = new double[out.size()];
      for (int i = 0; i < t.length; i++) {
         t[i] = out.get(i)[0];
         v[i] = Math.max(0, Math.min(1, out.get(i)[1]));
         c[i] = out.get(i)[2];
      }
      return new double[][]{t, v, c};
   }

   /** How soft Melt makes each S-curve (Bitwig curvature on each half), 0..0.95. */
   static volatile double meltAmount = 0;

   /** Simplify (0..1): redraws the stretch with as few points as fit it (0 = off). */
   static volatile double simplifyAmount = 0;

   /**
    * How far a simplified curve may stray from the original at this Simplify amount, as a share of the
    * stretch's own swing (so a small pitch wiggle is held as tightly as a full-range sweep).
    */
   static double simplifyTolerance(double amount) {
      return 0.01 + amount * 0.05;
   }

   /**
    * The curve through (t, v) with curvature c (per segment), redrawn with as few points as keep its shape,
    * the way you'd draw it by hand with Alt-curved segments: a point on every peak and trough, one on the
    * steepest spot between them (where the curve turns from bending one way to the other), and each segment
    * in between bent to fit. A segment that still strays more than the tolerance is split at its worst spot.
    * Higher amounts also let go of wiggles that are small next to the whole stretch.
    */
   static double[][] simplify(double[] t, double[] v, double[] c, double amount) {
      int n = t.length;
      if (n < 3) {
         return new double[][]{t, v, c};
      }
      // Sample the original curve finely (its own points included).
      java.util.TreeSet<Double> times = new java.util.TreeSet<>();
      int samples = 960;
      for (int i = 0; i <= samples; i++) {
         times.add(t[0] + (t[n - 1] - t[0]) * i / samples);
      }
      for (double x : t) {
         times.add(x);
      }
      double[] st = new double[times.size()];
      double[] sv = new double[st.length];
      int k = 0;
      int seg = 0;
      for (double x : times) {
         while (seg < n - 2 && x > t[seg + 1]) {
            seg++;
         }
         st[k] = x;
         sv[k] = Span.segment(x, t[seg], t[seg + 1], v[seg], v[seg + 1], c[seg]);
         k++;
      }
      int m = st.length;
      double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
      for (double x : sv) {
         lo = Math.min(lo, x);
         hi = Math.max(hi, x);
      }
      double swing = hi - lo;
      if (swing < 1e-9) {   // flat: just the two ends
         return new double[][]{{st[0], st[m - 1]}, {sv[0], sv[m - 1]}, {0, 0}};
      }

      // Peaks and troughs, ignoring wiggles smaller than the detail this amount lets go of.
      double detail = swing * Math.max(0.002, amount * amount * 0.12);
      List<Integer> ext = extremes(sv, detail);

      // Between each pair, the steepest spot: where the curve stops bending one way and starts the other.
      java.util.TreeSet<Integer> keys = new java.util.TreeSet<>(ext);
      for (int i = 0; i + 1 < ext.size(); i++) {
         int a = ext.get(i), b = ext.get(i + 1);
         int steep = -1;
         double most = 0;
         for (int j = a + 1; j < b; j++) {
            double dt = st[j + 1] - st[j - 1];
            if (dt <= 0) {
               continue;
            }
            double slope = Math.abs(sv[j + 1] - sv[j - 1]) / dt;
            if (slope > most) {
               most = slope;
               steep = j;
            }
         }
         // Only where one curved segment can't follow it (an S, as between a peak and a trough).
         if (steep > a && steep < b && oneSegmentError(st, sv, a, b) > swing * simplifyTolerance(amount)) {
            keys.add(steep);
         }
      }

      double tol = swing * simplifyTolerance(amount);
      List<Integer> keep = new ArrayList<>();
      List<Double> curves = new ArrayList<>();
      keep.add(0);
      Integer prev = null;
      for (int key : keys) {
         if (prev != null) {
            fit(st, sv, prev, key, tol, keep, curves, 0);
         }
         prev = key;
      }
      int out = keep.size();
      double[] ot = new double[out], ov = new double[out], oc = new double[out];
      for (int i = 0; i < out; i++) {
         ot[i] = st[keep.get(i)];
         ov[i] = sv[keep.get(i)];
         oc[i] = i < curves.size() ? curves.get(i) : 0;
      }
      return new double[][]{ot, ov, oc};
   }

   /** The two ends plus every peak and trough that swings at least `detail` from the last one kept. */
   private static List<Integer> extremes(double[] v, double detail) {
      List<Integer> out = new ArrayList<>();
      int n = v.length;
      out.add(0);
      int cand = 0;          // the most extreme sample since the last kept turn
      int dir = 0;           // +1 rising, -1 falling, 0 not known yet
      for (int i = 1; i < n; i++) {
         if (dir == 0) {
            if (Math.abs(v[i] - v[0]) >= detail) {
               dir = v[i] > v[0] ? 1 : -1;
               cand = i;
            }
            continue;
         }
         if ((dir > 0 && v[i] >= v[cand]) || (dir < 0 && v[i] <= v[cand])) {
            cand = i;
         } else if (Math.abs(v[i] - v[cand]) >= detail) {
            out.add(cand);   // it turned back far enough: cand was a peak or trough
            dir = -dir;
            cand = i;
         }
      }
      if (cand != 0 && cand != n - 1 && out.get(out.size() - 1) != cand
            && Math.abs(v[n - 1] - v[cand]) >= detail * 0.5) {
         out.add(cand);
      }
      if (out.get(out.size() - 1) != n - 1) {
         out.add(n - 1);
      }
      return out;
   }

   /** The smallest worst-case error of one Bitwig curved segment through samples a..b. */
   private static double oneSegmentError(double[] st, double[] sv, int a, int b) {
      double bestErr = Double.MAX_VALUE;
      for (double c = -0.99; c <= 0.99 + 1e-12; c += 0.03) {
         double err = 0;
         for (int i = a + 1; i < b && err < bestErr; i++) {
            err = Math.max(err, Math.abs(Span.segment(st[i], st[a], st[b], sv[a], sv[b], c) - sv[i]));
         }
         bestErr = Math.min(bestErr, err);
      }
      return bestErr;
   }

   /** Fits samples a..b with one curved segment, or splits it; appends the kept end points and curvatures in order. */
   private static void fit(double[] st, double[] sv, int a, int b, double tol, List<Integer> keep, List<Double> curves, int depth) {
      double best = 0;
      double bestErr = Double.MAX_VALUE;
      int worst = -1;
      for (int pass = 0; pass < 3; pass++) {
         double width = pass == 0 ? 0.99 : pass == 1 ? 0.06 : 0.008;
         double step = pass == 0 ? 0.03 : pass == 1 ? 0.005 : 0.001;
         double from = pass == 0 ? -width : best - width;
         double to = pass == 0 ? width : best + width;
         for (double cv = from; cv <= to + 1e-12; cv += step) {
            double c = Math.max(-0.99, Math.min(0.99, cv));
            double err = 0;
            int at = -1;
            for (int i = a + 1; i < b; i++) {
               double e = Math.abs(Span.segment(st[i], st[a], st[b], sv[a], sv[b], c) - sv[i]);
               if (e > err) {
                  err = e;
                  at = i;
               }
               if (err >= bestErr) {
                  break;   // already worse than the best so far
               }
            }
            if (err < bestErr) {
               bestErr = err;
               best = c;
               worst = at;
            }
         }
      }
      if (bestErr <= tol || b - a < 2 || worst <= a || worst >= b || depth > 40) {
         curves.add(Math.abs(best) < 0.004 ? 0 : best);
         keep.add(b);
         return;
      }
      fit(st, sv, a, worst, tol, keep, curves, depth + 1);
      fit(st, sv, worst, b, tol, keep, curves, depth + 1);
   }

   static void melt(bob editor, hJh expression, ucd parameter, List<Object> items) {
      melt(editor, expression, parameter, items, false);
   }

   /**
    * Melt: the lines between the selected nodes become soft S-curves (like auto-fades). live: from the
    * panel's Melt value, on the panel's session (a shape already written is redrawn on the melted line).
    */
   static void melt(bob editor, hJh expression, ucd parameter, List<Object> items, boolean live) {
      try {
         OjS selection = editor.mR_();
         kPd clip = editor.mV_();
         List<Span> list;
         java.util.Map<dpB, List<hMP>> picked = byNote(items);
         if (live && sessionActive() && (picked.isEmpty() || insideHeld(picked))) {
            list = heldSpans;   // still the same stretch (or Bitwig dropped the selection after a write)
         } else {
            java.util.Map<dpB, List<hMP>> spans = picked;
            if (spans.isEmpty()) {
               NocShapePanel.status("Select expression points to melt between");
               return;
            }
            if (!live) {
               mergeKey = null;
            }
            list = startSession(spans, parameter);
         }
         render(list, editor, selection, clip, expression, parameter);
      } catch (Throwable t) {
         Expr.log("Expression shapes: melt failed", t);
      }
   }

   /** Expression points can arrive as a view (MGc/fBt, e.g. in the point's right-click menu): use the point itself. */
   static List<Object> unwrap(List<Object> items) {
      List<Object> out = new ArrayList<>(items.size());
      for (Object o : items) {
         if (o instanceof jvK m) {
            try {
               hMP c = m.lYc();
               if (c != null) {
                  rememberClip(c, m);
               }
               out.add(c != null ? c : o);
               continue;
            } catch (Throwable ignored) {
            }
         }
         out.add(o);
      }
      return out;
   }

   /** Runs the writes as one undoable step; with a merge key, merges into the previous step with the same key. */
   static void undoable(Object document, Runnable body) {
      undoable(document, "Expression Shape", body);
   }

   static void undoable(Object document, String name, Runnable body) {
      undoable(document, name, mergeKey != null && "Expression Shape".equals(name) ? mergeKey : new Object(), body);
   }

   /** One undoable step; writes with the same key merge into one step. */
   static void undoable(Object document, String name, Object key, Runnable body) {
      NocUndo edit = new NocUndo();
      boolean began = false;
      try {
         if (document instanceof oDc doc) {
            began = edit.cfM(doc, name, new FDA(key, 0));
         }
      } catch (Throwable t) {
         Expr.log("Expression shapes: could not start an undo step", t);
      }
      try {
         body.run();
      } finally {
         if (began) {
            try {
               edit.MRY();
            } catch (Throwable t) {
               Expr.log("Expression shapes: could not finish the undo step", t);
            }
         }
      }
   }

   static List<dpB> notesIn(List<Object> items) {
      Set<dpB> out = new LinkedHashSet<>();
      for (Object o : items) {
         if (o instanceof dpB w) {
            out.add(w);
         } else if (o instanceof bOw m) {
            try {
               out.add(m.cfM());
            } catch (Throwable ignored) {
            }
         }
      }
      return new ArrayList<>(out);
   }

   /** Selected notes from the editor selection (whatever form the selection holds them in). */
   static List<dpB> selectedNotes(OjS selection) {
      Set<dpB> out = new LinkedHashSet<>();
      List<Object> items = selectionItems(selection);
      try {
         items.addAll(selection.TgI().E3());
      } catch (Throwable ignored) {
      }
      try {
         items.addAll(selection.RDJ().IwV());
      } catch (Throwable ignored) {
      }
      try {
         items.addAll(selection.RDJ().UNj());
      } catch (Throwable ignored) {
      }
      for (Object o : items) {
         if (o instanceof dpB w) {
            out.add(w);
         } else if (o instanceof hMP) {
            // a selected expression point: handled as a span (see apply)
         } else if (o instanceof bOw m) {
            try {
               out.add(m.cfM());
            } catch (Throwable ignored) {
            }
         }
      }
      return new ArrayList<>(out);
   }

   /** Everything in the editor's event selection (notes, expression points…). */
   static List<Object> selectionItems(OjS selection) {
      List<Object> items = new ArrayList<>();
      try {
         if (selection instanceof jdO j) {
            iGx ev = j.dnz();   // the editor's event selection (notes, expression points…)
            if (ev != null) {
               items.addAll(ev.FzB());
            }
         } else if (selection instanceof Bz f) {
            Object ev = f.zxZ();
            if (ev instanceof ICh s) {
               items.addAll(s.FzB());
            }
         }
      } catch (Throwable t) {
         Expr.log("Expression shapes: could not read the event selection", t);
      }
      return items;
   }

   /** The note an expression point belongs to (its owner chain). */
   static dpB noteOf(Object point) {
      Object o = point;
      for (int i = 0; i < 12 && o != null; i++) {
         if (o instanceof dpB w) {
            return w;
         }
         try {
            o = ((oDc)o).K_();
         } catch (Throwable t) {
            return null;
         }
      }
      return null;
   }

   private static int chainLogged;

   /** Each note's clip, as told by the point views the editor selected (MGc.QmZ()); by note object. */
   private static final java.util.Map<Object, kPd> NOTE_CLIPS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

   private static void rememberClip(hMP point, jvK view) {
      try {
         Object clip = view.dnz();
         dpB note = noteOf(point);
         if (note != null && clip instanceof kPd c) {
            if (NOTE_CLIPS.size() > 512) {
               NOTE_CLIPS.clear();
            }
            NOTE_CLIPS.put(note, c);
         }
      } catch (Throwable ignored) {
      }
   }

   /** The clip a note belongs to (from its selected points, else walking up its owners); null when unknown. */
   static kPd clipOf(Object note) {
      kPd known = NOTE_CLIPS.get(note);
      if (known != null) {
         return known;
      }
      Object o = note;
      StringBuilder chain = new StringBuilder();
      for (int i = 0; i < 16 && o != null; i++) {
         if (o instanceof kPd c) {
            return c;
         }
         chain.append(o.getClass().getName()).append(" > ");
         try {
            Object up = ((oDc)o).K_();
            o = up == o ? null : up;
         } catch (Throwable t) {
            chain.append("(").append(t.getClass().getSimpleName()).append(")");
            break;
         }
      }
      if (chainLogged++ < 3) {
         Expr.log("Expression shapes: no clip above the note; owners: " + chain, null);
      }
      return null;
   }

   static double time(Object point) {
      return ((vFg)point).x3t();
   }

   /** Notes reachable from the right-clicked object or the editor's own selection model. */
   static List<dpB> notesFrom(bob editor, Object target) {
      Set<dpB> out = new LinkedHashSet<>();
      try {
         yXG sel = ((bos)editor).fFs();
         sel.Grk().forEach(o -> {
            if (o instanceof dpB w) {
               out.add(w);
            } else if (o instanceof bOw m) {
               try {
                  out.add(m.cfM());
               } catch (Throwable ignored) {
               }
            }
         });
      } catch (Throwable ignored) {
      }
      return new ArrayList<>(out);
   }

   private static String describe(Object o) {
      if (o == null) {
         return "null";
      }
      StringBuilder b = new StringBuilder(o.getClass().getName());
      for (Class<?> c = o.getClass().getSuperclass(); c != null && c != Object.class && b.length() < 200; c = c.getSuperclass()) {
         b.append('>').append(c.getName());
      }
      return b.toString();
   }

   private static String items(java.util.function.Supplier<java.util.Collection<?>> s) {
      try {
         java.util.Collection<?> c = s.get();
         StringBuilder b = new StringBuilder(c.size() + "[");
         int i = 0;
         for (Object o : c) {
            if (i++ > 3) {
               break;
            }
            b.append(o == null ? "null" : o.getClass().getName()).append(' ');
         }
         return b.append(']').toString();
      } catch (Throwable t) {
         return "err:" + t.getClass().getSimpleName();
      }
   }

   static void diagnose(bob editor, OjS selection, Object target) {
      try {
         StringBuilder b = new StringBuilder("Expression shapes diagnostics: target ").append(describe(target));
         b.append(" | selection ").append(describe(selection));
         b.append(" | events ").append(items(() -> selectionItems(selection)));
         for (OjS y : new OjS[]{selection, editor.mR_()}) {
            if (y == null) {
               continue;
            }
            b.append(" | Sjw ").append(items(() -> y.TgI().E3()));
            b.append(" a3J.xeZ ").append(items(() -> y.RDJ().IwV()));
            b.append(" a3J.KF3 ").append(items(() -> y.RDJ().UNj()));
         }
         try {
            yXG sel = ((bos)editor).fFs();
            b.append(" | PaR ").append(describe(sel)).append(" Klm ").append(items(() -> sel.Grk().toList()));
         } catch (Throwable t) {
            b.append(" | PaR err ").append(t);
         }
         Expr.log(b.toString(), null);
      } catch (Throwable t) {
         Expr.log("Expression shapes diagnostics failed", t);
      }
   }

   /** A value in the expression's units (as points report it) as 0..1 over its range. */
   static double normalized(double v, double[] range) {
      double lo = range[2], hi = range[3];
      if (!(hi > lo)) {
         return Math.max(0, Math.min(1, v));
      }
      return Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
   }

   /** Where the parameter rests, as a normalized value, and whether it only goes one way from there. */
   static double[] mapping(ucd parameter) {
      double center = 0.5;
      boolean unipolar = false;
      double lo = 0, hi = 1;
      try {
         Object m = cM.cfM(parameter);
         if (m instanceof aL a) {
            double min = a.FzB(), max = a.UNj(), def = a.dvk();
            lo = min;
            hi = max;
            if (max > min) {
               center = Math.max(0, Math.min(1, (def - min) / (max - min)));
               unipolar = center < 0.02 || center > 0.98;
            }
            if (!loggedMapping) {
               loggedMapping = true;
               Expr.log("Expression shapes: " + parameter + " range " + min + ".." + max + " default " + def, null);
            }
         }
      } catch (Throwable ignored) {
      }
      return new double[]{center, unipolar ? 1 : 0, lo, hi};
   }

   /** How a shape's -1..1 maps onto the parameter. */
   enum Mode {
      BIPOLAR,   // swings both ways around the resting value (transpose, pan, timbre)
      UP,        // rises from the resting value only (pressure)
      DOWN,      // the top stays at the resting value and dips below it (gain: 0 dB down toward -inf)
      RAW        // offsets in normalized units, centred on 0 (spans between points)
   }

   static Mode mode(hJh e, double[] range) {
      if (isGain(e)) {
         return Mode.DOWN;
      }
      if (range[1] > 0) {
         return range[0] < 0.5 ? Mode.UP : Mode.DOWN;
      }
      return Mode.BIPOLAR;
   }

   /** The chosen amount as a normalized offset. */
   static double amplitude(hJh e, double[] range) {
      double a = amount(e);
      if (isTranspose(e)) {
         double span = range[3] - range[2];
         return span > 0 ? a / span : a / 192.0;
      }
      return a / 100.0;
   }

   /** Points (times inside the note's content, normalized values) for one note. */
   static double[][] points(Shape shape, int cycles, double grow, double amp, double center, Mode mode,
         double start, double length) {
      List<double[]> pts = new ArrayList<>();
      double eps = Math.min(length / 1000.0, 1.0 / 960.0);
      Random random = new Random(Double.doubleToLongBits(start) * 31 + cycles);
      boolean oneWay = mode == Mode.UP || mode == Mode.DOWN;
      switch (shape) {
         case SINE -> {
            int n = Math.max(16, cycles * 16);
            for (int i = 0; i <= n; i++) {
               double x = i / (double)n;
               // one-way: start at the resting value (cosine), two-way: start in the middle (sine)
               pts.add(new double[]{x, oneWay ? Math.cos(2 * Math.PI * cycles * x) : Math.sin(2 * Math.PI * cycles * x)});
            }
         }
         case TRIANGLE -> {
            double[] tri = oneWay ? new double[]{1, 0, -1, 0} : new double[]{0, 1, 0, -1};
            for (int i = 0; i <= cycles * 4; i++) {
               pts.add(new double[]{i / (cycles * 4.0), tri[i % 4]});
            }
         }
         case SAW_UP, SAW_DOWN -> {
            double sign = shape == Shape.SAW_UP ? 1 : -1;
            for (int c = 0; c < cycles; c++) {
               double a = c / (double)cycles, b = (c + 1) / (double)cycles;
               pts.add(new double[]{a, -sign});
               pts.add(new double[]{c == cycles - 1 ? 1 : b - eps / length, sign});
            }
         }
         case SQUARE -> {
            for (int c = 0; c < cycles * 2; c++) {
               double a = c / (cycles * 2.0), b = (c + 1) / (cycles * 2.0);
               double v = c % 2 == 0 ? 1 : -1;
               pts.add(new double[]{a, v});
               pts.add(new double[]{c == cycles * 2 - 1 ? 1 : b - eps / length, v});
            }
         }
         case RAMP_UP -> {
            // two-way params: rest -> +amount; one-way (gain): silent -> full (fade in)
            pts.add(new double[]{0, oneWay ? -1 : 0});
            pts.add(new double[]{1, 1});
         }
         case RAMP_DOWN -> {
            pts.add(new double[]{0, oneWay ? 1 : 0});
            pts.add(new double[]{1, oneWay ? -1 : -1});
         }
         case FLAT -> {
            pts.add(new double[]{0, oneWay ? 1 : 0});
            pts.add(new double[]{1, oneWay ? 1 : 0});
         }
         case RANDOM -> {
            int steps = Math.max(2, cycles * 2);
            for (int i = 0; i < steps; i++) {
               double v = random.nextDouble() * 2 - 1;
               pts.add(new double[]{i / (double)steps, v});
               pts.add(new double[]{(i + 1) / (double)steps - eps / length, v});
            }
         }
      }
      double[] times = new double[pts.size()];
      double[] values = new double[pts.size()];
      double k = Math.max(-1, Math.min(1, skew));
      double power = 1 + Math.abs(k) * 2;
      for (int i = 0; i < pts.size(); i++) {
         double u = Math.max(0, Math.min(1, pts.get(i)[0]));
         // skew: positive squeezes the cycles toward the end of the note, negative toward the start
         double x = k > 0 ? 1 - Math.pow(1 - u, power) : k < 0 ? Math.pow(u, power) : u;
         // grow > 0: starts smaller and reaches full size at the end; grow < 0: fades out toward the end
         double e = grow > 0 ? 1 - grow * (1 - x) : grow < 0 ? 1 + grow * x : 1.0;
         if (edges > 0.001) {
            double f = Math.min(1, Math.min(x, 1 - x) / edges);
            e *= f * f * (3 - 2 * f);   // smooth fade in at the start and out at the end
         }
         double s = pts.get(i)[1];
         double v = switch (mode) {
            case BIPOLAR -> center + amp * e * s;
            // s = +1 is the resting value; s = -1 is the full amount away from it
            case UP -> center + (1 - center) * amp * e * (1 - s) / 2;
            case DOWN -> center - center * amp * e * (1 - s) / 2;
            case RAW -> amp * e * s;
         };
         times[i] = start + x * length;
         values[i] = mode == Mode.RAW ? v : Math.max(0, Math.min(1, v));
      }
      // Keep points at least 1/256 beat apart and strictly in order (very dense writes have crashed the
      // audio engine); always keep the first and the last.
      double minGap = 1.0 / 256.0;
      int n = 0;
      double[] kt = new double[times.length], kv = new double[times.length];
      for (int i = 0; i < times.length; i++) {
         boolean last = i == times.length - 1;
         if (n > 0 && !(times[i] - kt[n - 1] >= minGap)) {
            if (!last) {
               continue;
            }
            if (n > 1 && !(times[i] - kt[n - 1] > 1e-6)) {
               n--;   // the last one replaces a point sitting on top of it
            } else if (n == 1 && !(times[i] - kt[0] > 1e-6)) {
               continue;
            }
         }
         kt[n] = times[i];
         kv[n] = values[i];
         n++;
      }
      return new double[][]{java.util.Arrays.copyOf(kt, n), java.util.Arrays.copyOf(kv, n)};
   }
}
