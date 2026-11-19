package io.github.noctaur.trackview;

import bitwig.obf.*;
import bml.api.Bml;
import com.bitwig.flt.document.core.master.CmH; // a project document
import com.bitwig.flt.document.core.master.GJy; // a group of tracks (the project's top level is one)
import com.bitwig.flt.document.core.iface.qAF; // something with a name (tracks are)

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Piano Roll: Global Track View. Edit any track in the piano roll without going back to the arranger.
 *
 * <ul>
 * <li>An "Edit Tracks" submenu in the editor's track list right-click menu: every track of the project as
 *     a checkbox; ticking one adds it to the tracks being edited (layered), unticking removes it. It works
 *     through the arranger's own track selection, so everything else behaves as usual.</li>
 * <li>The editor's layer list shows from one track up (Bitwig: from two), so that menu is always there.</li>
 * <li>Layered editing switches on when the editor goes from one track to several.</li>
 * <li>The Bitwig 6 beta's Track List panel beside the editor in the Edit screen, with its footer button.</li>
 * <li>With "edit selected layers only" on, the greyed-out tracks' note expressions are drawn without their
 *     points and can't be hovered or grabbed.</li>
 * </ul>
 */
public final class TrackLayers {
   static final Bml.Mod MOD = Bml.mod("noctweaks-global-track-view");

   private TrackLayers() {
   }

   // ------------------------------------------------------------------ the project's track views

   private static final Set<GRp> VIEWS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
   private static volatile WeakReference<GRp> lastView = new WeakReference<>(null);

   /** A track view (arranger, mixer, ...) was made for a project. */
   static void viewMade(GRp view) {
      if (view != null) {
         VIEWS.add(view);
         lastView = new WeakReference<>(view);
      }
   }

   /** The views, the most recent last. */
   private static List<GRp> views() {
      List<GRp> out;
      synchronized (VIEWS) {
         out = new ArrayList<>(VIEWS);
      }
      GRp last = lastView.get();
      if (last != null) {
         out.remove(last);
         out.add(last);
      }
      return out;
   }

   /** The view whose selection the editor follows: the most recent one with tracks selected, else the most recent one. */
   private static GRp view() {
      List<GRp> views = views();
      GRp fallback = null;
      for (int i = views.size() - 1; i >= 0; i--) {
         GRp v = views.get(i);
         try {
            if (v.kys() == null) { // the view's project document
               continue;
            }
         } catch (Throwable t) {
            continue;
         }
         if (fallback == null) {
            fallback = v;
         }
         if (!selection(v).isEmpty()) {
            return v;
         }
      }
      return fallback;
   }

   // ------------------------------------------------------------------ Edit Tracks menu

   private static volatile boolean menuOff;

   /** The editor's track list built its right-click menu: add "Edit Tracks". */
   static void menu(dMt menu) {
      try {
         if (menuOff || menu == null) {
            return;
         }
         GRp view = view();
         if (view == null) {
            return;
         }
         Object d = view.kys(); // the view's project document
         if (!(d instanceof CmH doc)) {
            return;
         }
         List<Object> tracks = new ArrayList<>();
         collect(doc.svk(), tracks, 0); // svk: the project's top-level group
         if (tracks.isEmpty()) {
            return;
         }
         dMt sub = menu.sCA("Edit Tracks"); // a submenu
         for (Object t : tracks) {
            sub.beI(name(t), new Toggle(view, t)); // a checkbox row
         }
         sub.beI("Edit All Tracks", () -> select(view, new ArrayList<>(tracks))); // a plain row
      } catch (Throwable t) {
         menuOff = true;
         MOD.log("Edit Tracks menu switched off", t);
      }
   }

   /** Every track in the project, groups opened up (groups themselves aren't edited in the piano roll). */
   private static void collect(GJy group, List<Object> out, int depth) {
      if (group == null || depth > 16) {
         return;
      }
      for (int i = 0; i < group.qaW(); i++) { // qaW: number of tracks in the group
         Object t = group.MgQ(i);             // MgQ: the track at an index
         if (t instanceof GJy sub) {
            collect(sub, out, depth + 1);
         } else if (t != null) {
            out.add(t);
         }
      }
   }

   private static String name(Object track) {
      try {
         String n = ((qAF) track).cc_(); // the track's name
         return n == null || n.isBlank() ? "Track" : n;
      } catch (Throwable t) {
         return "Track";
      }
   }

   /** The tracks selected in the arranger. */
   static List<Object> selection(GRp view) {
      List<Object> out = new ArrayList<>();
      try {
         if (view instanceof Muq x) { // a view with a track selection
            for (Object o : x.MgQ()) {  // the selected tracks
               out.add(o);
            }
         }
      } catch (Throwable t) {
         MOD.log("could not read the track selection", t);
      }
      return out;
   }

   static void select(GRp view, List<Object> tracks) {
      try {
         view.beI(tracks); // select exactly these tracks
      } catch (Throwable t) {
         MOD.log("could not change the track selection", t);
      }
   }

   /** A menu checkbox: ticked when the track is being edited; clicking adds or removes it. */
   static final class Toggle implements lIU { // a boolean value a menu row can show and set
      private final GRp view;
      private final Object track;

      Toggle(GRp view, Object track) {
         this.view = view;
         this.track = track;
      }

      @Override
      public boolean getBoolValue() {
         return selection(view).contains(track);
      }

      @Override
      public void setBoolValue(boolean on) {
         List<Object> now = selection(view);
         now.remove(track);
         if (on) {
            now.add(track);
         }
         if (now.isEmpty()) {
            now.add(track); // keep at least one track in the editor
         }
         select(view, now);
      }

      @Override
      public Object getValue() {
         return getBoolValue();
      }

      @Override
      public boolean isSettable() {
         return true;
      }

      @Override
      public void setValue(Object v) {
         if (v instanceof Boolean b) {
            setBoolValue(b);
         }
      }

      @Override
      public boolean isObservable() {
         return false;
      }

      @Override
      public void addListener(NVD l) { // NVD: a value listener
      }

      @Override
      public void removeListener(NVD l) {
      }
   }

   // ------------------------------------------------------------------ layer list and layered editing

   private static volatile WeakReference<RDV> editorSettings = new WeakReference<>(null);
   private static volatile int lastLayerCount;

   /** An editor built its layer list: remember its settings (the layered-editing switch). */
   static void editorSeen(aNi editor) {
      try {
         if (editor != null && editor.sM3() != null && editor.sM3().scr() != null) { // the editor's settings
            editorSettings = new WeakReference<>(editor.sM3().scr());
         }
      } catch (Throwable t) {
         MOD.log("could not read the editor settings", t);
      }
   }

   /**
    * Whether the layer list shows for this many layers: from one up. Going from one layer to several switches
    * layered editing on (turning it off yourself sticks until next time). null: Bitwig's own answer.
    */
   static Boolean listVisible(Integer count) {
      if (count == null) {
         return null;
      }
      int n = count;
      int before = lastLayerCount;
      lastLayerCount = n;
      if (n >= 2 && before < 2) {
         RDV settings = editorSettings.get(); // the editor's settings
         if (settings != null) {
            Fx.beI(() -> { // later, on Bitwig's UI thread
               try {
                  if (!settings.ZRB()) {     // layered editing off
                     settings.sCA(true);     // switch it on
                  }
               } catch (Throwable t) {
                  MOD.log("could not switch layered editing on", t);
               }
            });
         }
      }
      return n >= 1 ? Boolean.TRUE : null;
   }

   // ------------------------------------------------------------------ Track List panel

   /**
    * The Edit screen's layout (the editor over the mixer/devices panel). Puts the Bitwig 6 beta's Track List
    * panel, still in Bitwig but in no layout, to its left, so the footer gets its button back and "Toggle
    * Track List" works again.
    */
   static Object editLayout(alv content) { // alv: a layout node
      try {
         if (content == null) {
            return null;
         }
         ami list = new ami();         // a panel node
         list.BBg("track_list");       // the panel's ID: Bitwig's Track List
         list.wGt = true;              // a show/hide button in the footer
         list.SQL = "track_list.svg";  // Bitwig's own icon for it
         list.I_();                    // finish the node
         alX split = alX.beI("track_list_and_editor_splitter", true, list, content); // a left/right splitter
         split.beI(dXg.beI);           // the list keeps its width when the window resizes
         return split;
      } catch (Throwable t) {
         MOD.log("Track List panel could not be added", t);
         return content;
      }
   }

   // ------------------------------------------------------------------ layer filter for expressions

   private static volatile boolean filterOff;
   private static boolean skip;   // the current visit is of a greyed-out layer and doesn't paint: skip it
   private static boolean dots;   // painting a greyed-out layer's expressions: leave out the point dots

   /** A layer's note expressions are about to be visited (painted or hit tested). */
   static void visit(Object layer, Object visitor) {
      boolean filtered = filteredOut(layer);
      boolean painter = visitor instanceof btz; // the expression painter
      dots = filtered && painter;
      skip = filtered && !painter;
   }

   static void endVisit() {
      dots = false;
      skip = false;
   }

   static boolean skip() {
      return skip;
   }

   static boolean hideDots() {
      return dots;
   }

   /** True for a layer the editor's "edit selected layers only" filter has greyed out. */
   private static boolean filteredOut(Object layer) {
      try {
         return !filterOff && layer instanceof bpl r && r.mEL(); // the test that greys its notes out
      } catch (Throwable t) {
         filterOff = true;
         MOD.log("layer filter for expressions switched off", t);
         return false;
      }
   }
}
