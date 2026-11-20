package io.github.noctaur.trackview;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.IWT; // draws and hit tests one note's expression curve
import com.bitwig.graphics.SYo;                 // a graphics context

/** Every hook of Piano Roll: Global Track View. The logic is in {@link TrackLayers}. */
public final class TrackViewHooks {
   private TrackViewHooks() {
   }

   private static final ThreadLocal<Integer> LAYER_COUNT = new ThreadLocal<>();

   // ------------------------------------------------------------------ track views and the Edit Tracks menu

   /** bju: a project's track view widget, made for a track view model (GRp). */
   @Hook(target = big.class, method = "<init>", at = At.BEFORE_RETURN, id = "remember-track-view")
   public static void trackView(@Arg(0) ICh view) {
      try {
         TrackLayers.viewMade(view);
      } catch (Throwable t) {
         TrackLayers.MOD.log("track view", t);
      }
   }

   /** aOW: the editor's track (layer) list; beI(dMt, eqv) fills its right-click menu. */
   @Hook(target = aNP.class, method = "cfM(LdKW;LeoV;)V", at = At.BEFORE_RETURN, id = "edit-tracks-menu")
   public static void editTracksMenu(@Arg(0) dKW menu) {
      TrackLayers.menu(menu);
   }

   // ------------------------------------------------------------------ layer list from one track, layered editing

   /** aNi: the note editor; Gef(Integer) answers whether its layer list shows for that many layers. */
   @Hook(target = aMh.class, method = "MRY(Ljava/lang/Integer;)Ljava/lang/Boolean;", at = At.ENTRY, id = "layer-count")
   public static void layerCount(@Arg(0) Integer count) {
      LAYER_COUNT.set(count);
   }

   @Hook(target = aMh.class, method = "MRY(Ljava/lang/Integer;)Ljava/lang/Boolean;", at = At.WRAP_RETURN, id = "layer-list-visible")
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
   @Hook(target = aMh.class, method = "MRY(LPUL;LbiQ;)Ldve;", at = At.ENTRY, id = "editor-seen")
   public static void editorSeen(@This aMh editor) {
      TrackLayers.editorSeen(editor);
   }

   // ------------------------------------------------------------------ Track List panel

   /** amq.Gef(): the Edit screen's layout. */
   @Hook(target = alr.class, method = "MRY()Lakw;", at = At.WRAP_RETURN, id = "track-list-panel")
   public static Object trackListPanel(@Return akw content) {
      return TrackLayers.editLayout(content);
   }

   // ------------------------------------------------------------------ layer filter for note expressions
   //
   // bpF, bpw, bpy and bpV are the editor's layer kinds. Each visits a layer's note expressions (for painting
   // and for hit testing) in two methods; the one with a bpW first comes from the other. On entry the hook
   // notes whether the layer is greyed out by "edit selected layers only"; the calls that do the visiting are
   // then skipped for it (hit tests) or run without point dots (painting).

   private static final String VISIT = "cfM(LboJ;Ldt;Lucd;DDLboQ;LpCi;Z)V";    // visit with a note
   private static final String VISIT_ALL = "cfM(Ldt;Lucd;DDLboQ;LpCi;Z)V";    // visit every note
   private static final String WITH_NOTE = "cfM(LboJ;LdH;DDLboQ;LpCi;ZZZ)V";  // the layer's own visit, one note
   private static final String NO_NOTE = "cfM(LdH;DDLboQ;LpCi;LpCi;ZZZ)V";    // the layer's own visit, no note
   private static final String CURVE = "cfM(LII;LdH;DDLOjS;ZLpCi;LpCi;LpCi;LpCi;ZZ)V"; // one curve
   private static final String AUDIO_CURVE = "cfM(Lekt;LdH;DDLyXG;ZLpCi;LpCi;LpCi;Z)V";   // one audio curve

   // entry and exit, per layer kind and method

   @Hook(target = bos.class, method = VISIT, at = At.ENTRY, id = "bpf-visit-entry")
   public static void bpfVisit(@This bos layer, @Arg(1) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bos.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpf-visit-exit")
   public static void bpfVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bok.class, method = VISIT, at = At.ENTRY, id = "bpw-visit-entry")
   public static void bpwVisit(@This bok layer, @Arg(1) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bok.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpw-visit-exit")
   public static void bpwVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bok.class, method = VISIT_ALL, at = At.ENTRY, id = "bpw-visit-all-entry")
   public static void bpwVisitAll(@This bok layer, @Arg(0) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bok.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpw-visit-all-exit")
   public static void bpwVisitAllExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bom.class, method = VISIT, at = At.ENTRY, id = "bpy-visit-entry")
   public static void bpyVisit(@This bom layer, @Arg(1) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bom.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpy-visit-exit")
   public static void bpyVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = bom.class, method = VISIT_ALL, at = At.ENTRY, id = "bpy-visit-all-entry")
   public static void bpyVisitAll(@This bom layer, @Arg(0) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = bom.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpy-visit-all-exit")
   public static void bpyVisitAllExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = boI.class, method = VISIT, at = At.ENTRY, id = "bpv-visit-entry")
   public static void bpvVisit(@This boI layer, @Arg(1) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = boI.class, method = VISIT, at = At.BEFORE_RETURN, id = "bpv-visit-exit")
   public static void bpvVisitExit() {
      TrackLayers.endVisit();
   }

   @Hook(target = boI.class, method = VISIT_ALL, at = At.ENTRY, id = "bpv-visit-all-entry")
   public static void bpvVisitAll(@This boI layer, @Arg(0) dt visitor) {
      enter(layer, visitor);
   }

   @Hook(target = boI.class, method = VISIT_ALL, at = At.BEFORE_RETURN, id = "bpv-visit-all-exit")
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

   @Hook(target = bos.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bos.class, call = WITH_NOTE, id = "bpf-with-note")
   public static void bpfWithNote(bos r, boJ note, dH v, double a, double b, boQ c, pCi d, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.cfM(note, v, a, b, c, d, e, f, g);
      }
   }

   @Hook(target = bos.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bos.class, call = NO_NOTE, id = "bpf-no-note")
   public static void bpfNoNote(bos r, dH v, double a, double b, boQ c, pCi d, pCi d2, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.cfM(v, a, b, c, d, d2, e, f, g);
      }
   }

   @Hook(target = bok.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bok.class, call = WITH_NOTE, id = "bpw-with-note")
   public static void bpwWithNote(bok r, boJ note, dH v, double a, double b, boQ c, pCi d, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.cfM(note, v, a, b, c, d, e, f, g);
      }
   }

   @Hook(target = bok.class, method = VISIT, at = At.REPLACE_CALL, callOwner = bok.class, call = NO_NOTE, id = "bpw-no-note")
   public static void bpwNoNote(bok r, dH v, double a, double b, boQ c, pCi d, pCi d2, boolean e, boolean f, boolean g) {
      if (!TrackLayers.skip()) {
         r.cfM(v, a, b, c, d, d2, e, f, g);
      }
   }

   @Hook(target = bok.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = IWT.class, call = CURVE, id = "bpw-all-curve")
   public static void bpwAllCurve(II n, dH v, double a, double b, OjS c, boolean d, pCi e, pCi f, pCi g, pCi h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         IWT.cfM(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = bom.class, method = VISIT, at = At.REPLACE_CALL, callOwner = IWT.class, call = CURVE, id = "bpy-curve")
   public static void bpyCurve(II n, dH v, double a, double b, OjS c, boolean d, pCi e, pCi f, pCi g, pCi h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         IWT.cfM(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = bom.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = IWT.class, call = CURVE, id = "bpy-all-curve")
   public static void bpyAllCurve(II n, dH v, double a, double b, OjS c, boolean d, pCi e, pCi f, pCi g, pCi h, boolean i, boolean j) {
      if (!TrackLayers.skip()) {
         IWT.cfM(n, v, a, b, c, d, e, f, g, h, i, j);
      }
   }

   @Hook(target = boI.class, method = VISIT, at = At.REPLACE_CALL, callOwner = IWT.class, call = AUDIO_CURVE, id = "bpv-curve")
   public static void bpvCurve(ekt n, dH v, double a, double b, yXG c, boolean d, pCi e, pCi f, pCi g, boolean h) {
      if (!TrackLayers.skip()) {
         IWT.cfM(n, v, a, b, c, d, e, f, g, h);
      }
   }

   @Hook(target = boI.class, method = VISIT_ALL, at = At.REPLACE_CALL, callOwner = IWT.class, call = AUDIO_CURVE, id = "bpv-all-curve")
   public static void bpvAllCurve(ekt n, dH v, double a, double b, yXG c, boolean d, pCi e, pCi f, pCi g, boolean h) {
      if (!TrackLayers.skip()) {
         IWT.cfM(n, v, a, b, c, d, e, f, g, h);
      }
   }

   // btC paints a curve for btz (the expression painter), point by point; the btz call draws a point's dot.
   private static final String DOT = "cfM(Lcom/bitwig/graphics/SYo;DDDLdfo;Z)V";

   @Hook(target = bsn.class, method = "automationWillBePlayedBack", at = At.REPLACE_CALL, callOwner = bsk.class, call = DOT, id = "point-dot")
   public static void pointDot(bsk painter, SYo g, double x, double y, double w, dfo point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   @Hook(target = bsn.class, method = "automationSegmentWillBePlayedBack", at = At.REPLACE_CALL, callOwner = bsk.class, call = DOT, id = "segment-dot")
   public static void segmentDot(bsk painter, SYo g, double x, double y, double w, dfo point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   @Hook(target = bsn.class, method = "automationAfterEndPointWillBePlayedBack", at = At.REPLACE_CALL, callOwner = bsk.class, call = DOT, id = "end-dot")
   public static void endDot(bsk painter, SYo g, double x, double y, double w, dfo point, boolean hovered) {
      dot(painter, g, x, y, w, point, hovered);
   }

   private static void dot(bsk painter, SYo g, double x, double y, double w, dfo point, boolean hovered) {
      if (!TrackLayers.hideDots()) {
         painter.cfM(g, x, y, w, point, hovered); // draw the dot
      }
   }
}
