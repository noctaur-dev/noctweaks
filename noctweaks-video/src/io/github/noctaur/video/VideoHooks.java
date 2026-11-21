package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
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
         if (m instanceof dKW menu && arranger.gfw() instanceof g2L project) {
            Video.menu(menu, project);
         }
      } catch (Throwable t) {
         Video.log("Video: arranger menu", t);
      }
   }
}
