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
 * video's sound: the track is an AV track. Empty otherwise. Checked about twice a second while painted.
 */
final class AVIcon extends dve {
   static final int W = 14, H = 11;

   private static final iok FILM = iok.cfM(168, 168, 168);
   private static final iok HOLE = iok.cfM(36, 36, 36);

   private final WeakReference<xJo> track;
   private boolean video;
   private long checkedAt;

   private AVIcon(xJo track) {
      this.track = new WeakReference<>(track);
   }

   static AVIcon create(xJo track) {
      AVIcon i = new AVIcon(track);
      i.H_();   // Bitwig's initialisation
      return i;
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
         long now = System.currentTimeMillis();
         if (now - checkedAt > 500) {
            checkedAt = now;
            video = Follow.trackHasVideo(track.get());
         }
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
