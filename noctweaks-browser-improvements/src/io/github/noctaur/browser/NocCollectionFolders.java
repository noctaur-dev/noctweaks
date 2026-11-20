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

   private static volatile YBi collectionsWwc;

   /** The collections folder as Bitwig's file handle (library/collections/7). */
   static YBi collectionsWwc() {
      return collectionsWwc;
   }

   public static void load(YBi dir, List out) {
      collectionsWwc = dir;
      // Top level: exactly what stock Bitwig does.
      for (Object o : dir.ucQ()) {
         YBi f = (YBi)o;
         if (f.TtY() && f.TMF().endsWith(".collection")) {
            add(out, f.lYc(), f);
         }
      }

      File root;
      try {
         root = dir.vpw();
      } catch (Throwable t) {
         return;
      }

      if (root != null && root.isDirectory()) {
         collectionsDir = root;
         for (File sub : sorted(root.listFiles(File::isDirectory))) {
            if (!hidden(sub)) {
               walk(dir.UNj(sub.getName()), sub, sub.getName(), out, 1);
            }
         }
      }
   }

   private static void walk(YBi wdir, File fdir, String prefix, List out, int depth) {
      if (depth > MAX_DEPTH) {
         return;
      }

      for (File f : sorted(fdir.listFiles(File::isFile))) {
         String n = f.getName();
         if (n.endsWith(".collection") && !hidden(f)) {
            String base = n.substring(0, n.length() - ".collection".length());
            add(out, prefix + SEP + base, wdir.UNj(n));
         }
      }

      for (File sub : sorted(fdir.listFiles(File::isDirectory))) {
         if (!hidden(sub)) {
            walk(wdir.UNj(sub.getName()), sub, prefix + SEP + sub.getName(), out, depth + 1);
         }
      }
   }

   private static void add(List out, String name, YBi file) {
      try {
         out.add(GK.cfM(name, 0, file));
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
      final List<DZ> nodes = new ArrayList<>();
      final Map<String, KI> folders = new LinkedHashMap<>();     // container per path: folder (xG) or parent collection (FJ)
      final Map<String, String> folderParent = new HashMap<>();
      final List<KI> folderList = new ArrayList<>();
      int[][] foldersOf = new int[0][];
      int[] counts = new int[0];
      boolean[] marked = new boolean[0];
      int[] markedList = new int[0];
      int markedCount;
      boolean active;
      final List<DZ> pendingRemove = new ArrayList<>();   // empty nodes El asked to hide; finish() decides
   }

   private static final Map<yF, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile yF lastRoot;
   private static volatile State lastState;

   private static State state(yF root) {
      yF r = lastRoot;
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
   public static void attach(yF root, DZ node, int index) {
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
   public static void built(yF root) {
      State s = STATES.get(root);
      if (s == null) {
         return;
      }
      if (root.wGc() != null && !root.wGc().isEmpty()) {
         sectionName = root.wGc();
      }

      try {
         place(root, s);
         s.active = !s.folderList.isEmpty();
      } catch (Throwable t) {
         log("Collection folders disabled for this view", t);
         s.active = false;
         s.folderList.clear();
         for (DZ node : s.nodes) {
            if (node != null && node.qm() == null) {
               root.FzB(node);
            }
         }
      }
   }

   private static void place(yF root, State s) {
      boolean anyFolder = false;
      for (DZ node : s.nodes) {
         if (node != null && node.wGc() != null && node.wGc().contains(SEP)) {
            anyFolder = true;
            break;
         }
      }

      if (!anyFolder) {
         // No folders anywhere: keep Bitwig's own order and behaviour untouched.
         for (DZ node : s.nodes) {
            if (node != null) {
               root.FzB(node);
            }
         }
         return;
      }

      root.MRY = ORDER;

      // A collection "Drums" and a folder "Drums/" next to it are one thing: the collection holds the
      // folder's contents as sub-collections.
      Map<String, DZ> byName = new HashMap<>();
      Map<DZ, String> fullNames = new HashMap<>();
      for (DZ node : s.nodes) {
         if (node != null) {
            String full = fullName(node);
            if (full != null) {
               byName.putIfAbsent(full, node);
               fullNames.put(node, full);
            }
         }
      }

      Map<String, List<M22>> queries = new HashMap<>();
      List<int[]> foldersOf = new ArrayList<>();
      Set<String> parents = new HashSet<>();

      for (int i = 0; i < s.nodes.size(); i++) {
         DZ node = s.nodes.get(i);
         if (node == null) {
            foldersOf.add(new int[0]);
            continue;
         }

         String full = fullNames.get(node);
         String[] parts = full == null ? new String[0] : full.split(SEP);
         List<Integer> chain = new ArrayList<>();
         M22 q = query(node);
         String key = "";
         for (int p = 0; p < parts.length - 1; p++) {
            String parentKey = key;
            key = key.isEmpty() ? parts[p] : key + SEP + parts[p];
            KI f = s.folders.get(key);
            if (f == null) {
               DZ own = byName.get(key);
               if (own instanceof GW) {
                  f = (KI)own;
                  parents.add(key);
               } else {
                  yF folder = yF.IwV();
                  folder.MRY = ORDER;
                  folder.IwV(parts[p]);
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
            ((KI)node).IwV(parts[parts.length - 1]);
         }
      }

      // A parent collection's own items count towards itself as well.
      for (int i = 0; i < s.nodes.size(); i++) {
         DZ node = s.nodes.get(i);
         String full = node == null ? null : fullNames.get(node);
         KI own = full == null ? null : s.folders.get(full);
         if (own == node) {
            int[] chain = foldersOf.get(i);
            int[] withOwn = Arrays.copyOf(chain, chain.length + 1);
            withOwn[chain.length] = s.folderList.indexOf(own);
            foldersOf.set(i, withOwn);
            M22 q = query(node);
            if (q != null) {
               queries.computeIfAbsent(full, k -> new ArrayList<>()).add(q);
            }
         }
      }

      Map<String, M22> parentQueries = new HashMap<>();
      List<String> keys = new ArrayList<>(s.folders.keySet());
      keys.sort(Comparator.comparingInt((String k) -> k.split(SEP).length).thenComparing(k -> k));
      for (String key : keys) {
         KI f = s.folders.get(key);
         List<M22> qs = queries.get(key);
         M22 union = qs == null || qs.isEmpty() ? null : zvZ.cfM((java.util.Collection)qs);
         if (f instanceof yF folder) {
            if (union != null) {
               folder.cfM(union);
            }
            String parentKey = s.folderParent.get(key);
            KI parent = parentKey == null || parentKey.isEmpty() ? root : s.folders.get(parentKey);
            parent.Hnt((DZ)folder);
         } else if (union != null) {
            parentQueries.put(key, union);
         }
      }

      PARENTS = parents;
      PARENT_QUERIES = parentQueries;

      for (DZ node : s.nodes) {
         if (node == null) {
            continue;
         }

         String full = fullNames.get(node);
         int cut = full == null ? -1 : full.lastIndexOf(SEP);
         KI parent = cut < 0 ? root : s.folders.get(full.substring(0, cut));
         (parent == null ? root : parent).Hnt(node);
      }

      int n = s.folderList.size();
      s.foldersOf = foldersOf.toArray(new int[0][]);
      s.counts = new int[n];
      s.marked = new boolean[n];
      s.markedList = new int[n];
      s.markedCount = 0;
   }

   /** Hook at the start of xG.tY1() (copy a node): copies of our folders keep their name and identity. */
   public static KI copyFolder(Object node) {
      String key = FOLDER_KEYS.get(node);
      if (key == null) {
         return null;
      }
      yF copy = yF.IwV();
      copy.MRY = ORDER;
      copy.IwV(((DZ)node).wGc());
      FOLDER_KEYS.put(copy, key);
      return copy;
   }

   // Parent collections of the latest tree, by full name ("Drums", "Drums/Kits").
   private static volatile Set<String> PARENTS = Collections.emptySet();
   private static volatile Map<String, M22> PARENT_QUERIES = Collections.emptyMap();

   static boolean isParent(Object node) {
      if (!(node instanceof GW) || PARENTS.isEmpty()) {
         return false;
      }
      String full = fullName((DZ)node);
      return full != null && PARENTS.contains(full);
   }

   /** Hook at the start of JN.hN_(): a parent collection orders its sub-collections like a folder. */
   public static Lm nodeOrder(Object node) {
      return isParent(node) ? ORDER : null;
   }

   /** Hook at the start of FJ.IYM(Dn): a parent collection shows its own items plus all sub-collections. */
   public static ohJ parentQuery(Object node) {
      if (!(node instanceof GW) || PARENT_QUERIES.isEmpty()) {
         return null;
      }
      String full = fullName((DZ)node);
      M22 q = full == null ? null : PARENT_QUERIES.get(full);
      return q == null ? null : new ohJ(q);
   }

   private static String fullName(DZ node) {
      try {
         return ((GW)node).z3I().IwV();
      } catch (Throwable t) {
         return node.wGc();
      }
   }

   private static M22 query(DZ node) {
      try {
         return ((GW)node).z3I().TMF();
      } catch (Throwable t) {
         return null;
      }
   }

   /** Called when an item matched collection #index. */
   public static void hit(yF root, int index) {
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
   public static void itemDone(yF root) {
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
   public static void removeChild(yF root, DZ node) {
      State s = STATES.get(root);
      if (s != null) {
         s.pendingRemove.add(node);   // finish() knows which tab this is and the parents' totals
         return;
      }
      KI parent = node.qm();
      if (parent != null) {
         parent.IwV(node);
      } else {
         root.IwV(node);
      }
   }

   /**
    * Sets folder counts and hides what's empty. In the Collections tab empty collections and folders
    * stay visible (a new collection starts empty); elsewhere Bitwig's own rule applies.
    */
   public static void finish(FE el, yF root) {
      boolean flat = NocBrowser.sourceActive(el);
      State s = STATES.get(root);
      if (s != null) {
         try {
            if (!flat) {
               for (DZ node : s.pendingRemove) {
                  if (!(s.active && s.folderList.contains(node))) {
                     KI parent = node.qm();
                     (parent != null ? parent : root).IwV(node);
                  }
               }
            }
            if (s.active) {
               for (int i = s.folderList.size() - 1; i >= 0; i--) {
                  KI f = s.folderList.get(i);
                  f.TMF(s.counts[i]);
                  if (!flat && s.counts[i] == 0 && el.cfM((DZ)f, false)) {
                     KI parent = f.qm();
                     if (parent != null) {
                        parent.IwV(f);
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

      KI top = root.qm();
      String seen = top == null ? null : top.wGc();
      if (seen != null && !seen.equals("All") && !FOCUS_NAMES.contains(seen)) {
         stockRootName = seen;
      }
      String stockName = stockRootName;
      String focused = null;
      if (flat) {
         flatten(root);
      } else if (NocBrowser.isTracks(NocBrowser.currentSource(el))) {
         hideSection(root);
      } else {
         focused = focus(el, root);
      }
      // The root row reads "All" in the Collections tab (it means all collections there), Bitwig's own
      // name elsewhere. The shown tree is merged, not replaced, so rename that one too.
      try {
         String name = flat ? "All" : focused != null ? focused : stockName;
         KI shown = el == null ? null : el.NC();
         if (name != null && shown != null && !name.equals(shown.wGc())) {
            shown.IwV(name);
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
   private static final java.util.Map<FE, Object> EXPANDED_FOR = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   /** Tracks button: the Location column leaves out the Collections section (track presets aren't in them). */
   private static void hideSection(yF root) {
      try {
         KI top = root.qm();
         if (top != null) {
            top.IwV(root);
         }
      } catch (Throwable t) {
         log("Could not hide the Collections section", t);
      }
   }

   /**
    * A pinned collection: the root row becomes that collection (its name replaces "All"), with the collections
    * inside it directly under it, all expanded the first time it opens.
    */
   private static String focus(FE el, yF root) {
      try {
         Object src = NocBrowser.currentSource(el);
         if (!(src instanceof VT wc) || src == NocBrowser.SOURCE) {
            return null;
         }
         GT target = wc.E3();   // the pinned collection
         String name = target == null ? null : target.IwV();
         KI top = root.qm();
         if (name == null || top == null) {
            return null;
         }
         DZ node = find(root, name, 0);
         if (node == null) {
            return null;
         }
         KI parent = node.qm();
         if (parent != null) {
            parent.IwV(node);
         }
         for (Object child : new ArrayList<Object>(top.hbP())) {
            top.IwV((DZ)child);
         }
         if (node instanceof KI k) {
            for (Object child : new ArrayList<Object>(k.hbP())) {
               k.IwV((DZ)child);
               top.Hnt((DZ)child);
            }
            try {
               top.TMF(k.iHb());
            } catch (Throwable ignored) {
            }
         } else {
            top.Hnt(node);
         }
         String shown = node.wGc();
         if (shown == null || shown.isEmpty()) {
            int slash = name.lastIndexOf('/');
            shown = slash >= 0 ? name.substring(slash + 1) : name;
         }
         FOCUS_NAMES.add(shown);
         if (EXPANDED_FOR.get(el) != src) {
            EXPANDED_FOR.put(el, src);
            try {
               DU open = el.dvk();
               if (open != null) {
                  open.MRY(top);
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

   private static void expandAll(KI node, DU open, int depth) {
      if (depth > 24) {
         return;
      }
      for (Object child : new ArrayList<Object>(node.hbP())) {
         if (child instanceof KI k) {
            open.MRY(k);
            expandAll(k, open, depth + 1);
         }
      }
   }

   /** The row of the collection with this full name, under node. */
   private static DZ find(KI node, String name, int depth) {
      if (node == null || depth > 24) {
         return null;
      }
      for (Object child : new ArrayList<Object>(node.hbP())) {
         if (child instanceof GW h) {
            try {
               if (name.equals(h.z3I().IwV())) {
                  return h;
               }
            } catch (Throwable ignored) {
            }
         }
         if (child instanceof KI k) {
            DZ hit = find(k, name, depth + 1);
            if (hit != null) {
               return hit;
            }
         }
      }
      return null;
   }

   private static void flatten(yF root) {
      KI top = root.qm();
      if (top == null) {
         return;
      }

      List<DZ> moved = new ArrayList<>();
      try {
         for (Object child : new ArrayList<Object>(top.hbP())) {
            top.IwV((DZ)child);
         }
         for (Object child : new ArrayList<Object>(root.hbP())) {
            root.IwV((DZ)child);
            moved.add((DZ)child);
         }
         for (DZ child : moved) {
            top.Hnt(child);
         }
         top.TMF(root.iHb());   // total of the Collections section (each item once)
      } catch (Throwable t) {
         log("Could not flatten the Collections view", t);
         // Put the section back in one piece.
         try {
            for (DZ child : moved) {
               if (child.qm() == top) {
                  top.IwV(child);
               }
               if (child.qm() == null) {
                  root.Hnt(child);
               }
            }
            if (root.qm() == null) {
               top.Hnt((DZ)root);
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private static volatile String sectionName = "Collections";

   /** The Collections section itself (or Bitwig's copy of it), as opposed to one of our folders. */
   static boolean isSection(Object node) {
      return node instanceof yF && (STATES.containsKey(node) || sectionName.equals(((DZ)node).wGc()))
         && NocCollectionFolders.folderKey(node) == null;
   }

   /**
    * A node that can sit directly under the location root in the Collections tab. Decided by kind, not
    * by identity: Bitwig re-inserts copies of the selected row and its parents when it refreshes, and
    * those copies must still sort next to (and merge with) the originals.
    */
   private static boolean flat(Object node) {
      return node instanceof GW || (node instanceof yF && !isSection(node));
   }

   /**
    * Hook at the start of CQ.IYM(JN, CL, JN) (publish a rebuilt tree, keeping the selected row). In the
    * Collections tab a selection that no longer exists there (the Collections heading, a device folder
    * from the Location tab) is not re-inserted, so it can't show up as a stray row.
    */
   public static DZ publishSelection(Object column, DZ selected, KI fresh) {
      if (column instanceof FE) {
         NocBrowser.pruneSections(column, fresh);
      }
      try {
         if (selected == null || selected.qm() == null || !NocBrowser.sourceActive(column) || flat(selected)) {
            return selected;
         }
         return null;
      } catch (Throwable t) {
         return selected;
      }
   }

   /** Hook at the start of DS.IYM(CL): the location root also accepts our flattened children. */
   public static boolean accepts(DZ node) {
      return flat(node);
   }

   /**
    * Hook at the start of DS.IYM(JN, JN), the location root's order. Returns Integer.MIN_VALUE to let
    * Bitwig decide. Flattened children sort among themselves like inside Collections, and right after
    * the Collections section relative to anything else, so trees can still be merged on refresh.
    */
   public static int compare(KI a, KI b) {
      boolean fa = flat(a);
      boolean fb = flat(b);
      if (!fa && !fb) {
         return Integer.MIN_VALUE;
      }
      if (fa && fb) {
         int c = ORDER.compare(a, b);
         return c != 0 ? c : lLU.IwV(String.valueOf(a.wGc()), String.valueOf(b.wGc()));
      }
      long section = ((long)Fi.FzB.ordinal() << 24) * 2 + 1;   // just after Collections
      long other = rank(fa ? b : a) * 2;
      int c = Long.compare(section, other);
      return fa ? c : -c;
   }

   private static long rank(KI n) {
      EX dd = n.QNk();
      if (dd != null) {
         return dd.E1b();
      }
      if (n instanceof yF) {
         return (long)Fi.FzB.ordinal() << 24;
      }
      if (n instanceof Fj dr) {
         return (long)dr.dEI().ordinal() << 24;
      }
      return 0;
   }

   // ------------------------------------------------------------------ order

   /** Folders first (by name), then Favorites, then collections by name; same as stock for collections. */
   static final class Order implements Lm {
      @Override
      public boolean cfM(DZ c) {
         return c instanceof GW || c instanceof yF;
      }

      @Override
      public int compare(Object a, Object b) {
         boolean fa = a instanceof yF;
         boolean fb = b instanceof yF;
         if (fa != fb) {
            return fa ? -1 : 1;
         }

         if (fa) {
            return lLU.IwV(((DZ)a).wGc(), ((DZ)b).wGc());
         }

         if (a instanceof GW x && b instanceof GW y) {
            int fav = (y.emh() ? 1 : 0) - (x.emh() ? 1 : 0);
            // Full names: Bitwig's copies of a row carry the full name ("Drums/Kit"), the row itself
            // shows the short one. Siblings share the prefix, so the order is the same.
            return fav != 0 ? fav : lLU.IwV(String.valueOf(fullName(x)), String.valueOf(fullName(y)));
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
