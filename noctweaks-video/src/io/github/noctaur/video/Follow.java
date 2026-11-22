package io.github.noctaur.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.flt.document.core.master.qjn;   // an audio file in the project
import com.bitwig.flt.document.core.master.ruS;   // a project's tracks

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The picture follows the sound: the video's time is read from the arranger clip under the playhead that
 * plays the video's sound (the WAV made when it was dropped). Move the clip and the picture moves with it;
 * cut it, and each piece shows its own part; between clips the panel is black.
 *
 * Bitwig 6.1.4 names: track xJo.HVx().iAj() is its arranger clip timeline (PSn, cfM() the clips); an audio
 * clip Nbl has its position AGr.ODA() and length Jsq.QWG() (beats); Nbl.z().sut() is its audio events
 * (zmK, cfM() the list), each an ocL with its position in the clip mgr.ODA() and its file Scv().uWE().IwV().
 */
final class Follow {
   private Follow() {
   }

   /** A clip playing the video's sound: where it is (beats), and where in it the sound starts. */
   record Span(double start, double end, double soundStart) {
   }

   private static List<Span> spans = List.of();
   private static long scannedAt;
   private static File scannedFor;
   private static boolean loggedScan;

   /**
    * The video time for the playhead: from the clip under it, or (when no clip in the project plays the
    * video's sound) from the start set when it was dropped. Negative = nothing to show. UI thread only.
    */
   static double videoTime(g2L doc, Video.Link link) {
      if (link == null || doc == null) {
         return -1;
      }
      double secs = Clock.seconds(doc);
      List<Span> found = spans(doc, link.sound());
      if (found == null) {   // the clips couldn't be read: the old way
         return secs - link.startSeconds();
      }
      if (found.isEmpty()) {
         return -1;   // the sound's clip was deleted: nothing to show
      }
      double beats = Clock.secondsToBeats(doc, secs);
      for (Span s : found) {
         if (beats >= s.start() - 1e-9 && beats < s.end()) {
            double from = s.start() + s.soundStart();
            return beats < from ? -1 : secs - Clock.beatsToSeconds(doc, from);
         }
      }
      return -1;   // between clips
   }

   /** The clips that play the sound, read again every few hundred milliseconds; null if they can't be read. */
   static List<Span> spans(g2L doc, File sound) {
      long now = System.currentTimeMillis();
      if (sound != null && sound.equals(scannedFor) && now - scannedAt < 300) {
         return spans;
      }
      scannedAt = now;
      scannedFor = sound;
      List<Span> out = sound == null ? null : scan(doc, sound);
      spans = out;
      if (!loggedScan && out != null) {
         loggedScan = true;
         Video.log("Video: " + out.size() + " clip(s) play " + sound.getName() + ": " + out, null);
      }
      return out;
   }

   private static List<Span> scan(g2L doc, File sound) {
      try {
         List<Span> out = new ArrayList<>();
         ruS tracks = doc.spd();
         int n = tracks == null ? 0 : tracks.ugU();
         for (int i = 0; i < n; i++) {
            if (!(tracks.FzB(i) instanceof xJo track)) {
               continue;
            }
            Object timeline = track.HVx().iAj();
            if (!(timeline instanceof PSn clips)) {
               continue;
            }
            for (Object c : clips.cfM()) {
               if (c instanceof Nbl clip) {
                  Double soundStart = soundStart(clip, sound);
                  if (soundStart != null) {
                     double start = clip.ODA();
                     out.add(new Span(start, start + clip.QWG(), soundStart));
                  }
               }
            }
         }
         return out;
      } catch (Throwable t) {
         Video.log("Video: reading the clips", t);
         return null;
      }
   }

   /** Where in the clip its event playing the sound starts (beats), or null if it doesn't play it. */
   private static Double soundStart(Nbl clip, File sound) {
      gIs content = clip.z();
      if (content == null) {
         return null;
      }
      zmK events = content.sut();
      if (events == null) {
         return null;
      }
      for (Object e : events.cfM()) {
         if (e instanceof ocL ev && plays(ev, sound)) {
            return ev.ODA();
         }
      }
      return null;
   }

   static boolean plays(ocL ev, File sound) {
      qjn f = ev.Scv();
      if (f == null || f.uWE() == null) {
         return false;
      }
      File file = f.uWE().IwV();
      // the same file, or a copy of it (Bitwig can copy dropped audio into the project's folder)
      return file != null && (file.getAbsoluteFile().equals(sound.getAbsoluteFile()) || file.getName().equals(sound.getName()));
   }
}
