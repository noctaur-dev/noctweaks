// NocTweaks ARA Bridge: the host side of ARA, after Celemony's ARA MiniHost example (Apache 2.0).
#include "ara_document.h"

#include <cstdio>
#include <cstring>
#include <mutex>
#include <map>

#include "dr_wav.h"

#if defined (_WIN32)
    #include <windows.h>
#endif

using namespace ARA;

namespace bridge {

namespace {

void logLine (const char* fmt, const char* a = "", double v = 0) {
    std::fprintf (stderr, "[noctweaks-ara-bridge] ");
    std::fprintf (stderr, fmt, a, v);
    std::fprintf (stderr, "\n");
}

bool openWav (drwav* wav, const std::string& path) {
#if defined (_WIN32)
    int n = MultiByteToWideChar (CP_UTF8, 0, path.c_str (), -1, nullptr, 0);
    std::wstring w (n, L'\0');
    MultiByteToWideChar (CP_UTF8, 0, path.c_str (), -1, w.data (), n);
    return drwav_init_file_w (wav, w.c_str (), nullptr);
#else
    return drwav_init_file (wav, path.c_str (), nullptr);
#endif
}

/** One reader per ARA audio reader: Melodyne may read from several threads, each with its own reader. */
struct Reader {
    drwav wav {};
    bool is64 = false;
    bool open = false;
    std::vector<float> scratch;
    std::mutex lock;
};

// ARA is started once per plug-in binary and stopped when its last document goes.
std::map<const ARAFactory*, int>& factoryUsers () {
    static std::map<const ARAFactory*, int> m;
    return m;
}

}  // namespace

struct AraDocument::Impl {
    const ARAFactory* factory;
    bool counted = false;            // counted among the factory's users (ARA started for it)
    const ARADocumentControllerInstance* instance = nullptr;
    ARADocumentControllerRef ref = nullptr;
    const ARADocumentControllerInterface* dc = nullptr;

    Session session;
    std::string trackName;
    std::string sourceID, modificationID;
    drwav_uint64 frames = 0;
    unsigned channels = 1;
    double fileRate = 44100;

    double bpm = 120;
    ARAContentTempoEntry tempo[2] { { 0.0, 0.0 }, { 0.5, 1.0 } };
    ARAContentBarSignature signature { 4, 4, 0.0 };

    std::vector<uint8_t> archive;    // the plug-in's own bytes, restored from
    std::string archiveID;           // which plug-in (document archive ID) wrote them
    std::vector<uint8_t>* writing = nullptr;

    ARAMusicalContextRef context = nullptr;
    ARARegionSequenceRef sequence = nullptr;
    ARAAudioSourceRef source = nullptr;
    ARAAudioModificationRef modification = nullptr;
    ARAPlaybackRegionRef region = nullptr;

    // ------------------------------------------------------------------ host interfaces

    static Impl* self (ARAAudioAccessControllerHostRef r) { return (Impl*) r; }

    // audio access: Melodyne reads the clip's file through these
    static ARAAudioReaderHostRef ARA_CALL createReader (ARAAudioAccessControllerHostRef ctl, ARAAudioSourceHostRef, ARABool use64) {
        auto* d = (Impl*) ctl;
        auto* r = new Reader ();
        r->is64 = use64 != kARAFalse;
        r->open = openWav (&r->wav, d->session.file);
        return (ARAAudioReaderHostRef) r;
    }
    static ARABool ARA_CALL readSamples (ARAAudioAccessControllerHostRef, ARAAudioReaderHostRef rr, ARASamplePosition pos,
                                         ARASampleCount count, void* const buffers[]) {
        auto* r = (Reader*) rr;
        std::lock_guard<std::mutex> g (r->lock);
        unsigned ch = r->open ? r->wav.channels : 1;
        for (unsigned c = 0; c < ch; ++c)
            std::memset (buffers[c], 0, (size_t) count * (r->is64 ? sizeof (double) : sizeof (float)));
        if (!r->open || pos < 0)
            return r->open ? kARATrue : kARAFalse;
        if (!drwav_seek_to_pcm_frame (&r->wav, (drwav_uint64) pos))
            return kARAFalse;
        r->scratch.resize ((size_t) count * ch);
        drwav_uint64 got = drwav_read_pcm_frames_f32 (&r->wav, (drwav_uint64) count, r->scratch.data ());
        for (drwav_uint64 i = 0; i < got; ++i)
            for (unsigned c = 0; c < ch; ++c) {
                float v = r->scratch[i * ch + c];
                if (r->is64)
                    ((double*) buffers[c])[i] = v;
                else
                    ((float*) buffers[c])[i] = v;
            }
        return kARATrue;
    }
    static void ARA_CALL destroyReader (ARAAudioAccessControllerHostRef, ARAAudioReaderHostRef rr) {
        auto* r = (Reader*) rr;
        if (r->open)
            drwav_uninit (&r->wav);
        delete r;
    }

    // archiving: Melodyne's edits, kept in the bridge's state in Bitwig's project
    static ARASize ARA_CALL archiveSize (ARAArchivingControllerHostRef ctl, ARAArchiveReaderHostRef) {
        return (ARASize) ((Impl*) ctl)->archive.size ();
    }
    static ARABool ARA_CALL readArchive (ARAArchivingControllerHostRef ctl, ARAArchiveReaderHostRef, ARASize pos, ARASize len, ARAByte buf[]) {
        auto* d = (Impl*) ctl;
        if (pos + len > d->archive.size ())
            return kARAFalse;
        std::memcpy (buf, d->archive.data () + pos, len);
        return kARATrue;
    }
    static ARABool ARA_CALL writeArchive (ARAArchivingControllerHostRef ctl, ARAArchiveWriterHostRef, ARASize pos, ARASize len, const ARAByte buf[]) {
        auto* d = (Impl*) ctl;
        if (!d->writing)
            return kARAFalse;
        if (d->writing->size () < pos + len)
            d->writing->resize (pos + len);
        std::memcpy (d->writing->data () + pos, buf, len);
        return kARATrue;
    }
    static void ARA_CALL archivingProgress (ARAArchivingControllerHostRef, float) {}
    static void ARA_CALL unarchivingProgress (ARAArchivingControllerHostRef, float) {}
    static ARAPersistentID ARA_CALL getArchiveID (ARAArchivingControllerHostRef ctl, ARAArchiveReaderHostRef) {
        auto* d = (Impl*) ctl;
        return d->archiveID.empty () ? nullptr : d->archiveID.c_str ();
    }

    // content access: tempo and bar signature
    static ARABool ARA_CALL contextAvailable (ARAContentAccessControllerHostRef, ARAMusicalContextHostRef, ARAContentType t) {
        return (t == kARAContentTypeTempoEntries || t == kARAContentTypeBarSignatures) ? kARATrue : kARAFalse;
    }
    static ARAContentGrade ARA_CALL contextGrade (ARAContentAccessControllerHostRef, ARAMusicalContextHostRef, ARAContentType) {
        return kARAContentGradeAdjusted;
    }
    static ARAContentReaderHostRef ARA_CALL contextReader (ARAContentAccessControllerHostRef, ARAMusicalContextHostRef, ARAContentType t, const ARAContentTimeRange*) {
        return (ARAContentReaderHostRef) (intptr_t) (t == kARAContentTypeTempoEntries ? 1 : t == kARAContentTypeBarSignatures ? 2 : 0);
    }
    static ARABool ARA_CALL sourceAvailable (ARAContentAccessControllerHostRef, ARAAudioSourceHostRef, ARAContentType) { return kARAFalse; }
    static ARAContentGrade ARA_CALL sourceGrade (ARAContentAccessControllerHostRef, ARAAudioSourceHostRef, ARAContentType) { return kARAContentGradeInitial; }
    static ARAContentReaderHostRef ARA_CALL sourceReader (ARAContentAccessControllerHostRef, ARAAudioSourceHostRef, ARAContentType, const ARAContentTimeRange*) { return nullptr; }
    static ARAInt32 ARA_CALL eventCount (ARAContentAccessControllerHostRef, ARAContentReaderHostRef r) {
        return (intptr_t) r == 1 ? 2 : (intptr_t) r == 2 ? 1 : 0;
    }
    static const void* ARA_CALL eventData (ARAContentAccessControllerHostRef ctl, ARAContentReaderHostRef r, ARAInt32 i) {
        auto* d = (Impl*) ctl;
        if ((intptr_t) r == 1)
            return &d->tempo[i];
        if ((intptr_t) r == 2)
            return &d->signature;
        return nullptr;
    }
    static void ARA_CALL destroyContentReader (ARAContentAccessControllerHostRef, ARAContentReaderHostRef) {}

    // model updates: only logged for now
    static void ARA_CALL analysisProgress (ARAModelUpdateControllerHostRef, ARAAudioSourceHostRef, ARAAnalysisProgressState s, float v) {
        if (s == kARAAnalysisProgressCompleted)
            logLine ("analysis finished%s (%.0f%%)", "", 100.0 * v);
    }
    static void ARA_CALL sourceChanged (ARAModelUpdateControllerHostRef, ARAAudioSourceHostRef, const ARAContentTimeRange*, ARAContentUpdateFlags) {}
    static void ARA_CALL modificationChanged (ARAModelUpdateControllerHostRef, ARAAudioModificationHostRef, const ARAContentTimeRange*, ARAContentUpdateFlags) {}
    static void ARA_CALL regionChanged (ARAModelUpdateControllerHostRef, ARAPlaybackRegionHostRef, const ARAContentTimeRange*, ARAContentUpdateFlags) {}
    static void ARA_CALL documentChanged (ARAModelUpdateControllerHostRef) {}

    ARAAudioAccessControllerInterface audioAccess { ARA_IMPLEMENTED_STRUCT_SIZE (ARAAudioAccessControllerInterface, destroyAudioReader),
                                                    &createReader, &readSamples, &destroyReader };
    ARAArchivingControllerInterface archiving { ARA_IMPLEMENTED_STRUCT_SIZE (ARAArchivingControllerInterface, getDocumentArchiveID),
                                                &archiveSize, &readArchive, &writeArchive, &archivingProgress, &unarchivingProgress, &getArchiveID };
    ARAContentAccessControllerInterface content { ARA_IMPLEMENTED_STRUCT_SIZE (ARAContentAccessControllerInterface, destroyContentReader),
                                                  &contextAvailable, &contextGrade, &contextReader,
                                                  &sourceAvailable, &sourceGrade, &sourceReader,
                                                  &eventCount, &eventData, &destroyContentReader };
    ARAModelUpdateControllerInterface updates { ARA_IMPLEMENTED_STRUCT_SIZE (ARAModelUpdateControllerInterface, notifyDocumentDataChanged),
                                                &analysisProgress, &sourceChanged, &modificationChanged, &regionChanged, &documentChanged };
    ARADocumentControllerHostInstance host {};

    explicit Impl (const ARAFactory* f) : factory (f) {
        host = { ARA_IMPLEMENTED_STRUCT_SIZE (ARADocumentControllerHostInstance, playbackControllerInterface),
                 (ARAAudioAccessControllerHostRef) this, &audioAccess,
                 (ARAArchivingControllerHostRef) this, &archiving,
                 (ARAContentAccessControllerHostRef) this, &content,
                 (ARAModelUpdateControllerHostRef) this, &updates,
                 nullptr, nullptr };
    }

    void destroy () {
        if (!dc)
            return;
        dc->beginEditing (ref);
        if (region) dc->destroyPlaybackRegion (ref, region);
        if (modification) dc->destroyAudioModification (ref, modification);
        if (source) {
            dc->enableAudioSourceSamplesAccess (ref, source, kARAFalse);
            dc->destroyAudioSource (ref, source);
        }
        if (sequence) dc->destroyRegionSequence (ref, sequence);
        if (context) dc->destroyMusicalContext (ref, context);
        dc->endEditing (ref);
        dc->destroyDocumentController (ref);
        region = nullptr; modification = nullptr; source = nullptr; sequence = nullptr; context = nullptr;
        dc = nullptr; ref = nullptr; instance = nullptr;
    }
};

AraDocument::AraDocument (const ARAFactory* factory) : impl (new Impl (factory)) {}

AraDocument::~AraDocument () {
    impl->destroy ();
    if (impl->counted && --factoryUsers ()[impl->factory] == 0)
        impl->factory->uninitializeARA ();
}

ARADocumentControllerRef AraDocument::controller () const {
    return impl->ref;
}

ARAPlaybackRegionRef AraDocument::region () const {
    return impl->region;
}

void AraDocument::setTempo (double bpm) {
    if (bpm <= 0)
        return;
    impl->bpm = bpm;
    impl->tempo[1] = { 60.0 / bpm, 1.0 };   // one quarter note later
    if (impl->dc && impl->context) {
        impl->dc->beginEditing (impl->ref);
        impl->dc->updateMusicalContextContent (impl->ref, impl->context, nullptr, kARAContentUpdateEverythingChanged);
        impl->dc->endEditing (impl->ref);
    }
}

// Saved form: [ID length (4 bytes)][document archive ID][the plug-in's bytes].
void AraDocument::setArchive (std::vector<uint8_t> bytes) {
    impl->archive.clear ();
    impl->archiveID.clear ();
    if (bytes.size () < 4)
        return;
    uint32_t n = 0;
    std::memcpy (&n, bytes.data (), 4);
    if (4 + (size_t) n > bytes.size ())
        return;
    impl->archiveID.assign ((const char*) bytes.data () + 4, n);
    impl->archive.assign (bytes.begin () + 4 + n, bytes.end ());
}

std::vector<uint8_t> AraDocument::store () {
    std::vector<uint8_t> out;
    if (!impl->dc || !impl->dc->storeObjectsToArchive)
        return out;
    impl->writing = &out;
    bool ok = impl->dc->storeObjectsToArchive (impl->ref, (ARAArchiveWriterHostRef) 1, nullptr) != kARAFalse;
    impl->writing = nullptr;
    if (!ok || out.empty ())
        return {};
    std::string id = impl->factory->documentArchiveID ? impl->factory->documentArchiveID : "";
    std::vector<uint8_t> saved (4 + id.size ());
    uint32_t n = (uint32_t) id.size ();
    std::memcpy (saved.data (), &n, 4);
    std::memcpy (saved.data () + 4, id.data (), id.size ());
    saved.insert (saved.end (), out.begin (), out.end ());
    return saved;
}

bool AraDocument::build (const Session& s, const std::string& trackName, std::string& error) {
    auto& d = *impl;
    d.destroy ();
    d.session = s;
    d.trackName = trackName;

    drwav wav;
    if (!openWav (&wav, s.file)) {
        error = "can't read " + s.file + " (WAV files only for now)";
        return false;
    }
    d.frames = wav.totalPCMFrameCount;
    d.channels = wav.channels;
    d.fileRate = wav.sampleRate;
    drwav_uninit (&wav);

    if (!d.counted && factoryUsers ()[d.factory]++ == 0) {
        if (d.factory->lowestSupportedApiGeneration > kARAAPIGeneration_2_0_Final
            || d.factory->highestSupportedApiGeneration < kARAAPIGeneration_2_0_Final) {
            error = "this plug-in's ARA version isn't supported";
            factoryUsers ()[d.factory]--;
            return false;
        }
        static ARAInterfaceConfiguration config { ARA_IMPLEMENTED_STRUCT_SIZE (ARAInterfaceConfiguration, assertFunctionAddress),
                                                  kARAAPIGeneration_2_0_Final, nullptr };
        d.factory->initializeARAWithConfiguration (&config);
    }
    d.counted = true;

    ARADocumentProperties docProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARADocumentProperties, name), "Bitwig" };
    d.instance = d.factory->createDocumentControllerWithDocument (&d.host, &docProps);
    if (!d.instance) {
        error = "the plug-in made no ARA document";
        return false;
    }
    d.ref = d.instance->documentControllerRef;
    d.dc = d.instance->documentControllerInterface;

    double fileSeconds = d.fileRate > 0 ? (double) d.frames / d.fileRate : 0;
    double offset = s.offset < 0 ? 0 : s.offset;
    double length = s.length > 0 ? s.length : fileSeconds - offset;
    d.sourceID = "file:" + s.file;
    d.modificationID = "clip:" + s.file + "@" + std::to_string (offset);

    d.dc->beginEditing (d.ref);
    ARAMusicalContextProperties ctxProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARAMusicalContextProperties, color), nullptr, 0, nullptr };
    d.context = d.dc->createMusicalContext (d.ref, (ARAMusicalContextHostRef) 1, &ctxProps);
    ARARegionSequenceProperties seqProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARARegionSequenceProperties, color),
                                           d.trackName.c_str (), 0, d.context, nullptr };
    d.sequence = d.dc->createRegionSequence (d.ref, (ARARegionSequenceHostRef) 2, &seqProps);
    ARAAudioSourceProperties srcProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARAAudioSourceProperties, merits64BitSamples),
                                        s.file.c_str (), d.sourceID.c_str (), (ARASampleCount) d.frames, d.fileRate,
                                        (ARAChannelCount) d.channels, kARAFalse };
    d.source = d.dc->createAudioSource (d.ref, (ARAAudioSourceHostRef) 3, &srcProps);
    ARAAudioModificationProperties modProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARAAudioModificationProperties, persistentID),
                                              d.trackName.c_str (), d.modificationID.c_str () };
    d.modification = d.dc->createAudioModification (d.ref, d.source, (ARAAudioModificationHostRef) 4, &modProps);
    ARAPlaybackRegionProperties regProps { ARA_IMPLEMENTED_STRUCT_SIZE (ARAPlaybackRegionProperties, color),
                                           kARAPlaybackTransformationNoChanges,
                                           offset, length, s.start, length,
                                           d.context, d.sequence, d.trackName.c_str (), nullptr };
    d.region = d.dc->createPlaybackRegion (d.ref, d.modification, (ARAPlaybackRegionHostRef) 5, &regProps);

    bool compatible = d.archiveID == (d.factory->documentArchiveID ? d.factory->documentArchiveID : "");
    for (ARASize i = 0; !compatible && i < d.factory->compatibleDocumentArchiveIDsCount; ++i)
        compatible = d.archiveID == d.factory->compatibleDocumentArchiveIDs[i];
    if (!d.archive.empty () && compatible && d.dc->restoreObjectsFromArchive) {
        bool ok = d.dc->restoreObjectsFromArchive (d.ref, (ARAArchiveReaderHostRef) 1, nullptr) != kARAFalse;
        logLine ("restoring Melodyne's edits %s", ok ? "worked" : "failed");
    }
    d.dc->endEditing (d.ref);
    d.dc->enableAudioSourceSamplesAccess (d.ref, d.source, kARATrue);
    return true;
}

}  // namespace bridge
