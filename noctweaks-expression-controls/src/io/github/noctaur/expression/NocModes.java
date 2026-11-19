package io.github.noctaur.expression;

import bitwig.obf.*;
import com.bitwig.ramona.core.EO;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NocTweaks: editor edit modes always visible.
 *
 * Bitwig 6 hides the editor's edit modes (audio: Stretch, Gain, Pan, Transpose, Formant, Onsets;
 * notes: the note expressions) behind a hold-to-open button in the editor's bottom-left corner. That
 * button is a stock option button (dBN) whose choices are those modes. This adds Bitwig's own
 * segmented option widget (dBj) right under it, bound to the same setting, so every mode is one click
 * away; the original button keeps working.
 *
 * Hook (inserted by the build-time patcher):
 *   dBN.IYM(CPO)  -> drawn(button)   the option button is drawn (its choices are known by then)
 */
public final class NocModes {
   private NocModes() {
   }

   private static final Map<Object, Object[]> DONE = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile boolean disabled;
   private static volatile boolean loggedLayout;
   private static int stripsLogged;

   public static void drawn(Object button) {
      try {
         if (disabled || !(button instanceof dDG n)) {
            return;
         }
         Object[] state = DONE.get(n);
         if (state != null && state[0] == Boolean.TRUE) {
            if (state.length > 1 && state[1] instanceof dDc strip) {
               syncHighlight(n, strip);
               NocShapePanel.seen(n);   // the note editor (its expression) for the Inspector's shape panel
            }
            return;   // already handled; the strip follows the same setting by itself
         }
         List<Object> options = new ArrayList<>();
         n.beI((java.util.function.Consumer<Object>)options::add);
         if (options.isEmpty() || !isEditMode(options.get(0))) {
            DONE.put(n, new Object[]{Boolean.TRUE});   // some other option button: leave it alone
            return;
         }
         Object[] mine = {Boolean.TRUE, null};
         DONE.put(n, mine);
         Fx.beI(() -> mine[1] = insertStrip(n, options));
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Edit mode strip disabled", t);
      }
   }

   static boolean isEditMode(Object option) {
      return option instanceof JsH || option instanceof lFl;
   }

   private static dDc insertStrip(dDG n, List<Object> options) {
      try {
         Object up = n.oRH();
         if (!(up instanceof dxu parent)) {
            Expr.log("Edit mode strip: the mode button has no parent", null);
            return null;
         }
         uB1 source = n.IvP("selected_choice");
         if (source == null) {
            Expr.log("Edit mode strip: the mode button isn't bound to a setting", null);
            return null;
         }
         dDc strip = NocModeStrip.create(n);   // vertical; clicking also switches the mode on
         strip.kwH(options.toArray());
         doI.beI((EO)strip.waK(), source);
         Object label = n.sCA;   // the mode button's label
         if (label instanceof dsR r) {
            strip.beI(r);
         }
         Object icon = n.MgQ;    // the mode button's icon
         if (icon instanceof dsj q) {
            strip.beI(q);
         }
         strip.Gef(dso.Gef);   // icons only: text widened the editor's left column and misaligned it
         int at = parent.jn_().indexOf(n);
         parent.BBg(strip, at < 0 ? parent.jn_().size() : at + 1);
         syncHighlight(n, strip);
         if (stripsLogged++ < 6) {
            StringBuilder names = new StringBuilder();
            for (Object o : options) {
               names.append(o).append(' ');
            }
            Expr.log("Edit mode strip modes: " + names + "(button shows " + n.vYz() + ")", null);
         }
         if (!loggedLayout) {
            loggedLayout = true;
            String orientation = "?";
            try {
               if (parent instanceof dXL box) {
                  orientation = String.valueOf(box.HOV());
               }
            } catch (Throwable ignored) {
            }
            Expr.log("Edit mode strip added (" + options.size() + " modes, " + parent.getClass().getName()
               + " " + orientation + ", label " + (label != null) + ", icon " + (icon != null) + ")", null);
         }
         return strip;
      } catch (Throwable t) {
         disabled = true;
         Expr.log("Edit mode strip could not be added", t);
         return null;
      }
   }

   /**
    * While the mode is switched off, no button in the strip is lit (so the strip doesn't look active);
    * when it's on, the strip shows the selected mode. Called whenever the hold button repaints.
    */
   static void syncHighlight(dDG n, dDc strip) {
      try {
         Object want = n.mm2() ? n.vYz() : null;
         Object shown = strip.Iba();
         if (want == null ? shown != null : !want.equals(shown)) {
            strip.af(want);
         }
      } catch (Throwable t) {
         Expr.log("Edit mode strip: highlight sync failed", t);
      }
   }

}
