package io.github.noctaur.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.iface.WSA;   // a track or group
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.flt.document.core.master.qjn;   // an audio file in the project
import com.bitwig.flt.document.core.master.ruS;   // a project's tracks

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The picture follows the sound: the video's time is read from the arranger clip under the playhead that
 * plays a video's sound (the WAV made when it was dropped). Move the clip and the picture moves with it;
 * cut it, and each piece shows its own part; between clips the panel is black. With several videos, the clip
 * on the highest track wins, as in a video editor; a muted track is skipped. Tracks inside groups count, in
 * the order the arranger lists them.
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
   record Span(Video.Link link, int track, boolean muted, double start, double end, double playStart, double eventPos,
               double fileStart) {
   }

   /** What the panel shows now: which video, at what time (seconds). */
   record Now(Video.Link link, double time) {
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
   private static Object scannedLinks;
   private static String lastText;

   /**
    * The video and time to show for the playhead: from the clips under it that play a video's sound, the one
    * on the highest track (as video editors do: the top track covers the ones below). Null = nothing to show.
    * When the clips can't be read, the latest video from the start set when it was dropped. UI thread only.
    */
   static Now now(g2L doc) {
      if (doc == null || !Video.hasLinks()) {
         return null;
      }
      double secs = Clock.seconds(doc);
      List<Span> found = spans(doc);
      if (found == null) {
         Video.Link l = Video.link();
         double t = secs - l.startSeconds();
         return t < 0 ? null : new Now(l, t);
      }
      double beats = Clock.secondsToBeats(doc, secs);
      for (Span s : found) {   // in track order, top first
         if (s.muted()) {
            continue;   // a muted AV track hides its picture too, so the next one down shows
         }
         double t = timeAt(doc, s, beats);
         if (t >= 0) {
            return new Now(s.link(), t);
         }
      }
      return null;   // no video clip under the playhead
   }

   /** Every clip that plays a video's sound, top track first; read again every few hundred ms; null if unreadable. */
   static List<Span> spans(g2L doc) {
      long now = System.currentTimeMillis();
      List<Video.Link> links = Video.links();
      if (links == scannedLinks && now - scannedAt < 300) {
         return spans;
      }
      scannedAt = now;
      scannedLinks = links;
      List<Span> out = scan(doc);
      spans = out;
      String text = out == null ? "the clips can't be read" : out.size() + " video clip(s): " + out;
      if (!text.equals(lastText)) {   // logged when the clips change (for testing)
         lastText = text;
         Video.log("Video: " + text, null);
      }
      return out;
   }

   private static List<Span> scan(g2L doc) {
      try {
         List<Span> out = new ArrayList<>();
         ruS tracks = doc.spd();
         if (tracks != null) {
            walk(tracks, false, out, new int[1], 0);
         }
         return out;
      } catch (Throwable t) {
         Video.log("Video: reading the clips", t);
         return null;
      }
   }

   /**
    * The tracks of a group (ruS: the project's top level, or a group track) in the order the arranger lists
    * them, groups opened: SC.FzB(i) is its i-th track or group. A group's mute covers its tracks.
    */
   private static void walk(ruS group, boolean groupMuted, List<Span> out, int[] order, int depth) {
      int n = group.ugU();
      for (int i = 0; i < n; i++) {
         Object item = group.FzB(i);
         boolean muted = groupMuted || muted(item);
         if (item instanceof ruS sub) {
            if (depth < 16 && sub != group) {
               walk(sub, muted, out, order, depth + 1);
            }
            continue;
         }
         if (!(item instanceof xJo track)) {
            continue;
         }
         int index = order[0]++;
         Object timeline = track.HVx().iAj();
         if (!(timeline instanceof PSn clips)) {
            continue;
         }
         for (Object c : clips.cfM()) {
            if (c instanceof Nbl clip) {
               Span sp = span(clip, index, muted);
               if (sp != null) {
                  out.add(sp);
               }
            }
         }
      }
   }

   /** Whether a track or group is muted: WSA.tp2() is its channel, uva().lYc().iCS() its mute (as its header shows it). */
   static boolean muted(Object trackOrGroup) {
      try {
         if (trackOrGroup instanceof WSA w) {
            iKU channel = w.tp2();
            return channel != null && channel.uva().lYc().iCS().getBoolValue();
         }
      } catch (Throwable t) {
         if (!mutedFailed) {
            mutedFailed = true;
            Video.log("Video: reading a track's mute", t);
         }
      }
      return false;
   }

   private static boolean mutedFailed;

   /** The clip as a span of its video, or null if it doesn't play a video's sound. */
   static Span span(Nbl clip) {
      return span(clip, -1, false);
   }

   private static Span span(Nbl clip, int track, boolean muted) {
      gIs content = clip.z();
      if (content == null) {
         return null;
      }
      zmK events = content.sut();
      if (events == null) {
         return null;
      }
      for (Object e : events.cfM()) {
         if (e instanceof ocL ev) {
            Video.Link link = Video.linkForSound(fileOf(ev));
            if (link != null) {
               double start = clip.x3t();
               return new Span(link, track, muted, start, start + clip.nfq(), clip.dC_(), ev.x3t(), ev.ODA());
            }
         }
      }
      return null;
   }

   private static File fileOf(ocL ev) {
      qjn f = ev.Scv();
      return f == null || f.uWE() == null ? null : f.uWE().IwV();
   }



   /** Whether one of this track's arranger clips plays a video's sound. UI thread. */
   static boolean trackHasVideo(xJo track) {
      if (track == null || !Video.hasLinks()) {
         return false;
      }
      try {
         if (track.HVx().iAj() instanceof PSn clips) {
            for (Object c : clips.cfM()) {
               if (c instanceof Nbl clip && span(clip) != null) {
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
