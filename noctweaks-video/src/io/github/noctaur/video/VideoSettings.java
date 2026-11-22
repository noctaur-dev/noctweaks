package io.github.noctaur.video;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/** The Video mod's options in Bitwig's Settings (Locations page): where the sound copies of videos go. */
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
   }
}
