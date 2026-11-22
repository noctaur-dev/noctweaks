package io.github.noctaur.video;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/**
 * The Video mod's options in Bitwig's Settings: where the sound copies go and what decodes (Locations), and how
 * the film strip sits on video clips (User Interface; also in the Video menus).
 */
public final class VideoSettings implements PreferenceProvider {
   public VideoSettings() {
   }

   @Override
   public void declare(Preferences preferences) {
      Section video = preferences.section(Page.LOCATIONS, "noctweaks-video", "Video");
      preferences.dropdown("sound_copies", "Sound copies of videos", Sounds.PLACES)
         .in(video)
         .value(Sounds::place, Sounds::setPlace);
      preferences.dropdown("decoder", "Decode pictures with", Decode.CHOICES)
         .in(video)
         .value(Decode::choice, Decode::choose);

      Section clips = preferences.section(Page.USER_INTERFACE, "noctweaks-video-clips", "Video Clips");
      preferences.choice("film_strip", "Film strip", "Separate", "Over the waveform")
         .in(clips)
         .value(FilmStrip::mode, FilmStrip::setMode);
      preferences.slider("film_strip_opacity", "Film strip opacity (over the waveform)", 0, 100)
         .in(clips)
         .wholeNumbers()
         .value(FilmStrip::opacityPercent, FilmStrip::setOpacityPercent);
   }
}
