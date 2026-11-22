package io.github.noctaur.video;

import bitwig.obf.dpN;   // Bitwig's UI event loop
import com.bitwig.graphics.qc1;

import java.util.List;

/**
 * Frees Bitwig bitmaps a few seconds later, on Bitwig's UI thread. Bitwig's renderer draws on its own thread
 * after the paint that used a bitmap, and drawing a freed bitmap crashes Bitwig, so a bitmap that might still
 * be drawn is never freed at once.
 */
final class Disposal {
   private Disposal() {
   }

   static void later(List<qc1> bitmaps) {
      if (bitmaps == null || bitmaps.isEmpty()) {
         return;
      }
      Thread t = new Thread(() -> {
         try {
            Thread.sleep(3000);
            dpN.TVZ().asyncExec(() -> {
               for (qc1 b : bitmaps) {
                  try {
                     if (b != null) {
                        b.iy_();
                     }
                  } catch (Throwable e) {
                     Video.log("Video: freeing a picture", e);
                  }
               }
            });
         } catch (Throwable e) {   // never let an exception leave the thread
            Video.log("Video: freeing pictures", e);
         }
      }, "noctweaks-video-free");
      t.setDaemon(true);
      t.start();
   }
}
