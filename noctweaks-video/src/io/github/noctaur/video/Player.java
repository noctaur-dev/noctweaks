package io.github.noctaur.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The video window: an mpv process, driven over mpv's JSON IPC (one command per line).
 * Bitwig's own Java UI runs headless, so the picture has to be another program's window.
 *
 * Windows: a named pipe, opened twice (one handle to write commands, one to read mpv's events), because a
 * read waiting on a synchronous pipe handle blocks writes on the same handle. macOS/Linux: a Unix socket.
 */
final class Player {
   private Player() {
   }

   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

   private static Process process;
   private static OutputStream out;
   private static Thread reader;
   private static File current;

   /** mpv's own idea of where it is (seconds), and when it said so. */
   static volatile double reportedPos = Double.NaN;
   static volatile long reportedAt;
   static volatile boolean reportedPaused = true;

   static synchronized boolean running() {
      return process != null && process.isAlive();
   }

   static synchronized File current() {
      return running() ? current : null;
   }

   /** Opens the window on a video (or switches the open window to it). */
   static synchronized void show(File video) {
      try {
         if (running() && out != null) {
            if (!video.equals(current)) {
               send("loadfile", video.getAbsolutePath(), "replace");
               current = video;
            }
            return;
         }
         File mpv = Tools.find("mpv");
         if (mpv == null) {
            Video.log("Video: mpv not found. Install mpv, or put it in the mod's tools folder (arranger menu > Video > Open Video Tools Folder)", null);
            Tools.openToolsFolder();
            return;
         }
         String ipc = WINDOWS
            ? "\\\\.\\pipe\\noctweaks-video-" + ProcessHandle.current().pid()
            : new File(System.getProperty("java.io.tmpdir"), "noctweaks-video-" + ProcessHandle.current().pid() + ".sock").getAbsolutePath();
         if (!WINDOWS) {
            new File(ipc).delete();
         }
         List<String> cmd = new ArrayList<>(List.of(
            mpv.getAbsolutePath(),
            "--input-ipc-server=" + ipc,
            "--idle=yes",
            "--force-window=yes",
            "--keep-open=always",
            "--pause=yes",
            "--audio=no",                       // the sound plays in Bitwig
            "--osc=no",
            "--ontop=yes",
            "--geometry=640x360-40+80",
            "--title=Video: ${filename}",
            "--hr-seek=yes",                    // exact frames when scrubbing
            "--save-position-on-quit=no",
            "--terminal=no",
            video.getAbsolutePath()));
         Video.log("Video: starting " + String.join(" ", cmd), null);
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.redirectErrorStream(true);
         pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
         process = pb.start();
         current = video;
         connect(ipc);
      } catch (Throwable t) {
         Video.log("Video: could not open the video window", t);
         close();
      }
   }

   private static void connect(String ipc) throws Exception {
      InputStream in = null;
      long until = System.currentTimeMillis() + 5000;
      Exception last = null;
      while (System.currentTimeMillis() < until && process.isAlive()) {
         try {
            if (WINDOWS) {
               // mpv makes the pipe's next instance only after a client connects: open the two one by one.
               if (out == null) {
                  RandomAccessFile w = new RandomAccessFile(ipc, "rw");
                  out = new java.io.FileOutputStream(w.getFD());
               }
               RandomAccessFile r = new RandomAccessFile(ipc, "rw");
               in = new java.io.FileInputStream(r.getFD());
            } else {
               SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
               ch.connect(UnixDomainSocketAddress.of(ipc));
               out = Channels.newOutputStream(ch);
               in = Channels.newInputStream(ch);
            }
            break;
         } catch (Exception e) {
            last = e;
            Thread.sleep(100);
         }
      }
      if (out == null || in == null) {
         throw new IllegalStateException("could not connect to mpv at " + ipc, last);
      }
      InputStream events = in;
      reader = new Thread(() -> readEvents(events), "noctweaks-video-mpv");
      reader.setDaemon(true);
      reader.start();
      sendRaw("{\"command\":[\"observe_property\",1,\"time-pos\"]}");
      sendRaw("{\"command\":[\"observe_property\",2,\"pause\"]}");
      Video.log("Video: connected to mpv", null);
   }

   private static void readEvents(InputStream in) {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
         String line;
         while ((line = r.readLine()) != null) {
            // Only two properties are watched; a tiny reader is enough:
            // {"event":"property-change","id":1,"name":"time-pos","data":12.345}
            if (line.contains("\"property-change\"")) {
               String data = field(line, "data");
               if (line.contains("\"time-pos\"")) {
                  try {
                     reportedPos = data == null || data.equals("null") ? Double.NaN : Double.parseDouble(data);
                     reportedAt = System.nanoTime();
                  } catch (NumberFormatException ignored) {
                  }
               } else if (line.contains("\"pause\"")) {
                  reportedPaused = "true".equals(data);
               }
            }
         }
      } catch (Throwable ignored) {
         // the window was closed
      }
   }

   private static String field(String json, String name) {
      int i = json.indexOf("\"" + name + "\":");
      if (i < 0) {
         return null;
      }
      int s = i + name.length() + 3;
      int e = s;
      while (e < json.length() && ",}".indexOf(json.charAt(e)) < 0) {
         e++;
      }
      return json.substring(s, e).trim();
   }

   static synchronized void seek(double seconds) {
      send("seek", String.format(Locale.ROOT, "%.4f", Math.max(0, seconds)), "absolute+exact");
   }

   static synchronized void pause(boolean paused) {
      sendRaw("{\"command\":[\"set_property\",\"pause\"," + paused + "]}");
   }

   private static void send(String... args) {
      StringBuilder b = new StringBuilder("{\"command\":[");
      for (int i = 0; i < args.length; i++) {
         if (i > 0) {
            b.append(',');
         }
         String a = args[i];
         boolean number = a.matches("-?\\d+(\\.\\d+)?");
         if (number) {
            b.append(a);
         } else {
            b.append('"').append(a.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
         }
      }
      sendRaw(b.append("]}").toString());
   }

   private static synchronized void sendRaw(String json) {
      if (out == null) {
         return;
      }
      try {
         out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
         out.flush();
      } catch (Throwable t) {
         Video.log("Video: lost the video window", t);
         out = null;
      }
   }

   static synchronized void close() {
      try {
         if (out != null) {
            sendRaw("{\"command\":[\"quit\"]}");
         }
      } catch (Throwable ignored) {
      }
      try {
         if (out != null) {
            out.close();
         }
      } catch (Throwable ignored) {
      }
      out = null;
      if (process != null) {
         process.destroy();
      }
      process = null;
      current = null;
      reportedPos = Double.NaN;
   }
}
