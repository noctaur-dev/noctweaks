package io.github.noctaur.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.master.g2L;

/**
 * Bitwig's transport, read the way Bitwig's own controller API reads it (TransportProxy): a project's
 * transport is g2L.ifN() (QSF); luv() is "playing", tSS() the play position in seconds, wYK() in beats.
 * FzB(double) and IwV(double) convert between beats and seconds through the tempo map; which way each goes
 * is worked out from the current tempo (dEI()) and logged.
 */
final class Clock {
   private Clock() {
   }

   static QSF transport(g2L doc) {
      return doc == null ? null : doc.ifN();
   }

   static boolean playing(g2L doc) {
      try {
         QSF t = transport(doc);
         return t != null && t.luv().getBoolValue();
      } catch (Throwable e) {
         return false;
      }
   }

   static double seconds(g2L doc) {
      try {
         QSF t = transport(doc);
         return t == null ? 0 : t.tSS().getDoubleValue();
      } catch (Throwable e) {
         return 0;
      }
   }

   /** Beats (Bitwig's timeline time) to seconds, through the project's tempo map. Call on Bitwig's thread. */
   static double beatsToSeconds(g2L doc, double beats) {
      try {
         QSF t = transport(doc);
         double a = t.FzB(beats);
         double b = t.IwV(beats);
         double bpm = t.dEI();
         double guess = bpm > 1 && bpm < 2000 ? beats * 60.0 / bpm : Double.NaN;
         Video.log("Video: beats " + beats + " -> FzB " + a + " / IwV " + b + " (tempo " + bpm + ", constant-tempo guess " + guess + ")", null);
         if (!Double.isNaN(guess)) {
            return Math.abs(a - guess) <= Math.abs(b - guess) ? a : b;
         }
         return a;
      } catch (Throwable e) {
         Video.log("Video: could not convert the drop time to seconds", e);
         return 0;
      }
   }
}
