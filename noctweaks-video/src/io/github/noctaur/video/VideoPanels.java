package io.github.noctaur.video;

import bitwig.obf.aBW;
import bml.panels.PanelProvider;
import com.bitwig.flt.document.core.master.g2L;
import bml.panels.Panels;

/**
 * The Video panel: a button in the bottom bar (next to Bitwig's own panel buttons) and the picture above it,
 * with a Detach button that shows it in a window of its own too.
 */
public final class VideoPanels implements PanelProvider {
   @Override
   public void declare(Panels panels) {
      panels.view("video", "Video")
            .icon("video_icon")
            .content(context -> {
               try {   // the window's project, so the panel follows its transport from the start
                  if (context.window() instanceof aBW view && view.mtS() instanceof g2L doc) {
                     Video.seen(doc);
                  }
               } catch (Throwable t) {
                  Video.log("Video: panel's project", t);
               }
               return Detach.panelContent();   // the Detach bar, then the picture
            });
   }
}
