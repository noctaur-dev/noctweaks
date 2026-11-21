package io.github.noctaur.trackpresets;

import bml.api.Bml;
import java.util.Locale;

/** The mod's handle in Patchbay: its log, settings and files. */
final class TrackPresets {
   static final Bml.Mod MOD = Bml.mod("noctweaks-track-presets");

   private TrackPresets() {
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

   /** The command that opens a folder in the system's file manager. */
   static String fileManager() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      return os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open";
   }

   /** Whether the Tracks button goes on the browser's sidebar (on unless hidden from the browser's menu). */
   static boolean buttonShown() {
      try {
         return MOD.settings().getBoolean("show-button", true);
      } catch (Throwable t) {
         return true;
      }
   }

   static void setButtonShown(boolean shown) {
      try {
         MOD.settings().putBoolean("show-button", shown);
         MOD.settings().flush();
      } catch (Throwable t) {
         log("Could not save the Tracks button setting", t);
      }
   }

   /** Whether the new-track popup browser gets the Tracks button (on unless switched off in the browser's menu). */
   static boolean newTrackPopup() {
      try {
         return MOD.settings().getBoolean("new-track-popup", true);
      } catch (Throwable t) {
         return true;
      }
   }

   static void setNewTrackPopup(boolean on) {
      try {
         MOD.settings().putBoolean("new-track-popup", on);
         MOD.settings().flush();
      } catch (Throwable t) {
         log("Could not save the new track browser setting", t);
      }
   }
}
