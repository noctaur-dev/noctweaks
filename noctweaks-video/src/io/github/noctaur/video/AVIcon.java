package io.github.noctaur.video;

import bitwig.obf.dns;   // a widget, as Bitwig types it
import bitwig.obf.dve;   // a widget
import bitwig.obf.iok;   // a colour
import bitwig.obf.xJo;   // a track
import com.bitwig.base.geom.tfK;   // a widget size
import com.bitwig.graphics.SYo;

import java.lang.ref.WeakReference;

/**
 * A small film icon under an audio track's icon in its header, shown while one of the track's clips plays a
 * video's sound: the track is an AV track. Empty otherwise. Checked twice a second, repainted when it changes.
 */
final class AVIcon extends dve {
   static final int W = 14, H = 11;

   private static final iok FILM = iok.cfM(168, 168, 168);
   private static final iok HOLE = iok.cfM(36, 36, 36);

   private final WeakReference<xJo> track;
   private boolean video;   // set by checkAll

   private AVIcon(xJo track) {
      this.track = new WeakReference<>(track);
   }

   static AVIcon create(xJo track) {
      AVIcon i = new AVIcon(track);
      i.H_();   // Bitwig's initialisation
      synchronized (ALL) {
         ALL.put(i, Boolean.TRUE);
      }
      startWatching();
      if (!logged) {
         logged = true;
         Video.log("Video: AV icons added to audio track headers", null);
      }
      return i;
   }

   private static boolean logged;

   // every AV icon made (weakly: they go when Bitwig drops their header); checked twice a second on Bitwig's UI
   // thread, and repainted when their track gains or loses a video clip, so the header needn't be redrawn by hand
   private static final java.util.Map<AVIcon, Boolean> ALL = new java.util.WeakHashMap<>();
   private static Thread watcher;

   private static synchronized void startWatching() {
      if (watcher != null) {
         return;
      }
      watcher = new Thread(() -> {
         try {
            while (true) {
               Thread.sleep(500);
               bitwig.obf.dpN.TVZ().asyncExec(AVIcon::checkAll);
            }
         } catch (Throwable t) {   // never let an exception leave the thread
            Video.log("Video: AV icons", t);
         }
      }, "noctweaks-video-av-icons");
      watcher.setDaemon(true);
      watcher.start();
   }

   private static void checkAll() {
      java.util.List<AVIcon> icons;
      synchronized (ALL) {
         icons = new java.util.ArrayList<>(ALL.keySet());
      }
      for (AVIcon i : icons) {
         try {
            boolean v = Follow.trackHasVideo(i.track.get());
            if (v != i.video) {
               i.video = v;
               if (i.ZTl()) {
                  i.jbi();   // repaint
               }
            }
         } catch (Throwable t) {
            Video.log("Video: AV icon", t);
         }
      }
   }

   @Override
   public tfK mL_() {
      return tfK.cfM();   // no minimum: a short track hides it rather than growing
   }

   @Override
   public tfK iH_() {
      return new tfK(W, H);
   }

   @Override
   public tfK nj_() {
      return new tfK(W, H);
   }

   @Override
   public dve MJ() {
      return this;
   }

   @Override
   public dns UZr() {
      return this;
   }

   @Override
   public void cfM(SYo g) {
      try {
         if (video && Sxo() >= W && yiX() >= H) {
            paintIcon(g);
         }
      } catch (Throwable t) {
         Video.log("Video: track icon", t);
      }
   }

   /** A film frame: a light strip with sprocket holes down each side and a dark picture in the middle. */
   private void paintIcon(SYo g) {
      double x = Math.floor((Sxo() - W) / 2.0), y = 0;
      g.qoU();
      g.nVd();
      g.MRY(x, y, W, H);
      g.Hnt(FILM);
      g.wGc();
      g.nVd();
      g.MRY(x + 4, y + 2, W - 8, H - 4);   // the picture
      for (int i = 0; i < 3; i++) {          // the holes
         g.MRY(x + 1, y + 1.5 + i * 3, 2, 1.8);
         g.MRY(x + W - 3, y + 1.5 + i * 3, 2, 1.8);
      }
      g.Hnt(HOLE);
      g.wGc();
      g.ucQ();
   }
}
