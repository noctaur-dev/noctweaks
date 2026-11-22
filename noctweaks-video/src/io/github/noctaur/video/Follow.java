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
 * Bitwig 6.1.4 names: track xJo.HVx().iAj() is its arranger clip timeline (PSn, cfM() the clips). An audio
 * clip Nbl: its position SMz.x3t(), its length asx.nfq() (Jsq.QWG() is its end), in beats, as Bitwig's
 * TestTemporalEvent and TestEnduringEvent read them; and zZi.dC_(), where in its content it starts playing
 * (trimming the clip's left edge moves it). Nbl.z().sut() is its audio events (zmK, cfM() the list), each an
 * ocL: its position in the clip's content SMz.x3t() (beats), where in the file it starts mgr.ODA() (seconds),
 * and its file Scv().uWE().IwV(). (AGr.ODA() on a clip is its tempo.) Read from a clip before and after a trim.
 */
final class Follow {
   private Follow() {
   }

   /**
    * A clip playing the video's sound: where it is and how far it goes (timeline beats), where in its content it
    * starts playing, and its sound's event: where that is in the content (beats) and where in the file (= the
    * video) it starts (seconds).
    */
   record Span(double start, double end, double playStart, double eventPos, double fileStart) {
   }

   /** The video time (seconds) a span shows at this timeline beat, or -1 where it shows nothing. */
   static double timeAt(g2L doc, Span s, double beat) {
      if (beat < s.start() - 1e-9 || beat >= s.end()) {
         return -1;
      }
      double into = beat - s.start() + s.playStart() - s.eventPos();   // beats into the sound's event
      if (into < -1e-9) {
         return -1;
      }
      return s.fileStart() + Clock.beatsToSeconds(doc, beat) - Clock.beatsToSeconds(doc, beat - into);
   }

   private static List<Span> spans = List.of();
   private static long scannedAt;
   private static File scannedFor;
   private static String lastText;

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
            return timeAt(doc, s, beats);
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
      String text = out == null ? "the clips can't be read" : out.size() + " clip(s) play " + sound.getName() + ": " + out;
      if (!text.equals(lastText)) {   // logged when the clips change (for testing)
         lastText = text;
         Video.log("Video: " + text, null);
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
                  Span sp = span(clip, sound);
                  if (sp != null) {
                     out.add(sp);
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

   /** The clip as a span of the video, or null if it doesn't play the video's sound. */
   static Span span(Nbl clip, File sound) {
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
            double start = clip.x3t();
            return new Span(start, start + clip.nfq(), clip.dC_(), ev.x3t(), ev.ODA());
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

   /** Whether one of this track's arranger clips plays the linked video's sound. UI thread. */
   static boolean trackHasVideo(xJo track) {
      Video.Link link = Video.link();
      if (link == null || link.sound() == null || track == null) {
         return false;
      }
      try {
         if (track.HVx().iAj() instanceof PSn clips) {
            for (Object c : clips.cfM()) {
               if (c instanceof Nbl clip && span(clip, link.sound()) != null) {
                  return true;
               }
            }
         }
      } catch (Throwable t) {
         Video.log("Video: reading a track's clips", t);
      }
      return false;
   }
}
