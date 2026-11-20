package io.github.noctaur.browser;

import bitwig.obf.*;
import com.bitwig.base.io.gJi;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NocTweaks: show each plugin once.
 *
 * Bitwig lists a plugin once per format (CLAP, VST3, VST2) and once per install location. This wraps
 * the browser's final search query: a plugin result is dropped only when another copy of the same
 * plugin (same name and creator) in the same or a better format has already matched the very same
 * search. So a plugin can never disappear because of a copy that isn't in the list you're looking at.
 *
 * The plugin kinds are searched best format first (CLAP, VST3, VST2 by default; see
 * .suite/settings.txt), so the copy that's kept is the preferred one.
 *
 * Hooks (inserted by the build-time patcher):
 *   Ij.ExU()               -> query(q)    the browser's final query, wrapped
 *   i1m.IYM(BUQ, int, Chp) -> order(list) search the plugin kinds in preference order
 */
public final class NocFormats {
   private NocFormats() {
   }

   static final int CLAP = 0, VST3 = 1, VST2 = 2, PRESET = -2;
   private static final String[] NAMES = {"clap", "vst3", "vst2"};

   private static volatile int[] rank = {0, 1, 2};   // rank[format], lower is preferred
   private static volatile boolean enabled = true;
   private static volatile boolean loaded;
   private static final Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** Hook at the end of Ij.ExU(): wraps the browser's final query. */
   public static M22 query(M22 q) {
      try {
         load();
         if (q == null || q instanceof Filter) {
            return q;
         }
         return new Filter(q);
      } catch (Throwable t) {
         NocBrowser.log("Plugin duplicate filter disabled", t);
         enabled = false;
         return q;
      }
   }

   /** Hook at the start of i1m.IYM(BUQ, int, Chp): plugin kinds in preference order, others untouched. */
   public static void order(List kinds) {
      try {
         load();
         if (!enabled || kinds == null) {
            return;
         }
         synchronized (kinds) {
            List<Integer> slots = new ArrayList<>();
            List<Object> plugins = new ArrayList<>();
            for (int i = 0; i < kinds.size(); i++) {
               Object k = kinds.get(i);
               if (kindFormat(k) >= 0) {
                  slots.add(i);
                  plugins.add(k);
               }
            }
            List<Object> sorted = new ArrayList<>(plugins);
            int[] r = rank;
            sorted.sort((a, b) -> Integer.compare(r[kindFormat(a)], r[kindFormat(b)]));
            if (!sorted.equals(plugins)) {
               for (int i = 0; i < slots.size(); i++) {
                  kinds.set(slots.get(i), sorted.get(i));
               }
            }
         }
      } catch (Throwable t) {
         // unmodifiable or unexpected: leave Bitwig's order
      }
   }

   private static int kindFormat(Object p) {
      try {
         Object kind = p instanceof MB li ? li.hUR() : null;
         if (kind instanceof NR) {
            return CLAP;
         }
         if (kind instanceof QV) {
            return VST3;
         }
         if (kind instanceof QN) {
            return VST2;
         }
      } catch (Throwable ignored) {
      }
      return -1;
   }

   static int format(Object cursor) {
      if (cursor instanceof NQ) {
         return CLAP;
      }
      if (cursor instanceof QT) {
         return VST3;
      }
      if (cursor instanceof QM) {
         return VST2;
      }
      return -1;
   }

   // ------------------------------------------------------------------ per-search memory

   private static final Object KEY = new Object();

   /** What has matched so far in one search: plugin key -> {rank, item id}. */
   static final class Pass {
      final Map<String, long[]> shown = new HashMap<>();
      final Map<String, String> entries = new HashMap<>();
   }

   static Pass pass(Ihc context) {
      synchronized (context) {
         Object p = context.cfM(KEY);
         if (p instanceof Pass pass) {
            return pass;
         }
         Pass pass = new Pass();
         context.cfM(KEY, pass);
         return pass;
      }
   }

   /** Called only for results that matched everything else: false if a copy already made the list. */
   static boolean admit(Pass pass, pAe cursor, int format) {
      try {
         // Entry identity is the file path: two installs of one VST3 share Bitwig's item id (the plugin's
         // class id), so the id alone can't tell the copies apart.
         String path = String.valueOf(cursor.IwV()) + "#" + cursor.wGc();
         return admit(pass, cursor.hUR().cfM(), ((PF)cursor).OzW(), path.hashCode() * 31L + path.length(), path, format);
      } catch (Throwable t) {
         return true;
      }
   }

   static boolean admit(Pass pass, String name, String creator, long id, int format) {
      return admit(pass, name, creator, id, String.valueOf(id), format);
   }

   static boolean admit(Pass pass, String name, String creator, long id, String entry, int format) {
      try {
         if (name == null || name.isBlank() || creator == null || creator.isBlank()) {
            return true;
         }
         String key = name.strip().toLowerCase(Locale.ROOT) + "|" + creator.strip().toLowerCase(Locale.ROOT);
         int mine = rank[format];
         synchronized (pass) {
            long[] seen = pass.shown.get(key);
            if (seen == null || mine < seen[0]) {
               pass.shown.put(key, new long[]{mine, id});
               pass.entries.put(key, entry);
               return true;
            }
            if (entry.equals(pass.entries.get(key))) {
               return true;   // the same entry asked again
            }
         }
         if (LOGGED.size() < 300 && LOGGED.add(key + "#" + format)) {
            NocBrowser.log("Showing \"" + name + "\" (" + creator + ") once: skipped another " + NAMES[format] + " copy", null);
         }
         return false;
      } catch (Throwable t) {
         return true;
      }
   }

   // ------------------------------------------------------------------ query + matcher

   /** The browser's query, unchanged, plus the once-per-plugin rule. Saves and compares as the original. */
   static final class Filter extends M22 {
      final M22 inner;

      Filter(M22 inner) {
         this.inner = inner;
      }

      @Override
      public nCp cfM(pAe cursor, Ihc context) {
         nCp m = inner.cfM(cursor, context);
         if (m == null) {
            return m;
         }
         int f = format(cursor);
         if (!enabled || f < 0 || context == null) {
            return m;
         }
         return new Match(m, cursor, f, pass(context));
      }

      @Override
      public void a_(gJi out) {
         inner.a_(out);
      }

      @Override
      public boolean cfM(M22 other) {
         return other instanceof Filter f && inner.equals(f.inner);
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
      public Set cfM(flj kind) {
         return inner.cfM(kind);
      }
   }

   /**
    * Wraps the original matcher. Bitwig matches in two steps: per directory (KF3/IYM(Mjz): all, some or
    * no files match) and then per file (jPB) when a directory is "some". The per-file step is the only
    * place a single result can be judged, so a directory the original query fully accepts is turned into
    * "some" here, and each file that the original query accepts is then checked against copies already
    * shown. Directories the original rejects stay rejected.
    */
   static final class Match extends nCp {
      private final nCp inner;
      private final pAe cursor;
      private final int format;
      private final Pass pass;

      Match(nCp inner, pAe cursor, int format, Pass pass) {
         this.inner = inner;
         this.cursor = cursor;
         this.format = format;
         this.pass = pass;
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
         return inner.IwV() && admit(pass, cursor, format);
      }
   }

   // ------------------------------------------------------------------ settings

   private static synchronized void load() {
      if (loaded) {
         return;
      }
      loaded = true;
      try {
         File settings = new File(NocBrowser.stateDir(), "settings.txt");
         if (!settings.isFile()) {
            settings.getParentFile().mkdirs();
            Files.writeString(settings.toPath(), "# NocTweaks settings (restart Bitwig after changing)\n"
               + "# Show each plugin once (best format, one install location): on / off\n"
               + "show-each-plugin-once = on\n"
               + "# Preferred format first\n"
               + "plugin-format-order = clap vst3 vst2\n", StandardCharsets.UTF_8);
            return;
         }
         for (String line : Files.readAllLines(settings.toPath(), StandardCharsets.UTF_8)) {
            String l = line.strip();
            int eq = l.indexOf('=');
            if (l.startsWith("#") || eq < 0) {
               continue;
            }
            String k = l.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String v = l.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
            if (k.equals("show-each-plugin-once")) {   // 0.6+; older builds read hide-duplicate-plugin-formats
               enabled = !(v.equals("off") || v.equals("no") || v.equals("false"));
            } else if (k.equals("plugin-format-order")) {
               int[] r = {9, 9, 9};
               int i = 0;
               for (String w : v.split("[\\s,>]+")) {
                  for (int f = 0; f < 3; f++) {
                     if (NAMES[f].equals(w) && r[f] == 9) {
                        r[f] = i++;
                     }
                  }
               }
               for (int f = 0; f < 3; f++) {
                  if (r[f] == 9) {
                     r[f] = i++;
                  }
               }
               rank = r;
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Could not read plugin settings", t);
      }
   }
}
