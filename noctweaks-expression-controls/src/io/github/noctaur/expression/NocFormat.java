package io.github.noctaur.expression;

import bitwig.obf.*;
import java.util.function.DoubleFunction;

/** NocTweaks: how a NocNumber reads in its field ("4 st", "35 %", "Grow 50 %"). */
final class NocFormat implements dPX {
   private final DoubleFunction<String> text;

   NocFormat(DoubleFunction<String> text) {
      this.text = text;
   }

   @Override
   public String beI(double v) {
      try {
         return text.apply(v);
      } catch (Throwable t) {
         return String.valueOf(v);
      }
   }

   @Override
   public String beI(double v, boolean a, boolean b) {
      return beI(v);
   }

   /** Typed-in text: the first number in it. */
   @Override
   public double beI(String s, double fallback) {
      try {
         java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?|-?\\.\\d+").matcher(s);
         if (m.find()) {
            double v = Double.parseDouble(m.group());
            String low = s.toLowerCase(java.util.Locale.ROOT);
            if (low.contains("fade") || low.contains("<") || low.contains("start")) {
               v = -Math.abs(v);
            }
            return v;
         }
      } catch (Throwable ignored) {
      }
      return fallback;
   }
}
