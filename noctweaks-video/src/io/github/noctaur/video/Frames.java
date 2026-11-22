package io.github.noctaur.video;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Video frames for the panel, decoded by mpv in the background and read raw (BGR0, 4 bytes a pixel) from its
 * output, at a fixed rate (FPS), scaled to fit the panel. The panel asks for a time; this keeps
 * reading forward to it while Bitwig plays, and starts mpv again at the new time when the playhead jumps
 * back or far ahead. mpv waits while nothing reads, so it never runs ahead of Bitwig.
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
      File mpv = mpvForPipes();
      if (mpv == null) {
         throw new IOException("mpv not found");
      }
      String exe = mpv.getAbsolutePath();
      List<String> cmd = new ArrayList<>(List.of(
         exe, "--no-config", "--really-quiet", "--terminal=no",
         String.format(Locale.ROOT, "--start=%.4f", t),
         "--audio=no", "--sub=no",
         "--o=-", "--of=rawvideo", "--ovc=rawvideo",
         String.format(Locale.ROOT,
            "--vf=fps=%d,scale=%d:%d,format=bgr0", (int) FPS, width, height),
         video.getAbsolutePath()));
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

   /** The video's size if it's known; if not, asks mpv on a background thread and returns null for now. */
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

   /** The video's length in seconds (known once size() has asked mpv), or NaN. */
   static double duration(File video) {
      Double d = DURATIONS.get(video);
      return d == null ? Double.NaN : d;
   }

   static File mpvForPipes() {
      File mpv = Tools.find("mpv");
      if (mpv == null) {
         return null;
      }
      // On Windows, mpv.com is the console front end of mpv.exe: it passes stdout through as a plain pipe.
      File com = new File(mpv.getParentFile(), "mpv.com");
      return WINDOWS && com.isFile() ? com : mpv;
   }

   /** The video's picture size (width, height), asked of mpv once per file; null if mpv can't tell. */
   static int[] size(File video) {
      int[] known = SIZES.get(video);
      if (known != null) {
         return known;
      }
      try {
         File mpv = mpvForPipes();
         if (mpv == null) {
            return null;
         }
         Process p = new ProcessBuilder(mpv.getAbsolutePath(), "--no-config", "--vo=null", "--ao=null", "--frames=1",
            "--term-playing-msg=SIZE ${width} ${height} ${duration}", video.getAbsolutePath()).redirectErrorStream(true).start();
         String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
         p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
         java.util.regex.Matcher m = java.util.regex.Pattern.compile("SIZE (\\d+) (\\d+) ?([0-9.]*)").matcher(text);
         if (m.find()) {
            int[] wh = { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
            try {
               DURATIONS.put(video, Double.parseDouble(m.group(3)));
            } catch (NumberFormatException ignored) {
            }
            SIZES.put(video, wh);
            Video.log("Video: " + video.getName() + " is " + wh[0] + " x " + wh[1], null);
            return wh;
         }
         Video.log("Video: mpv didn't give the size of " + video.getName() + ": " + text.strip(), null);
      } catch (Throwable t) {
         Video.log("Video: could not ask mpv for the video's size", t);
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
