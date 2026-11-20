package io.github.noctaur.browser;

import bitwig.obf.*;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;

/**
 * NocTweaks: Collections sidebar source + custom icons.
 *
 * Hooks (inserted by the build-time patcher):
 *   UB.<init>        -> register(ub)            adds the Collections source to Bitwig's built-in sources
 *   gyM.IYM(o1A)     -> label(owner)            name for the source (it has no Bitwig translation)
 *   cAV.tDS()        -> ensurePinned(palette)   pins the source to the sidebar once
 *   xG.ppu / FJ.ppu  -> treeIcon(node)          custom tree icons (SVG name)
 *   FJ.NFl           -> nodeImage(node)         custom collection image
 *   Ur.wtf           -> sourceImage(source)     custom sidebar button images
 *
 * Icons are configured in  <collections dir>/.suite/icons.txt , one line per entry:
 *   Effects/            = guitar orange     (folder: trailing slash; optional colour for the glyph)
 *   Effects/Delay       = timer             (collection: its full name)
 *   @sidebar            = folder-tree orange
 * Icon names are Lucide names (lucide.dev/icons). Colours are Bitwig's colour names.
 */
public final class NocBrowser {
   static final String SOURCE_ID = "suite_collections";
   static final Source SOURCE = new Source();

   private NocBrowser() {
   }

   // ------------------------------------------------------------------ source

   /** Bitwig built-in style source: only Collections and its folders in the Location column. */
   static final class Source extends Vu {
      @Override
      public String E3() {
         return SOURCE_ID;
      }

      @Override
      public String in_() {
         return "Collections";
      }

      @Override
      public List FzB() {
         return List.of("collections", "folders");
      }

      @Override
      public Set wGc() {
         return ztR.cfM(qD.class);
      }

      /** Open the Location column automatically when the button is clicked. */
      @Override
      public DC cfM(Jp ij) {
         try {
            return ij.tB1();
         } catch (Throwable t) {
            log("Could not open the Location column", t);
            return null;
         }
      }

      @Override
      public Wo TtY() {
         return Wo.MRY;
      }

      @Override
      public M22 MRY(EE dn) {
         try {
            List<M22> queries = new ArrayList<>();
            GK favorites = dn.MJs();
            if (favorites != null) {
               queries.add(favorites.TMF());
            }
            for (Object o : dn.Cuu()) {
               queries.add(((GT)o).TMF());
            }
            return queries.isEmpty() ? null : zvZ.cfM((Collection)queries);
         } catch (Throwable t) {
            log("Collections source query failed", t);
            return null;
         }
      }

   }

   /**
    * Hook at the start of cEo.IYM(IPw, Ij, aXo, Ij, …): a popup browser is opening. Only for a device's
    * preset folder button (Bitwig's "device_hotswap" popup, aXz): if it was left on Collections or Tracks,
    * open it in Everything so all of the device's presets show. Other popups keep Bitwig's own setting.
    */
   public static void popupOpening(Jp ij, Object popup) {
      try {
         if (ij == null || !(popup instanceof aXu p)) {
            return;
         }
         Object target = p.lYc();
         if (!(target instanceof aXE) && !(p.TMF() instanceof aXE)) {
            return;
         }
         Wl current = ij.Scv();
         String id = current == null ? null : current.cfM();
         if (id != null && (id.equals(((Wl)SOURCE).cfM()) || id.equals(TRACKS_ID))) {
            ij.cfM((Wl)VF.lYc());
         }
      } catch (Throwable t) {
         log("Could not switch the device preset browser to Everything", t);
      }
   }

   private static final java.util.Set<String> TRACED = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private static volatile Boolean tracing;

   /**
    * Diagnostics (off unless .suite/trace-menus exists): log where each kind of menu is built, so a
    * popup can be found in Bitwig's code. Hook at the start of dKG().
    */
   public static void traceMenu() {
      try {
         if (tracing == null) {
            tracing = new File(stateDir(), "trace-menus").exists();
         }
         if (!tracing || TRACED.size() > 60) {
            return;
         }
         StackTraceElement[] st = new Throwable().getStackTrace();
         StringBuilder b = new StringBuilder();
         for (int i = 1; i < st.length && i < 16; i++) {
            b.append(i == 1 ? "" : " < ").append(st[i].getClassName()).append('.').append(st[i].getMethodName());
         }
         String key = b.toString();
         if (TRACED.add(key)) {
            log("Menu built: " + key, null);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Diagnostics: the widget whose context menu is opening (hook at the start of duK.TdG). */
   public static void traceWidget(Object widget) {
      try {
         if (tracing == null) {
            tracing = new File(stateDir(), "trace-menus").exists();
         }
         if (!tracing || widget == null || TRACED.size() > 120) {
            return;
         }
         StringBuilder b = new StringBuilder();
         for (Class<?> c = widget.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            b.append(b.length() == 0 ? "" : " > ").append(c.getName());
         }
         String key = "W:" + b;
         if (TRACED.add(key)) {
            log("Menu widget: " + b, null);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Diagnostics: a selector's popup is opening; log the widget and its parents (hook at dBB.bpz). */
   public static void traceSelector(Object widget) {
      try {
         if (tracing == null) {
            tracing = new File(stateDir(), "trace-menus").exists();
         }
         if (!tracing || TRACED.size() > 160) {
            return;
         }
         StringBuilder b = new StringBuilder();
         Object w = widget;
         for (int i = 0; i < 14 && w != null; i++) {
            b.append(i == 0 ? "" : " in ").append(w.getClass().getName());
            Object up = null;
            try {
               up = ((dvW)w).RbC();
            } catch (Throwable ignored) {
            }
            if (up == null) {
               try {
                  up = ((dvW)w).MJ();
               } catch (Throwable ignored) {
               }
            }
            w = up == w ? null : up;
         }
         if (TRACED.add("S:" + b)) {
            log("Selector popup: " + b, null);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Diagnostics: an option button's menu is opening (hook at dBN.IYM(dKG, double, double, eoV)). */
   public static void traceOptions(Object widget) {
      try {
         if (tracing == null) {
            tracing = new File(stateDir(), "trace-menus").exists();
         }
         if (!tracing || !(widget instanceof dCm n) || TRACED.size() > 200) {
            return;
         }
         List<String> opts = new ArrayList<>();
         n.cfM((java.util.function.Consumer<Object>)o -> opts.add(o == null ? "null" : o.getClass().getName() + "=" + o));
         String value = "?";
         try {
            Object v = n.evx();
            value = v == null ? "null" : v.getClass().getName() + "=" + v;
         } catch (Throwable ignored) {
         }
         String cur = "?";
         try {
            Object c = n.urM();
            cur = c == null ? "null" : c.getClass().getName() + "=" + c;
         } catch (Throwable ignored) {
         }
         String key = "O:" + widget.getClass().getName() + opts;
         if (TRACED.add(key)) {
            log("Option button " + widget.getClass().getName() + " value " + value + " current " + cur + " options " + opts, null);
            traceSelector(widget);
         }
      } catch (Throwable t) {
         log("traceOptions failed", t);
      }
   }

   /** The Track Presets mod's sidebar button (its id): its icon can be set here, and it isn't a collection. */
   static final String TRACKS_ID = "suite_tracks";
   /** True once the Track Presets mod's button has been seen. */
   static volatile boolean tracksButton;

   static boolean isTracks(Object source) {
      try {
         return source instanceof Wl w && TRACKS_ID.equals(w.cfM());
      } catch (Throwable t) {
         return false;
      }
   }

   public static Vu source() {
      return SOURCE;
   }

   public static String label(zTz owner) {
      return owner == SOURCE ? "Collections" : null;
   }

   // ------------------------------------------------------------------ sidebar pin

   private static final Set<Object> SEEN = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
   private static Boolean pinThisSession;

   /** The quick sources (the sidebar's pinned buttons), as last seen. */
   private static volatile java.lang.ref.WeakReference<YO> palette = new java.lang.ref.WeakReference<>(null);

   static YO palette() {
      return palette.get();
   }

   /** The user took this button off the sidebar with our menu: don't put it back. */
   static boolean hidden(String which) {
      return new File(stateDir(), "sidebar-hide-" + which).exists();
   }

   static void setHidden(String which, boolean hide) {
      try {
         File f = new File(stateDir(), "sidebar-hide-" + which);
         if (hide) {
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), "hidden from the sidebar\n");
         } else {
            Files.deleteIfExists(f.toPath());
         }
         YO p = palette();
         if (p != null) {
            Wl source = SOURCE;
            if (hide) {
               p.MRY(source);                       // unpin it
            } else if (p.cfM(source) < 0) {
               p.cfM(YS.cfM(source), p.tp2());     // pin it at the end
            }
         }
      } catch (Throwable t) {
         log("Could not change the sidebar", t);
      }
   }

   /**
    * The sidebar's pinned buttons (quick sources) were asked for: make sure Collections and Tracks are there.
    * Bitwig drops buttons it doesn't know when it starts without this mod, so this runs every session instead
    * of once; hiding a button from the browser's menu (Sidebar Buttons) keeps it off.
    */
   public static void ensurePinned(YO p, boolean main) {
      try {
         if (p == null) {
            return;
         }
         if (main || palette.get() == null) {
            palette = new java.lang.ref.WeakReference<>(p);   // the main browser's (for Pin to Quick Sources)
         }
         if (!SEEN.add(p)) {
            return;
         }
         synchronized (PINNED_NOW) {
            for (GT c : PINNED_NOW) {
               pinInto(p, c);
            }
         }
         if (!hidden("collections") && p.cfM((Wl)SOURCE) < 0) {
            p.cfM(YS.cfM(SOURCE), p.tp2());
         }
      } catch (Throwable t) {
         log("Could not pin Collections and Tracks to the sidebar", t);
      }
   }

   /** Collections pinned this session, so a browser first opened later (a popup) gets them too. */
   private static final Set<GT> PINNED_NOW = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

   /** Every browser's quick sources seen so far: the main browser's and the popups' (each has its own). */
   private static List<YO> palettes() {
      List<YO> out = new ArrayList<>();
      synchronized (SEEN) {
         for (Object o : SEEN) {
            if (o instanceof YO z) {
               out.add(z);
            }
         }
      }
      YO main = palette();
      if (main != null && !out.contains(main)) {
         out.add(main);
      }
      return out;
   }

   private static void pinInto(YO p, GT collection) {
      VT source = collection.tp2();   // the collection as a browser source
      if (source != null && p.cfM((Wl)source) < 0) {
         p.cfM(YS.cfM(source), p.tp2());
      }
   }

   /** Adds a collection to every browser's quick sources (no limit: Bitwig's own drag stops at 12). */
   static void pinCollection(GT collection) {
      try {
         List<YO> all = palettes();
         if (all.isEmpty() || collection.tp2() == null) {
            log("Quick sources not ready yet: open the browser once, then try again", null);
            return;
         }
         PINNED_NOW.add(collection);
         for (YO p : all) {
            pinInto(p, collection);
         }
         log("Pinned " + collection.IwV() + " to " + all.size() + " browser(s)' quick sources", null);
      } catch (Throwable t) {
         log("Could not pin the collection", t);
      }
   }

   static boolean pinned(GT collection) {
      try {
         Wl source = collection.tp2();
         for (YO p : palettes()) {
            if (p.cfM(source) >= 0) {
               return true;
            }
         }
      } catch (Throwable t) {
      }
      return false;
   }

   static void unpinCollection(GT collection) {
      try {
         PINNED_NOW.remove(collection);
         Wl source = collection.tp2();
         for (YO p : palettes()) {
            if (p.cfM(source) >= 0) {
               p.MRY(source);
            }
         }
      } catch (Throwable t) {
         log("Could not unpin the collection", t);
      }
   }

   /** A browser session switched to a sidebar button: Collections and Tracks open on their Location column. */
   static void sourceChosen(Jp session) {
      try {
         Wl now = session.Scv();
         if (now == SOURCE || now instanceof VT) {
            Fq.cfM(() -> {
               try {
                  if (session.Scv() == now) {
                     if (now instanceof VT) {
                        DC location = session.tB1();   // the Location column
                        if (location != null) {
                           session.MRY((tE) location);   // open it
                        }
                     } else {
                        now.MRY(session);   // open the source's own column (Location)
                     }
                  }
               } catch (Throwable t) {
                  log("Could not open the Location column", t);
               }
            });
         }
      } catch (Throwable t) {
         log("source chosen", t);
      }
   }

   // ------------------------------------------------------------------ icons

   static final class Entry {
      final String icon;
      final String color;

      Entry(String icon, String color) {
         this.icon = icon;
         this.color = color;
      }
   }

   private static Map<String, Entry> icons = Collections.emptyMap();
   private static long iconsStamp = -1;
   private static long iconsChecked;

   /** icons.txt was just written: read it again on the next lookup. */
   static synchronized void iconsChanged() {
      iconsChecked = 0;
      iconsStamp = -1;
   }

   private static synchronized Map<String, Entry> icons() {
      long now = System.currentTimeMillis();
      if (now - iconsChecked < 2000) {
         return icons;
      }
      iconsChecked = now;
      File f = new File(stateDir(), "icons.txt");
      long stamp = f.isFile() ? f.lastModified() : 0;
      if (stamp == iconsStamp) {
         return icons;
      }
      iconsStamp = stamp;
      Map<String, Entry> m = new HashMap<>();
      if (stamp != 0) {
         try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
               String l = line.strip();
               if (l.isEmpty() || l.startsWith("#")) {
                  continue;
               }
               int eq = l.lastIndexOf('=');
               if (eq <= 0) {
                  continue;
               }
               String key = l.substring(0, eq).strip();
               String[] v = l.substring(eq + 1).strip().split("\\s+");
               if (v.length > 0 && !v[0].isEmpty()) {
                  m.put(key, new Entry(v[0], v.length > 1 ? v[1] : null));
               }
            }
         } catch (Throwable t) {
            log("Could not read icons.txt", t);
         }
      }
      icons = m;
      return m;
   }

   private static String folderKey(Object node) {
      String k = NocCollectionFolders.folderKey(node);
      return k == null ? null : k + "/";
   }

   private static String collectionKey(Object node) {
      try {
         return node instanceof GW fj ? fj.z3I().IwV() : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static Entry entryFor(Object node) {
      Map<String, Entry> m = icons();
      if (m.isEmpty()) {
         return null;
      }
      String key = node instanceof yF ? folderKey(node) : collectionKey(node);
      return key == null ? null : m.get(key);
   }

   /** SVG icon name for a tree node, or null for Bitwig's own. */
   public static String treeIcon(Object node) {
      try {
         Entry e = entryFor(node);
         if (e == null || !svg(e.icon).isFile()) {
            return null;
         }
         if (e.color != null && colour(e.color) != null) {
            String coloured = colouredSvg(e.icon, e.color.toLowerCase());
            if (coloured != null) {
               return coloured;
            }
         }
         return Icons.PREFIX + "lucide/" + e.icon + ".svg";
      } catch (Throwable t) {
         log("treeIcon failed", t);
      }
      return null;
   }

   /** Writes (once) a variant of the icon with the glyph in a fixed colour; returns its icon name. */
   private static String colouredSvg(String icon, String color) {
      try {
         String tag = color.replace("#", "x");   // a custom colour (#rrggbb) as xrrggbb in the file name
         File out = new File(new File(stateDir(), "cache/icons"), icon + "-" + tag + ".svg");
         if (!out.isFile()) {
            Color c = colour(color);
            String src = Files.readString(svg(icon).toPath(), StandardCharsets.UTF_8);
            String rgb = "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
            out.getParentFile().mkdirs();
            File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
            Files.writeString(tmp.toPath(), src.replace("stroke:white", "stroke:" + rgb).replace("fill:white", "fill:" + rgb), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         }
         return Icons.PREFIX + "coloured/" + icon + "-" + tag + ".svg";
      } catch (Throwable t) {
         log("Could not write coloured icon (folder not writable?)", t);
         return null;
      }
   }

   /** Image for a collection node, or null for Bitwig's own. */
   public static URI nodeImage(Object node) {
      try {
         Entry e = entryFor(node);
         if (e != null && node instanceof GW fj) {
            return tile(e.icon, e.color != null ? e.color : colorName(fj.z3I()));
         }
      } catch (Throwable t) {
         log("nodeImage failed", t);
      }
      return null;
   }

   /** Image for a sidebar button, or null for Bitwig's own. */
   public static URI sourceImage(Object source) {
      try {
         if (source == SOURCE) {
            Entry e = icons().get("@sidebar");
            return tile(e != null ? e.icon : "folder-tree", e != null && e.color != null ? e.color : "orange");
         }
         if (source instanceof Wl w && TRACKS_ID.equals(w.cfM())) {
            tracksButton = true;
            Entry e = icons().get("@tracks");   // the Track Presets mod's button: only an icon chosen here replaces its own
            return e == null ? null : tile(e.icon, e.color != null ? e.color : "blue");
         }
         if (source instanceof VT ve) {
            GT fd = ve.E3();
            Entry e = icons().get(fd.IwV());
            if (e != null) {
               return tile(e.icon, e.color != null ? e.color : colorName(fd));
            }
         }
      } catch (Throwable t) {
         log("sourceImage failed", t);
      }
      return null;
   }

   private static String colorName(GT fd) {
      try {
         return AlY.cfM(((GK)fd).MJ()).MRY();
      } catch (Throwable t) {
         return "default";
      }
   }

   /** A colour from icons.txt: one of Bitwig's colour names, or a custom one as #rrggbb. Null if neither. */
   static Color colour(String name) {
      if (name == null) {
         return null;
      }
      String n = name.trim().toLowerCase();
      if (n.matches("#[0-9a-f]{6}")) {
         return new Color(Integer.parseInt(n.substring(1), 16));
      }
      return PALETTE.get(n);
   }

   private static final Map<String, Color> PALETTE = Map.ofEntries(
      Map.entry("red", new Color(0xD9, 0x2E, 0x24)), Map.entry("orange", new Color(0xFF, 0x83, 0x2B)),
      Map.entry("yellow", new Color(0xE5, 0xC0, 0x1E)), Map.entry("lime", new Color(0xA3, 0xC6, 0x3B)),
      Map.entry("green", new Color(0x3F, 0xB3, 0x4F)), Map.entry("mint", new Color(0x3E, 0xC9, 0xA3)),
      Map.entry("turquoise", new Color(0x2F, 0xB5, 0xD6)), Map.entry("blue", new Color(0x3B, 0x6E, 0xE0)),
      Map.entry("purple", new Color(0x9A, 0x55, 0xD6)), Map.entry("white", new Color(0xE8, 0xE8, 0xE8)),
      Map.entry("black", new Color(0x1F, 0x1F, 0x1F)), Map.entry("light_grey", new Color(0xB0, 0xB0, 0xB0)),
      Map.entry("grey", new Color(0x80, 0x80, 0x80)), Map.entry("dark_grey", new Color(0x4A, 0x4A, 0x4A)));

   /**
    * Bitwig-style tile as PNG: black rounded square, ring and Lucide glyph in the colour
    * (where Bitwig draws its coloured grid). Cached per icon and colour.
    */
   private static URI tile(String icon, String color) throws Exception {
      File src = png(icon);
      if (!src.isFile()) {
         log("Icon image missing: " + src.getAbsolutePath(), null);
         return null;
      }
      Color fg = colour(color);
      if (fg == null) {
         fg = new Color(0xD0, 0xD0, 0xD0);
      }
      File out = new File(new File(stateDir(), "cache"), "tile3-" + icon + "-" + Integer.toHexString(fg.getRGB()) + ".png");
      if (!out.isFile()) {
         out.getParentFile().mkdirs();
         BufferedImage glyph = ImageIO.read(src);
         int size = 64;
         BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = img.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         int m = 3, ring = 3, arc = 14;
         g.setColor(fg);
         g.fillRoundRect(m, m, size - 2 * m, size - 2 * m, arc, arc);
         g.setColor(Color.BLACK);
         g.fillRoundRect(m + ring, m + ring, size - 2 * (m + ring), size - 2 * (m + ring), arc - 4, arc - 4);
         BufferedImage tinted = new BufferedImage(glyph.getWidth(), glyph.getHeight(), BufferedImage.TYPE_INT_ARGB);
         Graphics2D t = tinted.createGraphics();
         t.drawImage(glyph, 0, 0, null);
         t.setComposite(AlphaComposite.SrcIn);
         t.setColor(fg);
         t.fillRect(0, 0, glyph.getWidth(), glyph.getHeight());
         t.dispose();
         int pad = 15;
         g.drawImage(tinted, pad, pad, size - 2 * pad, size - 2 * pad, null);
         g.dispose();
         File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
         ImageIO.write(img, "png", tmp);
         Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return out.toURI();
   }

   // ------------------------------------------------------------------ paths

   /** True while the Collections source is selected in this browser session. */
   /** The sidebar source the browser session of this column is showing, or null. */
   static Object currentSource(Object column) {
      try {
         Jp session = ((DC)column).zpW();
         if (session == null) {
            return null;
         }
         Object src = session.Scv();
         return src != null ? src : session.BDm();
      } catch (Throwable t) {
         return null;
      }
   }

   static String sourceId(Object source) {
      try {
         return source instanceof Vu u ? u.E3() : null;
      } catch (Throwable t) {
         return null;
      }
   }

   /** Sources that keep Bitwig's own behaviour (they're meant to show every location). */
   static boolean stockSource(Object source) {
      String id = sourceId(source);
      return source == null || "everything".equals(id) || "file_browser".equals(id) || "my_library".equals(id);
   }

   /**
    * Hook at the start of El.IYM(CL, boolean) ("hide this row when it's empty?"): 1 hide, 0 keep, -1 let
    * Bitwig decide. With a sidebar button selected, the Location column only shows places that have
    * something for it: empty collections, folders and locations are hidden. The Collections button keeps
    * every collection; Music keeps its music locations but hides empty collections.
    */
   public static int hideEmpty(Object column, Object node) {
      try {
         boolean collectionLike = node instanceof GW || node instanceof yF;
         if (!collectionLike && !(node instanceof Ff)) {
            return -1;
         }
         Object src = currentSource(column);
         if (src == SOURCE) {
            return collectionLike ? 0 : -1;
         }
         if (stockSource(src)) {
            return -1;
         }
         if ("music".equals(sourceId(src)) && node instanceof Ff) {
            return 0;
         }
         return 1;
      } catch (Throwable t) {
         return -1;
      }
   }

   /** Removes section headings (My Library, Packages, …) left with nothing under them. */
   static void pruneSections(Object column, KI tree) {
      try {
         Object src = currentSource(column);
         if (tree == null || src == SOURCE || stockSource(src)) {
            return;
         }
         for (Object child : new java.util.ArrayList<Object>(tree.hbP())) {
            if (child instanceof Fj section && section.hbP().isEmpty()) {
               tree.IwV((DZ)section);
            }
         }
      } catch (Throwable t) {
         log("Could not tidy empty sections", t);
      }
   }

   static boolean sourceActive(Object column) {
      try {
         Jp session = ((DC)column).zpW();
         return session != null && (session.Scv() == SOURCE || session.BDm() == SOURCE);
      } catch (Throwable t) {
         return false;
      }
   }

   /** A Lucide icon's SVG, in the mod's icons folder. */
   static File svg(String icon) {
      return Browser.MOD.resource("icons").resolve("lucide/" + icon + ".svg").toFile();
   }

   private static File png(String icon) {
      return Browser.MOD.resource("icons").resolve("lucide-png/" + icon + ".png").toFile();
   }

   /** The command that opens a folder in the system's file manager. */
   static String fileManager() {
      String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
      return os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open";
   }

   static File stateDir() {
      File dir = NocCollectionFolders.collectionsDir();
      if (dir == null) {
         String local = System.getenv("LOCALAPPDATA");
         dir = new File(local != null ? local : System.getProperty("user.home"), "Bitwig Studio/library/collections/7");
      }
      return new File(dir, ".suite");
   }

   static void log(String message, Throwable t) {
      try {
         if (t == null) {
            Browser.MOD.log(message);
         } else {
            Browser.MOD.log(message, t);
         }
      } catch (Throwable ignored) {
      }
   }
}
