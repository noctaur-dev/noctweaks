package io.github.noctaur.browser;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the icon window ({@link IconPicker}) as its own small Java program, with Bitwig's own Java: Bitwig
 * runs without windowing (headless), so a window can't be opened from inside it. The window writes icons.txt;
 * NocBrowser notices the change within a couple of seconds.
 */
final class IconWindow {
   private IconWindow() {
   }

   static void open(List<String> keys, String title) {
      if (keys == null || keys.isEmpty()) {
         return;
      }
      Thread t = new Thread(() -> {
         try {
            File classes = unpack();
            File java = javaExe();
            List<String> cmd = new ArrayList<>(List.of(java.getAbsolutePath(), "-Djava.awt.headless=false",
               "-Dsun.java2d.uiScale.enabled=true", "-cp", classes.getAbsolutePath(), IconPicker.class.getName(),
               NocBrowser.stateDir().getAbsolutePath(), Browser.MOD.resource("icons").toString(), title));
            cmd.addAll(keys);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(classes.getParentFile(), "icon-window.log"));
            pb.start();
            NocBrowser.log("Icon window opened for " + keys, null);
         } catch (Throwable e) {
            NocBrowser.log("Could not open the icon window", e);
         }
      }, "noctweaks-icon-window");
      t.setDaemon(true);
      t.start();
   }

   /** javaw (no console window) next to the Java Bitwig runs on. */
   private static File javaExe() {
      File bin = new File(System.getProperty("java.home"), "bin");
      for (String n : new String[]{"javaw.exe", "java.exe", "java"}) {
         File f = new File(bin, n);
         if (f.isFile()) {
            return f;
         }
      }
      return new File(bin, "java");
   }

   /** Writes IconPicker's classes (it uses nothing of Bitwig's) to a cache folder the window runs from. */
   private static File unpack() throws Exception {
      File dir = new File(new File(NocBrowser.stateDir(), "cache"), "icon-window");
      String base = IconPicker.class.getName().replace('.', '/');
      File pkg = new File(dir, base.substring(0, base.lastIndexOf('/')));
      pkg.mkdirs();
      copy(base + ".class", dir);
      for (int i = 1; i < 64; i++) {
         if (!copy(base + "$" + i + ".class", dir)) {
            if (i > 12) {
               break;
            }
         }
      }
      return dir;
   }

   private static boolean copy(String resource, File dir) throws Exception {
      try (InputStream in = IconPicker.class.getClassLoader().getResourceAsStream(resource)) {
         if (in == null) {
            return false;
         }
         File out = new File(dir, resource);
         Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
         return true;
      }
   }
}
