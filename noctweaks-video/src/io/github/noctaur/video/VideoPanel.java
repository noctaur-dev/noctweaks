package io.github.noctaur.video;

import bitwig.obf.dns;   // a widget, as Bitwig types it
import bitwig.obf.dpN;   // Bitwig's UI event loop
import bitwig.obf.dve;   // a widget
import bitwig.obf.iok;   // a colour
import com.bitwig.base.geom.tfK;            // a widget size
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.graphics.Jkg;             // a pixel format (MRY: RGB24 in 32 bits)
import com.bitwig.graphics.SYo;             // Bitwig's drawing surface
import com.bitwig.graphics.oRV;             // a bitmap with its pixel memory
import com.bitwig.graphics.qc1;             // a bitmap
import com.bitwig.graphics.sal;             // Bitwig's graphics system

import java.io.File;
import java.nio.ByteBuffer;

/**
 * The Video panel's picture: Bitwig's own widget, painting the frame for the playhead into one of Bitwig's
 * bitmaps. While it's on screen it repaints about 30 times a second; the frames come from Frames.
 */
final class VideoPanel extends dve {
   private static final iok BLACK = iok.cfM(0, 0, 0);

   private Frames frames;
   private qc1 bitmap;
   private int bw, bh;
   private Frames.Frame drawn;
   private Thread ticker;
   private volatile boolean repaintQueued;

   private VideoPanel() {
   }

   static VideoPanel create() {
      VideoPanel p = new VideoPanel();
      p.H_();   // Bitwig's initialisation
      return p;
   }

   @Override
   public tfK mL_() {
      return tfK.cfM();   // no minimum
   }

   @Override
   public tfK iH_() {
      return new tfK(480, 270);
   }

   @Override
   public tfK nj_() {
      return tfK.MRY();   // grows with the panel
   }

   @Override
   public dve MJ() {
      return this;
   }

   @Override
   public dns UZr() {
      return this;
   }

   // ---------- repainting ----------

   private synchronized void startTicking() {
      if (ticker != null && ticker.isAlive()) {
         return;
      }
      ticker = new Thread(() -> {
         try {
            while (true) {
               Thread.sleep(33);
               repaintSoon();
            }
         } catch (InterruptedException ignored) {
         }
      }, "noctweaks-video-panel");
      ticker.setDaemon(true);
      ticker.start();
   }

   private synchronized void stopTicking() {
      if (ticker != null) {
         ticker.interrupt();
         ticker = null;
      }
   }

   private void repaintSoon() {
      if (repaintQueued) {
         return;
      }
      repaintQueued = true;
      dpN.TVZ().asyncExec(() -> {
         repaintQueued = false;
         try {
            if (!ZTl()) {        // taken off screen
               stopTicking();
               closeFrames();
            } else {
               jbi();           // repaint
            }
         } catch (Throwable t) {
            Video.log("Video: panel", t);
         }
      });
   }

   @Override
   public void iy_() {   // disposed
      stopTicking();
      closeFrames();
      super.iy_();
   }

   private void closeFrames() {
      if (frames != null) {
         frames.close();
         frames = null;
      }
   }

   // ---------- painting ----------

   @Override
   public void cfM(SYo g) {
      startTicking();   // painting means it is on screen
      try {
         paint(g);
      } catch (Throwable t) {
         Video.log("Video: panel paint", t);
      }
   }

   private void paint(SYo g) {
      double w = Sxo(), h = yiX();
      g.qoU();
      g.nVd();
      g.MRY(0, 0, w, h);
      g.Hnt(BLACK);
      g.wGc();
      g.ucQ();
      if (w < 8 || h < 8) {
         return;
      }
      Video.Link link = Video.link();
      g2L doc = Video.project();
      if (link == null || doc == null || !link.video().isFile()) {
         closeFrames();
         return;
      }

      // decode at the size the picture shows in the panel (fitted, at most 1280 x 720): drawn 1:1, no scaling
      int[] vs = sizeOf(link.video());
      if (vs == null) {
         return;
      }
      double fit = Math.min(Math.min(w / vs[0], h / vs[1]), Math.min(1280.0 / vs[0], 720.0 / vs[1]));
      int fw = Math.max(2, (int) (vs[0] * fit) & ~1), fh = Math.max(2, (int) (vs[1] * fit) & ~1);
      if (frames == null || !frames.video().equals(link.video()) || Math.abs(frames.width() - fw) > 4 || Math.abs(frames.height() - fh) > 4) {
         closeFrames();
         frames = new Frames(link.video(), fw, fh);
         drawn = null;
      }
      frames.want(Video.videoTime(doc));

      Frames.Frame f = frames.latest();
      if (f == null) {
         return;
      }
      if (bitmap == null || bw != f.width() || bh != f.height()) {
         sal gs = afn();
         bitmap = gs.cfM(f.width(), f.height(), Jkg.MRY);
         bw = f.width();
         bh = f.height();
         drawn = null;
      }
      if (f != drawn && bitmap instanceof oRV mem) {
         ByteBuffer px = mem.FzB();
         int stride = Math.max(mem.TMF(), f.width() * 4);
         int row = f.width() * 4;
         for (int y = 0; y < f.height(); y++) {
            px.position(y * stride);
            px.put(f.pixels(), y * row, row);
         }
         px.rewind();
         drawn = f;
      }
      bitmap.cfM(g, Math.floor((w - bw) / 2), Math.floor((h - bh) / 2));
   }

   // the video's size, asked of mpv on a background thread (the panel stays black until it's known)
   private File sizing;
   private volatile int[] size;
   private File sized;

   private int[] sizeOf(File video) {
      if (video.equals(sized) && size != null) {
         return size;
      }
      if (!video.equals(sizing)) {
         sizing = video;
         size = null;
         Thread t = new Thread(() -> {
            int[] s = Frames.size(video);
            size = s;
            sized = video;
         }, "noctweaks-video-size");
         t.setDaemon(true);
         t.start();
      }
      return video.equals(sized) ? size : null;
   }
}
