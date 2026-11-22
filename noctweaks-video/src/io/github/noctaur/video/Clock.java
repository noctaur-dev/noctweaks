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

   /**
    * Beats (Bitwig's timeline time) to seconds, through the project's tempo map. QSF.IwV(beats) is
    * beats -> seconds and FzB(seconds) the other way (seen in Bitwig 6.1.7: beat 110 at 110 BPM gave
    * IwV 60.0, FzB 201.67). Call on Bitwig's thread.
    */
   static double beatsToSeconds(g2L doc, double beats) {
      try {
         QSF t = transport(doc);
         double s = t.IwV(beats);
         return s;
      } catch (Throwable e) {
         Video.log("Video: could not convert the drop time to seconds", e);
         return 0;
      }
   }

   static double secondsToBeats(g2L doc, double seconds) {
      try {
         return transport(doc).FzB(seconds);
      } catch (Throwable e) {
         return Double.NaN;
      }
   }
}
