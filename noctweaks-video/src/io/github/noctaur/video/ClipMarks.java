package io.github.noctaur.video;

import bitwig.obf.Nbl;   // an audio clip
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

   private static final iok FILM = iok.cfM(16, 16, 16);
   private static final iok HOLE = iok.cfM(92, 92, 92);
   private static final iok WAITING = iok.cfM(44, 44, 44);
   private static final double HEADER = 14;   // the clip's name bar

   // bitmaps made per stretch of painting (making one waits for Bitwig's renderer, so only a few at a time)
   private static long budgetAt;
   private static final int[] BUDGET = { 0 };

   static void paint(SYo g, dve widget, DoubleRectangle r, Nbl clip) {
      Video.Link link = Video.link();
      if (link == null || link.sound() == null || g == null || r == null) {
         return;
      }
      Double soundStart = Follow.soundStart(clip, link.sound());
      if (soundStart == null) {
         return;   // not a video clip
      }
      double x0 = r.Hnt(), y0 = r.FzB(), w = r.IwV(), h = r.UNj();
      if (w < 6 || h < 6) {
         return;
      }
      double right = widget == null ? x0 + w : Math.min(x0 + w, widget.Sxo());
      double left = Math.max(x0, 0);
      if (right <= left) {
         return;
      }

      boolean pictures = h >= 44;
      double bandH = pictures ? Math.min(58, Math.floor((h - HEADER) * 0.6)) : 6;
      double bandY = pictures ? y0 + HEADER : y0 + h - bandH;

      g.qoU();
      g.nVd();
      g.MRY(left, bandY, right - left, bandH);
      g.Hnt(FILM);
      g.wGc();
      g.ucQ();

      double hole = pictures ? 2 : 1.5, pad = pictures ? 4 : 0;
      if (pictures) {
         pictures(g, widget, link, clip, soundStart, x0, w, left, right, bandY + pad, bandH - 2 * pad);
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
      g.Hnt(HOLE);
      g.wGc();
      g.ucQ();
   }

   private static void pictures(SYo g, dve widget, Video.Link link, Nbl clip, double soundStart,
                                double x0, double w, double left, double right, double y, double th) {
      if (th < 10) {
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
      double clipStart = clip.x3t(), clipLen = clip.QWG();
      double zero = Clock.beatsToSeconds(doc, clipStart + soundStart);   // where the video's 0 s is
      double scale = th / thumbs.height;
      double tw = Math.floor(thumbs.width * scale), slot = tw + 2;
      int first = (int) Math.max(0, Math.floor((left - x0 - 1) / slot));
      int last = (int) Math.floor((Math.min(right, x0 + w) - x0 - 1 - tw) / slot);
      boolean waiting = false;
      for (int k = first, n = 0; k <= last && n < 400; k++, n++) {
         double sx = x0 + 1 + k * slot;
         double beat = clipStart + (sx + tw / 2 - x0) / w * clipLen;
         double t = Clock.beatsToSeconds(doc, beat) - zero;
         if (t < 0) {
            continue;   // before the video starts
         }
         qc1 b = thumbs.bitmap(thumbs.index(t), gs, BUDGET);
         if (b == null) {
            if (!thumbs.finished() || thumbs.index(t) < thumbs.count()) {
               waiting = true;
               g.qoU();
               g.nVd();
               g.MRY(sx, y, tw, th);
               g.Hnt(WAITING);
               g.wGc();
               g.ucQ();
            }
            continue;
         }
         g.qoU();
         g.FzB(sx, y);
         g.Hnt(scale, scale);
         b.cfM(g, 0, 0, CHR.IwV);   // as Bitwig's image widget draws (see VideoPanel)
         g.ucQ();
      }
      if (waiting) {
         thumbs.repaintSoon();
      }
   }
}
