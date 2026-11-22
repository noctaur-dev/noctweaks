package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.base.geom.DoubleRectangle;
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
   public static void arrangerMenuArgs(@Arg(1) dKW menu) {
      MENU.set(menu);
   }

   @Hook(target = aZv.class, method = "cfM(Ldve;LdKW;)V", at = At.BEFORE_RETURN, id = "arranger-menu")
   public static void arrangerMenu(@This aZv arranger) {
      Object m = MENU.get();
      MENU.remove();
      try {
         Video.log("Video: arranger menu (" + (m == null ? "no menu" : "menu") + ", project " + arranger.gfw() + ")", null);
         if (m instanceof dKW menu && arranger.gfw() instanceof g2L project) {
            Video.seen(project);
            Video.menu(menu, project);
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
   // While a painter paints an audio clip: the clip (from the paint's start) and the rectangle the painter
   // works out for it (its Hnt, called first thing in the paint), for drawing on top at the paint's end.
   // (At the end of the paint Bitwig's arguments are gone, so they are kept here.)
   private static final class Painting {
      Object clip;
      DoubleRectangle rect;
   }

   private static final ThreadLocal<Painting> PAINTING = ThreadLocal.withInitial(Painting::new);

   @Hook(target = bpN.class, method = "cfM(Lso1;LlbB;LLA1;LMQG;DIDD)V", at = At.ENTRY, id = "video-clip-paint-start")
   public static void clipPaintStart(@Arg(3) MQG clip) {
      startPaint(clip);
   }

   @Hook(target = bst.class, method = "cfM(Lso1;LlbB;LLA1;LMQG;DIDD)V", at = At.ENTRY, id = "video-clip-paint-start-2")
   public static void clipPaintStart2(@Arg(3) MQG clip) {
      startPaint(clip);
   }

   @Hook(target = bpN.class, method = "Hnt(Lso1;LlbB;LLA1;LMQG;DIDD)Lcom/bitwig/base/geom/DoubleRectangle;",
         at = At.WRAP_RETURN, id = "video-clip-rect")
   public static DoubleRectangle clipRect(@Return DoubleRectangle r) {
      keepRect(r);
      return r;
   }

   @Hook(target = bst.class, method = "Hnt(Lso1;LlbB;LLA1;LMQG;DIDD)Lcom/bitwig/base/geom/DoubleRectangle;",
         at = At.WRAP_RETURN, id = "video-clip-rect-2")
   public static DoubleRectangle clipRect2(@Return DoubleRectangle r) {
      keepRect(r);
      return r;
   }

   @Hook(target = bpN.class, method = "cfM(Lso1;LlbB;LLA1;LMQG;DIDD)V", at = At.BEFORE_RETURN, id = "video-clip-paint")
   public static void clipPainted(@This bpN painter) {
      painted(painter.MIO(), painter.LaE());
   }

   @Hook(target = bst.class, method = "cfM(Lso1;LlbB;LLA1;LMQG;DIDD)V", at = At.BEFORE_RETURN, id = "video-clip-paint-2")
   public static void clipPainted2(@This bst painter) {
      painted(painter.MIO(), painter.LaE());
   }

   private static void startPaint(MQG clip) {
      Painting p = PAINTING.get();
      p.clip = clip instanceof Nbl && Video.link() != null ? clip : null;
      p.rect = null;
   }

   private static void keepRect(DoubleRectangle r) {
      Painting p = PAINTING.get();
      if (p.clip != null && p.rect == null) {
         p.rect = r;
      }
   }

   private static void painted(SYo g, dve widget) {
      Painting p = PAINTING.get();
      Object clip = p.clip;
      DoubleRectangle r = p.rect;
      p.clip = null;
      p.rect = null;
      try {
         if (r != null && clip instanceof Nbl audio) {
            ClipMarks.paint(g, widget, r, audio);
         }
      } catch (Throwable t) {
         clipPaintFailed(t);
      }
   }

   private static int paintFailures;

   private static void clipPaintFailed(Throwable t) {
      if (paintFailures++ < 5) {   // painting repeats: log only the first few
         Video.log("Video: marking a video clip", t);
      }
   }
}
