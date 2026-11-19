package io.github.noctaur.expression;

import bml.api.Bml;

/** The mod's handle in BML: its log and its files. */
final class Expr {
   static final Bml.Mod MOD = Bml.mod("noctweaks-expression-controls");

   private Expr() {
   }

   static void log(String message, Throwable t) {
      if (t == null) {
         MOD.log(message);
      } else {
         MOD.log(message, t);
      }
   }
}
