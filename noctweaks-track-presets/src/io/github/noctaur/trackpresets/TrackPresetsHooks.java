package io.github.noctaur.trackpresets;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.document.core.master.IbI;                          // what a dropped browser item is added to
import com.bitwig.flt.document.core.master.zwV;                         // what a dropped browser item becomes
import com.bitwig.flt.document.core.master.g2L;                          // a loaded project (a dropped preset)
import com.bitwig.base.io.z2y;                                          // a library file (path + sub-item)

import java.util.List;

/**
 * Every hook of Track Presets. The logic is in NocTracks.
 *
 * Some hooks share a method with the Browser mod (the sidebar button's label, the browser's menu): each mod
 * handles only its own button, so they work alone or together.
 */
public final class TrackPresetsHooks {
   private TrackPresetsHooks() {
   }

   private static final ThreadLocal<Object> NODE = new ThreadLocal<>();     // entry -> return, one method at a time
   private static final ThreadLocal<Object[]> ARGS = new ThreadLocal<>();

   private static Object take() {
      Object o = NODE.get();
      NODE.remove();
      return o;
   }

   // ================================================================== the Tracks button

   /** The list of browser sources (sidebar buttons); its constructor adds Bitwig's. Add Tracks after them. */
   @Hook(target = Vv.class, method = "<init>", at = At.BEFORE_RETURN, id = "sources")
   public static void sources(@This Vv list) {
      try {
         list.cfM(NocTracks.source()); // add a source
      } catch (Throwable t) {
         TrackPresets.log("could not add the Tracks button", t);
      }
   }

   /** A sidebar button's label (Bitwig has no translation for ours). */
   @Hook(target = xJ.class, method = "cfM(LWyZ;)Ljava/lang/String;", at = At.ENTRY, id = "label-entry")
   public static void labelEntry(@This xJ button) {
      NODE.set(button);
   }

   @Hook(target = xJ.class, method = "cfM(LWyZ;)Ljava/lang/String;", at = At.WRAP_RETURN, id = "label")
   public static String label(@Return String bitwig) {
      try {
         Object b = take();
         return b instanceof xJ g && g.Hnt == NocTracks.SOURCE ? "Tracks" : bitwig; // Hnt: the button's source
      } catch (Throwable t) {
         return bitwig;
      }
   }

   /** The sidebar's pinned buttons (quick sources) of one browser. */
   private static final ThreadLocal<Object> PIN_OWNER = new ThreadLocal<>();

   @Hook(target = cBh.class, method = "lYc()LYO;", at = At.ENTRY, id = "pin-entry")
   public static void pinEntry(@This cBh browser) {
      PIN_OWNER.set(browser);
   }

   /** Tracks goes on the main browser's sidebar only (cBj); the popup browsers keep their own buttons. */
   @Hook(target = cBh.class, method = "lYc()LYO;", at = At.WRAP_RETURN, id = "pin")
   public static Object pin(@Return YO pinned) {
      Object owner = PIN_OWNER.get();
      PIN_OWNER.remove();
      NocTracks.ensurePinned(pinned, owner instanceof cBj);
      return pinned;
   }

   /** A browser session switches to another sidebar button: Tracks opens on its Location column. */
   @Hook(target = Jp.class, method = "cfM(LWl;Z)V", at = At.BEFORE_RETURN, id = "source-chosen")
   public static void sourceChosen(@This Jp session) {
      NocTracks.sourceChosen(session);
   }

   /** The browser's right-click menu: Show Tracks Button. */
   @Hook(target = cFm.class, method = "cfM(LdKW;Ljava/util/List;)V", at = At.ENTRY, id = "browser-menu-args")
   public static void browserMenuArgs(@Arg(0) dKW menu) {
      NODE.set(menu);
   }

   @Hook(target = cFm.class, method = "cfM(LdKW;Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "browser-menu")
   public static void browserMenu() {
      Object m = take();
      try {
         if (m instanceof dKW menu) {
            NocTracks.browserMenu(menu);
         }
      } catch (Throwable t) {
         TrackPresets.log("browser menu", t);
      }
   }

   /** The browser's final query: the projects the main browser now lists are kept to track presets. */
   @Hook(target = Jp.class, method = "tSS()LM22;", at = At.WRAP_RETURN, id = "presets-only")
   public static Object presetsOnly(@Return M22 query) {
      try {
         return NocTracks.presetsOnly(query);
      } catch (Throwable t) {
         return query;
      }
   }

   // ================================================================== track presets

   /** bju: a track view widget made for a track view model. */
   @Hook(target = big.class, method = "<init>", at = At.BEFORE_RETURN, id = "track-view")
   public static void trackView(@Arg(0) ICh view) {
      try {
         NocTracks.view(view);
      } catch (Throwable t) {
         TrackPresets.log("track view", t);
      }
   }

   /**
    * ra.Gef(): which result kinds a browser lists (a fixed set made in its constructor). The main browser (cCH)
    * also gets projects, so the Tracks button can list track presets (NocFormats keeps only those).
    */
   @Hook(target = qH.class, method = "MRY()Ljava/util/Set;", at = At.ENTRY, id = "browser-kinds-entry")
   public static void browserKindsEntry(@This qH browser) {
      NODE.set(browser);
   }

   @Hook(target = qH.class, method = "MRY()Ljava/util/Set;", at = At.WRAP_RETURN, id = "browser-kinds")
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static Object browserKinds(@Return java.util.Set kinds) {
      Object b = take();
      if (!(b instanceof cBj) || kinds == null) {
         return kinds;
      }
      try {
         qD[] now = (qD[]) kinds.toArray(new qD[0]);
         qD[] want = NocTracks.kinds(now);
         if (want.length == now.length) {
            return kinds;
         }
         return KINDS.computeIfAbsent(kinds, k -> {
            java.util.Set out = new java.util.HashSet(k);
            out.addAll(java.util.Arrays.asList(want));
            return java.util.Collections.unmodifiableSet(out);
         });
      } catch (Throwable t) {
         TrackPresets.log("track presets: browser kinds", t);
         return kinds;
      }
   }

   private static final java.util.Map<java.util.Set, java.util.Set> KINDS =
      java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

   /** bnG.beI(dMt, dwF, bju, yAn): the track header right-click menu. */
   @Hook(target = bmy.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.ENTRY, id = "track-menu-args")
   public static void trackMenuArgs(@Arg(0) dKW menu, @Arg(1) dve owner, @Arg(2) big header) {
      ARGS.set(new Object[]{menu, owner, header});
   }

   @Hook(target = bmy.class, method = "cfM(LdKW;Ldve;Lbig;LkbG;)V", at = At.BEFORE_RETURN, id = "track-menu")
   public static void trackMenu() {
      Object[] a = ARGS.get();
      ARGS.remove();
      try {
         if (a != null) {
            NocTracks.menu((dKW) a[0], (dve) a[1], (big) a[2]);
         }
      } catch (Throwable t) {
         TrackPresets.log("track menu", t);
      }
   }

   /** baQ.beI(dMt, double, double, eqv): the arranger's background menu. */
   @Hook(target = aZy.class, method = "cfM(LdKW;DDLeoV;)V", at = At.ENTRY, id = "area-menu-args")
   public static void areaMenuArgs(@Arg(0) dKW menu) {
      NODE.set(menu);
   }

   @Hook(target = aZy.class, method = "cfM(LdKW;DDLeoV;)V", at = At.BEFORE_RETURN, id = "area-menu")
   public static void areaMenu(@This aZy area) {
      Object m = take();
      try {
         if (m instanceof dKW menu) {
            NocTracks.areaMenu(menu, area.BBT()); // vlg: its track view
         }
      } catch (Throwable t) {
         TrackPresets.log("area menu", t);
      }
   }

   /** baN.beI(dwF, dMt): the arranger's right-click menu (empty space and lanes). */
   @Hook(target = aZv.class, method = "cfM(Ldve;LdKW;)V", at = At.ENTRY, id = "arranger-menu-args")
   public static void arrangerMenuArgs(@Arg(1) dKW menu) {
      NODE.set(menu);
   }

   @Hook(target = aZv.class, method = "cfM(Ldve;LdKW;)V", at = At.BEFORE_RETURN, id = "arranger-menu")
   public static void arrangerMenu(@This aZv arranger) {
      Object m = take();
      try {
         if (m instanceof dKW menu) {
            NocTracks.arrangerMenu(menu, arranger.gfw()); // dNa: its project
         }
      } catch (Throwable t) {
         TrackPresets.log("arranger menu", t);
      }
   }

   /** aNi.beI(dwF, dMt): the arranger timeline's right-click menu. */
   @Hook(target = aMh.class, method = "cfM(Ldve;LdKW;)V", at = At.ENTRY, id = "lane-menu-args")
   public static void laneMenuArgs(@Arg(1) dKW menu) {
      NODE.set(menu);
   }

   @Hook(target = aMh.class, method = "cfM(Ldve;LdKW;)V", at = At.BEFORE_RETURN, id = "lane-menu")
   public static void laneMenu(@This aMh timeline) {
      Object m = take();
      try {
         if (m instanceof dKW menu) {
            NocTracks.laneMenu(menu, timeline);
         }
      } catch (Throwable t) {
         TrackPresets.log("lane menu", t);
      }
   }

   /** bac.beI(dwF, dMt): a panel's background menu; ours goes right after "Add Group Track" (alv.Gef). */
   @Hook(target = aYH.class, method = "cfM(Ldve;LdKW;)V", at = At.REPLACE_CALL, callOwner = akw.class, call = "MRY(LdKW;LaBW;Lakn;)V", id = "panel-menu")
   public static void panelMenu(akw items, dKW menu, aBW panel, akn where) {
      items.MRY(menu, panel, where); // Bitwig's own items
      try {
         NocTracks.panelMenu(menu, panel);
      } catch (Throwable t) {
         TrackPresets.log("panel menu", t);
      }
   }

   /** rmj.Gef(Object, Awj): a dropped browser item; a one-track preset adds that track, not a "Project" group. */
   @Hook(target = DPq.class, method = "MRY(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/IbI;)Lcom/bitwig/flt/document/core/master/zwV;",
         at = At.ENTRY, id = "drop-args")
   public static void dropArgs(@Arg(0) Object item, @Arg(1) IbI target) {
      DROP.set(new Object[]{item, target});
      try {
         TrackPresets.log("Track presets: drop reached (" + (item == null ? "null" : item.getClass().getName()) + ", "
            + (target == null ? "no file" : String.valueOf(target.cfM())) + ")", null);
      } catch (Throwable ignored) {
      }
   }

   private static final ThreadLocal<Object[]> DROP = new ThreadLocal<>();

   /**
    * The file's sub-item (Lpf.MgQ(): which track of the project, as an index; empty = the whole project as a
    * group). A one-track preset with no sub-item reads as "0", so Bitwig's own code adds just that track.
    * (WRAP_RETURN below only sees the method's last return, not the "whole project" one.)
    */
   @Hook(target = DPq.class, method = "MRY(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/IbI;)Lcom/bitwig/flt/document/core/master/zwV;",
         at = At.REPLACE_CALL, callOwner = z2y.class, call = "FzB()Ljava/lang/String;", id = "drop-track-index")
   public static String dropTrackIndex(z2y file) {
      String sub = file.FzB();
      try {
         Object[] a = DROP.get();
         if ((sub == null || sub.isEmpty()) && a != null && a[0] instanceof g2L doc) {
            String index = NocTracks.dropIndex(doc, file);
            if (index != null) {
               return index;
            }
         }
      } catch (Throwable t) {
         TrackPresets.log("Track presets: drop index", t);
      }
      return sub;
   }

   @Hook(target = DPq.class, method = "MRY(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/IbI;)Lcom/bitwig/flt/document/core/master/zwV;",
         at = At.WRAP_RETURN, id = "drop-track")
   public static Object dropTrack(@Return zwV result) {
      Object[] a = DROP.get();
      DROP.remove();
      if (a == null) {
         TrackPresets.log("Track presets: drop hook ran without its arguments", null);
      }
      try {
         if (a != null) {
            Object mine = NocTracks.dropped(result, a[0], a[1]);
            if (mine instanceof zwV s) {
               return s;
            }
         }
      } catch (Throwable t) {
         TrackPresets.log("drop", t);
      }
      return result;
   }
}
