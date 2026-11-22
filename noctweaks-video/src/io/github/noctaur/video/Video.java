package io.github.noctaur.video;

import bitwig.obf.*;
import bml.api.Bml;
import com.bitwig.base.io.z2y;
import com.bitwig.flt.document.core.master.g2L;

import java.io.File;
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

   // ------------------------------------------------------------------ the linked video

   /** The video, its sound and where it starts on the timeline (seconds). */
   record Link(File video, File sound, double startSeconds) {
   }

   private static volatile Link link = loadLink();
   private static volatile java.lang.ref.WeakReference<g2L> project = new java.lang.ref.WeakReference<>(null);

   static Link link() {
      return link;
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

   /**
    * The video time (seconds) for the playhead, or a negative number when there's nothing to show: read
    * from the clip playing the video's sound (see Follow). UI thread only.
    */
   static double videoTime(g2L doc) {
      return Follow.videoTime(doc, link);
   }

   private static Link loadLink() {
      try {
         String v = MOD.settings().get("video", "");
         if (v.isEmpty()) {
            return null;
         }
         String s = MOD.settings().get("sound", "");
         double start = Double.parseDouble(MOD.settings().get("start-seconds", "0"));
         return new Link(new File(v), s.isEmpty() ? null : new File(s), start);
      } catch (Throwable t) {
         log("Video: could not read the saved video", t);
         return null;
      }
   }

   static void setLink(Link l) {
      link = l;
      try {
         if (l == null) {
            MOD.settings().remove("video");
            MOD.settings().remove("sound");
            MOD.settings().remove("start-seconds");
         } else {
            MOD.settings().put("video", l.video().getAbsolutePath());
            MOD.settings().put("sound", l.sound() == null ? "" : l.sound().getAbsolutePath());
            MOD.settings().put("start-seconds", Double.toString(l.startSeconds()));
         }
         MOD.settings().flush();
      } catch (Throwable t) {
         log("Video: could not save the video", t);
      }
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
         setLink(new Link(f, sound, start));
         if (Player.running()) {
            Player.show(f);   // the pop-out window, if it's open, follows too
            Sync.start();
         }
         return;   // one video at a time for now
      }
   }

   // ------------------------------------------------------------------ the arranger's menu

   static void menu(dKW menu, g2L doc) {
      menu.ELS();
      dKW m = menu.Hnt("Video");
      Link l = link;
      if (l == null) {
         m.cfM("Drop a video file on a track to add it", () -> {
         });
      } else {
         String name = l.video().getName();
         m.cfM("Video: " + name + " (shown in the Video panel)", () -> project = new java.lang.ref.WeakReference<>(doc));
         if (Player.running()) {
            m.cfM("Close Pop-Out Window", Player::close);
         } else {
            m.cfM("Pop Out to a Window", () -> {
               project = new java.lang.ref.WeakReference<>(doc);
               Player.show(l.video());
               Sync.start();
            });
         }
         double now = Clock.seconds(doc);
         m.cfM(String.format(Locale.ROOT, "Set Video Start to Playhead (%.2f s)", now), () -> {
            project = new java.lang.ref.WeakReference<>(doc);
            Link cur = link;
            if (cur != null) {
               setLink(new Link(cur.video(), cur.sound(), Clock.seconds(doc)));
               Sync.kick();
            }
         });
         m.cfM(String.format(Locale.ROOT, "Video Starts at %.3f s: Earlier by 1 Frame", l.startSeconds()), () -> nudge(-1.0 / 30));
         m.cfM("Video Start: Later by 1 Frame", () -> nudge(1.0 / 30));
         m.ELS();
         m.cfM("Forget Video", () -> {
            Player.close();
            setLink(null);
         });
      }
      m.ELS();
      soundCopyItems(m);
      m.ELS();
      m.cfM("Describe Clips at Playhead (for linking video to clip edits)", () -> Inspect.describe(doc));
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
      m.cfM("Describe This Clip (for linking video to clip edits)", () -> Inspect.describeClips(project, clips));
      m.ELS();
      soundCopyItems(m);
      if (project != null && link != null) {
         m.cfM(String.format(Locale.ROOT, "Set Video Start to Playhead (%.2f s)", Clock.seconds(project)), () -> {
            Link cur = link;
            if (cur != null) {
               setLink(new Link(cur.video(), cur.sound(), Clock.seconds(project)));
               Sync.kick();
            }
         });
      }
   }

   /** Where the sound copies of videos go (also in Settings > Locations > Video). */
   private static void soundCopyItems(dKW m) {
      m.cfM("Open Sound Copies Folder (" + Sounds.describe() + ")", Sounds::openFolder);
      m.cfM("Choose Folder for Sound Copies...", Sounds::chooseFolder);
   }

   private static void nudge(double seconds) {
      Link cur = link;
      if (cur != null) {
         setLink(new Link(cur.video(), cur.sound(), cur.startSeconds() + seconds));
         Sync.kick();
      }
   }
}
