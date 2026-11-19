package io.github.noctaur.expression;

import bitwig.obf.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleConsumer;

/**
 * NocTweaks: a number NocTweaks owns, shown and edited by Bitwig's own number
 * field (dNo) like any Inspector value. Changing it calls back.
 */
final class NocNumber implements Clo {
   private volatile double value;
   private volatile double min, max;
   private final boolean whole;
   private final DoubleConsumer changed;
   private final List<NVD> listeners = new CopyOnWriteArrayList<>();

   NocNumber(double value, double min, double max, boolean whole, DoubleConsumer changed) {
      this.value = value;
      this.min = min;
      this.max = max;
      this.whole = whole;
      this.changed = changed;
   }

   double get() {
      return value;
   }

   void range(double min, double max) {
      this.min = min;
      this.max = max;
   }

   /** Sets it without calling back (e.g. when the expression changes), and updates the field. */
   void show(double v) {
      double old = value;
      value = clamp(v);
      fire(old);
   }

   private double clamp(double v) {
      if (Double.isNaN(v)) {
         return value;
      }
      v = Math.max(min, Math.min(max, v));
      return whole ? Math.rint(v) : v;
   }

   private void set(double v) {
      double next = clamp(v);
      if (next == value) {
         return;
      }
      double old = value;
      value = next;
      fire(old);
      try {
         if (changed != null) {
            changed.accept(next);
         }
      } catch (Throwable t) {
         Expr.log("Shape panel: a value change failed", t);
      }
   }

   private void fire(double old) {
      MNh event = new MNh(this, old, value);   // Bitwig's value-changed event
      for (NVD l : listeners) {
         try {
            l.eventOccurred(event);
         } catch (Throwable t) {
            Expr.log("Shape panel: a value listener failed", t);
         }
      }
   }

   @Override public Number getMinValue() { return min; }
   @Override public Number getMaxValue() { return max; }
   @Override public int getIntegerValue() { return (int)Math.rint(value); }
   @Override public long getLongValue() { return Math.round(value); }
   @Override public double getDoubleValue() { return value; }
   @Override public void setIntegerValue(int v) { set(v); }
   @Override public void setLongValue(long v) { set(v); }
   @Override public void setFloatValue(float v) { set(v); }
   @Override public void setDoubleValue(double v) { set(v); }
   @Override public Object getValue() { return value; }
   @Override public boolean isSettable() { return true; }
   @Override public void setValue(Object v) {
      if (v instanceof Number n) {
         set(n.doubleValue());
      }
   }
   @Override public boolean isObservable() { return true; }
   @Override public void addListener(NVD l) {
      if (l != null) {
         listeners.add(l);
      }
   }
   @Override public void removeListener(NVD l) { listeners.remove(l); }
}
