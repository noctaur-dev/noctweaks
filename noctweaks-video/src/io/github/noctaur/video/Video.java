package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Bml;
import com.bitwig.base.io.z2y;
import com.bitwig.flt.document.core.master.g2L;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Video (prototype): drop a video file on an arranger track and its sound goes in as a normal audio clip,
 * while the picture plays in a window kept in step with Bitwig's transport.
 *
 * One video at a time for now. Its link (video file, its sound, where it starts) is kept in the mod's
 * settings, so after a restart the arranger's right-click menu can show it again.
 */
final class Video {
   static final Bml.Mod MOD = Bml.mod("noctweaks-video");

   static final Set<String> EXTENSIONS = Set.of("mp4", "mov", "m4v", "mkv", "webm", "avi");

   private Video() {
   }

   static void log(String message, Throwable t) {
      try {
         if (t == null) {
            MOD.log(message);
         } else {
            MOD.log(message, t);
         }
      } catch (Throwable ignored) {
      }
   }

   static boolean isVideo(File f) {
      if (f == null) {
         return false;
      }
      String n = f.getName();
      int dot = n.lastIndexOf('.');
      return dot > 0 && EXTENSIONS.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT));
   }

   // ------------------------------------------------------------------ the linked videos

   /** A video, its sound (the WAV its clips play) and the start set when it was dropped (seconds; a fallback). */
   record Link(File video, File sound, double startSeconds) {
   }

   // every video dropped (latest last); kept in the mod's settings
   private static volatile List<Link> links = loadLinks();
   private static volatile java.lang.ref.WeakReference<g2L> project = new java.lang.ref.WeakReference<>(null);

   static List<Link> links() {
      return links;
   }

   static boolean hasLinks() {
      return !links.isEmpty();
   }

   /** The latest video dropped (for the pop-out window and diagnostics), or null. */
   static Link link() {
      List<Link> l = links;
      return l.isEmpty() ? null : l.get(l.size() - 1);
   }

   /** The video whose sound this audio file is (the same file, or a copy with its name), or null. */
   static Link linkForSound(File file) {
      if (file == null) {
         return null;
      }
      File abs = file.getAbsoluteFile();
      Link byName = null;
      for (Link l : links) {
         if (l.sound() == null) {
            continue;
         }
         if (l.sound().getAbsoluteFile().equals(abs)) {
            return l;
         }
         if (byName == null && l.sound().getName().equals(file.getName())) {
            byName = l;   // Bitwig can copy dropped audio into the project's folder
         }
      }
      return byName;
   }

   static g2L project() {
      return project.get();
   }

   /** Remembers the project the arranger is showing, so the panel can follow its transport. */
   static void seen(g2L doc) {
      if (doc != null && project.get() != doc) {
         project = new java.lang.ref.WeakReference<>(doc);
      }
   }

   private static List<Link> loadLinks() {
      List<Link> out = new ArrayList<>();
      try {
         int n = Integer.parseInt(MOD.settings().get("video-count", "-1"));
         if (n < 0) {   // saved by an older version: one video
            String v = MOD.settings().get("video", "");
            if (!v.isEmpty()) {
               String s = MOD.settings().get("sound", "");
               out.add(new Link(new File(v), s.isEmpty() ? null : new File(s),
                  Double.parseDouble(MOD.settings().get("start-seconds", "0"))));
            }
         }
         for (int i = 0; i < n; i++) {
            String v = MOD.settings().get("video." + i, "");
            String s = MOD.settings().get("sound." + i, "");
            if (!v.isEmpty()) {
               out.add(new Link(new File(v), s.isEmpty() ? null : new File(s),
                  Double.parseDouble(MOD.settings().get("start." + i, "0"))));
            }
         }
      } catch (Throwable t) {
         log("Video: could not read the saved videos", t);
      }
      return List.copyOf(out);
   }

   private static void saveLinks(List<Link> l) {
      links = List.copyOf(l);
      try {
         int old = Integer.parseInt(MOD.settings().get("video-count", "0"));
         for (int i = l.size(); i < old; i++) {
            MOD.settings().remove("video." + i);
            MOD.settings().remove("sound." + i);
            MOD.settings().remove("start." + i);
         }
         MOD.settings().remove("video");
         MOD.settings().remove("sound");
         MOD.settings().remove("start-seconds");
         for (int i = 0; i < l.size(); i++) {
            MOD.settings().put("video." + i, l.get(i).video().getAbsolutePath());
            MOD.settings().put("sound." + i, l.get(i).sound() == null ? "" : l.get(i).sound().getAbsolutePath());
            MOD.settings().put("start." + i, Double.toString(l.get(i).startSeconds()));
         }
         MOD.settings().put("video-count", Integer.toString(l.size()));
         MOD.settings().flush();
      } catch (Throwable t) {
         log("Video: could not save the videos", t);
      }
   }

   /** Adds a video (replacing an earlier link of the same video or sound). */
   static synchronized void addLink(Link l) {
      List<Link> next = new ArrayList<>();
      for (Link o : links) {
         if (!o.video().equals(l.video()) && (o.sound() == null || !o.sound().equals(l.sound()))) {
            next.add(o);
         }
      }
      next.add(l);
      saveLinks(next);
   }

   static synchronized void forget(Link l) {
      List<Link> next = new ArrayList<>(links);
      next.remove(l);
      saveLinks(next);
   }

   static synchronized void forgetAll() {
      saveLinks(List.of());
   }

   // ------------------------------------------------------------------ dropping a video

   private static volatile double laneTime = Double.NaN;
   private static volatile long laneTimeAt;

   /** Where on the timeline (Bitwig's time, beats) a file is being dropped onto a lane. */
   static void laneDrop(double time) {
      laneTime = time;
      laneTimeAt = System.currentTimeMillis();
   }

   /**
    * Files dropped into a project, before Bitwig loads them. Each video file is replaced (in Bitwig's own
    * array) by a WAV of its sound, so Bitwig adds that as an ordinary audio clip.
    */
   static void dropping(g2L doc, z2y[] files, Object target) {
      if (files == null) {
         return;
      }
      for (int i = 0; i < files.length; i++) {
         File f = files[i] == null ? null : files[i].cfM();
         if (!isVideo(f)) {
            continue;
         }
         log("Video: " + f.getName() + " dropped (target " + (target == null ? "none" : target.getClass().getName()) + ")", null);
         File sound = Tools.extractSound(f);
         if (sound == null) {
            log("Video: no sound file for " + f.getName() + ", Bitwig will try the video file itself", null);
            continue;
         }
         files[i] = new z2y(sound);

         // The picture follows the clip Bitwig makes from the sound (see Follow), wherever it goes; this start
         // is only used if the clips can't be read.
         double start = 0;
         project = new java.lang.ref.WeakReference<>(doc);
         addLink(new Link(f, sound, start));
         if (Player.running()) {
            Player.show(f);   // the pop-out window, if it's open, follows too
            Sync.start();
         }
         return;   // one video per drop
      }
   }

   // ------------------------------------------------------------------ the arranger's menu

   static void menu(dKW menu, g2L doc) {
      menu.ELS();
      dKW m = menu.Hnt("Video");
      List<Link> l = links;
      if (l.isEmpty()) {
         m.cfM("Drop a video file on a track to add it", () -> {
         });
      } else {
         m.cfM(l.size() == 1 ? "1 video (the highest AV track shows in the panel)"
            : l.size() + " videos (the highest AV track shows in the panel)", () -> seen(doc));
         Link latest = link();
         if (Player.running()) {
            m.cfM("Close Pop-Out Window", Player::close);
         } else {
            m.cfM("Pop Out to a Window: " + latest.video().getName(), () -> {
               seen(doc);
               Player.show(latest.video());
               Sync.start();
            });
         }
         m.ELS();
         m.cfM("Forget All Videos", () -> {
            Player.close();
            forgetAll();
         });
      }
      m.ELS();
      soundCopyItems(m);
      m.ELS();
      m.cfM("Describe Clips at Playhead (for testing)", () -> Inspect.describe(doc));
      m.cfM("Open Video Tools Folder (mpv, ffmpeg)", Tools::openToolsFolder);
   }

   /** The right-click menu of a selection: a Video submenu when it holds an audio clip. */
   static void clipMenu(dKW menu, java.util.List<Object> items) {
      java.util.List<Object> clips = new java.util.ArrayList<>();
      for (Object o : items) {
         if (o instanceof Nbl) {   // an audio clip
            clips.add(o);
         }
      }
      if (clips.isEmpty()) {
         return;
      }
      g2L doc = project();
      try {
         if (clips.get(0) instanceof zZi clip && clip.oQj() instanceof g2L d) {   // the clip's project
            doc = d;
            seen(d);
         }
      } catch (Throwable ignored) {
      }
      g2L project = doc;
      menu.ELS();
      dKW m = menu.Hnt("Video");
      Follow.Span span = clips.get(0) instanceof Nbl n ? Follow.span(n) : null;
      if (span != null) {
         Link l = span.link();
         m.cfM("Video: " + l.video().getName(), () -> {
         });
         m.cfM("Forget This Video (its clips become plain audio)", () -> forget(l));
         m.ELS();
      }
      m.cfM("Describe This Clip (for testing)", () -> Inspect.describeClips(project, clips));
      m.ELS();
      soundCopyItems(m);
   }

   /** Where the sound copies of videos go (also in Settings > Locations > Video). */
   private static void soundCopyItems(dKW m) {
      m.cfM("Open Sound Copies Folder (" + Sounds.describe() + ")", Sounds::openFolder);
      m.cfM("Choose Folder for Sound Copies...", Sounds::chooseFolder);
   }


}
