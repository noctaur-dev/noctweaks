package io.github.noctaur.editmodes;

import bml.api.Bml;

/** The mod's handle in Patchbay: its log. */
final class EditModes {
   static final Bml.Mod MOD = Bml.mod("noctweaks-edit-modes");

   private EditModes() {
   }

   static void log(String message, Throwable t) {
      try {
         if (t == null) {
            MOD.log(message);
         } else {
            MOD.log(message, t);
         }
      } catch (Throwable ignored) {
      }
   }
}
