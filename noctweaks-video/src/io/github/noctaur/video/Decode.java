package io.github.noctaur.video;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The program that decodes the pictures: mpv or ffmpeg, whichever the mod has (mpv first, unless Settings >
 * Locations > Video says otherwise). The mod's zips come with one or the other; either one alone does
 * everything the panel and the film strips need. Both are asked
 * for the same thing: raw BGR0 frames on their output, at a fixed rate, scaled to a size, from a time on.
 */
final class Decode {
   private Decode() {
   }

   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

   /** mpv, for decoding (on Windows mpv.com, its console front end, which passes its output through as a pipe). */
   static File mpv() {
      File mpv = Tools.find("mpv");
      if (mpv == null) {
         return null;
      }
      File com = new File(mpv.getParentFile(), "mpv.com");
      return WINDOWS && com.isFile() ? com : mpv;
   }

   static File ffmpeg() {
      return Tools.find("ffmpeg");
   }

   static final int AUTOMATIC = 0, MPV = 1, FFMPEG = 2;
   static final String[] CHOICES = { "Automatic (mpv if it's there)", "mpv", "ffmpeg" };

   /** Settings > Locations > Video > Decode pictures with. */
   static int choice() {
      try {
         int c = Integer.parseInt(Video.MOD.settings().get("decoder", "0"));
         return c >= 0 && c < CHOICES.length ? c : AUTOMATIC;
      } catch (Throwable t) {
         return AUTOMATIC;
      }
   }

   static void choose(int c) {
      try {
         Video.MOD.settings().put("decoder", Integer.toString(c));
         Video.MOD.settings().flush();
         Video.log("Video: pictures decoded by " + CHOICES[c] + " (from the next video or seek)", null);
      } catch (Throwable t) {
         Video.log("Video: could not save the decoder", t);
      }
   }

   /** The program to decode with: the chosen one, or the other if it isn't there. */
   private static boolean useFfmpeg() {
      int c = choice();
      File mpv = c == FFMPEG ? null : mpv();
      if (mpv != null) {
         return false;
      }
      if (ffmpeg() != null) {
         return true;
      }
      return false;   // the chosen ffmpeg isn't there: mpv, if it is
   }

   /** Which one decodes, for the log. */
   static String which() {
      return useFfmpeg() ? "ffmpeg" : mpv() != null ? "mpv" : "nothing (neither mpv nor ffmpeg was found)";
   }

   /**
    * The command that writes the video's frames from `start` (seconds) on, `fps` a second, scaled to w x h, as raw
    * BGR0 on its output (frame i shows start + i / fps). Null if neither program is there.
    */
   static List<String> frames(File video, double start, double fps, int w, int h) {
      boolean ff = useFfmpeg();
      File mpv = ff ? null : mpv();
      if (mpv != null) {
         return List.of(mpv.getAbsolutePath(), "--no-config", "--really-quiet", "--terminal=no",
            String.format(Locale.ROOT, "--start=%.4f", Math.max(0, start)),
            "--audio=no", "--sub=no", "--o=-", "--of=rawvideo", "--ovc=rawvideo",
            String.format(Locale.ROOT, "--vf=fps=%s,scale=%d:%d,format=bgr0", rate(fps), w, h),
            video.getAbsolutePath());
      }
      File ffmpeg = ff ? ffmpeg() : null;
      if (ffmpeg != null) {
         return List.of(ffmpeg.getAbsolutePath(), "-nostdin", "-hide_banner", "-loglevel", "error",
            "-ss", String.format(Locale.ROOT, "%.4f", Math.max(0, start)), "-i", video.getAbsolutePath(),
            "-an", "-sn", "-dn",
            "-vf", String.format(Locale.ROOT, "fps=%s,scale=%d:%d,format=bgr0", rate(fps), w, h),
            "-f", "rawvideo", "-pix_fmt", "bgr0", "-");
      }
      return null;
   }

   private static String rate(double fps) {
      return fps == Math.rint(fps) ? Integer.toString((int) fps) : String.format(Locale.ROOT, "%.6f", fps);
   }

   /** The video's picture size and length: {width, height, seconds (NaN if unknown)}, or null. */
   static double[] probe(File video) {
      try {
         boolean ff = useFfmpeg();
         File mpv = ff ? null : mpv();
         if (mpv != null) {
            String text = run(List.of(mpv.getAbsolutePath(), "--no-config", "--vo=null", "--ao=null", "--frames=1",
               "--term-playing-msg=SIZE ${width} ${height} ${duration}", video.getAbsolutePath()));
            Matcher m = Pattern.compile("SIZE (\\d+) (\\d+) ?([0-9.]*)").matcher(text);
            if (m.find()) {
               return new double[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), number(m.group(3)) };
            }
            Video.log("Video: mpv didn't give the size of " + video.getName() + ": " + text.strip(), null);
         }
         File ffmpeg = ff ? ffmpeg() : null;
         if (ffmpeg != null) {
            // ffmpeg describes its input when it has no output to make (and exits with an error, which is fine)
            String text = run(List.of(ffmpeg.getAbsolutePath(), "-nostdin", "-hide_banner", "-i", video.getAbsolutePath()));
            Matcher v = Pattern.compile("Stream #[^\\n]*Video: [^\\n]*?(\\d{2,5})x(\\d{2,5})").matcher(text);
            if (v.find()) {
               int w = Integer.parseInt(v.group(1)), h = Integer.parseInt(v.group(2));
               // phone videos are often stored sideways with a rotation; ffmpeg turns the frames upright
               Matcher r = Pattern.compile("(?:rotation of|rotate\\s*:)\\s*(-?\\d+(?:\\.\\d+)?)").matcher(text);
               if (r.find() && Math.abs(Math.abs(Double.parseDouble(r.group(1))) - 90) < 1) {
                  int t = w;
                  w = h;
                  h = t;
               }
               double seconds = Double.NaN;
               Matcher d = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)").matcher(text);
               if (d.find()) {
                  seconds = Integer.parseInt(d.group(1)) * 3600 + Integer.parseInt(d.group(2)) * 60
                     + Double.parseDouble(d.group(3));
               }
               return new double[] { w, h, seconds };
            }
            Video.log("Video: ffmpeg didn't give the size of " + video.getName() + ": " + text.strip(), null);
         }
         if (mpv == null && ffmpeg == null) {
            Video.log("Video: neither mpv nor ffmpeg was found, so there's no picture", null);
         }
      } catch (Throwable t) {
         Video.log("Video: could not read the video's size", t);
      }
      return null;
   }

   private static double number(String s) {
      try {
         return s == null || s.isEmpty() ? Double.NaN : Double.parseDouble(s);
      } catch (NumberFormatException e) {
         return Double.NaN;
      }
   }

   private static String run(List<String> cmd) throws Exception {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      p.waitFor(10, TimeUnit.SECONDS);
      return text;
   }
}
