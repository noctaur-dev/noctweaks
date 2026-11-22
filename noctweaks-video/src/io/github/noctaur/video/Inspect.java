package io.github.noctaur.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.flt.document.core.master.qjn;   // an audio file in the project
import com.bitwig.flt.document.core.master.ruS;   // a project's tracks

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A one-off diagnostic for linking the video to clip edits: writes what Bitwig holds for the arranger clips
 * at the playhead (every number each clip and audio event has, by Bitwig's internal names) to
 * tools/describe.txt and the log. Bitwig's clip data has no names to read, so this is how the right fields
 * (clip start, length, offset into the file) are found once; the mod then reads only those.
 *
 * Walk (from Bitwig's own test classes): track xJo.HVx() -> Tsu.iAj() (arranger clip timeline, a PSn) ->
 * clips; an audio clip TWN -> Nbl.z() (gIs, its audio events) -> ocL -> Scv() (qjn, the file) -> uWE().IwV().
 */
final class Inspect {
   private Inspect() {
   }

   static void describe(g2L doc) {
      StringBuilder out = new StringBuilder();
      try {
         double secs = Clock.seconds(doc);
         double beats = Clock.secondsToBeats(doc, secs);
         out.append(String.format(Locale.ROOT, "playhead %.6f s = %.6f beats%n", secs, beats));
         Video.Link l = Video.link();
         out.append("linked video: ").append(l == null ? "none" : l.video() + " / " + l.sound()).append('\n');
         ruS tracks = doc.spd();
         int n = tracks == null ? 0 : tracks.ugU();
         for (int i = 0; i < n; i++) {
            Object t = tracks.FzB(i);
            out.append("\ntrack ").append(i).append(' ').append(t.getClass().getName()).append('\n');
            if (!(t instanceof xJo track)) {
               continue;
            }
            Object timeline = track.HVx().iAj();
            if (!(timeline instanceof PSn clips)) {
               out.append("  clip timeline: ").append(timeline == null ? "none" : timeline.getClass().getName()).append('\n');
               continue;
            }
            List<?> list = clips.cfM();
            out.append("  clips: ").append(list.size()).append('\n');
            int shown = 0;
            for (Object c : list) {
               if (shown++ >= 6) {
                  out.append("  ...\n");
                  break;
               }
               out.append("  clip ").append(c.getClass().getName()).append(": ").append(numbers(c)).append('\n');
               if (c instanceof Nbl audioClip) {
                  Object content = audioClip.z();
                  out.append("    content ").append(content == null ? "none" : content.getClass().getName())
                     .append(": ").append(numbers(content)).append('\n');
                  events(content, out, "    ");
               }
            }
         }
      } catch (Throwable t) {
         out.append("\nstopped: ").append(t);
         Video.log("Video: describe", t);
      }
      Video.log("Video: clips at the playhead\n" + out, null);
      try {
         File dir = Tools.toolsFolder();
         if (dir != null) {
            dir.mkdirs();
            Files.writeString(new File(dir, "describe.txt").toPath(), out.toString(), StandardCharsets.UTF_8);
         }
      } catch (Throwable t) {
         Video.log("Video: could not write describe.txt", t);
      }
   }

   /** The audio events of a clip's content: gIs may be a timeline itself, or hold lanes (takes) of them. */
   private static void events(Object content, StringBuilder out, String indent) {
      List<?> evs = null;
      if (content instanceof PSn p) {
         evs = p.cfM();
      } else if (content instanceof gIs lanes) {
         try {
            Object lane = lanes.Hnt(0);
            out.append(indent).append("lane 0 ").append(lane == null ? "none" : lane.getClass().getName())
               .append(": ").append(numbers(lane)).append('\n');
            if (lane instanceof PSn p) {
               evs = p.cfM();
            }
         } catch (Throwable t) {
            out.append(indent).append("lane 0: ").append(t).append('\n');
         }
      }
      if (evs == null) {
         out.append(indent).append("no event list found\n");
         return;
      }
      out.append(indent).append("events: ").append(evs.size()).append('\n');
      int shown = 0;
      for (Object e : evs) {
         if (shown++ >= 6) {
            break;
         }
         out.append(indent).append("event ").append(e.getClass().getName()).append(": ").append(numbers(e)).append('\n');
         if (e instanceof ocL audio) {
            try {
               qjn file = audio.Scv();
               out.append(indent).append("  file ").append(file == null ? "none" : file.uWE().IwV())
                  .append(": ").append(numbers(file)).append('\n');
            } catch (Throwable t) {
               out.append(indent).append("  file: ").append(t).append('\n');
            }
         }
      }
   }

   /** Every number the object gives without arguments, by name (a diagnostic: names are Bitwig's). */
   private static String numbers(Object o) {
      if (o == null) {
         return "";
      }
      StringBuilder b = new StringBuilder();
      Method[] ms = o.getClass().getMethods();
      Arrays.sort(ms, Comparator.comparing(Method::getName));
      for (Method m : ms) {
         Class<?> r = m.getReturnType();
         if (m.getParameterCount() != 0 || java.lang.reflect.Modifier.isStatic(m.getModifiers())
               || !(r == double.class || r == long.class || r == int.class)   // no booleans: some are actions
               || m.getDeclaringClass() == Object.class || m.getName().equals("hashCode")) {
            continue;
         }
         try {
            m.setAccessible(true);
            Object v = m.invoke(o);
            b.append(m.getName()).append('=').append(v instanceof Double d ? String.format(Locale.ROOT, "%.6g", d) : v).append(' ');
         } catch (Throwable ignored) {
         }
      }
      return b.toString().trim();
   }
}
