package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.base.geom.DoubleRectangle;
import com.bitwig.base.geom.IBe;   // an orientation (MRY: vertical)
import com.bitwig.graphics.SYo;
import com.bitwig.base.io.z2y;                                  // a file Bitwig loads (path + sub-item)
import com.bitwig.flt.document.core.master.g2L;                 // a loaded project

/**
 * Every hook of the Video mod. The logic is in Video.
 *
 * Bitwig 6.1.4 names:
 *   dzN.cfM(g2L, hKf, wat)  an arranger clip lane making the target for a drop; hKf.Hnt() is the drop's time
 *   tLg.cfM(z2y)Z           that target asking whether it takes a file (nw3.MRY: is it audio?)
 *   g2L.cfM(z2y[], ivQ, ZuD) a project loading dropped files into a target (every drop goes through here)
 *   aZv.cfM(dve, dKW)       the arranger's right-click menu (empty space)
 */
public final class VideoHooks {
   private VideoHooks() {
   }

   private static final ThreadLocal<Object> MENU = new ThreadLocal<>();
   private static final ThreadLocal<dve> MENU_WIDGET = new ThreadLocal<>();
   private static final ThreadLocal<dve> CLIP_MENU_WIDGET = new ThreadLocal<>();

   /** A file is being dragged over an arranger lane: note where, so a video starts where it was dropped. */
   @Hook(target = dzN.class, method = "cfM(Lcom/bitwig/flt/document/core/master/g2L;LhKf;Lwat;)LZuD;",
         at = At.ENTRY, id = "lane-drop-time")
   public static void laneDropTime(@Arg(1) hKf where) {
      try {
         Video.laneDrop(where.Hnt());
      } catch (Throwable t) {
         Video.log("Video: drop time", t);
      }
   }

   /** The lane takes audio files only: a video file is taken too (its sound is added as the clip). */
   @Hook(target = tLg.class, method = "cfM(Lcom/bitwig/base/io/z2y;)Z", at = At.REPLACE_CALL,
         callOwner = nw3.class, call = "MRY(Lcom/bitwig/base/io/z2y;)Z", id = "drop-accept")
   public static boolean dropAccept(z2y file) {
      boolean audio = nw3.MRY(file);
      try {
         if (!audio && file != null && Video.isVideo(file.cfM())) {
            return true;
         }
      } catch (Throwable t) {
         Video.log("Video: drop check", t);
      }
      return audio;
   }

   /** Dropped files about to load: each video file is swapped for its sound (a WAV beside it) and linked. */
   @Hook(target = g2L.class, method = "cfM([Lcom/bitwig/base/io/z2y;LivQ;LZuD;)V", at = At.ENTRY, id = "drop-files")
   public static void dropFiles(@This g2L project, @Arg(0) z2y[] files, @Arg(2) ZuD target) {
      try {
         Video.dropping(project, files, target);
      } catch (Throwable t) {
         Video.log("Video: drop", t);
      }
   }

   /** The arranger's right-click menu: the Video submenu. */
   @Hook(target = aZv.class, method = "cfM(Ldve;LdKW;)V", at = At.ENTRY, id = "arranger-menu-args")
   public static void arrangerMenuArgs(@Arg(0) dve widget, @Arg(1) dKW menu) {
      MENU.set(menu);
      MENU_WIDGET.set(widget);
   }

   @Hook(target = aZv.class, method = "cfM(Ldve;LdKW;)V", at = At.BEFORE_RETURN, id = "arranger-menu")
   public static void arrangerMenu(@This aZv arranger) {
      Object m = MENU.get();
      MENU.remove();
      dve widget = MENU_WIDGET.get();
      MENU_WIDGET.remove();
      try {
         Video.log("Video: arranger menu (" + (m == null ? "no menu" : "menu") + ", project " + arranger.gfw() + ")", null);
         if (m instanceof dKW menu && arranger.gfw() instanceof g2L project) {
            Video.seen(project);
            Video.menu(menu, project, widget);
         }
      } catch (Throwable t) {
         Video.log("Video: arranger menu", t);
      }
   }

   /**
    * bkS: the menu for a selection (right-click a clip): a Video submenu when an audio clip is selected.
    * big.UNj().FzB() is the selected items (as Expression Controls reads them).
    */
   @Hook(target = bkS.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.ENTRY, id = "video-clip-menu-widget")
   public static void clipMenuWidget(@Arg(1) dve widget) {
      CLIP_MENU_WIDGET.set(widget);
   }

   @Hook(target = bkS.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.BEFORE_RETURN, id = "video-clip-menu")
   public static void clipMenu(@Arg(0) dKW menu, @Arg(2) big header) {
      dve widget = CLIP_MENU_WIDGET.get();
      CLIP_MENU_WIDGET.remove();
      try {
         Video.clipMenu(menu, header == null ? java.util.List.of() : new java.util.ArrayList<Object>(header.UNj().FzB()), widget);
      } catch (Throwable t) {
         Video.log("Video: clip menu", t);
      }
   }

   /**
    * bpN / bst: Bitwig's painters for an audio clip on a timeline. After one paints a clip, a clip playing a
    * video's sound gets its film strip (ClipMarks), in the rectangle the painter gives for that clip (Hnt with
    * the same arguments); MIO() is the painter's drawing surface and LaE() its widget.
    */
   // Bitwig paints a timeline clip in two steps: bro's public cfM(lbB, LA1, MQG, ...) works out the clip's
   // rectangle and drawing surface, then calls the clip kind's own cfM(SYo, lbB, LA1, MQG, ..., rectangle) to
   // draw it (each kind of clip overrides it without calling super, hence one ENTRY hook per class). The ENTRY
   // hooks note the surface, clip and rectangle; once the public paint returns, a video clip gets its film
   // strip on top. (At the return Bitwig's arguments are gone, so they are kept here.)
   private static final String INNER = "cfM(Lcom/bitwig/graphics/SYo;LlbB;LLA1;LMQG;LiKU;IDDDDDDZZZZZLcom/bitwig/base/geom/DoubleRectangle;)V";

   private static final class Painting {
      SYo g;
      Object clip;
      DoubleRectangle rect;
      double bandY = Double.NaN; // where the strip goes (NaN: worked out from the clip's rectangle)
      double bandH;

      void clear() {
         g = null;
         clip = null;
         rect = null;
         bandY = Double.NaN;
         bandH = 0;
      }
   }

   /** Where the strip goes on this clip: the top of its body (separate: the room the waveform was moved out of). */
   private static void body(Painting p, SYo g, Nbl clip, DoubleRectangle r) {
      double y = r.FzB(), h = r.UNj();
      if (FilmStrip.separate()) {
         double band = FilmStrip.separateBand(h);
         p.bandY = y;
         p.bandH = Math.max(0, band - 1);
      } else {
         double[] b = FilmStrip.overBand(y, h);
         p.bandY = b[0];
         p.bandH = b[1];
      }
   }

   // ---------------------------------------------------------------- the lane's waveform, below the strip
   //
   // bqc (the arranger's audio clips) doesn't draw a clip's waveform as it paints the clip: it collects the
   // waveforms in a drawer (brj) made when the track lane starts (bqc.cfM(DDII)), placed below the lane's header
   // (uWE(), the header's height), and draws them all when the lane ends (bqc.fJR()). So:
   // - separate: before a video clip is painted, the lane's drawer is drawn out (fJR) and made again with the
   //   header taller by the strip's height (its uWE() call replaced while it's made), so the waveform starts below the
   //   strip; before a clip without video, it's made again as it was;
   // - the strips are drawn when the lane ends, after the waveforms, so over the waveform they're on top.

   private static final class Lane {
      double a, b;
      int c, d;
      int shift;
   }

   private static final java.util.Map<Object, Lane> LANES =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
   private static final ThreadLocal<Integer> LANE_SHIFT = new ThreadLocal<>();
   private static volatile boolean laneOff;   // switched off after a failure

   @Hook(target = bqc.class, method = "cfM(DDII)V", at = At.ENTRY, id = "video-lane-start")
   public static void laneStart(@This bqc painter, @Arg(0) double a, @Arg(1) double b, @Arg(2) int c, @Arg(3) int d) {
      try {
         Integer shift = LANE_SHIFT.get();
         if (shift == null) {
            drawStrips();   // anything left from a lane that didn't end
         }
         Lane l = LANES.computeIfAbsent(painter, k -> new Lane());
         l.a = a;
         l.b = b;
         l.c = c;
         l.d = d;
         l.shift = shift == null ? 0 : shift;
      } catch (Throwable t) {
         clipPaintFailed(t);
      }
   }

   @Hook(target = bqc.class, method = "cfM(DDII)V", at = At.REPLACE_CALL, callOwner = bqc.class, call = "uWE()I",
         id = "video-lane-header")
   public static int laneHeader(bqc painter) {
      int header = painter.uWE();
      Integer shift = LANE_SHIFT.get();
      return shift == null ? header : header + shift;
   }

   @Hook(target = bqc.class, method = "fJR()V", at = At.BEFORE_RETURN, id = "video-lane-end")
   public static void laneEnd() {
      drawStrips();
   }

   /** Before bqc paints a clip: its lane's waveform placed for it (below the strip for a video clip, if separate). */
   private static void lane(bqc painter, MQG clip, DoubleRectangle r) {
      if (laneOff || painter == null || r == null) {
         return;
      }
      try {
         int want = 0;
         if (clip instanceof Nbl n && FilmStrip.separate() && Video.hasLinks() && Follow.span(n) != null) {
            want = (int) FilmStrip.separateBand(r.UNj());
         }
         Lane l = LANES.get(painter);
         if (l == null || l.shift == want) {
            return;
         }
         painter.fJR();             // draws the waveforms collected so far, where they were placed
         LANE_SHIFT.set(want);
         try {
            painter.cfM(l.a, l.b, l.c, l.d);   // and makes the drawer again, the header taller by `want`
         } finally {
            LANE_SHIFT.remove();
         }
      } catch (Throwable t) {
         laneOff = true;
         Video.log("Video: moving the waveform below the film strip switched off", t);
      }
   }

   // ---------------------------------------------------------------- strips drawn when the lane ends

   private record Strip(SYo g, bro painter, dve widget, DoubleRectangle r, Nbl clip, double y, double h, double alpha) {
   }

   private static final ThreadLocal<java.util.List<Strip>> STRIPS = ThreadLocal.withInitial(java.util.ArrayList::new);

   private static void drawStrips() {
      java.util.List<Strip> list = STRIPS.get();
      if (list.isEmpty()) {
         return;
      }
      Strip[] all = list.toArray(new Strip[0]);
      list.clear();
      for (Strip s : all) {
         try {
            ClipMarks.paint(s.g(), s.painter(), s.widget(), s.r(), s.clip(), s.y(), s.h(), s.alpha());
         } catch (Throwable t) {
            clipPaintFailed(t);
         }
      }
   }

   private static final java.util.Set<String> SEEN_PAINTERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

   private static final ThreadLocal<Painting> PAINTING = ThreadLocal.withInitial(Painting::new);

   @Hook(target = bro.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bro")
   public static void clipBody0(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bro");
   }

   @Hook(target = bpA.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpA")
   public static void clipBody1(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpA");
   }

   @Hook(target = bpD.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpD")
   public static void clipBody2(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpD");
   }

   @Hook(target = bpZ.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpZ")
   public static void clipBody3(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpZ");
   }

   @Hook(target = bpt.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpt")
   public static void clipBody4(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpt");
   }

   @Hook(target = bpy.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpy")
   public static void clipBody5(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpy");
   }

   @Hook(target = bpz.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpz")
   public static void clipBody6(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bpz");
   }

   @Hook(target = bqY.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bqY")
   public static void clipBody7(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bqY");
   }

   @Hook(target = bqc.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bqc")
   public static void clipBody8(@This bqc painter, @Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      lane(painter, clip, r);   // bqc paints the arranger's audio clips
      note(g, clip, r, "bqc");
   }

   @Hook(target = brC.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brC")
   public static void clipBody9(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "brC");
   }

   @Hook(target = brD.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brD")
   public static void clipBody10(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "brD");
   }

   @Hook(target = brI.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brI")
   public static void clipBody11(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "brI");
   }

   @Hook(target = brX.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brX")
   public static void clipBody12(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "brX");
   }

   @Hook(target = brv.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brv")
   public static void clipBody13(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "brv");
   }

   @Hook(target = bsg.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bsg")
   public static void clipBody14(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r, "bsg");
   }

   @Hook(target = bro.class, method = "cfM(LlbB;LLA1;LMQG;LiKU;DDDDDDIZZZZZZ)V", at = At.ENTRY, id = "video-clip-paint-start")
   public static void clipPaintStart(@Arg(2) MQG clip) {
      PAINTING.get().clear();
   }

   @Hook(target = bro.class, method = "cfM(LlbB;LLA1;LMQG;LiKU;DDDDDDIZZZZZZ)V", at = At.BEFORE_RETURN, id = "video-clip-paint")
   public static void clipPainted(@This bro painter) {
      Painting p = PAINTING.get();
      SYo g = p.g;
      Object clip = p.clip;
      DoubleRectangle r = p.rect;
      double bandY = p.bandY, bandH = p.bandH;
      p.clear();
      try {
         if (g != null && r != null && clip instanceof Nbl audio && Follow.span(audio) != null) {
            if (painter instanceof bqc && STRIPS.get().size() < 2000) {
               // after the lane's waveforms, which bqc draws when the lane ends
               STRIPS.get().add(new Strip(g, painter, painter.LaE(), r, audio, bandY, bandH, FilmStrip.alpha()));
            } else {
               ClipMarks.paint(g, painter, painter.LaE(), r, audio, bandY, bandH, FilmStrip.alpha());
            }
         }
      } catch (Throwable t) {
         clipPaintFailed(t);
      }
   }

   private static void note(SYo g, MQG clip, DoubleRectangle r, String painter) {
      if (clip instanceof Nbl n && Video.hasLinks()) {
         Painting p = PAINTING.get();
         p.clear();
         p.g = g;
         p.clip = clip;
         p.rect = r;
         try {
            if (g != null && r != null && Follow.span(n) != null) {
               body(p, g, n, r);
            }
         } catch (Throwable t) {
            clipPaintFailed(t);
         }
         try {   // once per kind of painter, for testing: which ones paint video clips, and where
            if (r != null && SEEN_PAINTERS.size() < 20 && Follow.span(n) != null && SEEN_PAINTERS.add(painter)) {
               Video.log(String.format(java.util.Locale.ROOT, "Video: painter %s paints a video clip at y %.0f, %.0f high",
                  painter, r.FzB(), r.UNj()), null);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   /**
    * bEo: a track's icon (top-left in its header). E3(track) builds it: Bitwig's icon (dqT) padded by
    * dnF.cfM(icon, ...). For an audio track that padding is put around a column instead: the icon, then an
    * AVIcon under it, which shows a film icon while the track has a video clip.
    */
   private static final ThreadLocal<Object> ICON_TRACK = new ThreadLocal<>();

   @Hook(target = bEo.class, method = "E3(Lcom/bitwig/flt/document/core/iface/RYd;)V", at = At.ENTRY, id = "av-track-icon-track")
   public static void iconTrack(@Arg(0) Object track) {
      ICON_TRACK.set(track);
   }

   @Hook(target = bEo.class, method = "E3(Lcom/bitwig/flt/document/core/iface/RYd;)V", at = At.REPLACE_CALL,
         callOwner = dnF.class, call = "cfM(Ldve;IIII)LdnF;", id = "av-track-icon")
   public static dnF iconPadding(dve icon, int a, int b, int c, int d) {
      Object track = ICON_TRACK.get();
      ICON_TRACK.remove();
      try {
         if (track instanceof xJo t && "audio_track.svg".equals(bEo.cfM(t, IBe.cfM))) {
            return dnF.cfM(dWb.cfM(IBe.MRY, 2, icon, AVIcon.create(t)), a, b, c, d);   // a column: icon, film icon
         }
      } catch (Throwable e) {
         Video.log("Video: track icon", e);
      }
      return dnF.cfM(icon, a, b, c, d);
   }

   private static int paintFailures;

   private static void clipPaintFailed(Throwable t) {
      if (paintFailures++ < 5) {   // painting repeats: log only the first few
         Video.log("Video: marking a video clip", t);
      }
   }
}
