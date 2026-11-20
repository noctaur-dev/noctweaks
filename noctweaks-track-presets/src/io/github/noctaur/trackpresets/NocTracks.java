package io.github.noctaur.trackpresets;

import bitwig.obf.*;
import com.bitwig.base.async.oRV;
import com.bitwig.base.io.gJi;
import com.bitwig.base.io.z2y;
import com.bitwig.flt.document.core.iface.WSA;
import com.bitwig.flt.document.core.iface.iYN;
import com.bitwig.flt.document.core.master.IbI;
import com.bitwig.flt.document.core.master.zwV;
import com.bitwig.flt.document.core.master.ruS;
import com.bitwig.flt.document.core.master.g2L;
import com.bitwig.flt.document.file.Uev;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * NocTweaks: track presets.
 *
 * Saves whole tracks (devices, plugin states, mixer settings, colour, name, clips) as files and adds them back
 * into a project, the way Bitwig itself copies tracks between two open projects. A preset is a small Bitwig
 * project holding just the saved tracks, stored in <My Library>/Track Presets/<Name>.bwproject.
 */
public final class NocTracks {
   static final String EXT = ".bwproject";        // indexed by Bitwig's browser as a project
   static final String OLD_EXT = ".bwtrack";      // 0.8.0 test build
   static final Source SOURCE = new Source();

   private NocTracks() {
   }

   // ------------------------------------------------------------------ menu

   public static void menu(dKW menu, dve owner, big header) {
      try {
         List<Object> tracks = selected(header);
         if (tracks.isEmpty()) {
            return;
         }

         menu.ELS();
         String what = tracks.size() == 1 ? "Track" : tracks.size() + " Tracks";
         String base = clean(name(tracks.get(0)));
         File existing = new File(folder(), base + EXT);
         if (existing.isFile()) {
            // Same name already saved: replace it, or keep both by numbering the new one.
            dKW save = menu.Hnt("Save " + what + " as Track Preset");
            save.cfM("Replace \"" + base + "\"", () -> save(tracks, base));
            String variant = variant(base);
            save.cfM("Save as New Variant \"" + variant + "\"", () -> save(tracks, variant));
         } else {
            menu.cfM("Save " + what + " as Track Preset", () -> save(tracks, base));
         }

         Object after = tracks.get(tracks.size() - 1);
         addPresetsMenu(menu.Hnt("Add Track Preset Below \"" + clean(name(after)) + "\""), f -> load(f, after, null));
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not build the menu", t);
      }
   }

   // Track views (one per open project window), seen when their track headers are made.
   private static final java.util.Set<ICh> VIEWS = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
   private static final java.util.Set<Object> MENUS = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));

   private static volatile java.lang.ref.WeakReference<ICh> lastView = new java.lang.ref.WeakReference<>(null);

   /** The project track views seen so far (most recent last). */
   static List<ICh> views() {
      List<ICh> out = new ArrayList<>();
      synchronized (VIEWS) {
         out.addAll(VIEWS);
      }
      ICh last = lastView.get();
      if (last != null) {
         out.remove(last);
         out.add(last);
      }
      return out;
   }

   static String trackName(Object track) {
      return name(track);
   }

   /** Hook at the start of bhW(SWl): remember the project's track view (for its selected track). */
   public static void view(ICh view) {
      try {
         if (view != null) {
            VIEWS.add(view);
            lastView = new java.lang.ref.WeakReference<>(view);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Hook at the end of the arranger's right-click menu (aZn): dead space and track lanes. */
   public static void arrangerMenu(dKW menu, Object document) {
      try {
         g2L doc = document instanceof g2L o ? o : null;
         if (doc == null) {
            return;
         }
         ICh found = null;
         synchronized (VIEWS) {
            for (ICh v : VIEWS) {
               try {
                  if (v.pYg() == doc) {
                     found = v;
                  }
               } catch (Throwable ignored) {
               }
            }
         }
         areaMenu(menu, found, doc);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not build the arranger menu", t);
      }
   }

   /** Hook at the end of aLW's right-click menu (the arranger timeline: empty space and lanes). */
   public static void laneMenu(dKW menu, Object timeline) {
      try {
         Object doc = ((aMh)timeline).TD2().gfw();
         arrangerMenu(menu, doc);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not build the timeline menu", t);
      }
   }

   /**
    * Hook on the result of pc2.jPB(Object, FAq): the track Bitwig makes from a dropped project file. For a
    * whole project that is the project's track list (shown as a group named "Project"); for a track preset
    * holding one track, use that track itself.
    */
   public static Object dropped(Object result, Object document, Object item) {
      try {
         if (!(item instanceof IbI faq)) {
            TrackPresets.log("Track presets: drop of " + kind(item) + ", not a library file", null);
            return result;
         }
         var where = faq.cfM();
         File f = where == null ? null : where.cfM();
         String p = f != null ? f.getPath() : String.valueOf(where);
         p = p.replace('\\', '/').toLowerCase(Locale.ROOT);
         if (!p.contains("track presets/") || p.contains("/.previous/")) {
            return result;   // not a track preset: a project dropped as usual
         }
         if (!(result instanceof ruS root)) {
            TrackPresets.log("Track presets: dropped " + p + " gave " + kind(result) + ", left as is", null);
            return result;
         }
         int n = root.ugU();   // top-level tracks (as when adding a preset from the menu)
         Object first = n == 1 ? root.FzB(0) : null;
         if (first instanceof zwV one) {
            TrackPresets.log("Track presets: dropped " + p + " as its track (" + kind(one) + ")", null);
            return one;
         }
         TrackPresets.log("Track presets: dropped " + p + " (" + n + " tracks, kept as a group)", null);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: drop check failed", t);
      }
      return result;
   }

   /** "0" when this dropped file is a track preset holding one track (so only that track is added), else null. */
   static String dropIndex(g2L doc, z2y file) {
      File f = file.cfM();
      String p = (f != null ? f.getPath() : String.valueOf(file)).replace('\\', '/').toLowerCase(Locale.ROOT);
      if (!p.contains("track presets/") || p.contains("/.previous/")) {
         return null;   // a project: dropped as a group, as usual
      }
      ruS root = doc.spd();
      int n = root == null ? 0 : root.ugU();   // top-level tracks
      if (n != 1) {
         TrackPresets.log("Track presets: dropped " + p + " (" + n + " tracks, kept as a group)", null);
         return null;
      }
      List<?> all = root.E1b();               // the list Bitwig picks the sub-item from
      Object first = root.FzB(0);
      int index = all.indexOf(first);
      if (index < 0) {
         TrackPresets.log("Track presets: dropped " + p + ": its track isn't in the list, kept as a group", null);
         return null;
      }
      TrackPresets.log("Track presets: dropped " + p + " as its track (#" + index + ")", null);
      return String.valueOf(index);
   }

   private static String kind(Object o) {
      return o == null ? "null" : o.getClass().getName();
   }

   /** Hook in aYD.IYM(duK, dKG) after the "Add … Track" items of a panel's background menu. */
   public static void panelMenu(dKW menu, Object panel) {
      try {
         arrangerMenu(menu, ((aBW)panel).mtS());
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not build the panel menu", t);
      }
   }

   /** Hook at the end of aZr's right-click menu. */
   public static void areaMenu(dKW menu, ICh view) {
      Object d = null;
      try {
         d = view.pYg();
      } catch (Throwable ignored) {
      }
      areaMenu(menu, view, d instanceof g2L o ? o : null);
   }

   private static void areaMenu(dKW menu, ICh view, g2L doc) {
      try {
         if (menu == null || !MENUS.add(menu)) {
            return;   // already added to this menu
         }
         Object cursor = null;
         try {
            cursor = view == null ? null : view.LIK();   // the selected track
         } catch (Throwable ignored) {
         }
         if (doc == null && cursor != null) {
            doc = document(cursor);
         }
         if (doc == null) {
            TrackPresets.log("Track presets: menu without a project, not adding presets", null);
            return;
         }
         final Object after = cursor instanceof WSA && document(cursor) == doc ? cursor : null;
         final g2L into = doc;
         menu.ELS();
         String where = after == null ? " (at the End)" : " Below \"" + clean(name(after)) + "\"";
         addPresetsMenu(menu.Hnt("Add Track Preset" + where), f -> load(f, after, into));
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not build the arranger menu", t);
      }
   }

   /** Collections that hold track presets (as submenus), then all presets, then the folder. */
   /**
    * The presets, as two submenus: Collections (each collection that holds a preset, with those presets) and
    * All (every preset), then Open Track Presets Folder.
    */
   private static void addPresetsMenu(dKW insert, java.util.function.Consumer<File> pick) {
      File[] presets = presets();
      if (presets.length == 0) {
         insert.cfM("(none saved yet: right-click a track > Save Track as Track Preset)", () -> {
         });
      } else {
         dKW collections = null;
         try {
            for (Object o : EE.UNj().Cuu()) {   // the user's collections
               if (!(o instanceof GK fw)) {
                  continue;
               }
               List<File> in = new ArrayList<>();
               for (File f : presets) {
                  try {
                     if (fw.cfM(new z2y(f))) {   // the collection holds this file
                        in.add(f);
                     }
                  } catch (Throwable ignored) {
                  }
               }
               if (!in.isEmpty()) {
                  if (collections == null) {
                     collections = insert.Hnt("Collections");
                  }
                  dKW sub = collections.Hnt(String.valueOf(fw.IwV()).replace("/", "  ›  "));
                  for (File f : in) {
                     sub.cfM(title(f), () -> pick.accept(f));
                  }
               }
            }
         } catch (Throwable t) {
            TrackPresets.log("Track presets: could not list collections", t);
         }
         dKW all = insert.Hnt("All");
         for (File f : presets) {
            all.cfM(title(f), () -> pick.accept(f));
         }
      }
      insert.ELS();
      insert.cfM("Open Track Presets Folder", NocTracks::openFolder);
   }

   /** "Name 2", "Name 3"… : the first that isn't saved yet. */
   static String variant(String base) {
      String stem = base;
      File dir = folder();
      for (int i = 2; ; i++) {
         String n = stem + " " + i;
         if (!new File(dir, n + EXT).exists()) {
            return n;
         }
      }
   }

   private static List<Object> selected(big header) {
      List<Object> out = new ArrayList<>();
      try {
         for (Object o : header.UNj().FzB()) {
            if (o instanceof WSA) {
               out.add(o);
            }
         }
      } catch (Throwable t) {
         TrackPresets.log("Track presets: no track selection", t);
      }
      return out;
   }

   // ------------------------------------------------------------------ save

   static void save(List<Object> tracks, String base) {
      try {
         Object first = tracks.get(0);
         g2L src = document(first);
         if (src == null) {
            TrackPresets.log("Track presets: can't find the project of this track (" + first.getClass().getName() + ")", null);
            return;
         }

         File dir = folder();
         dir.mkdirs();
         File out = new File(dir, base + EXT);
         if (out.exists()) {
            // Same name: replace it, keeping the previous version in .previous
            File keep = new File(dir, ".previous");
            keep.mkdirs();
            java.nio.file.Files.move(out.toPath(), new File(keep, base + " " + System.currentTimeMillis() + EXT).toPath(),
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         }

         // A new, unopened project holding copies of the tracks (as Bitwig does for .bwclip files and
         // for dragging tracks onto a new project tab).
         g2L doc = g2L.qwJ();
         doc.TMF(src.nfq());
         try {
            doc.spd().HL(src.spd().jOH().fFs().UNj());
         } catch (Throwable t) {
            TrackPresets.log("Track presets: tempo not copied", t);
         }
         final File target = out;
         final boolean[] written = {false};
         Runnable write = () -> {
            synchronized (written) {
               if (written[0]) {
                  return;
               }
               written[0] = true;
            }
            try {
               int n = doc.spd().ugU();
               Uev.Hnt().MRY(doc, target);
               TrackPresets.log("Track presets: saved " + n + " of " + tracks.size() + " track(s) to " + target
                  + (n == 0 ? " (EMPTY: tracks " + describe(tracks, src) + ")" : ""), null);
               // Plug-in states live in separate files; put them inside the preset like a saved project does.
               Thread t = new Thread(() -> embedPluginStates(target), "Track preset plug-in states");
               t.setDaemon(true);
               t.start();
            } catch (Throwable t) {
               TrackPresets.log("Track presets: writing " + target + " failed", t);
            }
         };
         // Copying can finish later (plug-in states come from the audio engine): write when it's done.
         doc.spd().cfM(new ArrayList<Object>(tracks), 0, true, new oRV() {
            @Override
            public void cfM(Object result) {
               Fq.MRY(write);
            }

            @Override
            public void cfM(Exception e) {
               TrackPresets.log("Track presets: copying the track failed", e);
               Fq.MRY(write);
            }
         }, null);
         // In case the copy never reports back.
         new java.util.Timer(true).schedule(new java.util.TimerTask() {
            @Override
            public void run() {
               Fq.cfM(write);
            }
         }, 8000);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: save failed", t);
      }
   }

   private static final java.util.regex.Pattern STATE_FILE = java.util.regex.Pattern.compile(
      "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.[A-Za-z]");

   /**
    * A Bitwig project file is a header, the document, then a zip of "plugin-states/<file>" entries whose
    * start is stored in the header (characters 24-40, hex). Bitwig's plain document writer leaves the zip
    * out, so the preset only names its plug-in state files. This adds them (waiting a few seconds for
    * ones the audio engine is still writing).
    */
   static void embedPluginStates(File target) {
      File dir;
      try {
         dir = iJW.Nnn();
      } catch (Throwable t) {
         TrackPresets.log("Track presets: plug-in state folder not found", t);
         return;
      }
      embedPluginStates(target, dir);
   }

   static void embedPluginStates(File target, File dir) {
      try {
         byte[] data = java.nio.file.Files.readAllBytes(target.toPath());
         if (data.length < 40 || data[0] != 'B' || data[1] != 't' || data[2] != 'W' || data[3] != 'g') {
            TrackPresets.log("Track presets: " + target.getName() + " has an unexpected header; plug-in states not added", null);
            return;
         }
         long resources = Long.parseLong(new String(data, 24, 16, java.nio.charset.StandardCharsets.US_ASCII), 16);
         if (resources != 0) {
            return;   // already has them
         }
         // Plug-in state files are named <uuid>.<extension>; find the uuids the document names that way.
         java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
         java.util.regex.Matcher m = STATE_FILE.matcher(new String(data, java.nio.charset.StandardCharsets.ISO_8859_1));
         while (m.find()) {
            ids.add(m.group(1).toLowerCase(Locale.ROOT));
         }
         if (ids.isEmpty()) {
            return;
         }
         List<File> files = new ArrayList<>();
         List<String> missing = new ArrayList<>();
         for (int wait = 0; wait < 40; wait++) {
            files.clear();
            missing.clear();
            File[] all = dir.listFiles();
            for (String id : ids) {
               File found = null;
               if (all != null) {
                  for (File f : all) {
                     if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).startsWith(id + ".")) {
                        found = f;
                     }
                  }
               }
               if (found == null) {
                  missing.add(id);
               } else {
                  files.add(found);
               }
            }
            if (missing.isEmpty()) {
               break;
            }
            Thread.sleep(250);
         }
         java.io.ByteArrayOutputStream zipBytes = new java.io.ByteArrayOutputStream();
         int added = 0;
         try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(zipBytes)) {
            for (File f : files) {
               zip.putNextEntry(new java.util.zip.ZipEntry("plugin-states/" + f.getName()));
               zip.write(java.nio.file.Files.readAllBytes(f.toPath()));
               zip.closeEntry();
               added++;
            }
         }
         if (added == 0) {
            TrackPresets.log("Track presets: no plug-in state files found for " + target.getName() + " (missing " + missing + ")", null);
            return;
         }
         byte[] header = String.format("%016x", (long)data.length).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
         System.arraycopy(header, 0, data, 24, 16);
         File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
         try (java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(data);
            zipBytes.writeTo(out);
         }
         java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         TrackPresets.log("Track presets: added " + added + " plug-in state(s) to " + target.getName()
            + (missing.isEmpty() ? "" : " (still missing " + missing + ")"), null);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not add plug-in states to " + target, t);
      }
   }

   // ------------------------------------------------------------------ load

   static void load(File file, Object after, g2L project) {
      try {
         g2L dst = after != null ? document(after) : project;
         if (dst == null) {
            TrackPresets.log("Track presets: can't find the project to insert into", null);
            return;
         }
         try {
            qBz.cfM(file);   // unpack the preset's plug-in states (as Bitwig does for packaged files)
         } catch (Throwable ignored) {
            // no plug-in states inside (older preset or none needed)
         }
         g2L preset = g2L.cfM(file, iJW.UNj());
         ruS from = preset.spd();
         List<Object> tracks = new ArrayList<>();
         for (int i = 0; i < from.ugU(); i++) {
            tracks.add(from.FzB(i));
         }
         if (tracks.isEmpty()) {
            TrackPresets.log("Track presets: " + file.getName() + " has no tracks", null);
            return;
         }

         ruS into = dst.spd();
         int at = into.ugU();
         try {
            int i = after == null ? -1 : into.FzB((WSA)after);
            if (i >= 0) {
               at = i + 1;
            }
         } catch (Throwable ignored) {
            // nested track (inside a group): add at the end of the project
         }
         into.FzB(tracks, at, true, null);
         TrackPresets.log("Track presets: inserted " + tracks.size() + " track(s) from " + file.getName() + " at " + at, null);
      } catch (Throwable t) {
         TrackPresets.log("Track presets: insert failed", t);
      }
   }

   // ------------------------------------------------------------------ Tracks sidebar button

   /** Browser button listing the track presets (projects in My Library/Track Presets). */
   static final class Source extends Vu {
      @Override
      public String E3() {
         return "suite_tracks";
      }

      @Override
      public String in_() {
         return "Tracks";
      }

      @Override
      public List FzB() {
         return List.of("tracks", "track presets");
      }

      @Override
      public java.util.Set wGc() {
         return java.util.Set.of(qD.E3);   // projects
      }

      /** The button's image (the Browser mod can replace it with an icon of the user's choice). */
      @Override
      public java.net.URI hUR() {
         java.net.URI bitwig = null;
         try {
            bitwig = super.hUR();   // the Browser mod puts an icon chosen for @tracks here
            if (bitwig != null && "file".equals(bitwig.getScheme()) && new File(bitwig).isFile()) {
               return bitwig;
            }
         } catch (Throwable ignored) {
         }
         try {
            return TrackPresets.MOD.resource("icons").resolve("tracks.png").toUri();
         } catch (Throwable t) {
            return bitwig;
         }
      }

      @Override
      public Wo TtY() {
         return Wo.MRY;
      }

      @Override
      public M22 MRY(EE dn) {
         if (!asked) {
            asked = true;
            TrackPresets.log("Tracks button: searching for track presets", null);
         }
         return PresetsOnly.INSTANCE;
      }
   }

   private static volatile boolean asked;

   public static Vu source() {
      return SOURCE;
   }

   /** Matches files under a "Track Presets" folder; judged per file. */
   static final class PresetsOnly extends M22 {
      static final PresetsOnly INSTANCE = new PresetsOnly();

      @Override
      public nCp cfM(pAe cursor, Ihc context) {
         return new InFolder(cursor);
      }

      @Override
      public void a_(gJi out) {
      }

      @Override
      public boolean cfM(M22 other) {
         return other instanceof PresetsOnly;
      }

      @Override
      public int MRY() {
         return 3;
      }

      @Override
      public void cfM(List list) {
      }

      @Override
      public java.util.Set cfM(flj kind) {
         return null;
      }
   }


   static final class InFolder extends nCp {
      private final pAe cursor;

      InFolder(pAe cursor) {
         this.cursor = cursor;
      }

      @Override
      public void cfM() {
      }

      @Override
      public INH cfM(INH parent) {
         return INH.MRY;   // decide per file
      }

      @Override
      public void Hnt() {
      }

      @Override
      public boolean MRY() {
         return isPreset(cursor);
      }
   }

   /** True for a browser item stored under a "Track Presets" folder. */
   static boolean isPreset(pAe cursor) {
      String full = "", dir = "", name = "", root = "";
      try {
         try {
            full = String.valueOf(cursor.lYc());   // path inside the location, folders included
         } catch (Throwable ignored) {
         }
         try {
            dir = String.valueOf(cursor.IwV());
         } catch (Throwable ignored) {
         }
         try {
            name = String.valueOf(cursor.UNj());
         } catch (Throwable ignored) {
         }
         try {
            EX dd = ((Ph)cursor).UZr();
            root = dd == null || dd.MJ() == null ? "" : dd.MJ().getPath();
         } catch (Throwable ignored) {
         }
         String all = ("/" + root + "/" + full + "|/" + root + "/" + dir + "/" + name).replace('\\', '/').toLowerCase(Locale.ROOT);
         boolean in = !all.contains("/.previous/") && all.contains("/track presets/");
         if (SEEN.size() < 12 && SEEN.add(full + "|" + dir)) {
            TrackPresets.log("Tracks: project item path='" + full + "' dir='" + dir + "' name='" + name + "' in '" + root + "' -> " + in, null);
         }
         return in;
      } catch (Throwable t) {
         return false;
      }
   }

   private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** Hook at the start of cAX(qg...): the main browser also searches projects, so track presets can show. */
   public static qD[] kinds(qD[] kinds) {
      try {
         for (qD k : kinds) {
            if (k == qD.E3) {
               return kinds;
            }
         }
         qD[] out = Arrays.copyOf(kinds, kinds.length + 1);
         out[kinds.length] = qD.E3;
         return out;
      } catch (Throwable t) {
         return kinds;
      }
   }

   // ------------------------------------------------------------------ helpers

   private static String describe(List<Object> tracks, g2L src) {
      StringBuilder b = new StringBuilder();
      for (Object t : tracks) {
         g2L d = document(t);
         b.append(t.getClass().getSimpleName()).append(d == src ? " in this project" : d == null ? " no project" : " in another project").append("; ");
      }
      return b.toString();
   }

   private static g2L document(Object track) {
      try {
         Object d = ((WSA)track).apV();
         return d instanceof g2L o ? o : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static String name(Object track) {
      try {
         String n = ((iYN)track).cb_();
         return n == null || n.isBlank() ? "Track" : n;
      } catch (Throwable t) {
         return "Track";
      }
   }

   private static String clean(String n) {
      String s = n.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", " ").strip();
      return s.isEmpty() ? "Track" : s;
   }

   /** <My Library>/Track Presets, so Bitwig's browser indexes the presets (as projects). */
   static File folder() {
      try {
         EE dn = EE.UNj();
         for (List<?> list : List.of(dn.OzW(), dn.UZr(), dn.NC(), dn.BNq(), dn.E1b())) {
            for (Object o : list) {
               if (o instanceof EX dd && "MY_LIBRARY".equals(String.valueOf(dd.E3())) && dd.MJ() != null) {
                  return new File(dd.MJ(), "Track Presets");
               }
            }
         }
      } catch (Throwable t) {
         TrackPresets.log("Track presets: My Library not found", t);
      }
      return new File(System.getProperty("user.home"), "Documents/Bitwig Studio/Library/Track Presets");
   }

   static File[] presets() {
      File dir = folder();
      File[] all = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(EXT));
      if (all == null) {
         return new File[0];
      }
      Arrays.sort(all, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
      return all;
   }

   static String title(File f) {
      String n = f.getName();
      return n.substring(0, n.length() - EXT.length());
   }

   private static void openFolder() {
      try {
         File dir = folder();
         dir.mkdirs();
         new ProcessBuilder(TrackPresets.fileManager(), dir.getAbsolutePath()).start();
      } catch (Throwable t) {
         TrackPresets.log("Track presets: could not open " + folder(), t);
      }
   }

   // ------------------------------------------------------------------ the Tracks button

   private static final java.util.Set<Object> PINNED = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
   private static volatile java.lang.ref.WeakReference<YO> mainPalette = new java.lang.ref.WeakReference<>(null);

   /**
    * A browser's sidebar buttons (quick sources) were asked for: Tracks goes on the main browser's, every session
    * (Bitwig drops buttons it doesn't know when it starts without this mod), unless it's hidden from the menu.
    * A popup browser's sidebar never gets it.
    */
   static void ensurePinned(YO p, boolean main) {
      try {
         if (p == null) {
            return;
         }
         if (main) {
            mainPalette = new java.lang.ref.WeakReference<>(p);
         }
         if (!PINNED.add(p)) {
            return;
         }
         int at = p.cfM((Wl)SOURCE);
         if (main) {
            if (TrackPresets.buttonShown() && at < 0) {
               p.cfM(YS.cfM(SOURCE), p.tp2());   // pin it at the end
            }
         } else if (at >= 0 && !newTrackPopupOn()) {
            p.MRY((Wl)SOURCE);   // popups only get it while a new track's browser is open (popupOpening)
         }
      } catch (Throwable t) {
         TrackPresets.log("Could not pin the Tracks button", t);
      }
   }

   /** Show or hide the Tracks button (the browser's right-click menu). */
   static void browserMenu(dKW menu) {
      boolean popup = TrackPresets.newTrackPopup();
      menu.cfM((popup ? "✓ " : "    ") + "Track Presets in New Track Browser", () -> TrackPresets.setNewTrackPopup(!popup));
      boolean shown = TrackPresets.buttonShown();
      menu.cfM((shown ? "Hide" : "Show") + " Tracks Button", () -> {
         TrackPresets.setButtonShown(!shown);
         try {
            YO p = mainPalette.get();
            if (p != null) {
               if (shown) {
                  p.MRY((Wl)SOURCE);                          // unpin it
               } else if (p.cfM((Wl)SOURCE) < 0) {
                  p.cfM(YS.cfM(SOURCE), p.tp2());           // pin it at the end
               }
            }
         } catch (Throwable t) {
            TrackPresets.log("Could not change the sidebar", t);
         }
      });
   }

   /** The browser switched to Tracks: open its Location column, where the preset folders are. */
   static void sourceChosen(Jp session) {
      try {
         Wl now = session.Scv();
         if (now != SOURCE) {
            return;
         }
         Fq.cfM(() -> {
            try {
               if (session.Scv() == now) {
                  now.MRY(session);   // open the source's own column (Location)
               }
            } catch (Throwable t) {
               TrackPresets.log("Could not open the Location column", t);
            }
         });
      } catch (Throwable t) {
         TrackPresets.log("source chosen", t);
      }
   }

   // ------------------------------------------------------------------ projects: track presets only

   /** The browser's query, with projects (which the main browser now lists) kept to track presets. */
   static M22 presetsOnly(M22 query) {
      return query == null || query instanceof PresetsQuery ? query : new PresetsQuery(query);
   }

   static final class PresetsQuery extends M22 {
      final M22 inner;

      PresetsQuery(M22 inner) {
         this.inner = inner;
      }

      @Override
      public nCp cfM(pAe cursor, Ihc context) {
         nCp m = inner.cfM(cursor, context);
         if (m == null || !(cursor instanceof Nv)) {   // Nv: a project in the browser
            return m;
         }
         return new PresetMatch(m, cursor);
      }

      @Override
      public void a_(gJi out) {
         inner.a_(out);
      }

      @Override
      public boolean cfM(M22 other) {
         return other instanceof PresetsQuery q && inner.equals(q.inner);
      }

      @Override
      public int MRY() {
         return inner.MRY();
      }

      @Override
      public void cfM(List list) {
         inner.cfM(list);
      }

      @Override
      public java.util.Set cfM(flj kind) {
         return inner.cfM(kind);
      }
   }

   /** The original matcher, judged per file: a project matches only when it's a track preset. */
   static final class PresetMatch extends nCp {
      private final nCp inner;
      private final pAe cursor;

      PresetMatch(nCp inner, pAe cursor) {
         this.inner = inner;
         this.cursor = cursor;
      }

      @Override
      public void cfM() {
         inner.cfM();
      }

      @Override
      public INH cfM(INH parent) {
         INH s = inner.UNj();   // enter the directory in the original matcher
         return s == INH.cfM ? INH.MRY : s;
      }

      @Override
      public void Hnt() {
         inner.hUR();           // leave the directory in the original matcher
      }

      @Override
      public boolean MRY() {
         return inner.IwV() && isPreset(cursor);
      }
   }

   // ------------------------------------------------------------------ the new-track popup browser

   /** The insertion point of the popup browser that's open now, when it's an empty track's (else null). */
   private static volatile aXs newTrack;
   /** The kind set (and sidebar) that popup uses. */
   private static volatile Object newTrackKinds;

   static boolean newTrackPopupOn() {
      return TrackPresets.newTrackPopup();
   }

   static boolean isNewTrackKinds(Object kinds) {
      return kinds != null && kinds == newTrackKinds && newTrack != null;
   }

   /** True for the browser that opens on a new, empty instrument or audio track. */
   private static boolean emptyTrack(aXs target) {
      try {
         if (!(target instanceof aXU u)) {
            return false;
         }
         aXt mode = u.LaE();   // what is being added: an empty track's first device has its own modes
         return mode == aXt.cfM || mode == aXt.MRY;
      } catch (Throwable t) {
         return false;
      }
   }

   static void popupOpening(aXK where) {
      try {
         aXs target = where == null ? null : where.FzB();   // the insertion point the popup is for
         boolean mine = target != null && newTrackPopupOn() && emptyTrack(target);
         cEv kinds = target == null ? null : target.IwV();   // the popup's kind set (it has its own sidebar)
         TrackPresets.log("Popup browser for " + (target == null ? "nothing" : target.getClass().getName())
            + (mine ? " (an empty track: Tracks button shown)" : ""), null);
         newTrack = mine ? target : null;
         newTrackKinds = mine ? kinds : null;
         if (kinds == null) {
            return;
         }
         YO palette = kinds.lYc();   // that popup's sidebar buttons
         int at = palette.cfM((Wl)SOURCE);
         if (mine && at < 0) {
            palette.cfM(YS.cfM(SOURCE), palette.tp2());
         } else if (!mine && at >= 0) {
            palette.MRY((Wl)SOURCE);
         }
      } catch (Throwable t) {
         TrackPresets.log("Track presets: popup browser", t);
      }
   }

   static void popupClosing(aXs target, boolean ok) {
      if (target != null && target == newTrack) {
         TrackPresets.log("New track browser " + (ok ? "OK" : "closed"), null);
         newTrack = null;
      }
   }

   /** The track preset picked in the new track's browser (added on OK), and the project it goes into. */
   private static volatile File pendingPreset;
   private static volatile g2L pendingProject;

   private static File presetFile(z2y[] files) {
      try {
         if (files == null || files.length != 1 || files[0] == null) {
            return null;
         }
         File f = files[0].cfM();
         String p = f == null ? "" : f.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);
         return p.endsWith(EXT) && p.contains("track presets/") && !p.contains("/.previous/") ? f : null;
      } catch (Throwable t) {
         return null;
      }
   }

   /** Bitwig loads the picked item into the new track for a preview: remember a track preset for OK. */
   static void previewing(z2y[] files, g2L project) {
      if (newTrack == null) {
         return;
      }
      File f = presetFile(files);
      pendingPreset = f;
      pendingProject = f == null ? null : project;
      if (f != null) {
         TrackPresets.log("New track browser: picked " + f.getName(), null);
      }
   }

   static boolean previewIsPreset(z2y[] files) {
      return newTrack != null && presetFile(files) != null;
   }

   /** OK in the new track's browser: a picked track preset replaces the new, empty track. */
   static void popupCommit(aXs target) {
      File file = pendingPreset;
      g2L project = pendingProject;
      boolean mine = target != null && target == newTrack;
      popupClosing(target, true);
      pendingPreset = null;
      pendingProject = null;
      if (!mine || file == null || !(target instanceof aXC c)) {
         return;
      }
      try {
         Object track = c.qoU().K_();   // the empty track (its device chain's owner)
         g2L into = document(track) != null ? document(track) : project;
         Fq.cfM(() -> {
            try {
               load(file, track, into);
               if (into != null && track != null) {
                  into.cfM(java.util.List.of(track));   // remove the empty track
               }
               TrackPresets.log("New track browser: " + file.getName() + " added in place of the new track", null);
            } catch (Throwable t) {
               TrackPresets.log("New track browser: could not add " + file.getName(), t);
            }
         });
      } catch (Throwable t) {
         TrackPresets.log("New track browser: OK", t);
      }
   }
}
