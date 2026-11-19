package io.github.noctaur.browser;

import bitwig.obf.*;
import com.bitwig.base.async.PPR;
import com.bitwig.base.io.vQq;
import com.bitwig.base.io.Lpf;
import com.bitwig.flt.document.core.iface.zCn;
import com.bitwig.flt.document.core.iface.qAF;
import com.bitwig.flt.document.core.master.Awj;
import com.bitwig.flt.document.core.master.sJU;
import com.bitwig.flt.document.core.master.GJy;
import com.bitwig.flt.document.core.master.CmH;
import com.bitwig.flt.document.file.JSA;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * NocTweaks: track presets. EXPERIMENTAL.
 *
 * Saves whole tracks (devices, plugin states, mixer settings, colour, name, clips) as files and adds
 * them back into a project, the way Bitwig itself copies tracks between two open projects.
 *
 * A preset is a small Bitwig project holding just the saved tracks, written without opening it
 * (the same way Bitwig writes .bwclip files), stored as <Library>/Track Presets/<Name>.bwtrack.
 *
 * Hook (inserted by the build-time patcher):
 *   bml.IYM(dKG, duK, bhW, gC)      -> menu(menu, owner, header)   track header right-click menu
 *   aZr.IYM(dKG, double, double, eoV) -> areaMenu(menu, view)     arranger background right-click menu
 *   cAX.<init>(qg...)               -> kinds(kinds)               main browser also lists projects (presets only)
 */
public final class NocTracks {
   static final String EXT = ".bwproject";        // indexed by Bitwig's browser as a project
   static final String OLD_EXT = ".bwtrack";      // 0.8.0 test build
   static final Source SOURCE = new Source();

   private NocTracks() {
   }

   // ------------------------------------------------------------------ menu

   public static void menu(dMt menu, dwF owner, bju header) {
      try {
         List<Object> tracks = selected(header);
         if (tracks.isEmpty()) {
            return;
         }

         menu.fpX();
         String what = tracks.size() == 1 ? "Track" : tracks.size() + " Tracks";
         String base = clean(name(tracks.get(0)));
         File existing = new File(folder(), base + EXT);
         if (existing.isFile()) {
            // Same name already saved: replace it, or keep both by numbering the new one.
            dMt save = menu.sCA("Save " + what + " as Track Preset");
            save.beI("Replace \"" + base + "\"", () -> save(tracks, base));
            String variant = variant(base);
            save.beI("Save as New Variant \"" + variant + "\"", () -> save(tracks, variant));
         } else {
            menu.beI("Save " + what + " as Track Preset", () -> save(tracks, base));
         }

         Object after = tracks.get(tracks.size() - 1);
         addPresetsMenu(menu.sCA("Add Track Preset Below \"" + clean(name(after)) + "\""), f -> load(f, after, null));
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not build the menu", t);
      }
   }

   // Track views (one per open project window), seen when their track headers are made.
   private static final java.util.Set<GRp> VIEWS = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
   private static final java.util.Set<Object> MENUS = java.util.Collections.newSetFromMap(
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));

   private static volatile java.lang.ref.WeakReference<GRp> lastView = new java.lang.ref.WeakReference<>(null);

   /** The project track views seen so far (most recent last). */
   static List<GRp> views() {
      List<GRp> out = new ArrayList<>();
      synchronized (VIEWS) {
         out.addAll(VIEWS);
      }
      GRp last = lastView.get();
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
   public static void view(GRp view) {
      try {
         if (view != null) {
            VIEWS.add(view);
            lastView = new java.lang.ref.WeakReference<>(view);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Hook at the end of the arranger's right-click menu (aZn): dead space and track lanes. */
   public static void arrangerMenu(dMt menu, Object document) {
      try {
         CmH doc = document instanceof CmH o ? o : null;
         if (doc == null) {
            return;
         }
         GRp found = null;
         synchronized (VIEWS) {
            for (GRp v : VIEWS) {
               try {
                  if (v.kys() == doc) {
                     found = v;
                  }
               } catch (Throwable ignored) {
               }
            }
         }
         areaMenu(menu, found, doc);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not build the arranger menu", t);
      }
   }

   /** Hook at the end of aLW's right-click menu (the arranger timeline: empty space and lanes). */
   public static void laneMenu(dMt menu, Object timeline) {
      try {
         Object doc = ((aNi)timeline).np2().dNa();
         arrangerMenu(menu, doc);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not build the timeline menu", t);
      }
   }

   /**
    * Hook on the result of pc2.jPB(Object, FAq): the track Bitwig makes from a dropped project file. For a
    * whole project that is the project's track list (shown as a group named "Project"); for a track preset
    * holding one track, use that track itself.
    */
   public static Object dropped(Object result, Object document, Object item) {
      try {
         if (!(item instanceof Awj faq)) {
            NocBrowser.log("Track presets: drop of " + kind(item) + ", not a library file", null);
            return result;
         }
         var where = faq.beI();
         File f = where == null ? null : where.beI();
         String p = f != null ? f.getPath() : String.valueOf(where);
         p = p.replace('\\', '/').toLowerCase(Locale.ROOT);
         if (!p.contains("track presets/") || p.contains("/.previous/")) {
            return result;   // not a track preset: a project dropped as usual
         }
         if (!(result instanceof GJy root)) {
            NocBrowser.log("Track presets: dropped " + p + " gave " + kind(result) + ", left as is", null);
            return result;
         }
         int n = root.qaW();   // top-level tracks (as when adding a preset from the menu)
         Object first = n == 1 ? root.MgQ(0) : null;
         if (first instanceof sJU one) {
            NocBrowser.log("Track presets: dropped " + p + " as its track (" + kind(one) + ")", null);
            return one;
         }
         NocBrowser.log("Track presets: dropped " + p + " (" + n + " tracks, kept as a group)", null);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: drop check failed", t);
      }
      return result;
   }

   /** "0" when this dropped file is a track preset holding one track (so only that track is added), else null. */
   static String dropIndex(CmH doc, Lpf file) {
      File f = file.beI();
      String p = (f != null ? f.getPath() : String.valueOf(file)).replace('\\', '/').toLowerCase(Locale.ROOT);
      if (!p.contains("track presets/") || p.contains("/.previous/")) {
         return null;   // a project: dropped as a group, as usual
      }
      GJy root = doc.svk();
      int n = root == null ? 0 : root.qaW();   // top-level tracks
      if (n != 1) {
         NocBrowser.log("Track presets: dropped " + p + " (" + n + " tracks, kept as a group)", null);
         return null;
      }
      List<?> all = root.CtA();               // the list Bitwig picks the sub-item from
      Object first = root.MgQ(0);
      int index = all.indexOf(first);
      if (index < 0) {
         NocBrowser.log("Track presets: dropped " + p + ": its track isn't in the list, kept as a group", null);
         return null;
      }
      NocBrowser.log("Track presets: dropped " + p + " as its track (#" + index + ")", null);
      return String.valueOf(index);
   }

   private static String kind(Object o) {
      return o == null ? "null" : o.getClass().getName();
   }

   /** Hook in aYD.IYM(duK, dKG) after the "Add … Track" items of a panel's background menu. */
   public static void panelMenu(dMt menu, Object panel) {
      try {
         arrangerMenu(menu, ((aCZ)panel).cdX());
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not build the panel menu", t);
      }
   }

   /** Hook at the end of aZr's right-click menu. */
   public static void areaMenu(dMt menu, GRp view) {
      Object d = null;
      try {
         d = view.kys();
      } catch (Throwable ignored) {
      }
      areaMenu(menu, view, d instanceof CmH o ? o : null);
   }

   private static void areaMenu(dMt menu, GRp view, CmH doc) {
      try {
         if (menu == null || !MENUS.add(menu)) {
            return;   // already added to this menu
         }
         Object cursor = null;
         try {
            cursor = view == null ? null : view.mm2();   // the selected track
         } catch (Throwable ignored) {
         }
         if (doc == null && cursor != null) {
            doc = document(cursor);
         }
         if (doc == null) {
            NocBrowser.log("Track presets: menu without a project, not adding presets", null);
            return;
         }
         final Object after = cursor instanceof zCn && document(cursor) == doc ? cursor : null;
         final CmH into = doc;
         menu.fpX();
         String where = after == null ? " (at the End)" : " Below \"" + clean(name(after)) + "\"";
         addPresetsMenu(menu.sCA("Add Track Preset" + where), f -> load(f, after, into));
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not build the arranger menu", t);
      }
   }

   /** Collections that hold track presets (as submenus), then all presets, then the folder. */
   private static void addPresetsMenu(dMt insert, java.util.function.Consumer<File> pick) {
      File[] presets = presets();
      if (presets.length == 0) {
         insert.beI("(none saved yet: right-click a track > Save Track as Track Preset)", () -> {
         });
      }
      try {
         for (Object o : EN.unz().sBA()) {
            if (!(o instanceof GS fw)) {
               continue;
            }
            List<File> in = new ArrayList<>();
            for (File f : presets) {
               try {
                  if (fw.beI(new Lpf(f))) {
                     in.add(f);
                  }
               } catch (Throwable ignored) {
               }
            }
            if (!in.isEmpty()) {
               dMt sub = insert.sCA(String.valueOf(fw.BBg()).replace("/", "  ›  "));
               for (File f : in) {
                  sub.beI(title(f), () -> pick.accept(f));
               }
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not list collections", t);
      }
      if (presets.length > 0) {
         insert.fpX();
      }
      for (File f : presets) {
         insert.beI(title(f), () -> pick.accept(f));
      }
      insert.fpX();
      insert.beI("Open Track Presets Folder", NocTracks::openFolder);
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

   private static List<Object> selected(bju header) {
      List<Object> out = new ArrayList<>();
      try {
         for (Object o : header.unz().MgQ()) {
            if (o instanceof zCn) {
               out.add(o);
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Track presets: no track selection", t);
      }
      return out;
   }

   // ------------------------------------------------------------------ save

   static void save(List<Object> tracks, String base) {
      try {
         Object first = tracks.get(0);
         CmH src = document(first);
         if (src == null) {
            NocBrowser.log("Track presets: can't find the project of this track (" + first.getClass().getName() + ")", null);
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
         CmH doc = CmH.qbl();
         doc.byf(src.ckt());
         try {
            doc.svk().IvP(src.svk().eAA().ccv().unz());
         } catch (Throwable t) {
            NocBrowser.log("Track presets: tempo not copied", t);
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
               int n = doc.svk().qaW();
               JSA.sCA().Gef(doc, target);
               NocBrowser.log("Track presets: saved " + n + " of " + tracks.size() + " track(s) to " + target
                  + (n == 0 ? " (EMPTY: tracks " + describe(tracks, src) + ")" : ""), null);
               // Plug-in states live in separate files; put them inside the preset like a saved project does.
               Thread t = new Thread(() -> embedPluginStates(target), "Track preset plug-in states");
               t.setDaemon(true);
               t.start();
            } catch (Throwable t) {
               NocBrowser.log("Track presets: writing " + target + " failed", t);
            }
         };
         // Copying can finish later (plug-in states come from the audio engine): write when it's done.
         doc.svk().beI(new ArrayList<Object>(tracks), 0, true, new PPR() {
            @Override
            public void beI(Object result) {
               Fx.Gef(write);
            }

            @Override
            public void beI(Exception e) {
               NocBrowser.log("Track presets: copying the track failed", e);
               Fx.Gef(write);
            }
         }, null);
         // In case the copy never reports back.
         new java.util.Timer(true).schedule(new java.util.TimerTask() {
            @Override
            public void run() {
               Fx.beI(write);
            }
         }, 8000);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: save failed", t);
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
         dir = XAe.cCV();
      } catch (Throwable t) {
         NocBrowser.log("Track presets: plug-in state folder not found", t);
         return;
      }
      embedPluginStates(target, dir);
   }

   static void embedPluginStates(File target, File dir) {
      try {
         byte[] data = java.nio.file.Files.readAllBytes(target.toPath());
         if (data.length < 40 || data[0] != 'B' || data[1] != 't' || data[2] != 'W' || data[3] != 'g') {
            NocBrowser.log("Track presets: " + target.getName() + " has an unexpected header; plug-in states not added", null);
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
            NocBrowser.log("Track presets: no plug-in state files found for " + target.getName() + " (missing " + missing + ")", null);
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
         NocBrowser.log("Track presets: added " + added + " plug-in state(s) to " + target.getName()
            + (missing.isEmpty() ? "" : " (still missing " + missing + ")"), null);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not add plug-in states to " + target, t);
      }
   }

   // ------------------------------------------------------------------ load

   static void load(File file, Object after, CmH project) {
      try {
         CmH dst = after != null ? document(after) : project;
         if (dst == null) {
            NocBrowser.log("Track presets: can't find the project to insert into", null);
            return;
         }
         try {
            gwa.beI(file);   // unpack the preset's plug-in states (as Bitwig does for packaged files)
         } catch (Throwable ignored) {
            // no plug-in states inside (older preset or none needed)
         }
         CmH preset = CmH.beI(file, XAe.unz());
         GJy from = preset.svk();
         List<Object> tracks = new ArrayList<>();
         for (int i = 0; i < from.qaW(); i++) {
            tracks.add(from.MgQ(i));
         }
         if (tracks.isEmpty()) {
            NocBrowser.log("Track presets: " + file.getName() + " has no tracks", null);
            return;
         }

         GJy into = dst.svk();
         int at = into.qaW();
         try {
            int i = after == null ? -1 : into.MgQ((zCn)after);
            if (i >= 0) {
               at = i + 1;
            }
         } catch (Throwable ignored) {
            // nested track (inside a group): add at the end of the project
         }
         into.MgQ(tracks, at, true, null);
         NocBrowser.log("Track presets: inserted " + tracks.size() + " track(s) from " + file.getName() + " at " + at, null);
      } catch (Throwable t) {
         NocBrowser.log("Track presets: insert failed", t);
      }
   }

   // ------------------------------------------------------------------ Tracks sidebar button

   /** Browser button listing the track presets (projects in My Library/Track Presets). */
   static final class Source extends Wc {
      @Override
      public String ymo() {
         return "suite_tracks";
      }

      @Override
      public String io_() {
         return "Tracks";
      }

      @Override
      public List MgQ() {
         return List.of("tracks", "track presets");
      }

      @Override
      public java.util.Set scr() {
         return java.util.Set.of(qV.ymo);   // projects
      }

      @Override
      public WX Heb() {
         return WX.Gef;
      }

      @Override
      public jma Gef(EN dn) {
         if (!asked) {
            asked = true;
            NocBrowser.log("Tracks button: searching for track presets", null);
         }
         return PresetsOnly.INSTANCE;
      }
   }

   private static volatile boolean asked;

   public static Wc source() {
      return SOURCE;
   }

   /** Matches files under a "Track Presets" folder; judged per file. */
   static final class PresetsOnly extends jma {
      static final PresetsOnly INSTANCE = new PresetsOnly();

      @Override
      public AKW beI(ZWh cursor, oiy context) {
         return new InFolder(cursor);
      }

      @Override
      public void a_(vQq out) {
      }

      @Override
      public boolean beI(jma other) {
         return other instanceof PresetsOnly;
      }

      @Override
      public int Gef() {
         return 3;
      }

      @Override
      public void beI(List list) {
      }

      @Override
      public java.util.Set beI(xme kind) {
         return null;
      }
   }


   static final class InFolder extends AKW {
      private final ZWh cursor;

      InFolder(ZWh cursor) {
         this.cursor = cursor;
      }

      @Override
      public void beI() {
      }

      @Override
      public QlC beI(QlC parent) {
         return QlC.Gef;   // decide per file
      }

      @Override
      public void sCA() {
      }

      @Override
      public boolean Gef() {
         return isPreset(cursor);
      }
   }

   /** True for a browser item stored under a "Track Presets" folder. */
   static boolean isPreset(ZWh cursor) {
      String full = "", dir = "", name = "", root = "";
      try {
         try {
            full = String.valueOf(cursor.Bqk());   // path inside the location, folders included
         } catch (Throwable ignored) {
         }
         try {
            dir = String.valueOf(cursor.BBg());
         } catch (Throwable ignored) {
         }
         try {
            name = String.valueOf(cursor.unz());
         } catch (Throwable ignored) {
         }
         try {
            Ff dd = ((PE)cursor).BZt();
            root = dd == null || dd.neH() == null ? "" : dd.neH().getPath();
         } catch (Throwable ignored) {
         }
         String all = ("/" + root + "/" + full + "|/" + root + "/" + dir + "/" + name).replace('\\', '/').toLowerCase(Locale.ROOT);
         boolean in = !all.contains("/.previous/") && all.contains("/track presets/");
         if (SEEN.size() < 12 && SEEN.add(full + "|" + dir)) {
            NocBrowser.log("Tracks: project item path='" + full + "' dir='" + dir + "' name='" + name + "' in '" + root + "' -> " + in, null);
         }
         return in;
      } catch (Throwable t) {
         return false;
      }
   }

   private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** Hook at the start of cAX(qg...): the main browser also searches projects, so track presets can show. */
   public static qV[] kinds(qV[] kinds) {
      try {
         for (qV k : kinds) {
            if (k == qV.ymo) {
               return kinds;
            }
         }
         qV[] out = Arrays.copyOf(kinds, kinds.length + 1);
         out[kinds.length] = qV.ymo;
         return out;
      } catch (Throwable t) {
         return kinds;
      }
   }

   // ------------------------------------------------------------------ helpers

   private static String describe(List<Object> tracks, CmH src) {
      StringBuilder b = new StringBuilder();
      for (Object t : tracks) {
         CmH d = document(t);
         b.append(t.getClass().getSimpleName()).append(d == src ? " in this project" : d == null ? " no project" : " in another project").append("; ");
      }
      return b.toString();
   }

   private static CmH document(Object track) {
      try {
         Object d = ((zCn)track).z3j();
         return d instanceof CmH o ? o : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static String name(Object track) {
      try {
         String n = ((qAF)track).cc_();
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
         EN dn = EN.unz();
         for (List<?> list : List.of(dn.fmw(), dn.BZt(), dn.qJX(), dn.jTT(), dn.CtA())) {
            for (Object o : list) {
               if (o instanceof Ff dd && "MY_LIBRARY".equals(String.valueOf(dd.ymo())) && dd.neH() != null) {
                  return new File(dd.neH(), "Track Presets");
               }
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Track presets: My Library not found", t);
      }
      return new File(System.getProperty("user.home"), "Documents/Bitwig Studio/Library/Track Presets");
   }

   /** The 0.8.0 test build saved .bwtrack files elsewhere: move them in once. */
   private static void migrate(File dir) {
      try {
         File collections = NocCollectionFolders.collectionsDir();
         if (collections == null || collections.getParentFile() == null) {
            return;
         }
         File old = new File(collections.getParentFile().getParentFile(), "track-presets");
         File[] files = old.listFiles(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(OLD_EXT));
         if (files == null) {
            return;
         }
         dir.mkdirs();
         for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - OLD_EXT.length());
            File to = new File(dir, base + EXT);
            if (!to.exists()) {
               java.nio.file.Files.move(f.toPath(), to.toPath());
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not move old presets", t);
      }
   }

   static File[] presets() {
      File dir = folder();
      migrate(dir);
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
         new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
      } catch (Throwable t) {
         NocBrowser.log("Track presets: could not open " + folder(), t);
      }
   }
}
