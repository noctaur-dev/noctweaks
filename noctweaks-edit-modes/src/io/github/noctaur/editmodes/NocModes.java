package io.github.noctaur.editmodes;

import bitwig.obf.*;
import com.bitwig.ramona.core.uzc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * NocTweaks: Edit Modes. The editor's edit modes always visible.
 *
 * Bitwig 6 hides the editor's edit modes (audio: Stretch, Gain, Pan, Transpose, Formant, Onsets;
 * notes: the note expressions) behind a hold-to-open button in the editor's bottom-left corner. That
 * button is a stock option button  whose choices are those modes. This adds Bitwig's own
 * segmented option widget  right under it, bound to the same setting, so every mode is one click
 * away; the original button keeps working.
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
         if (disabled || !(button instanceof dCm n)) {
            return;
         }
         Object[] state = DONE.get(n);
         if (state != null && state[0] == Boolean.TRUE) {
            if (state.length > 1 && state[1] instanceof dBE strip) {
               syncHighlight(n, strip);
            }
            return;   // already handled; the strip follows the same setting by itself
         }
         List<Object> options = new ArrayList<>();
         n.cfM((java.util.function.Consumer<Object>)options::add);
         if (options.isEmpty() || !isEditMode(options.get(0))) {
            DONE.put(n, new Object[]{Boolean.TRUE});   // some other option button: leave it alone
            return;
         }
         Object[] mine = {Boolean.TRUE, null};
         DONE.put(n, mine);
         Fq.cfM(() -> mine[1] = insertStrip(n, options));
      } catch (Throwable t) {
         disabled = true;
         EditModes.log("Edit mode strip disabled", t);
      }
   }

   static boolean isEditMode(Object option) {
      return option instanceof Xx1 || option instanceof hJh;
   }

   private static dBE insertStrip(dCm n, List<Object> options) {
      try {
         Object up = n.RbC();
         if (!(up instanceof dvW parent)) {
            EditModes.log("Edit mode strip: the mode button has no parent", null);
            return null;
         }
         rMe source = n.HL("selected_choice");
         if (source == null) {
            EditModes.log("Edit mode strip: the mode button isn't bound to a setting", null);
            return null;
         }
         dBE strip = NocModeStrip.create(n);   // vertical; clicking also switches the mode on
         strip.ERO(options.toArray());
         dns.cfM((uzc)strip.AU3(), source);
         Object label = n.Hnt;   // the mode button's label
         if (label instanceof dru r) {
            strip.cfM(r);
         }
         Object icon = n.FzB;    // the mode button's icon
         if (icon instanceof dqL q) {
            strip.cfM(q);
         }
         strip.MRY(dqR.MRY);   // icons only: text widened the editor's left column and misaligned it
         int at = parent.jm_().indexOf(n);
         parent.IwV(strip, at < 0 ? parent.jm_().size() : at + 1);
         syncHighlight(n, strip);
         if (stripsLogged++ < 6) {
            StringBuilder names = new StringBuilder();
            for (Object o : options) {
               names.append(o).append(' ');
            }
            EditModes.log("Edit mode strip modes: " + names + "(button shows " + n.urM() + ")", null);
         }
         if (!loggedLayout) {
            loggedLayout = true;
            String orientation = "?";
            try {
               if (parent instanceof dWb box) {
                  orientation = String.valueOf(box.abo());
               }
            } catch (Throwable ignored) {
            }
            EditModes.log("Edit mode strip added (" + options.size() + " modes, " + parent.getClass().getName()
               + " " + orientation + ", label " + (label != null) + ", icon " + (icon != null) + ")", null);
         }
         return strip;
      } catch (Throwable t) {
         disabled = true;
         EditModes.log("Edit mode strip could not be added", t);
         return null;
      }
   }

   /**
    * While the mode is switched off, no button in the strip is lit (so the strip doesn't look active);
    * when it's on, the strip shows the selected mode. Called whenever the hold button repaints.
    */
   static void syncHighlight(dCm n, dBE strip) {
      try {
         Object want = n.wuu() ? n.urM() : null;
         Object shown = strip.xlY();
         if (want == null ? shown != null : !want.equals(shown)) {
            strip.vpw(want);
         }
      } catch (Throwable t) {
         EditModes.log("Edit mode strip: highlight sync failed", t);
      }
   }

}
