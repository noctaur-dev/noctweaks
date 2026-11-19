package io.github.noctaur.expression;

import bitwig.obf.*;
/**
 * NocTweaks: the always-visible edit mode strip (see NocModes).
 *
 * Bitwig's own segmented option widget, plus one thing: clicking a mode also switches the hold
 * button on (as choosing that mode from its menu does), so the mode is active straight away; clicking
 * the mode that's already on switches it off again.
 */
public class NocModeStrip extends dDc {
   private dCa button;

   static NocModeStrip create(dCa button) {
      NocModeStrip s = new NocModeStrip();
      s.button = button;
      s.PJ3(false);   // vertical
      s.I_();
      return s;
   }

   /** A button was clicked (Heb: the user picks a choice; iMb, which calls it, can't be overridden from Java). */
   @Override
   public void Heb(Object choice) {
      try {
         // Clicking the mode that's already on switches it off again (like the hold button).
         if (button instanceof dDG n && choice != null && n.mm2() && choice.equals(n.vYz())) {
            button.y3f(false);
            af((Object)null);
            return;
         }
      } catch (Throwable t) {
         Expr.log("Edit mode strip: could not switch the mode off", t);
      }
      super.Heb(choice);
      try {
         if (button != null) {
            button.y3f(true);
         }
      } catch (Throwable t) {
         Expr.log("Edit mode strip: could not switch the mode on", t);
      }
   }
}
