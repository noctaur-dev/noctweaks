package io.github.noctaur.expression;

import bitwig.obf.dou;           // a window's image loader and cache (icons by name)
import com.bitwig.graphics.haN;  // a loaded image

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The mod's own SVG icons. Bitwig loads icons by name, from its own resources folder, and caches them per
 * window and scale. A name given out here starts with {@link #PREFIX}; when a window is asked for one, the
 * hook on its loader (an ENTRY hook, so it composes with other mods') renders the mod's file with Bitwig's own
 * SVG renderer and puts it in the window's cache first, so Bitwig finds it there and tints it as its own.
 */
final class Icons {
   static final String PREFIX = "noctweaks-expression/";

   private Icons() {
   }

   /** The icon name for a file in the mod's icons folder ("shape/sine.svg"). */
   static String name(String file) {
      return PREFIX + file;
   }

   /** A window is about to load an icon: put ours in its cache first. */
   @SuppressWarnings("unchecked")
   static void loading(dou window, String name, double scale) {
      if (name == null || !name.startsWith(PREFIX) || window == null) {
         return;
      }
      try {
         Map<Object, Object> cache = window.Anf;  // icons already loaded, by name and scale
         String key = window.unz(name, scale);    // the cache key for this name and scale
         if (cache.containsKey(key)) {
            return;
         }
         Path file = Expr.MOD.resource("icons").resolve(name.substring(PREFIX.length()));
         try (InputStream in = Files.newInputStream(file)) {
            haN image = window.Anf().beI(in, null, scale); // Bitwig's SVG renderer, as for its own icons
            if (image != null) {
               cache.put(key, image);
            }
         }
      } catch (Throwable t) {
         Expr.log("could not load the icon " + name, t);
      }
   }
}
