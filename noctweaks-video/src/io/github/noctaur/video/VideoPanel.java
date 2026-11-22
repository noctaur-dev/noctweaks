package io.github.noctaur.video;

import bitwig.obf.dns;   // a widget, as Bitwig types it
import bitwig.obf.dpN;   // Bitwig's UI event loop
import bitwig.obf.dve;   // a widget
import bitwig.obf.iok;   // a colour
import com.bitwig.base.geom.tfK;            // a widget size
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.graphics.CHR;             // how a bitmap is filtered when drawn (IwV: bilinear)
import com.bitwig.graphics.Jkg;             // a pixel format (MRY: RGB24 in 32 bits)
import com.bitwig.graphics.SYo;             // Bitwig's drawing surface
import com.bitwig.base.platform.NativeMemoryBlock;   // native memory Bitwig hands to its graphics
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
   private NativeMemoryBlock block;   // the frame's pixels as Bitwig's graphics take them
   private int blockStride;
   private int[] channels;            // for each byte of a pixel in Bitwig's order: 0-2 = B,G,R of the frame, 3 = alpha
   private byte[] row;
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
         } catch (Throwable t) {   // never let an exception leave the thread
            Video.log("Video: panel ticker", t);
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
      freeBitmap();
      super.iy_();
   }

   private void freeBitmap() {
      try {
         if (bitmap != null) {
            bitmap.iy_();
         }
         if (block != null) {
            block.iy_();
         }
      } catch (Throwable t) {
         Video.log("Video: freeing the picture", t);
      }
      bitmap = null;
      block = null;
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

      // decode at the size the picture shows in the panel (fitted, at most 960 x 540): drawn 1:1, no scaling
      int[] vs = sizeOf(link.video());
      if (vs == null) {
         return;
      }
      double fit = Math.min(Math.min(w / vs[0], h / vs[1]), Math.min(960.0 / vs[0], 540.0 / vs[1]));
      int fw = Math.max(2, (int) (vs[0] * fit) & ~1), fh = Math.max(2, (int) (vs[1] * fit) & ~1);
      if (frames == null || !frames.video().equals(link.video()) || Math.abs(frames.width() - fw) > 4 || Math.abs(frames.height() - fh) > 4) {
         closeFrames();
         frames = new Frames(link.video(), fw, fh);
         drawn = null;
      }
      double vt = Video.videoTime(doc);
      frames.want(vt);
      status(vt);

      Frames.Frame f = frames.latest();
      if (f == null) {
         return;
      }
      if (f != drawn) {
         upload(f);
         drawn = f;
      }
      if (bitmap != null) {
         // drawn as Bitwig's own image widget draws (with a filter: the plain call draws it as a surface, which
         // shows nothing on Windows), scaled up to fill the panel
         double s = Math.min(w / bw, h / bh);
         g.qoU();
         g.FzB(Math.floor((w - bw * s) / 2), Math.floor((h - bh * s) / 2));
         g.Hnt(s, s);
         bitmap.cfM(g, 0, 0, CHR.IwV);
         g.ucQ();
      }
   }

   /**
    * Puts a frame into a new Bitwig bitmap, the way Bitwig loads its own images: the pixels go into native
    * memory from Bitwig's allocator, laid out as its graphics system wants (its stride and channel order),
    * and the system makes a bitmap from that memory. Works for every graphics system Bitwig uses.
    */
   private void upload(Frames.Frame f) {
      sal gs = afn();
      if (gs == null) {
         return;
      }
      int w = f.width(), h = f.height();
      if (block == null || bw != w || bh != h) {
         freeBitmap();
         blockStride = gs.cfM(Jkg.MRY, w);
         block = sal.cfM.cfM(h * blockStride * Jkg.MRY.cfM());   // the size Bitwig itself allocates
         bw = w;
         bh = h;
         row = new byte[Math.max(blockStride, w * 4)];
         channels = channelOrder(gs);
      }
      ByteBuffer mem = block.E3();
      byte[] src = f.pixels();
      int[] ch = channels;
      for (int y = 0; y < h; y++) {
         int s = y * w * 4;
         for (int x = 0; x < w; x++, s += 4) {
            int d = x * 4;
            row[d] = ch[0] == 3 ? (byte) 255 : src[s + ch[0]];
            row[d + 1] = ch[1] == 3 ? (byte) 255 : src[s + ch[1]];
            row[d + 2] = ch[2] == 3 ? (byte) 255 : src[s + ch[2]];
            row[d + 3] = ch[3] == 3 ? (byte) 255 : src[s + ch[3]];
         }
         mem.put(y * blockStride, row, 0, w * 4);
      }
      qc1 old = bitmap;
      bitmap = gs.cfM(block, w, h, Jkg.MRY);   // copies the memory
      if (old != null) {
         old.iy_();
      }
      if (!loggedUpload) {
         loggedUpload = true;
         try {   // the first picture, saved by Bitwig to tools/first-frame.png (for testing)
            File dir = Tools.toolsFolder();
            if (dir != null && bitmap != null) {
               File png = new File(dir, "first-frame.png");
               bitmap.cfM(png, new com.bitwig.base.async.lBQ() {
                  @Override
                  public void taskCompleted() {
                     Video.log("Video: first frame saved to " + png, null);
                  }

                  @Override
                  public void cfM(Exception e) {
                     Video.log("Video: could not save the first frame", e);
                  }
               });
            }
         } catch (Throwable t) {
            Video.log("Video: could not save the first frame", t);
         }
         Video.log("Video: first frame uploaded (" + w + " x " + h + ", stride " + blockStride + ", channels "
            + java.util.Arrays.toString(ch) + ", bitmap " + (bitmap == null ? "none" : bitmap.getClass().getSimpleName()) + ")", null);
      }
   }

   private boolean loggedUpload;

   /** Asks the graphics system where it puts blue, green, red and alpha in a pixel. */
   private static int[] channelOrder(sal gs) {
      ByteBuffer probe = ByteBuffer.allocate(4);
      gs.cfM(probe, 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4);   // B, G, R, A, as Bitwig passes them
      int[] ch = new int[4];
      for (int i = 0; i < 4; i++) {
         int v = probe.get(i);
         ch[i] = v >= 1 && v <= 4 ? v - 1 : 3;   // 0 = B, 1 = G, 2 = R (the frame's bytes), 3 = alpha
      }
      return ch;
   }

   // a line in the log every few seconds while the panel is shown (for testing)
   private long lastStatus;
   private int statusCount, paints;

   private void status(double vt) {
      paints++;
      long now = System.currentTimeMillis();
      if (now - lastStatus < 3000 || statusCount >= 40) {
         return;
      }
      lastStatus = now;
      statusCount++;
      Frames.Frame f = frames == null ? null : frames.latest();
      Video.log(String.format(java.util.Locale.ROOT,
         "Video panel: %d paints, playhead %.3f beats, video time %.2f s, frame %s, bitmap %s, panel %d x %d",
         paints, beatsNow(), vt,
         f == null ? "none yet" : String.format(java.util.Locale.ROOT, "%.2f s %dx%d", f.time(), f.width(), f.height()),
         bitmap == null ? "none" : bitmap.getClass().getSimpleName(),
         Sxo(), yiX()), null);
      paints = 0;
   }

   private static double beatsNow() {
      try {
         g2L doc = Video.project();
         return doc == null ? Double.NaN : Clock.secondsToBeats(doc, Clock.seconds(doc));
      } catch (Throwable t) {
         return Double.NaN;
      }
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
