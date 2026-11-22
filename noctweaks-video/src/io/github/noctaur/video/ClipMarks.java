package io.github.noctaur.video;

import bitwig.obf.Nbl;   // an audio clip
import bitwig.obf.bpa;   // a timeline painter
import bitwig.obf.dve;   // a widget
import bitwig.obf.iok;   // a colour
import com.bitwig.base.geom.DoubleRectangle;
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.graphics.CHR;
import com.bitwig.graphics.SYo;
import com.bitwig.graphics.qc1;
import com.bitwig.graphics.sal;

/**
 * Marks the clips that play a video's sound, so they read as video in the arranger: a film strip across the
 * top of the clip, showing the pictures at those points of the video (they follow the clip when it's moved,
 * cut or slipped, as the panel does). Clips too short for pictures get a film edge along their bottom.
 *
 * Drawn after Bitwig paints the audio clip (see VideoHooks.clipPainted), in the clip's own rectangle.
 */
final class ClipMarks {
   private ClipMarks() {
   }

   private static final int[] FILM = { 16, 16, 16 };
   private static final int[] HOLE = { 92, 92, 92 };
   private static final int[] WAITING = { 44, 44, 44 };
   private static final double HEADER = 14;   // the clip's name bar (only for the fallback geometry)

   // bitmaps made per stretch of painting (making one waits for Bitwig's renderer, so only a few at a time)
   private static boolean logged;
   private static long budgetAt;
   private static final int[] BUDGET = { 0 };

   private static iok colour(int[] rgb, double alpha) {
      return iok.cfM(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, (float) Math.max(0, Math.min(1, alpha)));
   }

   /**
    * Draws the strip in the band from bandY, bandH high (from VideoHooks: the top of the waveform's area,
    * freed for it when separate, or over the waveform), at alpha. With bandY NaN the band is worked out from the
    * clip's whole rectangle, as before there was a choice (a fallback for painters other than the waveform's).
    *
    * The clip's rectangle as Bitwig hands it to the painter is cut to the part being painted (when the
    * playhead moves, only a thin column is), so the strip's pictures are placed from the clip's start and
    * end on the timeline instead (timeline.MRY: a time to x), and only drawn where the rectangle is.
    */
   static void paint(SYo g, bpa timeline, dve widget, DoubleRectangle r, Nbl clip, double bandY, double bandH, double alpha) {
      if (g == null || r == null || !Video.hasLinks() || alpha <= 0.001) {
         return;
      }
      Follow.Span span = Follow.span(clip);
      if (span == null) {
         return;   // not a video clip
      }
      FilmStrip.paintedIn(widget);
      Video.Link link = span.link();
      double x0 = timeline.MRY(span.start()), x1 = timeline.MRY(span.end()), w = x1 - x0;
      if (Double.isNaN(bandY)) {
         double y0 = r.FzB(), h = r.UNj();
         boolean big = h >= 44;
         bandH = big ? Math.min(58, Math.floor((h - HEADER) * 0.6)) : 6;
         bandY = big ? y0 + HEADER : y0 + h - bandH;
      }
      if (!logged) {   // once, for testing
         logged = true;
         Video.log(String.format(java.util.Locale.ROOT, "Video: film strip at x %.0f, y %.0f (%.0f x %.0f), %s, opacity %.2f",
            x0, bandY, w, bandH, FilmStrip.separate() ? "separate" : "over the waveform", alpha), null);
      }
      if (w < 6 || bandH < 2) {
         return;
      }
      double left = Math.max(x0, r.Hnt()), right = Math.min(x1, r.Hnt() + r.IwV());   // the part being painted
      if (right <= left) {
         return;
      }

      boolean pictures = bandH >= 16;
      g.qoU();
      g.nVd();
      g.MRY(left, bandY, right - left, bandH);
      g.Hnt(colour(FILM, alpha));
      g.wGc();
      g.ucQ();

      double hole = pictures ? 2 : 1.5, pad = pictures ? (bandH >= 30 ? 4 : 3) : 0;
      if (pictures) {
         pictures(g, widget, link, clip, span, x0, w, left, right, bandY + pad, bandH - 2 * pad, alpha);
      }

      // sprocket holes along the strip's edges
      g.qoU();
      g.nVd();
      double start = x0 + 2 + Math.max(0, Math.floor((left - x0) / 7) * 7);
      for (double x = start; x < right - 3; x += 7) {
         g.MRY(x, bandY + 1, 3, hole);
         if (pictures) {
            g.MRY(x, bandY + bandH - 1 - hole, 3, hole);
         }
      }
      g.Hnt(colour(HOLE, alpha));
      g.wGc();
      g.ucQ();
   }

   private static void pictures(SYo g, dve widget, Video.Link link, Nbl clip, Follow.Span span,
                                double x0, double w, double left, double right, double y, double th, double alpha) {
      if (th < 8) {
         return;
      }
      Thumbs thumbs = Thumbs.of(link.video());
      if (thumbs == null) {
         return;   // the video's size isn't known yet: the next paint tries again
      }
      thumbs.paintedIn(widget);
      g2L doc = clip.oQj() instanceof g2L d ? d : Video.project();
      if (doc == null) {
         return;
      }
      long now = System.currentTimeMillis();
      if (now - budgetAt > 40) {
         budgetAt = now;
         BUDGET[0] = 6;
      }
      sal gs = widget == null ? null : widget.afn();
      double clipStart = span.start(), clipLen = span.end() - span.start();
      double scale = th / thumbs.height;
      double tw = Math.floor(thumbs.width * scale), slot = tw + 2;
      // every picture that reaches into the part being painted (Bitwig clips the drawing to it) and starts
      // inside the clip; the last one is cut off at the clip's end, so the strip runs to the end with no gap
      int first = (int) Math.max(0, Math.floor((left - x0 - 1 - tw) / slot) + 1);
      int last = (int) Math.min(Math.floor((w - 2) / slot), Math.floor((right - x0 - 1) / slot));
      double end = x0 + w - 1;   // where the pictures stop
      boolean waiting = false;
      for (int k = first, n = 0; k <= last && n < 400; k++, n++) {
         double sx = x0 + 1 + k * slot;
         double vw = Math.min(tw, end - sx);   // the part of this picture that's shown
         if (vw < 1) {
            continue;
         }
         double beat = clipStart + (sx + vw / 2 - x0) / w * clipLen;   // the middle of what's shown
         double t = Follow.timeAt(doc, span, beat);
         if (t < 0) {
            continue;   // before the video starts
         }
         int index = thumbs.index(t);
         if (thumbs.finished() && index >= thumbs.count() && thumbs.count() > 0) {
            index = thumbs.count() - 1;   // the very end of the video: its last picture
         }
         qc1 b = thumbs.bitmap(index, gs, BUDGET);
         if (b == null) {
            if (!thumbs.finished() || index < thumbs.count()) {
               waiting = true;
               g.qoU();
               g.nVd();
               g.MRY(sx, y, vw, th);
               g.Hnt(colour(WAITING, alpha));
               g.wGc();
               g.ucQ();
            }
            continue;
         }
         g.qoU();
         if (vw < tw) {   // cut off at the clip's end
            g.nVd();
            g.MRY(sx, y, vw, th);
            g.lYc();   // clip to that rectangle
         }
         g.FzB(sx, y);
         g.Hnt(scale, scale);
         if (alpha >= 0.999) {
            b.cfM(g, 0, 0, CHR.IwV);   // as Bitwig's image widget draws (see VideoPanel)
         } else {
            g.cfM(b, 0, 0, alpha);     // Bitwig's "paint offscreen image with alpha"
         }
         g.ucQ();
      }
      if (waiting) {
         thumbs.repaintSoon();
      }
   }
}
