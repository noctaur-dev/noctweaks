package io.github.noctaur.video;

import bitwig.obf.dpN;   // Bitwig's UI event loop
import bitwig.obf.dve;   // a widget
import com.bitwig.base.platform.NativeMemoryBlock;
import com.bitwig.graphics.Jkg;
import com.bitwig.graphics.qc1;
import com.bitwig.graphics.sal;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small pictures of the linked video, for its clips in the arranger: decoded by ffmpeg or mpv in the background, one
 * every second or so (at most MAX for the whole video), and turned into Bitwig bitmaps a few at a time while
 * the arranger paints. Kept until the linked video changes.
 */
final class Thumbs {
   static final int HEIGHT = 54;
   private static final int MAX = 300;

   // the pictures of each video, the most recently used last; the oldest goes past MAX_VIDEOS (UI thread)
   private static final int MAX_VIDEOS = 8;
   private static final java.util.LinkedHashMap<File, Thumbs> ALL = new java.util.LinkedHashMap<>(16, 0.75f, true);

   final File video;
   final int width, height;
   final double fps;                 // pictures per second of video
   private final List<byte[]> pixels = new ArrayList<>();   // guarded by itself
   private final List<qc1> bitmaps = new ArrayList<>();     // UI thread
   private volatile boolean closed;
   private volatile boolean finished;   // every picture is in
   private WeakReference<dve> widget = new WeakReference<>(null);
   private volatile boolean repaintQueued;

   private Thumbs(File video, int width, int height, double fps) {
      this.video = video;
      this.width = width;
      this.height = height;
      this.fps = fps;
      Thread t = new Thread(this::decode, "noctweaks-video-thumbs");
      t.setDaemon(true);
      t.start();
   }

   /** The pictures of this video (started the first time it's asked for), or null while its size isn't known. */
   static Thumbs of(File video) {
      Thumbs t = ALL.get(video);
      if (t != null) {
         return t;
      }
      int[] size = Frames.sizeIfKnown(video);   // never waits: asked of ffmpeg or mpv in the background
      double dur = Frames.duration(video);
      if (size == null || size[1] <= 0) {
         return null;
      }
      if (ALL.size() >= MAX_VIDEOS) {
         java.util.Iterator<Thumbs> it = ALL.values().iterator();
         Thumbs oldest = it.next();
         it.remove();
         oldest.close();
      }
      int w = Math.max(2, (int) Math.round(HEIGHT * (double) size[0] / size[1]) & ~1);
      double fps = Double.isNaN(dur) || dur <= 0 ? 1 : Math.min(1, MAX / dur);
      t = new Thumbs(video, w, HEIGHT, fps);
      ALL.put(video, t);
      Video.log(String.format(Locale.ROOT, "Video: making clip pictures of %s (%d x %d, every %.2f s)",
         video.getName(), w, HEIGHT, 1 / fps), null);
      return t;
   }

   /** Which picture shows the video at this time (seconds). */
   int index(double time) {
      return (int) Math.floor(Math.max(0, time) * fps + 0.5);
   }

   boolean finished() {
      return finished;
   }

   int count() {
      synchronized (pixels) {
         return pixels.size();
      }
   }

   /** The bitmap of picture i, made now if its pixels are in and `budget` allows; null otherwise. UI thread. */
   qc1 bitmap(int i, sal gs, int[] budget) {
      if (i < bitmaps.size() && bitmaps.get(i) != null) {
         return bitmaps.get(i);
      }
      byte[] px;
      synchronized (pixels) {
         px = i < pixels.size() ? pixels.get(i) : null;
      }
      if (px == null || budget[0] <= 0 || gs == null) {
         return null;
      }
      budget[0]--;
      qc1 b = make(gs, px, width, height);
      while (bitmaps.size() <= i) {
         bitmaps.add(null);
      }
      bitmaps.set(i, b);
      return b;
   }

   /** The arranger widget to repaint when more pictures are ready. */
   void paintedIn(dve w) {
      if (widget.get() != w) {
         widget = new WeakReference<>(w);
      }
   }

   /** Asks the arranger to paint again soon (more pictures to show). */
   void repaintSoon() {
      if (repaintQueued) {
         return;
      }
      repaintQueued = true;
      try {
         dpN.TVZ().asyncExec(() -> {
            repaintQueued = false;
            try {
               dve w = widget.get();
               if (w != null && w.ZTl()) {
                  w.jbi();
               }
            } catch (Throwable t) {
               Video.log("Video: clip pictures", t);
            }
         });
      } catch (Throwable t) {
         repaintQueued = false;
      }
   }

   private void close() {
      closed = true;
      List<qc1> old = new ArrayList<>(bitmaps);
      bitmaps.clear();
      Disposal.later(old);
   }

   private void decode() {
      Process p = null;
      try {
         java.util.List<String> cmd = Decode.frames(video, 0, fps, width, height);
         if (cmd == null) {
            return;
         }
         p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
         InputStream in = p.getInputStream();
         int size = width * height * 4;
         while (!closed) {
            byte[] buf = new byte[size];
            int got = 0;
            while (got < size) {
               int n = in.read(buf, got, size - got);
               if (n < 0) {
                  break;
               }
               got += n;
            }
            if (got < size) {
               break;
            }
            int n;
            synchronized (pixels) {
               pixels.add(buf);
               n = pixels.size();
            }
            if (n <= 8 || n % 10 == 0) {
               repaintSoon();
            }
         }
         finished = true;
         repaintSoon();
         Video.log("Video: " + count() + " clip pictures of " + video.getName(), null);
      } catch (Throwable t) {   // never let an exception leave the thread
         Video.log("Video: clip pictures stopped", t);
      } finally {
         if (p != null) {
            try {
               p.destroyForcibly();
            } catch (Throwable ignored) {
            }
         }
      }
   }

   /** A Bitwig bitmap of BGR0 pixels, made the way Bitwig makes its own images (see VideoPanel). */
   static qc1 make(sal gs, byte[] src, int w, int h) {
      int stride = gs.cfM(Jkg.MRY, w);
      NativeMemoryBlock block = sal.cfM.cfM(h * stride * Jkg.MRY.cfM());
      try {
         ByteBuffer mem = block.E3();
         int[] ch = channelOrder(gs);
         byte[] row = new byte[w * 4];
         for (int y = 0; y < h; y++) {
            int s = y * w * 4;
            for (int x = 0; x < w; x++, s += 4) {
               int d = x * 4;
               for (int k = 0; k < 4; k++) {
                  row[d + k] = ch[k] == 3 ? (byte) 255 : src[s + ch[k]];
               }
            }
            mem.put(y * stride, row, 0, w * 4);
         }
         return gs.cfM(block, w, h, Jkg.MRY);   // copies the memory
      } finally {
         block.iy_();
      }
   }

   private static int[] order;

   private static int[] channelOrder(sal gs) {
      if (order == null) {
         ByteBuffer probe = ByteBuffer.allocate(4);
         gs.cfM(probe, 0, (byte) 1, (byte) 2, (byte) 3, (byte) 4);   // B, G, R, A, as Bitwig passes them
         int[] ch = new int[4];
         for (int i = 0; i < 4; i++) {
            int v = probe.get(i);
            ch[i] = v >= 1 && v <= 4 ? v - 1 : 3;
         }
         order = ch;
      }
      return order;
   }
}
