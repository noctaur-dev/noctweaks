package io.github.noctaur.arabridge;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;

/** The track header's right-click menu (bmy, as in Track Presets): the ARA Bridge submenu. */
public final class AraBridgeHooks {
   private AraBridgeHooks() {
   }

   private static final ThreadLocal<Object[]> ARGS = new ThreadLocal<>();

   @Hook(target = bmy.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.ENTRY, id = "track-menu-args")
   public static void trackMenuArgs(@Arg(0) dKW menu, @Arg(2) big header) {
      ARGS.set(new Object[]{menu, header});
   }

   @Hook(target = bmy.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.BEFORE_RETURN, id = "track-menu")
   public static void trackMenu() {
      Object[] a = ARGS.get();
      ARGS.remove();
      try {
         if (a != null) {
            AraBridge.menu((dKW) a[0], (big) a[1]);
         }
      } catch (Throwable t) {
         AraBridge.log("ARA Bridge: track menu", t);
      }
   }
}
