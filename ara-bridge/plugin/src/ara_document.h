// NocTweaks ARA Bridge: the host side of ARA. The bridge plays the part of the DAW for Melodyne: it describes
// one track (a region sequence) holding one clip (a playback region) of one audio file (an audio source), and
// lets Melodyne read that file directly, so no audio has to be transferred.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "ARA_API/ARAInterface.h"
#include "session.h"

namespace bridge {

class AraDocument {
public:
    explicit AraDocument (const ARA::ARAFactory* factory);
    ~AraDocument ();

    /** Makes the document for the session's clip. Restores Melodyne's edits if an archive was set. Main thread. */
    bool build (const Session& session, const std::string& trackName, std::string& error);

    ARA::ARADocumentControllerRef controller () const;
    ARA::ARAPlaybackRegionRef region () const;

    /** Melodyne's edits, to keep in Bitwig's project (the bridge's saved state). */
    std::vector<uint8_t> store ();
    void setArchive (std::vector<uint8_t> bytes);

    /** The project tempo, for the musical context (constant for now). */
    void setTempo (double bpm);

    struct Impl;

private:
    std::unique_ptr<Impl> impl;
};

}  // namespace bridge
