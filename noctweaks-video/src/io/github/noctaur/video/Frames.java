package io.github.noctaur.video;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Video frames for the panel, decoded by mpv or ffmpeg (see Decode) in the background and read raw (BGR0, 4 bytes a pixel) from its
 * output, at a fixed rate (FPS), scaled to fit the panel. The panel asks for a time; this keeps
 * reading forward to it while Bitwig plays, and starts the decoder again at the new time when the playhead
 * jumps back or far ahead. The decoder waits while nothing reads, so it never runs ahead of Bitwig.
 */
final class Frames {
   static final double FPS = 30;

   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

   /** A decoded frame: pixels in BGR0 rows, and the video time it shows. */
   record Frame(byte[] pixels, int width, int height, double time) {
   }

   private final File video;
   private final int width, height;
   private final Thread thread;
   private volatile boolean running = true;
   private volatile double target = Double.NaN;     // the video time wanted (seconds); NaN = nothing
   private volatile Frame latest;
   private volatile Runnable onFrame;

   private Process process;
   private InputStream out;
   private double next;          // the time of the next frame mpv will give
   private boolean ended;

   Frames(File video, int width, int height) {
      this.video = video;
      this.width = width & ~1;
      this.height = height & ~1;
      thread = new Thread(this::run, "noctweaks-video-frames");
      thread.setDaemon(true);
      thread.start();
   }

   File video() {
      return video;
   }

   int width() {
      return width;
   }

   int height() {
      return height;
   }

   void want(double time) {
      target = time;
   }

   Frame latest() {
      return latest;
   }

   void onFrame(Runnable r) {
      onFrame = r;
   }

   void close() {
      running = false;
      thread.interrupt();
      stop();
   }

   private void run() {
      try {
         loop();
      } catch (Throwable t) {   // an exception leaving a thread would take Bitwig down
         Video.log("Video: frames stopped", t);
      }
      try {
         stop();
      } catch (Throwable ignored) {
      }
   }

   private void loop() {
      byte[] buf = new byte[width * height * 4];
      double frame = 1.0 / FPS;
      while (running) {
         try {
            double t = target;
            if (Double.isNaN(t) || t < 0) {
               if (t < 0 && latest != null) {
                  latest = null;   // before the video's start: nothing to show
                  fire();
               }
               Thread.sleep(10);
               continue;
            }
            boolean behind = t < next - frame - 0.001;   // the playhead went back
            boolean farAhead = t > next + 2.0;            // or jumped ahead: start mpv there instead of decoding through
            if (process == null || behind || farAhead || (ended && behind)) {
               start(t);
            }
            if (ended || t < next) {
               Thread.sleep(5);
               continue;
            }
            // read frames up to the wanted time, showing only the last one
            if (!read(buf)) {
               ended = true;
               continue;
            }
            double shown = next;
            next += frame;
            if (t < next) {
               latest = new Frame(buf.clone(), width, height, shown);
               fire();
            }
         } catch (InterruptedException e) {
            break;
         } catch (Throwable e) {
            Video.log("Video: frames", e);
            try {
               stop();
               Thread.sleep(500);
            } catch (InterruptedException ie) {
               break;
            } catch (Throwable ignored) {
            }
         }
      }
   }

   private void fire() {
      Runnable r = onFrame;
      if (r != null) {
         r.run();
      }
   }

   private synchronized void start(double t) throws IOException {
      stop();
      List<String> cmd = Decode.frames(video, t, FPS, width, height);
      if (cmd == null) {
         throw new IOException("neither mpv nor ffmpeg was found");
      }
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);
      process = pb.start();
      out = process.getInputStream();
      next = t;
      ended = false;
   }

   private boolean read(byte[] buf) throws IOException {
      int got = 0;
      while (got < buf.length) {
         int n = out.read(buf, got, buf.length - got);
         if (n < 0) {
            return false;
         }
         got += n;
      }
      return true;
   }

   private static final java.util.Map<File, int[]> SIZES = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Map<File, Double> DURATIONS = new java.util.concurrent.ConcurrentHashMap<>();

   private static final java.util.Set<File> ASKING = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** The video's size if it's known; if not, asks the decoder on a background thread and returns null for now. */
   static int[] sizeIfKnown(File video) {
      int[] known = SIZES.get(video);
      if (known == null && ASKING.add(video)) {
         Thread t = new Thread(() -> {
            try {
               size(video);
            } catch (Throwable ignored) {
            }
         }, "noctweaks-video-size");
         t.setDaemon(true);
         t.start();
      }
      return known;
   }

   /** The video's length in seconds (known once size() has asked), or NaN. */
   static double duration(File video) {
      Double d = DURATIONS.get(video);
      return d == null ? Double.NaN : d;
   }



   /** The video's picture size (width, height), asked once per file; null if it can't be told. */
   static int[] size(File video) {
      int[] known = SIZES.get(video);
      if (known != null) {
         return known;
      }
      double[] probe = Decode.probe(video);
      if (probe != null) {
         int[] wh = { (int) probe[0], (int) probe[1] };
         if (!Double.isNaN(probe[2])) {
            DURATIONS.put(video, probe[2]);
         }
         SIZES.put(video, wh);
         Video.log("Video: " + video.getName() + " is " + wh[0] + " x " + wh[1] + " (decoded by " + Decode.which() + ")", null);
         return wh;
      }
      return null;
   }

   private synchronized void stop() {
      Process p = process;   // close() runs on another thread: never touch the field twice
      process = null;
      out = null;
      if (p != null) {
         try {
            p.destroyForcibly();
         } catch (Throwable ignored) {
         }
      }
   }
}
