package io.github.noctaur.browser;

import bitwig.obf.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * NocTweaks: organising collections from inside Bitwig's browser.
 *
 * On disk a collection "Drums/Kits" is library/collections/7/Drums/Kits.collection; a collection can
 * hold sub-collections by having a folder of the same name next to it (Drums.collection + Drums/).
 *
 * Hooks (inserted by the build-time patcher):
 *   cFb.IYM(dKG, List)  -> menu(view, menu, selection)  right-click menu entries in the Location tree
 *   Dn.IYM(String, int) -> createName(name)              "New collection inside…" puts it in the folder
 *   FD.IYM(String)      -> rename(fd, name)              renames and moves keep files and sub-collections together
 *   cDC.jPB(ti, Z)      -> treeView(view, model)         lets the Collections tab hide the "All Locations" row
 */
public final class NocEdit {
   private static final String SEP = "/";

   private NocEdit() {
   }

   // ------------------------------------------------------------------ right-click menu

   public static void menu(cGK view, dMt menu, List selection) {
      pendingParent = null;
      try {
         if (selection == null || selection.isEmpty()) {
            return;
         }

         List<String> collections = new ArrayList<>();   // full names of selected collections
         List<String> folders = new ArrayList<>();       // keys of selected plain folders
         for (Object o : selection) {
            if (o instanceof Hh fj && !fj.mYH()) {
               collections.add(fj.zzi().BBg());
            } else if (o instanceof yO && NocCollectionFolders.folderKey(o) != null) {
               folders.add(NocCollectionFolders.folderKey(o));
            } else {
               return;   // something else is selected (a section, a device folder…): leave Bitwig's menu alone
            }
         }

         EN dn = view.qVz().WYl();
         if (dn == null || NocCollectionFolders.collectionsWwc() == null) {
            return;
         }

         menu.fpX();
         List<String> iconKeys = new ArrayList<>(collections);
         for (String f : folders) {
            iconKeys.add(f + SEP);
         }
         String what = iconKeys.size() == 1 ? "\"" + leaf(iconKeys.get(0).replaceAll("/$", "")) + "\"" : iconKeys.size() + " items";
         menu.beI("Set Icon…", () -> IconWindow.open(iconKeys, what));
         if (selection.size() == 1 && !collections.isEmpty() && selection.get(0) instanceof Hh row) {
            Hc c = row.zzi();
            if (NocBrowser.pinned(c)) {
               menu.beI("Unpin from Quick Sources", () -> NocBrowser.unpinCollection(c));
            } else {
               menu.beI("Pin to Quick Sources", () -> NocBrowser.pinCollection(c));
            }
         }
         dMt buttons = menu.sCA("Sidebar Buttons");
         buttons.beI("Collections Button Icon…", () -> IconWindow.open(List.of("@sidebar"), "Collections button"));
         buttons.beI("Tracks Button Icon…", () -> IconWindow.open(List.of("@tracks"), "Tracks button"));
         buttons.fpX();
         for (String which : new String[]{"collections", "tracks"}) {
            boolean shown = !NocBrowser.hidden(which);
            String name = "collections".equals(which) ? "Collections" : "Tracks";
            buttons.beI((shown ? "✓ " : "    ") + "Show " + name + " Button", () -> NocBrowser.setHidden(which, shown));
         }
         if (selection.size() == 1) {
            String parent = collections.isEmpty() ? folders.get(0) : collections.get(0);
            menu.beI("New Collection Inside \"" + leaf(parent) + "\"…", () -> {
               pendingParent = parent;
               pendingSince = System.currentTimeMillis();
               cDk.beI((dwF)view, dn);
            });
         }

         if (selection.size() == 1 && !folders.isEmpty()) {
            String folder = folders.get(0);
            menu.beI("Remove Folder \"" + leaf(folder) + "\" (keep its collections)", () -> removeFolder(dn, folder));
         }

         List<String> moving = new ArrayList<>(collections);
         moving.addAll(folders);
         Set<String> targets = targets(dn);
         dMt sub = menu.sCA("Move To");
         boolean anyNested = moving.stream().anyMatch(k -> k.contains(SEP));
         if (anyNested) {
            sub.beI("Top Level", () -> move(dn, collections, folders, ""));
         }
         int added = 0;
         for (String t : targets) {
            boolean ok = true;
            for (String m : moving) {
               if (t.equals(m) || t.startsWith(m + SEP) || t.equals(parentOf(m))) {
                  ok = false;
                  break;
               }
            }
            if (ok) {
               String label = t.replace(SEP, "  ›  ");
               sub.beI(label, () -> move(dn, collections, folders, t));
               added++;
            }
         }
         if (added == 0 && !anyNested) {
            sub.beI("(nowhere else to move to)", () -> {
            });
         }
      } catch (Throwable t) {
         NocBrowser.log("Could not extend the collection menu", t);
      }
   }

   /** Every collection and folder that can hold others. */
   private static Set<String> targets(EN dn) {
      Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      for (Object o : dn.sBA()) {
         String name = ((Hc)o).BBg();
         if (name == null) {
            continue;
         }
         out.add(name);
         int cut = name.lastIndexOf(SEP);
         while (cut > 0) {
            name = name.substring(0, cut);
            out.add(name);
            cut = name.lastIndexOf(SEP);
         }
      }
      return out;
   }

   private static String leaf(String path) {
      int cut = path.lastIndexOf(SEP);
      return cut < 0 ? path : path.substring(cut + 1);
   }

   private static String parentOf(String path) {
      int cut = path.lastIndexOf(SEP);
      return cut < 0 ? "" : path.substring(0, cut);
   }

   // ------------------------------------------------------------------ create

   private static volatile String pendingParent;
   private static volatile long pendingSince;

   /** Hook at the start of Dn.IYM(String, int) (create collection): returns the name to create. */
   public static String createName(String name) {
      String parent = pendingParent;
      pendingParent = null;
      if (parent == null || name == null || name.contains(SEP)
         || System.currentTimeMillis() - pendingSince > 10 * 60 * 1000) {
         return name;
      }
      try {
         File root = NocCollectionFolders.collectionsWwc().af();
         new File(root, parent.replace(SEP, File.separator)).mkdirs();
         return parent + SEP + name;
      } catch (Throwable t) {
         NocBrowser.log("Could not create the collection inside " + parent, t);
         return name;
      }
   }

   // ------------------------------------------------------------------ move / rename

   private static final Set<String> EXPLICIT = Collections.synchronizedSet(new HashSet<>());
   private static final ThreadLocal<Boolean> CHILD = ThreadLocal.withInitial(() -> false);

   private static void move(EN dn, List<String> collections, List<String> folders, String target) {
      hold();
      try {
         for (String c : collections) {
            Hc fd = dn.BBg(c);
            if (fd != null) {
               String to = free(dn, target.isEmpty() ? leaf(c) : target + SEP + leaf(c));
               EXPLICIT.add(to);
               try {
                  fd.beI(to);
               } finally {
                  EXPLICIT.remove(to);
               }
            }
         }
         for (String f : folders) {
            moveFolder(dn, f, free(dn, target.isEmpty() ? leaf(f) : target + SEP + leaf(f)));
         }
      } catch (Throwable t) {
         NocBrowser.log("Move failed", t);
      } finally {
         release();
      }
   }

   /** The name itself if nothing uses it yet, otherwise "Name 2", "Name 3", … */
   private static String free(EN dn, String name) {
      File root = NocCollectionFolders.collectionsWwc().af();
      String candidate = name;
      for (int i = 2; dn.BBg(candidate) != null || new File(root, (candidate + ".collection").replace(SEP, File.separator)).exists()
         || new File(root, candidate.replace(SEP, File.separator)).exists(); i++) {
         candidate = name + " " + i;
      }
      return candidate;
   }

   /** Moves a plain folder (no collection of its own) and everything inside it. */
   private static void moveFolder(EN dn, String from, String to) throws Exception {
      File root = NocCollectionFolders.collectionsWwc().af();
      File src = new File(root, from.replace(SEP, File.separator));
      File dst = new File(root, to.replace(SEP, File.separator));
      if (!src.isDirectory() || dst.exists() || to.startsWith(from + SEP) || clash(dn, from, to, null)) {
         NocBrowser.log("Can't move folder " + from + " to " + to + " (name taken?)", null);
         return;
      }
      dst.getParentFile().mkdirs();
      Files.move(src.toPath(), dst.toPath());
      renameChildren(dn, from, to);
      renameIcons(from, to);
   }

   /**
    * Removes a plain folder: everything inside moves up one level. A collection whose name is already
    * taken up there is renamed "Name (from Folder)"; an exact duplicate of the one up there (same
    * file contents) is removed instead, with a copy kept in .suite/trash. Nothing else is deleted.
    */
   private static void removeFolder(EN dn, String key) {
      hold();
      try {
         File root = NocCollectionFolders.collectionsWwc().af();
         File dir = new File(root, key.replace(SEP, File.separator));
         File up = dir.getParentFile();
         String parent = parentOf(key);
         String[] names = dir.list();
         if (!dir.isDirectory() || names == null) {
            return;
         }

         // Each entry stem: "Delay" for Delay.collection and/or a Delay\ folder (they move together).
         Set<String> stems = new TreeSet<>();
         for (String n : names) {
            stems.add(n.endsWith(".collection") ? n.substring(0, n.length() - ".collection".length()) : n);
         }
         java.util.Map<String, String> newStem = new java.util.HashMap<>();
         Set<String> duplicates = new HashSet<>();
         for (String stem : stems) {
            File file = new File(dir, stem + ".collection");
            File sub = new File(dir, stem);
            File upFile = new File(up, stem + ".collection");
            File upSub = new File(up, stem);
            boolean taken = upFile.exists() || upSub.exists();
            if (!taken) {
               newStem.put(stem, stem);
            } else if (file.isFile() && !sub.exists() && upFile.isFile()
               && java.util.Arrays.equals(Files.readAllBytes(file.toPath()), Files.readAllBytes(upFile.toPath()))) {
               duplicates.add(stem);
            } else {
               String base = stem + " (from " + leaf(key) + ")";
               String candidate = base;
               for (int i = 2; new File(up, candidate + ".collection").exists() || new File(up, candidate).exists()
                  || newStem.containsValue(candidate); i++) {
                  candidate = base + " " + i;
               }
               newStem.put(stem, candidate);
            }
         }

         // Duplicates: keep a copy in .suite/trash, then let Bitwig delete the collection.
         for (String stem : duplicates) {
            String full = key + SEP + stem;
            Hc fd = dn.BBg(full);
            File src = new File(dir, stem + ".collection");
            File trash = new File(NocBrowser.stateDir(), "trash");
            trash.mkdirs();
            Files.copy(src.toPath(), new File(trash, full.replace(SEP, " - ") + " " + System.currentTimeMillis()
               + ".collection").toPath());
            if (fd != null) {
               dn.Gef(fd);
            }
            if (src.exists()) {
               src.delete();
            }
         }

         for (java.util.Map.Entry<String, String> e : newStem.entrySet()) {
            File file = new File(dir, e.getKey() + ".collection");
            File sub = new File(dir, e.getKey());
            if (file.exists()) {
               Files.move(file.toPath(), new File(up, e.getValue() + ".collection").toPath());
            }
            if (sub.exists()) {
               Files.move(sub.toPath(), new File(up, e.getValue()).toPath());
            }
         }
         String[] left = dir.list();
         if (left != null && left.length == 0) {
            dir.delete();
         }

         List<Hc> inside = new ArrayList<>();
         for (Object o : dn.sBA()) {
            String n = ((Hc)o).BBg();
            if (n != null && n.startsWith(key + SEP)) {
               inside.add((Hc)o);
            }
         }
         for (Hc c : inside) {
            String rest = c.BBg().substring(key.length() + 1);
            int cut = rest.indexOf(SEP);
            String stem = cut < 0 ? rest : rest.substring(0, cut);
            String to = newStem.get(stem);
            if (to == null) {
               continue;
            }
            String moved = to + rest.substring(stem.length());
            String n = parent.isEmpty() ? moved : parent + SEP + moved;
            c.Gef(wwc(n + ".collection"));
            EXPLICIT.add(n);
            CHILD.set(true);
            try {
               c.beI(n);
            } finally {
               CHILD.set(false);
               EXPLICIT.remove(n);
            }
         }
         for (java.util.Map.Entry<String, String> e : newStem.entrySet()) {
            renameIcons(key + SEP + e.getKey(), parent.isEmpty() ? e.getValue() : parent + SEP + e.getValue());
         }
      } catch (Throwable t) {
         NocBrowser.log("Remove folder failed", t);
      } finally {
         release();
      }
   }

   /**
    * Hook at the start of FD.IYM(String) (rename a collection). Returns the name to set without running
    * Bitwig's own rename, or null to let Bitwig rename a plain top-level collection as usual.
    */
   public static String rename(Hc fd, String name) {
      try {
         String old = fd.BBg();
         if (old == null || name == null || !(fd instanceof GS) || NocCollectionFolders.collectionsWwc() == null) {
            return null;
         }
         File root = NocCollectionFolders.collectionsWwc().af();
         File oldDir = new File(root, old.replace(SEP, File.separator));

         String target;
         if (EXPLICIT.contains(name)) {
            target = name;
         } else if (name.contains(SEP)) {
            target = name.replaceAll("^/+", "");
         } else if (old.contains(SEP)) {
            target = parentOf(old) + SEP + name;             // rename inside its folder
         } else if (oldDir.isDirectory()) {
            target = name;                                   // top-level parent: rename its folder too
         } else {
            return null;
         }

         if (target.equals(old)) {
            return old;
         }
         for (String seg : target.split(SEP)) {
            if (!validName(seg)) {
               NocBrowser.log("Invalid collection name: " + target, null);
               return old;
            }
         }
         EN dn = fd.y3f();
         if (target.startsWith(old + SEP) || (dn != null && clash(dn, old, target, fd))) {
            NocBrowser.log("Can't rename " + old + " to " + target + " (name taken?)", null);
            return old;
         }

         File src = fd.Hqm().af();
         File dst = new File(root, (target + ".collection").replace(SEP, File.separator));
         File newDir = new File(root, target.replace(SEP, File.separator));
         if (oldDir.isDirectory() && newDir.exists() && !newDir.equals(oldDir)) {
            NocBrowser.log("Can't move " + old + ": a folder " + target + " already exists", null);
            return old;
         }
         if (!src.equals(dst)) {
            if (dst.exists()) {
               return old;
            }
            dst.getParentFile().mkdirs();
            Files.move(src.toPath(), dst.toPath());
         }
         fd.Gef(wwc(target + ".collection"));
         if (oldDir.isDirectory() && !newDir.equals(oldDir)) {
            Files.move(oldDir.toPath(), newDir.toPath());
         }
         if (!CHILD.get()) {
            // The sub-collections follow at the end of this event, after Bitwig has applied the parent's
            // new name, and the browser redraws once when everything is done.
            hold();
            final String from = old;
            final String to = target;
            final File emptied = src.getParentFile();
            Fx.Gef(() -> {
               try {
                  if (dn != null) {
                     renameChildren(dn, from, to);
                  }
                  renameIcons(from, to);
                  pruneEmpty(emptied, root);
               } catch (Throwable t) {
                  NocBrowser.log("Rename of sub-collections failed", t);
               } finally {
                  releaseNow();
               }
            });
         }
         return target;
      } catch (Throwable t) {
         NocBrowser.log("Rename failed", t);
         return fd.BBg();
      }
   }

   private static boolean validName(String seg) {
      if (seg.isBlank() || seg.startsWith(".") || seg.endsWith(".") || seg.endsWith(" ")) {
         return false;
      }
      for (char ch : seg.toCharArray()) {
         if (ch < 32 || "<>:\"/\\|?*".indexOf(ch) >= 0) {
            return false;
         }
      }
      return true;
   }

   /** Another collection already has the new name (or one of the moved children's new names). */
   private static boolean clash(EN dn, String from, String to, Hc self) {
      Hc existing = dn.BBg(to);
      if (existing != null && existing != self) {
         return true;
      }
      for (Object o : dn.sBA()) {
         String n = ((Hc)o).BBg();
         if (n != null && n.startsWith(from + SEP) && dn.BBg(to + n.substring(from.length())) != null) {
            return true;
         }
      }
      return false;
   }

   /** Files have already moved with their folder: point each sub-collection at its new place and name. */
   private static void renameChildren(EN dn, String from, String to) {
      List<Hc> children = new ArrayList<>();
      for (Object o : dn.sBA()) {
         String n = ((Hc)o).BBg();
         if (n != null && n.startsWith(from + SEP)) {
            children.add((Hc)o);
         }
      }
      for (Hc c : children) {
         String n = to + c.BBg().substring(from.length());
         c.Gef(wwc(n + ".collection"));
         EXPLICIT.add(n);
         CHILD.set(true);
         try {
            c.beI(n);
         } finally {
            CHILD.set(false);
            EXPLICIT.remove(n);
         }
      }
   }

   private static scz wwc(String relative) {
      scz w = NocCollectionFolders.collectionsWwc();
      for (String seg : relative.split(SEP)) {
         w = w.unz(seg);
      }
      return w;
   }

   private static void pruneEmpty(File dir, File root) {
      while (dir != null && !dir.equals(root) && dir.isDirectory()) {
         String[] left = dir.list();
         if (left == null || left.length > 0 || !dir.delete()) {
            return;
         }
         dir = dir.getParentFile();
      }
   }

   /** Keeps custom icons attached when collections or folders move. */
   private static void renameIcons(String from, String to) {
      try {
         File f = new File(NocBrowser.stateDir(), "icons.txt");
         if (!f.isFile()) {
            return;
         }
         List<String> out = new ArrayList<>();
         boolean changed = false;
         for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.strip().startsWith("#")) {
               String key = line.substring(0, eq).strip();
               String bare = key.endsWith(SEP) ? key.substring(0, key.length() - 1) : key;
               if (bare.equals(from) || bare.startsWith(from + SEP)) {
                  String moved = to + key.substring(from.length());
                  line = moved + " " + line.substring(eq);
                  changed = true;
               }
            }
            out.add(line);
         }
         if (changed) {
            File tmp = new File(f.getParentFile(), "icons.txt.tmp");
            Files.write(tmp.toPath(), out, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable t) {
         NocBrowser.log("Could not update icons.txt after a move", t);
      }
   }

   // ------------------------------------------------------------------ one redraw per change

   private static int holds;   // UI thread only

   /** Hook at the start of Ij.LVA() (schedule a browser refresh): skipped while a change is half done. */
   public static boolean holdRefresh(Object session) {
      if (holds <= 0) {
         return false;
      }
      if (System.currentTimeMillis() - holdSince > 3000) {   // never leave the browser stuck
         holds = 0;
         return false;
      }
      return true;
   }

   private static long holdSince;

   private static void hold() {
      if (holds <= 0) {
         holdSince = System.currentTimeMillis();
      }
      holds++;
   }

   /** Ends a change at the end of the current event (after anything else it set off). */
   private static void release() {
      try {
         Fx.Gef(NocEdit::releaseNow);
      } catch (Throwable t) {
         releaseNow();
      }
   }

   private static void releaseNow() {
      if (--holds > 0) {
         return;
      }
      holds = 0;
      for (Object el : new ArrayList<>(VIEWS.keySet())) {
         try {
            ((FL)el).p1J();   // marks the Location column for refresh and schedules it
         } catch (Throwable t) {
            NocBrowser.log("Refresh failed", t);
         }
      }
   }

   // ------------------------------------------------------------------ "All Locations" row

   private static final Map<Object, Object> VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<Object, Boolean> SHOW_ROOT = Collections.synchronizedMap(new WeakHashMap<>());

   /** Hook at the end of cDC.jPB(ti, boolean): remembers which tree widget shows which column. */
   public static void treeView(Object view, Object model) {
      if (model instanceof FL && view instanceof cFm) {
         VIEWS.put(model, view);
      }
   }

   /** Hides the tree's root row while the Collections tab is active; restores it otherwise. */
   static void showRoot(FL el, boolean flat) {
      try {
         Object v = VIEWS.get(el);
         if (!(v instanceof cFm view)) {
            return;
         }
         eow tree = view.Anf;
         Boolean original = SHOW_ROOT.get(tree);
         if (original == null) {
            if (!flat) {
               return;   // never changed it: nothing to restore
            }
            original = tree.Anf;
            SHOW_ROOT.put(tree, original);
         }
         boolean want = flat ? false : original;
         if (tree.Anf != want) {
            Fx.Gef(() -> {
               try {
                  tree.Heb(want);
               } catch (Throwable t) {
                  NocBrowser.log("Could not change the root row", t);
               }
            });
         }
      } catch (Throwable t) {
         NocBrowser.log("Could not change the root row", t);
      }
   }
}
