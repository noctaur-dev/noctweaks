package io.github.noctaur.expression;

import bitwig.obf.dNH; // how a number field splits its value into parts (time fields: minutes, seconds, ...)

import java.util.function.DoubleFunction;

/**
 * A number field layout with one part: the whole value as one piece of text ("4", "35 %", "Grow 40 %").
 * Bitwig's Inspector number field otherwise shows and drags its value in time parts (0:12.000).
 */
final class NocLayout implements dNH {
   private final DoubleFunction<String> text;
   private final double step;
   private final String widest;

   NocLayout(DoubleFunction<String> text, double step, String widest) {
      this.text = text;
      this.step = step;
      this.widest = widest.replaceAll("[0-9]", "0");
   }

   /** How many parts. */
   @Override
   public int cfM() {
      return 1;
   }

   /** A part's name. */
   @Override
   public String cfM(int part) {
      return "value";
   }

   /** A part's text for this value. */
   @Override
   public String cfM(double value, int part) {
      try {
         return text.apply(value);
      } catch (Throwable t) {
         return String.valueOf(value);
      }
   }

   /** How much one step of dragging a part changes the value. */
   @Override
   public double MRY(int part) {
      return step;
   }

   /** The widest text a part can show (sizes the field). */
   @Override
   public String Hnt(int part) {
      return widest;
   }
}
