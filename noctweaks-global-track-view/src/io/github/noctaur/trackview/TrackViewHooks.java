package io.github.noctaur.trackview;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.sxg; // draws and hit tests one note's expression curve
import com.bitwig.graphics.ijY;                 // a graphics context

/** Every hook of Piano Roll: Global Track View. The logic is in {@link TrackLayers}. */
public final class TrackViewHooks {
   private TrackViewHooks() {
   }

   private static final ThreadLocal<Integer> LAYER_COUNT = new ThreadLocal<>();

   // ------------------------------------------------------------------ track views and the Edit Tracks menu

   /** bju: a project's track view widget, made for a track view model (GRp). */
   @Hook(target = bju.class, method = "<init>", at = At.BEFORE_RETURN, id = "remember-track-view")
   public static void trackView(@Arg(0) GRp view) {
      try {
         TrackLayers.viewMade(view);
      } catch (Throwable t) {
         TrackLayers.MOD.log("track view", t);
      }
   }

   /** aOW: the editor's track (layer) list; beI(dMt, eqv) fills its right-click menu. */
   @Hook(target = aOW.class, method = "beI(LdMt;Leqv;)V", at = At.BEFORE_RETURN, id = "edit-tracks-menu")
   public static void editTracksMenu(@Arg(0) dMt menu) {
      TrackLayers.menu(menu);
   }

   // ------------------------------------------------------------------ layer list from one track, layered editing

   /** aNi: the note editor; Gef(Integer) answers whether its layer list shows for that many layers. */
   @Hook(target = aNi.class, method = "Gef(Ljava/lang/Integer;)Ljava/lang/Boolean;", at = At.ENTRY, id = "layer-count")
   public static void layerCount(@Arg(0) Integer count) {
      LAYER_COUNT.set(count);
   }

   @Hook(target = aNi.class, method = "Gef(Ljava/lang/Integer;)Ljava/lang/Boolean;", at = At.WRAP_RETURN, id = "layer-list-visible")
   public static Boolean layerListVisible(@Return Boolean bitwig) {
      try {
         Boolean mine = TrackLayers.listVisible(LAYER_COUNT.get());
         return mine != null ? mine : bitwig;
      } catch (Throwable t) {
         return bitwig;
      } finally {
         LAYER_COUNT.remove();
      }
   }

   /** aNi.Gef(HSH, bke): the note editor builds its layer list. */
   @Hook(target = aNi.class, method = "Gef(LHSH;Lbke;)LdwF;", at = At.ENTRY, id = "editor-seen")
   public static void editorSeen(@This aNi editor) {
      TrackLayers.editorSeen(editor);
   }

   // ------------------------------------------------------------------ Track List panel

   /** amq.Gef(): the Edit screen's layout. */
   @Hook(target = amq.class, method = "Gef()Lalv;", at = At.WRAP_RETURN, id = "track-list-panel")
   public static Object trackListPanel(@Return alv content) {
      return TrackLayers.editLayout(content);
   }

   // ------------------------------------------------------------------ layer filter for note expressions
   //
   // bpF, bpw, bpy and bpV are the editor's layer kinds. Each visits a layer's note expressions (for painting
   // and for hit testing) in two methods; the one with a bpW first comes from the other. On entry the hook
   // notes whether the layer is greyed out by "edit selected layers only"; the calls that do the visiting are
   // then skipped for it (hit tests) or run without point dots (painting).

   private static final String VISIT = "beI(LbpW;LdE;Lsre;DDLbqf;Ltwa;Z)V";    // visit with a note (bpW)
   private static final String VISIT_ALL = "beI(LdE;Lsre;DDLbqf;Ltwa;Z)V";    // visit every note
   private static final String WITH_NOTE = "beI(LbpW;LdU;DDLbqf;Ltwa;ZZZ)V";  // the layer's own visit, one note
   private static final String NO_NOTE = "beI(LdU;DDLbqf;Ltwa;Ltwa;ZZZ)V";    // the layer's own visit, no note
   private static final String CURVE = "beI(Lyzs;LdU;DDLrBG;ZLtwa;Ltwa;Ltwa;Ltwa;ZZ)V"; // one curve
   private static final String AUDIO_CURVE = "beI(LNY;LdU;DDLdDL;ZLtwa;Ltwa;Ltwa;Z)V";   // one audio curve

   // entry and exit, per layer kind and method

   @Hook(target = bpF.class, method = VISIT, at = At.ENTRY, id = "bpf-visit-entry")
   public static void bpfVisit(@This bpF layer, @Arg(1) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpF.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpf-visit-exit")
   public static void bpfVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpw.class, method = VISIT, at = At.ENTRY, id = "bpw-visit-entry")
   public static void bpwVisit(@This bpw layer, @Arg(1) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpw.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpw-visit-exit")
   public static void bpwVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpw.class, method = VISIT_ALL, at = At.ENTRY, id = "bpw-visit-all-entry")
   public static void bpwVisitAll(@This bpw layer, @Arg(0) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpw.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpw-visit-all-exit")
   public static void bpwVisitAllExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpy.class, method = VISIT, at = At.ENTRY, id = "bpy-visit-entry")
   public static void bpyVisit(@This bpy layer, @Arg(1) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpy.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpy-visit-exit")
   public static void bpyVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpy.class, method = VISIT_ALL, at = At.ENTRY, id = "bpy-visit-all-entry")
   public static void bpyVisitAll(@This bpy layer, @Arg(0) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpy.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpy-visit-all-exit")
   public static void bpyVisitAllExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpV.class, method = VISIT, at = At.ENTRY, id = "bpv-visit-entry")
   public static void bpvVisit(@This bpV layer, @Arg(1) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpV.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpv-visit-exit")
   public static void bpvVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bpV.class, method = VISIT_ALL, at = At.ENTRY, id = "bpv-visit-all-entry")
   public static void bpvVisitAll(@This bpV layer, @Arg(0) dE visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bpV.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpv-visit-all-exit")
   public static void bpvVisitAllExit() {
      TrackLayers.endVisit();
   }

   private static void enter(Object layer, Object visitor) {
      try {
         TrackLayers.visit(layer, visitor);
      } catch (Throwable t) {
         TrackLayers.endVisit();
      }
   }

   // the visiting calls, skipped for a greyed-out layer's hit tests

   @Hook(target = bpF.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bpF.class, call = WITH_NOTE, id = "bpf-with-note")
   public static void bpfWithNote(bpF r, bpW note, dU v, double a, double b, bqf c, twa d, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.beI(note, v, a, b, c, d, e, f, g);
      }
   }

   @Hook(target = bpF.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bpF.class, call = NO_NOTE, id = "bpf-no-note")
   public static void bpfNoNote(bpF r, dU v, double a, double b, bqf c, twa d, twa d2, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.beI(v, a, b, c, d, d2, e, f, g);
      }
   }

   @Hook(target = bpw.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bpw.class, call = WITH_NOTE, id = "bpw-with-note")
   public static void bpwWithNote(bpw r, bpW note, dU v, double a, double b, bqf c, twa d, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.beI(note, v, a, b, c, d, e, f, g);
      }
   }

   @Hook(target = bpw.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bpw.class, call = NO_NOTE, id = "bpw-no-note")
   public static void bpwNoNote(bpw r, dU v, double a, double b, bqf c, twa d, twa d2, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.beI(v, a, b, c, d, d2, e, f, g);
      }
   }

   @Hook(target = bpw.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = sxg.class, call = CURVE, id = "bpw-all-curve")
   public static void bpwAllCurve(yzs n, dU v, double a, double b, rBG c, boolean d, twa e, twa f, twa g, twa h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         sxg.beI(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = bpy.class, method = VISIT, at = At.REPLACE_CALL, callOwner = sxg.class, call = CURVE, id = "bpy-curve")
   public static void bpyCurve(yzs n, dU v, double a, double b, rBG c, boolean d, twa e, twa f, twa g, twa h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         sxg.beI(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = bpy.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = sxg.class, call = CURVE, id = "bpy-all-curve")
   public static void bpyAllCurve(yzs n, dU v, double a, double b, rBG c, boolean d, twa e, twa f, twa g, twa h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         sxg.beI(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = bpV.class, method = VISIT, at = At.REPLACE_CALL, callOwner = sxg.class, call = AUDIO_CURVE, id = "bpv-curve")
   public static void bpvCurve(NY n, dU v, double a, double b, dDL c, boolean d, twa e, twa f, twa g, boolean h) {
      if (!TrackLayers.skip()) {
         sxg.beI(n, v, a, b, c, d, e, f, g, h);
      }
   }

   @Hook(target = bpV.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = sxg.class, call = AUDIO_CURVE, id = "bpv-all-curve")
   public static void bpvAllCurve(NY n, dU v, double a, double b, dDL c, boolean d, twa e, twa f, twa g, boolean h) {
      if (!TrackLayers.skip()) {
         sxg.beI(n, v, a, b, c, d, e, f, g, h);
      }
   }

   // btC paints a curve for btz (the expression painter), point by point; the btz call draws a point's dot.
   private static final String DOT = "beI(Lcom/bitwig/graphics/ijY;DDDLLCN;Z)V";

   @Hook(target = btC.class, method = "automationWillBePlayedBack", at = At.REPLACE_CALL, callOwner = btz.class, call = DOT, id = "point-dot")
   public static void pointDot(btz painter, ijY g, double x, double y, double w, LCN point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   @Hook(target = btC.class, method = "automationSegmentWillBePlayedBack", at = At.REPLACE_CALL, callOwner = btz.class, call = DOT, id = "segment-dot")
   public static void segmentDot(btz painter, ijY g, double x, double y, double w, LCN point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   @Hook(target = btC.class, method = "automationAfterEndPointWillBePlayedBack", at = At.REPLACE_CALL, callOwner = btz.class, call = DOT, id = "end-dot")
   public static void endDot(btz painter, ijY g, double x, double y, double w, LCN point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   private static void dot(btz painter, ijY g, double x, double y, double w, LCN point, boolean hovered) {
      if (!TrackLayers.hideDots()) {
         painter.beI(g, x, y, w, point, hovered); // draw the dot
      }
   }
}
