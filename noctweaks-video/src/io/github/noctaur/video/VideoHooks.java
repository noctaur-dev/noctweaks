package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
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
   @Hook(target = bkS.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.BEFORE_RETURN, id = "video-clip-menu")
   public static void clipMenu(@Arg(0) dKW menu, @Arg(2) big header) {
      try {
         Video.clipMenu(menu, header == null ? java.util.List.of() : new java.util.ArrayList<Object>(header.UNj().FzB()));
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
   }

   private static final ThreadLocal<Painting> PAINTING = ThreadLocal.withInitial(Painting::new);

   @Hook(target = bro.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bro")
   public static void clipBody0(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpA.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpA")
   public static void clipBody1(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpD.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpD")
   public static void clipBody2(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpZ.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpZ")
   public static void clipBody3(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpt.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpt")
   public static void clipBody4(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpy.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpy")
   public static void clipBody5(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bpz.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bpz")
   public static void clipBody6(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bqY.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bqY")
   public static void clipBody7(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bqc.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bqc")
   public static void clipBody8(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = brC.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brC")
   public static void clipBody9(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = brD.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brD")
   public static void clipBody10(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = brI.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brI")
   public static void clipBody11(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = brX.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brX")
   public static void clipBody12(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = brv.class, method = INNER, at = At.ENTRY, id = "video-clip-body-brv")
   public static void clipBody13(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bsg.class, method = INNER, at = At.ENTRY, id = "video-clip-body-bsg")
   public static void clipBody14(@Arg(0) SYo g, @Arg(3) MQG clip, @Arg(17) DoubleRectangle r) {
      note(g, clip, r);
   }

   @Hook(target = bro.class, method = "cfM(LlbB;LLA1;LMQG;LiKU;DDDDDDIZZZZZZ)V", at = At.ENTRY, id = "video-clip-paint-start")
   public static void clipPaintStart(@Arg(2) MQG clip) {
      Painting p = PAINTING.get();
      p.g = null;
      p.rect = null;
      p.clip = null;
   }

   @Hook(target = bro.class, method = "cfM(LlbB;LLA1;LMQG;LiKU;DDDDDDIZZZZZZ)V", at = At.BEFORE_RETURN, id = "video-clip-paint")
   public static void clipPainted(@This bro painter) {
      Painting p = PAINTING.get();
      SYo g = p.g;
      Object clip = p.clip;
      DoubleRectangle r = p.rect;
      p.g = null;
      p.clip = null;
      p.rect = null;
      try {
         if (g != null && r != null && clip instanceof Nbl audio) {
            ClipMarks.paint(g, painter, painter.LaE(), r, audio);
         }
      } catch (Throwable t) {
         clipPaintFailed(t);
      }
   }

   private static void note(SYo g, MQG clip, DoubleRectangle r) {
      if (clip instanceof Nbl && Video.hasLinks()) {
         Painting p = PAINTING.get();
         p.g = g;
         p.clip = clip;
         p.rect = r;
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
