package io.github.noctaur.arabridge;

import bitwig.obf.*;
import bml.api.Bml;
import com.bitwig.flt.document.core.iface.WSA;
import com.bitwig.flt.document.core.iface.iYN;
import com.bitwig.flt.document.core.master.g2L;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Bitwig side of the ARA Bridge (prototype). The bridge plug-in reads one small session file per track:
 * which audio file the clip plays, and where it sits on the timeline. This mod writes the "where" part.
 *
 * Next step: read the clip itself (its file, start, offset and length) so nothing has to be set by hand.
 */
final class AraBridge {
   static final Bml.Mod MOD = Bml.mod("noctweaks-ara-bridge");

   private AraBridge() {
   }

   static void log(String message, Throwable t) {
      try {
         if (t == null) {
            MOD.log(message);
         } else {
            MOD.log(message, t);
         }
      } catch (Throwable ignored) {
      }
   }

   private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

   /** The folder the bridge plug-in reads its session files from (the same rule as the plug-in's session.h). */
   static File folder() {
      String home = System.getProperty("user.home", ".");
      File base;
      if (OS.contains("win")) {
         String local = System.getenv("LOCALAPPDATA");
         base = new File(local != null ? local : home);
      } else if (OS.contains("mac")) {
         base = new File(home, "Library/Application Support");
      } else {
         String xdg = System.getenv("XDG_CONFIG_HOME");
         base = xdg != null ? new File(xdg) : new File(home, ".config");
      }
      return new File(new File(base, "NocTweaks"), "ara-bridge");
   }

   static String safeName(String n) {
      String s = n.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_").replaceAll("[ .]+$", "");
      return s.isEmpty() ? "Track" : s;
   }

   static File sessionFile(String track) {
      return new File(folder(), safeName(track) + ".txt");
   }

   static Map<String, String> read(File f) {
      Map<String, String> m = new LinkedHashMap<>();
      try {
         if (f.isFile()) {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
               int eq = line.indexOf('=');
               if (eq > 0) {
                  m.put(line.substring(0, eq), line.substring(eq + 1));
               }
            }
         }
      } catch (Throwable t) {
         log("ARA Bridge: could not read " + f, t);
      }
      return m;
   }

   static void write(File f, Map<String, String> m) {
      try {
         f.getParentFile().mkdirs();
         StringBuilder b = new StringBuilder();
         for (String k : new String[]{"plugin", "file", "start", "offset", "length"}) {
            b.append(k).append('=').append(m.getOrDefault(k, k.equals("plugin") || k.equals("file") ? "" : "0")).append('\n');
         }
         Files.writeString(f.toPath(), b.toString(), StandardCharsets.UTF_8);
         log("ARA Bridge: wrote " + f + "\n" + b, null);
      } catch (Throwable t) {
         log("ARA Bridge: could not write " + f, t);
      }
   }

   // ------------------------------------------------------------------ the track header menu

   static void menu(dKW menu, big header) {
      Object track = null;
      try {
         for (Object o : header.UNj().FzB()) {
            if (o instanceof WSA) {
               track = o;
               break;
            }
         }
      } catch (Throwable t) {
         log("ARA Bridge: no selected track", t);
      }
      if (track == null) {
         return;
      }
      String name = name(track);
      g2L doc = document(track);
      File session = sessionFile(name);
      Map<String, String> s = read(session);

      menu.ELS();
      dKW m = menu.Hnt("ARA Bridge");
      if (s.isEmpty()) {
         m.cfM("Open the bridge plug-in on \"" + name + "\" to choose its clip", () -> {
         });
      } else {
         String file = new File(s.getOrDefault("file", "")).getName();
         m.cfM("Clip: " + (file.isEmpty() ? "none" : file) + " at " + s.getOrDefault("start", "0") + " s", () -> {
         });
      }
      double now = seconds(doc);
      m.cfM(String.format(Locale.ROOT, "Clip Starts at Playhead (%.3f s)", now), () -> {
         Map<String, String> cur = read(session);
         cur.put("start", String.format(Locale.ROOT, "%.6f", seconds(doc)));
         write(session, cur);   // the bridge sees the change within half a second
      });
      m.cfM("Open ARA Bridge Folder", () -> {
         try {
            File dir = folder();
            dir.mkdirs();
            String open = OS.contains("win") ? "explorer.exe" : OS.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(open, dir.getAbsolutePath()).start();
         } catch (Throwable t) {
            log("ARA Bridge: could not open the folder", t);
         }
      });
   }

   private static double seconds(g2L doc) {
      try {
         return doc == null ? 0 : doc.ifN().tSS().getDoubleValue();   // the transport's play position in seconds
      } catch (Throwable t) {
         return 0;
      }
   }

   private static g2L document(Object track) {
      try {
         Object d = ((WSA) track).apV();
         return d instanceof g2L o ? o : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private static String name(Object track) {
      try {
         String n = ((iYN) track).cb_();
         return n == null || n.isBlank() ? "Track" : n;
      } catch (Throwable t) {
         return "Track";
      }
   }
}
