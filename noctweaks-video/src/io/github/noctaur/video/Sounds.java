package io.github.noctaur.video;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Where the sound copies go: the WAV of a video's sound, made when the video is dropped, that Bitwig plays as
 * the clip. By default they're kept out of the way in the mod's own data folder (sounds/); Settings >
 * Locations > Video can put them next to each video instead, or in a folder chosen from the Video menu.
 *
 * Each video's copy is in a folder of its own (named from the video's full path), so two videos with the same
 * name never share a copy, and the copy keeps the video's name, which is the name the clip shows.
 */
final class Sounds {
   private Sounds() {
   }

   static final int IN_MOD_FOLDER = 0, NEXT_TO_VIDEO = 1, CHOSEN_FOLDER = 2;
   static final String[] PLACES = { "In the Video mod's folder", "Next to each video", "In a folder you choose (Video menu)" };

   private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
   private static final boolean WINDOWS = OS.contains("win");
   private static final boolean MAC = OS.contains("mac");

   static int place() {
      try {
         int p = Integer.parseInt(Video.MOD.settings().get("sound-copies", "0"));
         return p >= 0 && p < PLACES.length ? p : IN_MOD_FOLDER;
      } catch (Throwable t) {
         return IN_MOD_FOLDER;
      }
   }

   static void setPlace(int p) {
      try {
         Video.MOD.settings().put("sound-copies", Integer.toString(p));
         Video.MOD.settings().flush();
      } catch (Throwable t) {
         Video.log("Video: could not save where sound copies go", t);
      }
   }

   static File chosenFolder() {
      try {
         String s = Video.MOD.settings().get("sound-folder", "");
         return s.isEmpty() ? null : new File(s);
      } catch (Throwable t) {
         return null;
      }
   }

   /** The mod's own folder for them (in Patchbay's data folder, kept across updates). */
   static File modFolder() {
      try {
         return Video.MOD.resource("sounds").toFile();
      } catch (Throwable t) {
         File tools = Tools.toolsFolder();
         return tools == null ? null : new File(tools.getParentFile(), "sounds");
      }
   }

   /** The folder the copies go in now (null: next to each video). */
   static File folder() {
      int p = place();
      if (p == NEXT_TO_VIDEO) {
         return null;
      }
      if (p == CHOSEN_FOLDER) {
         File f = chosenFolder();
         if (f != null) {
            return f;
         }
      }
      return modFolder();
   }

   /** Where the copy of this video's sound goes (the folder is made if needed). */
   static File copyFor(File video) {
      String base = video.getName().replaceFirst("\\.[^.]+$", "");
      File parent = video.getAbsoluteFile().getParentFile();
      if (place() == NEXT_TO_VIDEO && parent != null && parent.canWrite()) {
         return new File(parent, base + " (sound).wav");
      }
      File dir = folder();
      if (dir == null) {
         dir = modFolder();
      }
      File sub = new File(dir, base + " " + hash(video.getAbsolutePath()));
      sub.mkdirs();
      return new File(sub, base + ".wav");
   }

   private static String hash(String s) {
      long h = 1125899906842597L;
      for (byte b : s.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)) {
         h = 31 * h + b;
      }
      return String.format(Locale.ROOT, "%08x", (int) (h ^ (h >>> 32)));
   }

   static void openFolder() {
      File dir = folder();
      if (dir == null) {
         Video.log("Video: sound copies are kept next to each video", null);
         return;
      }
      Tools.open(dir);
   }

   /**
    * Asks for a folder with the system's own folder picker (on a background thread: it waits for the user).
    * Picking one puts the copies there from the next video on.
    */
   static void chooseFolder() {
      Thread t = new Thread(() -> {
         try {
            List<String> cmd;
            if (WINDOWS) {
               String ps = "Add-Type -AssemblyName System.Windows.Forms;"
                  + "$o = New-Object System.Windows.Forms.Form -Property @{TopMost = $true};"
                  + "$d = New-Object System.Windows.Forms.FolderBrowserDialog;"
                  + "$d.Description = 'Folder for the sound copies of videos (NocTweaks Video)';"
                  + "$d.ShowNewFolderButton = $true;"
                  + "if ($d.ShowDialog($o) -eq 'OK') { [Console]::Out.Write($d.SelectedPath) }";
               cmd = List.of("powershell.exe", "-NoProfile", "-STA", "-Command", ps);
            } else if (MAC) {
               cmd = List.of("/usr/bin/osascript", "-e",
                  "POSIX path of (choose folder with prompt \"Folder for the sound copies of videos (NocTweaks Video)\")");
            } else {
               cmd = List.of("zenity", "--file-selection", "--directory", "--title=Folder for the sound copies of videos");
            }
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            p.waitFor();
            if (out.isEmpty()) {
               Video.log("Video: no folder chosen for sound copies", null);
               return;
            }
            File dir = new File(out);
            if (!dir.isDirectory()) {
               Video.log("Video: not a folder: " + out, null);
               return;
            }
            Video.MOD.settings().put("sound-folder", dir.getAbsolutePath());
            Video.MOD.settings().flush();
            setPlace(CHOSEN_FOLDER);
            Video.log("Video: sound copies now go in " + dir, null);
         } catch (Throwable e) {   // never let an exception leave the thread
            Video.log("Video: could not choose a folder", e);
         }
      }, "noctweaks-video-choose-folder");
      t.setDaemon(true);
      t.start();
   }

   /** A short description of where they go, for the menu. */
   static String describe() {
      int p = place();
      if (p == NEXT_TO_VIDEO) {
         return "next to each video";
      }
      File f = folder();
      return p == CHOSEN_FOLDER && f != null ? f.getName() : "the Video mod's folder";
   }
}
