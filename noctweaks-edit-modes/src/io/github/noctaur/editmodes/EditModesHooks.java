package io.github.noctaur.editmodes;

import bitwig.obf.*;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;

/** The one hook of Editor: Edit Modes. The logic is in NocModes. */
public final class EditModesHooks {
   private EditModesHooks() {
   }

   /** An option button is painted. The editor's edit-mode button gets the strip beside it. */
   @Hook(target = dCm.class, method = "cfM(Lcom/bitwig/graphics/SYo;)V", at = At.ENTRY, id = "edit-modes")
   public static void editModes(@This dCm button) {
      try {
         NocModes.drawn(button);
      } catch (Throwable t) {
         EditModes.log("edit modes", t);
      }
   }
}
