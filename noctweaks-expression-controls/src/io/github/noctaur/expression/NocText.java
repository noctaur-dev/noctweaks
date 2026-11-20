package io.github.noctaur.expression;

import bitwig.obf.*;
import java.util.function.Supplier;

/** NocTweaks: a read-only text value whose text is worked out when it's read. */
public final class NocText implements LJt {
   private final Supplier<String> text;
   private final boolean observable;
   private volatile Object last = "";
   private final java.util.List<foz> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

   NocText(Supplier<String> text) {
      this(text, false);
   }

   NocText(Supplier<String> text, boolean observable) {
      this.text = text;
      this.observable = observable;
   }

   /** Tells whoever shows this text that it changed. */
   void changed() {
      Object v = getValue();
      VGL event = new VGL(this, last, v);   // Bitwig's value-changed event
      last = v;
      for (foz l : listeners) {
         try {
            l.eventOccurred(event);
         } catch (Throwable t) {
            Expr.log("Shape panel: a text listener failed", t);
         }
      }
   }

   @Override
   public Object getValue() {
      try {
         String s = text.get();
         return s == null ? "" : s;
      } catch (Throwable t) {
         return "";
      }
   }

   @Override
   public Object get() {
      return getValue();
   }

   @Override
   public boolean isSettable() {
      return false;
   }

   @Override
   public void setValue(Object value) {
   }

   @Override
   public boolean isObservable() {
      return observable;
   }

   @Override
   public void addListener(foz listener) {
      if (observable && listener != null) {
         listeners.add(listener);
      }
   }

   @Override
   public void removeListener(foz listener) {
      listeners.remove(listener);
   }
}
