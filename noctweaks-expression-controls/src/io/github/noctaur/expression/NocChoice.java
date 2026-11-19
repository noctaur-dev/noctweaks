package io.github.noctaur.expression;

import bitwig.obf.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * NocTweaks: a row of Bitwig's own segmented buttons that isn't bound to a
 * setting; clicking one lights it and calls back (the expression shape panel, see NocShapePanel).
 */
public class NocChoice extends dDc {
   private Consumer<Object> pick;

   static NocChoice create(Object[] options, Function<Object, String> label, Object selected, Consumer<Object> pick) {
      return create(options, label, selected, pick, null);
   }

   /** live: each label is re-read when the given registry is told it changed (labels that depend on the expression). */
   static NocChoice create(Object[] options, Function<Object, String> label, Object selected, Consumer<Object> pick,
         Consumer<NocText> live) {
      return create(options, label, null, selected, pick, live);
   }

   /** icon: an icon name per option (shown instead of the text). */
   static NocChoice create(Object[] options, Function<Object, String> label, Function<Object, String> icon, Object selected,
         Consumer<Object> pick, Consumer<NocText> live) {
      NocChoice s = new NocChoice();
      s.pick = pick;
      s.PJ3(true);   // horizontal
      s.I_();
      s.kwH((Object)options);
      s.beI((dsR)o -> {
         NocText t = new NocText(() -> label.apply(o), live != null);
         if (live != null) {
            live.accept(t);
         }
         return t;
      });
      if (icon != null) {
         s.beI((dsj)o -> new ATz(icon.apply(o)));
         s.Gef(dso.Gef);   // icons only
      } else {
         s.Gef(dso.beI);   // text only
      }
      s.af((Object)selected);
      return s;
   }

   /** A button was clicked (Heb: the user picks a choice; iMb, which calls it, can't be overridden from Java). */
   @Override
   public void Heb(Object choice) {
      try {
         af(choice);
      } catch (Throwable t) {
         Expr.log("Shape panel: could not light a button", t);
      }
      try {
         if (pick != null) {
            pick.accept(choice);
         }
      } catch (Throwable t) {
         Expr.log("Shape panel: a button failed", t);
      }
   }
}
