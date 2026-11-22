package io.github.noctaur.video;

import com.bitwig.flt.document.core.master.g2L;

/**
 * Keeps the video window in step with Bitwig's transport, about 40 times a second:
 * - play: seek to the playhead and play; while playing, re-seek if mpv drifts more than ~80 ms;
 * - stop: pause on the playhead's frame;
 * - while stopped, moving the playhead (clicking, scrubbing, jumping) moves the picture;
 * - before the video's start the window shows its first frame, paused.
 *
 * Reads Bitwig's transport values from its own thread; they are plain numbers Bitwig updates as it plays.
 */
final class Sync {
   private Sync() {
   }

   private static Thread thread;
   private static volatile boolean kicked;

   static synchronized void start() {
      if (thread != null && thread.isAlive()) {
         kick();
         return;
      }
      thread = new Thread(Sync::run, "noctweaks-video-sync");
      thread.setDaemon(true);
      thread.start();
   }

   /** Re-sync now (the start moved, a new video). */
   static void kick() {
      kicked = true;
   }

   private static void run() {
      boolean wasPlaying = false;
      double lastT = Double.NaN;
      long lastSeek = 0;
      boolean beforeStart = false;
      Video.log("Video: sync started", null);
      while (true) {
         try {
            Thread.sleep(25);
            if (!Player.running()) {
               if (Player.current() == null && Video.link() == null) {
                  break;
               }
               continue;
            }
            g2L doc = Video.project();
            Video.Link link = Video.link();
            if (doc == null || link == null) {
               continue;
            }
            boolean playing = Clock.playing(doc);
            double t = Clock.seconds(doc) - link.startSeconds();
            long now = System.nanoTime();
            boolean kick = kicked;
            kicked = false;

            if (playing != wasPlaying || kick) {
               if (playing) {
                  beforeStart = t < 0;
                  Player.seek(Math.max(0, t));
                  Player.pause(beforeStart);
               } else {
                  Player.pause(true);
                  Player.seek(Math.max(0, t));
               }
               lastSeek = now;
               wasPlaying = playing;
               lastT = t;
               continue;
            }

            if (playing) {
               if (beforeStart && t >= 0) {           // the playhead reached the video's start
                  beforeStart = false;
                  Player.seek(t);
                  Player.pause(false);
                  lastSeek = now;
               } else if (!beforeStart && t < 0) {    // looped back before the start
                  beforeStart = true;
                  Player.pause(true);
                  Player.seek(0);
                  lastSeek = now;
               } else if (!beforeStart && now - lastSeek > 400_000_000L) {
                  double pos = Player.reportedPos;
                  if (!Double.isNaN(pos)) {
                     double age = (now - Player.reportedAt) / 1e9;
                     double mpvNow = pos + (Player.reportedPaused ? 0 : age);
                     if (Math.abs(mpvNow - t) > 0.08) {   // drifted, or Bitwig looped or jumped
                        Player.seek(t + 0.03);           // a little ahead: the seek itself takes a moment
                        lastSeek = now;
                     }
                  }
               }
            } else if (Double.isNaN(lastT) || Math.abs(t - lastT) > 0.0005) {
               if (now - lastSeek > 40_000_000L) {       // scrubbing: at most 25 seeks a second
                  Player.seek(Math.max(0, t));
                  lastSeek = now;
                  lastT = t;
               }
            }
         } catch (InterruptedException e) {
            break;
         } catch (Throwable t) {
            Video.log("Video: sync", t);
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               break;
            }
         }
      }
      Video.log("Video: sync stopped", null);
   }
}
