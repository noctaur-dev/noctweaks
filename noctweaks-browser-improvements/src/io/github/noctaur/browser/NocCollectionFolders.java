package io.github.noctaur.browser;

import bitwig.obf.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NocTweaks: folders for Bitwig collections.
 *
 * Collections stored in sub-folders of library/collections/7 are loaded with a
 * path-style name ("Effects/Delay") and shown as nested folders under Collections
 * in the browser's Location tree.
 *
 * Lives in the default package on purpose: Bitwig's obfuscated browser classes are
 * there too, and their protected members are reachable from the same package.
 *
 * Hooks (inserted by the build-time patcher):
 *   FC.jPB()            -> load(dir, list)            recursive loading
 *   El.IYM(int, FD)     -> attach(root, node, index)  instead of root.AOA(node)
 *   El.IYM(BUQ)         -> built(root)                after all nodes are recorded
 *   El.a3J()            -> hit(root, index) / itemDone(root)   per-folder counts
 *   El.hU_()            -> removeChild(root, node) / finish(el, root)
 *
 * Every hook falls back to Bitwig's stock behaviour if anything goes wrong.
 */
public final class NocCollectionFolders {
   private static final String SEP = "/";
   private static final int MAX_DEPTH = 8;
   static final Order ORDER = new Order();

   private NocCollectionFolders() {
   }

   // ------------------------------------------------------------------ loading

   private static volatile File collectionsDir;
   private static final Map<Object, String> FOLDER_KEYS = Collections.synchronizedMap(new WeakHashMap<>());

   /** The collections folder seen at load time (library/collections/7). */
   public static File collectionsDir() {
      return collectionsDir;
   }

   /** Folder path ("Effects/Time") for a folder node we created, else null. */
   public static String folderKey(Object node) {
      return FOLDER_KEYS.get(node);
   }

   private static volatile scz collectionsWwc;

   /** The collections folder as Bitwig's file handle (library/collections/7). */
   static scz collectionsWwc() {
      return collectionsWwc;
   }

   public static void load(scz dir, List out) {
      collectionsWwc = dir;
      // Top level: exactly what stock Bitwig does.
      for (Object o : dir.jTs()) {
         scz f = (scz)o;
         if (f.Heb() && f.byf().endsWith(".collection")) {
            add(out, f.Bqk(), f);
         }
      }

      File root;
      try {
         root = dir.af();
      } catch (Throwable t) {
         return;
      }

      if (root != null && root.isDirectory()) {
         collectionsDir = root;
         for (File sub : sorted(root.listFiles(File::isDirectory))) {
            if (!hidden(sub)) {
               walk(dir.unz(sub.getName()), sub, sub.getName(), out, 1);
            }
         }
      }
   }

   private static void walk(scz wdir, File fdir, String prefix, List out, int depth) {
      if (depth > MAX_DEPTH) {
         return;
      }

      for (File f : sorted(fdir.listFiles(File::isFile))) {
         String n = f.getName();
         if (n.endsWith(".collection") && !hidden(f)) {
            String base = n.substring(0, n.length() - ".collection".length());
            add(out, prefix + SEP + base, wdir.unz(n));
         }
      }

      for (File sub : sorted(fdir.listFiles(File::isDirectory))) {
         if (!hidden(sub)) {
            walk(wdir.unz(sub.getName()), sub, prefix + SEP + sub.getName(), out, depth + 1);
         }
      }
   }

   private static void add(List out, String name, scz file) {
      try {
         out.add(GS.beI(name, 0, file));
      } catch (Throwable t) {
         log("Error loading collection " + name, t);
      }
   }

   private static boolean hidden(File f) {
      return f.getName().startsWith(".");  // also skips the .suite settings folder
   }

   private static File[] sorted(File[] files) {
      if (files == null) {
         return new File[0];
      }

      Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
      return files;
   }

   // ------------------------------------------------------------------ tree

   static final class State {
      final List<Ek> nodes = new ArrayList<>();
      final Map<String, KZ> folders = new LinkedHashMap<>();     // container per path: folder (xG) or parent collection (FJ)
      final Map<String, String> folderParent = new HashMap<>();
      final List<KZ> folderList = new ArrayList<>();
      int[][] foldersOf = new int[0][];
      int[] counts = new int[0];
      boolean[] marked = new boolean[0];
      int[] markedList = new int[0];
      int markedCount;
      boolean active;
      final List<Ek> pendingRemove = new ArrayList<>();   // empty nodes El asked to hide; finish() decides
   }

   private static final Map<yO, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile yO lastRoot;
   private static volatile State lastState;

   private static State state(yO root) {
      yO r = lastRoot;
      State s = lastState;
      if (r == root && s != null) {
         return s;
      }

      s = STATES.get(root);
      if (s != null) {
         lastState = s;
         lastRoot = root;
      }

      return s;
   }

   /** Replaces root.AOA(node): only records the node; built() places it. */
   public static void attach(yO root, Ek node, int index) {
      State s = index == 0 ? null : STATES.get(root);
      if (s == null) {
         s = new State();
         STATES.put(root, s);
         lastRoot = null;
         lastState = null;
      }

      while (s.nodes.size() <= index) {
         s.nodes.add(null);
      }

      s.nodes.set(index, node);
   }

   /** Runs once all collections are recorded: creates folders and places every node. */
   public static void built(yO root) {
      State s = STATES.get(root);
      if (s == null) {
         return;
      }
      if (root.scr() != null && !root.scr().isEmpty()) {
         sectionName = root.scr();
      }

      try {
         place(root, s);
         s.active = !s.folderList.isEmpty();
      } catch (Throwable t) {
         log("Collection folders disabled for this view", t);
         s.active = false;
         s.folderList.clear();
         for (Ek node : s.nodes) {
            if (node != null && node.iqe() == null) {
               root.MgQ(node);
            }
         }
      }
   }

   private static void place(yO root, State s) {
      boolean anyFolder = false;
      for (Ek node : s.nodes) {
         if (node != null && node.scr() != null && node.scr().contains(SEP)) {
            anyFolder = true;
            break;
         }
      }

      if (!anyFolder) {
         // No folders anywhere: keep Bitwig's own order and behaviour untouched.
         for (Ek node : s.nodes) {
            if (node != null) {
               root.MgQ(node);
            }
         }
         return;
      }

      root.Gef = ORDER;

      // A collection "Drums" and a folder "Drums/" next to it are one thing: the collection holds the
      // folder's contents as sub-collections.
      Map<String, Ek> byName = new HashMap<>();
      Map<Ek, String> fullNames = new HashMap<>();
      for (Ek node : s.nodes) {
         if (node != null) {
            String full = fullName(node);
            if (full != null) {
               byName.putIfAbsent(full, node);
               fullNames.put(node, full);
            }
         }
      }

      Map<String, List<jma>> queries = new HashMap<>();
      List<int[]> foldersOf = new ArrayList<>();
      Set<String> parents = new HashSet<>();

      for (int i = 0; i < s.nodes.size(); i++) {
         Ek node = s.nodes.get(i);
         if (node == null) {
            foldersOf.add(new int[0]);
            continue;
         }

         String full = fullNames.get(node);
         String[] parts = full == null ? new String[0] : full.split(SEP);
         List<Integer> chain = new ArrayList<>();
         jma q = query(node);
         String key = "";
         for (int p = 0; p < parts.length - 1; p++) {
            String parentKey = key;
            key = key.isEmpty() ? parts[p] : key + SEP + parts[p];
            KZ f = s.folders.get(key);
            if (f == null) {
               Ek own = byName.get(key);
               if (own instanceof Hh) {
                  f = (KZ)own;
                  parents.add(key);
               } else {
                  yO folder = yO.BBg();
                  folder.Gef = ORDER;
                  folder.BBg(parts[p]);
                  FOLDER_KEYS.put(folder, key);
                  f = folder;
               }
               s.folders.put(key, f);
               s.folderParent.put(key, parentKey);
               s.folderList.add(f);
            }

            chain.add(s.folderList.indexOf(f));
            if (q != null) {
               queries.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
            }
         }

         foldersOf.add(chain.stream().mapToInt(Integer::intValue).toArray());
         if (parts.length > 1) {
            ((KZ)node).BBg(parts[parts.length - 1]);
         }
      }

      // A parent collection's own items count towards itself as well.
      for (int i = 0; i < s.nodes.size(); i++) {
         Ek node = s.nodes.get(i);
         String full = node == null ? null : fullNames.get(node);
         KZ own = full == null ? null : s.folders.get(full);
         if (own == node) {
            int[] chain = foldersOf.get(i);
            int[] withOwn = Arrays.copyOf(chain, chain.length + 1);
            withOwn[chain.length] = s.folderList.indexOf(own);
            foldersOf.set(i, withOwn);
            jma q = query(node);
            if (q != null) {
               queries.computeIfAbsent(full, k -> new ArrayList<>()).add(q);
            }
         }
      }

      Map<String, jma> parentQueries = new HashMap<>();
      List<String> keys = new ArrayList<>(s.folders.keySet());
      keys.sort(Comparator.comparingInt((String k) -> k.split(SEP).length).thenComparing(k -> k));
      for (String key : keys) {
         KZ f = s.folders.get(key);
         List<jma> qs = queries.get(key);
         jma union = qs == null || qs.isEmpty() ? null : JUW.beI((java.util.Collection)qs);
         if (f instanceof yO folder) {
            if (union != null) {
               folder.beI(union);
            }
            String parentKey = s.folderParent.get(key);
            KZ parent = parentKey == null || parentKey.isEmpty() ? root : s.folders.get(parentKey);
            parent.sCA((Ek)folder);
         } else if (union != null) {
            parentQueries.put(key, union);
         }
      }

      PARENTS = parents;
      PARENT_QUERIES = parentQueries;

      for (Ek node : s.nodes) {
         if (node == null) {
            continue;
         }

         String full = fullNames.get(node);
         int cut = full == null ? -1 : full.lastIndexOf(SEP);
         KZ parent = cut < 0 ? root : s.folders.get(full.substring(0, cut));
         (parent == null ? root : parent).sCA(node);
      }

      int n = s.folderList.size();
      s.foldersOf = foldersOf.toArray(new int[0][]);
      s.counts = new int[n];
      s.marked = new boolean[n];
      s.markedList = new int[n];
      s.markedCount = 0;
   }

   /** Hook at the start of xG.tY1() (copy a node): copies of our folders keep their name and identity. */
   public static KZ copyFolder(Object node) {
      String key = FOLDER_KEYS.get(node);
      if (key == null) {
         return null;
      }
      yO copy = yO.BBg();
      copy.Gef = ORDER;
      copy.BBg(((Ek)node).scr());
      FOLDER_KEYS.put(copy, key);
      return copy;
   }

   // Parent collections of the latest tree, by full name ("Drums", "Drums/Kits").
   private static volatile Set<String> PARENTS = Collections.emptySet();
   private static volatile Map<String, jma> PARENT_QUERIES = Collections.emptyMap();

   static boolean isParent(Object node) {
      if (!(node instanceof Hh) || PARENTS.isEmpty()) {
         return false;
      }
      String full = fullName((Ek)node);
      return full != null && PARENTS.contains(full);
   }

   /** Hook at the start of JN.hN_(): a parent collection orders its sub-collections like a folder. */
   public static LG nodeOrder(Object node) {
      return isParent(node) ? ORDER : null;
   }

   /** Hook at the start of FJ.IYM(Dn): a parent collection shows its own items plus all sub-collections. */
   public static FzK parentQuery(Object node) {
      if (!(node instanceof Hh) || PARENT_QUERIES.isEmpty()) {
         return null;
      }
      String full = fullName((Ek)node);
      jma q = full == null ? null : PARENT_QUERIES.get(full);
      return q == null ? null : new FzK(q);
   }

   private static String fullName(Ek node) {
      try {
         return ((Hh)node).zzi().BBg();
      } catch (Throwable t) {
         return node.scr();
      }
   }

   private static jma query(Ek node) {
      try {
         return ((Hh)node).zzi().byf();
      } catch (Throwable t) {
         return null;
      }
   }

   /** Called when an item matched collection #index. */
   public static void hit(yO root, int index) {
      State s = state(root);
      if (s == null || !s.active || index < 0 || index >= s.foldersOf.length) {
         return;
      }

      for (int f : s.foldersOf[index]) {
         if (f >= 0 && !s.marked[f]) {
            s.marked[f] = true;
            s.markedList[s.markedCount++] = f;
         }
      }
   }

   /** Called after each item: a folder counts an item once even if several of its collections hold it. */
   public static void itemDone(yO root) {
      State s = state(root);
      if (s == null || !s.active || s.markedCount == 0) {
         return;
      }

      for (int i = 0; i < s.markedCount; i++) {
         int f = s.markedList[i];
         s.counts[f]++;
         s.marked[f] = false;
      }

      s.markedCount = 0;
   }

   /** Replaces root.xeZ(node) in El.hU_(): removes a node from whichever folder holds it. */
   public static void removeChild(yO root, Ek node) {
      State s = STATES.get(root);
      if (s != null) {
         s.pendingRemove.add(node);   // finish() knows which tab this is and the parents' totals
         return;
      }
      KZ parent = node.iqe();
      if (parent != null) {
         parent.BBg(node);
      } else {
         root.BBg(node);
      }
   }

   /**
    * Sets folder counts and hides what's empty. In the Collections tab empty collections and folders
    * stay visible (a new collection starts empty); elsewhere Bitwig's own rule applies.
    */
   public static void finish(FL el, yO root) {
      boolean flat = NocBrowser.sourceActive(el);
      State s = STATES.get(root);
      if (s != null) {
         try {
            if (!flat) {
               for (Ek node : s.pendingRemove) {
                  if (!(s.active && s.folderList.contains(node))) {
                     KZ parent = node.iqe();
                     (parent != null ? parent : root).BBg(node);
                  }
               }
            }
            if (s.active) {
               for (int i = s.folderList.size() - 1; i >= 0; i--) {
                  KZ f = s.folderList.get(i);
                  f.byf(s.counts[i]);
                  if (!flat && s.counts[i] == 0 && el.beI((Ek)f, false)) {
                     KZ parent = f.iqe();
                     if (parent != null) {
                        parent.BBg(f);
                     }
                  }
               }
            }
         } catch (Throwable t) {
            log("Collection folder counts failed", t);
         } finally {
            s.pendingRemove.clear();
         }
      }

      KZ top = root.iqe();
      String seen = top == null ? null : top.scr();
      if (seen != null && !seen.equals("All") && !FOCUS_NAMES.contains(seen)) {
         stockRootName = seen;
      }
      String stockName = stockRootName;
      String focused = null;
      if (flat) {
         flatten(root);
      } else if (NocBrowser.currentSource(el) == NocTracks.SOURCE) {
         hideSection(root);
      } else {
         focused = focus(el, root);
      }
      // The root row reads "All" in the Collections tab (it means all collections there), Bitwig's own
      // name elsewhere. The shown tree is merged, not replaced, so rename that one too.
      try {
         String name = flat ? "All" : focused != null ? focused : stockName;
         KZ shown = el == null ? null : el.qJX();
         if (name != null && shown != null && !name.equals(shown.scr())) {
            shown.BBg(name);
         }
      } catch (Throwable t) {
         log("Could not rename the root row", t);
      }
   }

   private static volatile String stockRootName = "All Locations";

   /**
    * Collections sidebar button: the Location tree shows the collections and folders directly,
    * without the other sections and without the "Collections" heading.
    */
   /**
    * A collection pinned as a quick source: its Location column shows only that collection and the
    * collections inside it, at the top, instead of every location.
    */
   /** Names the root row was given for a pinned collection (so they're not taken for Bitwig's own name). */
   private static final java.util.Set<String> FOCUS_NAMES = java.util.concurrent.ConcurrentHashMap.newKeySet();
   /** The source each column last opened its tree for (its rows are expanded once per source). */
   private static final java.util.Map<FL, Object> EXPANDED_FOR = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   /** Tracks button: the Location column leaves out the Collections section (track presets aren't in them). */
   private static void hideSection(yO root) {
      try {
         KZ top = root.iqe();
         if (top != null) {
            top.BBg(root);
         }
      } catch (Throwable t) {
         log("Could not hide the Collections section", t);
      }
   }

   /**
    * A pinned collection: the root row becomes that collection (its name replaces "All"), with the collections
    * inside it directly under it, all expanded the first time it opens.
    */
   private static String focus(FL el, yO root) {
      try {
         Object src = NocBrowser.currentSource(el);
         if (!(src instanceof WC wc) || src == NocBrowser.SOURCE) {
            return null;
         }
         Hc target = wc.ymo();   // the pinned collection
         String name = target == null ? null : target.BBg();
         KZ top = root.iqe();
         if (name == null || top == null) {
            return null;
         }
         Ek node = find(root, name, 0);
         if (node == null) {
            return null;
         }
         KZ parent = node.iqe();
         if (parent != null) {
            parent.BBg(node);
         }
         for (Object child : new ArrayList<Object>(top.wLf())) {
            top.BBg((Ek)child);
         }
         if (node instanceof KZ k) {
            for (Object child : new ArrayList<Object>(k.wLf())) {
               k.BBg((Ek)child);
               top.sCA((Ek)child);
            }
            try {
               top.byf(k.PJ3());
            } catch (Throwable ignored) {
            }
         } else {
            top.sCA(node);
         }
         String shown = node.scr();
         if (shown == null || shown.isEmpty()) {
            int slash = name.lastIndexOf('/');
            shown = slash >= 0 ? name.substring(slash + 1) : name;
         }
         FOCUS_NAMES.add(shown);
         if (EXPANDED_FOR.get(el) != src) {
            EXPANDED_FOR.put(el, src);
            try {
               Eg open = el.VNw();
               if (open != null) {
                  open.Gef(top);
                  expandAll(top, open, 0);
               }
            } catch (Throwable t) {
               log("Could not expand the pinned collection", t);
            }
         }
         return shown;
      } catch (Throwable t) {
         log("Could not focus the pinned collection", t);
         return null;
      }
   }

   private static void expandAll(KZ node, Eg open, int depth) {
      if (depth > 24) {
         return;
      }
      for (Object child : new ArrayList<Object>(node.wLf())) {
         if (child instanceof KZ k) {
            open.Gef(k);
            expandAll(k, open, depth + 1);
         }
      }
   }

   /** The row of the collection with this full name, under node. */
   private static Ek find(KZ node, String name, int depth) {
      if (node == null || depth > 24) {
         return null;
      }
      for (Object child : new ArrayList<Object>(node.wLf())) {
         if (child instanceof Hh h) {
            try {
               if (name.equals(h.zzi().BBg())) {
                  return h;
               }
            } catch (Throwable ignored) {
            }
         }
         if (child instanceof KZ k) {
            Ek hit = find(k, name, depth + 1);
            if (hit != null) {
               return hit;
            }
         }
      }
      return null;
   }

   private static void flatten(yO root) {
      KZ top = root.iqe();
      if (top == null) {
         return;
      }

      List<Ek> moved = new ArrayList<>();
      try {
         for (Object child : new ArrayList<Object>(top.wLf())) {
            top.BBg((Ek)child);
         }
         for (Object child : new ArrayList<Object>(root.wLf())) {
            root.BBg((Ek)child);
            moved.add((Ek)child);
         }
         for (Ek child : moved) {
            top.sCA(child);
         }
         top.byf(root.PJ3());   // total of the Collections section (each item once)
      } catch (Throwable t) {
         log("Could not flatten the Collections view", t);
         // Put the section back in one piece.
         try {
            for (Ek child : moved) {
               if (child.iqe() == top) {
                  top.BBg(child);
               }
               if (child.iqe() == null) {
                  root.sCA(child);
               }
            }
            if (root.iqe() == null) {
               top.sCA((Ek)root);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private static volatile String sectionName = "Collections";

   /** The Collections section itself (or Bitwig's copy of it), as opposed to one of our folders. */
   static boolean isSection(Object node) {
      return node instanceof yO && (STATES.containsKey(node) || sectionName.equals(((Ek)node).scr()))
         && NocCollectionFolders.folderKey(node) == null;
   }

   /**
    * A node that can sit directly under the location root in the Collections tab. Decided by kind, not
    * by identity: Bitwig re-inserts copies of the selected row and its parents when it refreshes, and
    * those copies must still sort next to (and merge with) the originals.
    */
   private static boolean flat(Object node) {
      return node instanceof Hh || (node instanceof yO && !isSection(node));
   }

   /**
    * Hook at the start of CQ.IYM(JN, CL, JN) (publish a rebuilt tree, keeping the selected row). In the
    * Collections tab a selection that no longer exists there (the Collections heading, a device folder
    * from the Location tab) is not re-inserted, so it can't show up as a stray row.
    */
   public static Ek publishSelection(Object column, Ek selected, KZ fresh) {
      if (column instanceof FL) {
         NocBrowser.pruneSections(column, fresh);
      }
      try {
         if (selected == null || selected.iqe() == null || !NocBrowser.sourceActive(column) || flat(selected)) {
            return selected;
         }
         return null;
      } catch (Throwable t) {
         return selected;
      }
   }

   /** Hook at the start of DS.IYM(CL): the location root also accepts our flattened children. */
   public static boolean accepts(Ek node) {
      return flat(node);
   }

   /**
    * Hook at the start of DS.IYM(JN, JN), the location root's order. Returns Integer.MIN_VALUE to let
    * Bitwig decide. Flattened children sort among themselves like inside Collections, and right after
    * the Collections section relative to anything else, so trees can still be merged on refresh.
    */
   public static int compare(KZ a, KZ b) {
      boolean fa = flat(a);
      boolean fb = flat(b);
      if (!fa && !fb) {
         return Integer.MIN_VALUE;
      }
      if (fa && fb) {
         int c = ORDER.compare(a, b);
         return c != 0 ? c : ZQW.BBg(String.valueOf(a.scr()), String.valueOf(b.scr()));
      }
      long section = ((long)Fs.MgQ.ordinal() << 24) * 2 + 1;   // just after Collections
      long other = rank(fa ? b : a) * 2;
      int c = Long.compare(section, other);
      return fa ? c : -c;
   }

   private static long rank(KZ n) {
      Ff dd = n.oHY();
      if (dd != null) {
         return dd.CtA();
      }
      if (n instanceof yO) {
         return (long)Fs.MgQ.ordinal() << 24;
      }
      if (n instanceof Ft dr) {
         return (long)dr.JTf().ordinal() << 24;
      }
      return 0;
   }

   // ------------------------------------------------------------------ order

   /** Folders first (by name), then Favorites, then collections by name; same as stock for collections. */
   static final class Order implements LG {
      @Override
      public boolean beI(Ek c) {
         return c instanceof Hh || c instanceof yO;
      }

      @Override
      public int compare(Object a, Object b) {
         boolean fa = a instanceof yO;
         boolean fb = b instanceof yO;
         if (fa != fb) {
            return fa ? -1 : 1;
         }

         if (fa) {
            return ZQW.BBg(((Ek)a).scr(), ((Ek)b).scr());
         }

         if (a instanceof Hh x && b instanceof Hh y) {
            int fav = (y.mYH() ? 1 : 0) - (x.mYH() ? 1 : 0);
            // Full names: Bitwig's copies of a row carry the full name ("Drums/Kit"), the row itself
            // shows the short one. Siblings share the prefix, so the order is the same.
            return fav != 0 ? fav : ZQW.BBg(String.valueOf(fullName(x)), String.valueOf(fullName(y)));
         }

         return 0;
      }
   }

   private static void log(String message, Throwable t) {
      try {
         NocBrowser.log(message, t);
      } catch (Throwable ignored) {
      }
   }
}
