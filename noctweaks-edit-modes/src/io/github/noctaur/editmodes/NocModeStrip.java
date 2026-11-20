package io.github.noctaur.editmodes;

import bitwig.obf.*;
/**
 * NocTweaks: the always-visible edit mode strip (see NocModes).
 *
 * Bitwig's own segmented option widget, plus one thing: clicking a mode also switches the hold
 * button on (as choosing that mode from its menu does), so the mode is active straight away; clicking
 * the mode that's already on switches it off again.
 */
public class NocModeStrip extends dBE {
   private dAE button;

   static NocModeStrip create(dAE button) {
      NocModeStrip s = new NocModeStrip();
      s.button = button;
      s.iHb(false);   // vertical
      s.H_();
      return s;
   }

   /** A button was clicked (Heb: the user picks a choice; iMb, which calls it, can't be overridden from Java). */
   @Override
   public void TtY(Object choice) {
      try {
         // Clicking the mode that's already on switches it off again (like the hold button).
         if (button instanceof dCm n && choice != null && n.wuu() && choice.equals(n.urM())) {
            button.uWE(false);
            vpw((Object)null);
            return;
         }
      } catch (Throwable t) {
         EditModes.log("Edit mode strip: could not switch the mode off", t);
      }
      super.TtY(choice);
      try {
         if (button != null) {
            button.uWE(true);
         }
      } catch (Throwable t) {
         EditModes.log("Edit mode strip: could not switch the mode on", t);
      }
   }
}
