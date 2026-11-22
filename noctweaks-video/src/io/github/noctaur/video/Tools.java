package io.github.noctaur.video;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The outside programs the mod uses: mpv (the video window, and the video's sound as a WAV) and, if it's
 * installed, ffmpeg (makes the WAV a little faster). mpv ships with the mod, in its tools folder (Windows:
 * tools/mpv/mpv.exe; macOS: tools/mpv.tar.gz, unpacked on first use). Looked for there first, then in the settings
 * ("mpv" / "ffmpeg" = a full path), then on PATH and where installers usually put them.
 */
final class Tools {
   private Tools() {
   }

   private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
   private static final boolean WINDOWS = OS.contains("win");
   private static final boolean MAC = OS.contains("mac");

   static File toolsFolder() {
      try {
         return Video.MOD.resource("tools").toFile();
      } catch (Throwable t) {
         return null;
      }
   }

   static void openToolsFolder() {
      open(toolsFolder());
   }

   /** Shows a folder in Explorer / Finder. */
   static void open(File dir) {
      try {
         if (dir == null) {
            return;
         }
         dir.mkdirs();
         String open = WINDOWS ? "explorer.exe" : MAC ? "open" : "xdg-open";
         new ProcessBuilder(open, dir.getAbsolutePath()).start();
      } catch (Throwable t) {
         Video.log("Video: could not open " + dir, t);
      }
   }

   /**
    * macOS: the mod ships mpv as Apple's own mpv.tar.gz (mpv.app inside), and unpacks it here on first use.
    * Unpacking with the Mac's own tar keeps mpv.app's code signature; quarantine is cleared so it can start.
    */
   private static void unpackMacMpv(File tools) {
      File app = new File(tools, "mpv.app");
      File archive = new File(tools, "mpv.tar.gz");
      if (!MAC || app.isDirectory() || !archive.isFile()) {
         return;
      }
      try {
         Video.log("Video: unpacking mpv.app", null);
         Process p = new ProcessBuilder("/usr/bin/tar", "-xzf", archive.getAbsolutePath(), "-C", tools.getAbsolutePath())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
         p.waitFor(2, TimeUnit.MINUTES);
         new ProcessBuilder("/usr/bin/xattr", "-dr", "com.apple.quarantine", app.getAbsolutePath())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(1, TimeUnit.MINUTES);
         Video.log("Video: mpv.app " + (app.isDirectory() ? "ready" : "could not be unpacked"), null);
      } catch (Throwable t) {
         Video.log("Video: could not unpack mpv.app", t);
      }
   }

   /** The program, or null. */
   static File find(String name) {
      String exe = WINDOWS ? name + ".exe" : name;
      List<File> places = new ArrayList<>();
      File tools = toolsFolder();
      if (tools != null) {
         if (name.equals("mpv")) {
            unpackMacMpv(tools);
         }
         places.add(new File(tools, exe));
         places.add(new File(new File(tools, name), exe));
         if (MAC && name.equals("mpv")) {
            places.add(new File(tools, "mpv.app/Contents/MacOS/mpv"));
         }
      }
      try {
         String set = Video.MOD.settings().get(name, "");
         if (!set.isBlank()) {
            places.add(new File(set.trim()));
         }
      } catch (Throwable ignored) {
      }
      String path = System.getenv("PATH");
      if (path != null) {
         for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank()) {
               places.add(new File(dir, exe));
            }
         }
      }
      String home = System.getProperty("user.home", "");
      if (WINDOWS) {
         String local = System.getenv("LOCALAPPDATA");
         String pf = System.getenv("ProgramFiles");
         if (pf != null) {
            places.add(Path.of(pf, name, exe).toFile());
            places.add(Path.of(pf, name, "bin", exe).toFile());
         }
         if (local != null) {
            places.add(Path.of(local, "Programs", name, exe).toFile());
            places.add(Path.of(local, "Microsoft", "WinGet", "Links", exe).toFile());
         }
         places.add(Path.of(home, "scoop", "shims", exe).toFile());
         places.add(Path.of("C:\\", name, exe).toFile());
         places.add(Path.of("C:\\", name, "bin", exe).toFile());
      } else {
         places.add(new File("/opt/homebrew/bin/" + name));
         places.add(new File("/usr/local/bin/" + name));
         places.add(new File("/usr/bin/" + name));
         if (MAC && name.equals("mpv")) {
            places.add(new File("/Applications/mpv.app/Contents/MacOS/mpv"));
         }
      }
      for (File f : places) {
         if (f.isFile() && f.canExecute()) {
            return f;
         }
      }
      return null;
   }

   /**
    * The video's sound as a WAV beside it ("<name> (sound).wav"), made once and reused while it's newer than
    * the video. If the video's folder can't be written, it goes in the mod's tools folder. Null if neither
    * ffmpeg nor mpv is found or both fail.
    */
   static File extractSound(File video) {
      File out = Sounds.copyFor(video);   // where the user wants sound copies kept
      if (out.isFile() && out.length() > 44 && out.lastModified() >= video.lastModified()) {
         Video.log("Video: using the sound made before, " + out, null);
         return out;
      }
      File ffmpeg = find("ffmpeg");
      if (ffmpeg != null && run(List.of(ffmpeg.getAbsolutePath(), "-nostdin", "-y", "-i", video.getAbsolutePath(),
            "-vn", "-map", "0:a:0", "-c:a", "pcm_s24le", out.getAbsolutePath()), out)) {
         return out;
      }
      File mpv = find("mpv");
      if (mpv != null && run(List.of(mpv.getAbsolutePath(), "--no-config", "--video=no", "--ao=pcm",
            "--ao-pcm-file=" + out.getAbsolutePath(), "--ao-pcm-waveheader=yes", "--terminal=no",
            video.getAbsolutePath()), out)) {
         return out;
      }
      Video.log("Video: could not make a sound file (ffmpeg " + (ffmpeg == null ? "not found" : "failed")
         + ", mpv " + (mpv == null ? "not found" : "failed") + ")", null);
      return null;
   }

   private static boolean run(List<String> cmd, File out) {
      try {
         Video.log("Video: running " + String.join(" ", cmd), null);
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.redirectErrorStream(true);
         File logFile = new File(System.getProperty("java.io.tmpdir"), "noctweaks-video-tool.log");
         pb.redirectOutput(logFile);
         Process p = pb.start();
         if (!p.waitFor(5, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            Video.log("Video: gave up after 5 minutes", null);
            return false;
         }
         boolean ok = p.exitValue() == 0 && out.isFile() && out.length() > 44;
         Video.log("Video: exit " + p.exitValue() + (ok ? ", made " + out + " (" + out.length() / 1024 + " KB)" : ", see " + logFile), null);
         return ok;
      } catch (Throwable t) {
         Video.log("Video: could not run " + cmd.get(0), t);
         return false;
      }
   }
}
