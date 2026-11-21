// NocTweaks ARA Bridge (prototype): a CLAP effect that hosts an ARA plug-in (Melodyne) and plays the part of
// the DAW for it. Bitwig has no ARA, so the bridge tells Melodyne about the clip itself (which file, where it
// sits on the timeline), from a small session file the Patchbay mod writes. Melodyne then reads the file
// directly, so nothing has to be transferred, and renders the edited clip in time with Bitwig's transport.
//
// Put it on an audio track holding one clip. Open its window to see Melodyne.
#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include <clap/clap.h>

#include "ara_document.h"
#include "session.h"
#include "tinyfiledialogs.h"
#include "vst3_host.h"

namespace {

using namespace bridge;

const char* const features[] = { CLAP_PLUGIN_FEATURE_AUDIO_EFFECT, CLAP_PLUGIN_FEATURE_STEREO, nullptr };

const clap_plugin_descriptor_t descriptor = {
    CLAP_VERSION_INIT,
    "io.github.noctaur.ara-bridge",
    "NocTweaks ARA Bridge",
    "noctaur",
    "https://github.com/noctaur-dev/noctweaks",
    "",
    "",
    "0.1.0-dev.1",
    "Hosts an ARA plug-in (Melodyne) for one clip, without transferring audio. Prototype.",
    features,
};

void log (const std::string& s) {
    std::fprintf (stderr, "[noctweaks-ara-bridge] %s\n", s.c_str ());
}

struct Bridge {
    clap_plugin_t plugin {};
    const clap_host_t* host = nullptr;
    const clap_host_timer_support_t* hostTimer = nullptr;
    const clap_host_track_info_t* hostTrack = nullptr;
    const clap_host_gui_t* hostGui = nullptr;
    clap_id timer = CLAP_INVALID_ID;

    double sampleRate = 44100;
    uint32_t maxFrames = 1024;
    bool active = false;

    std::string track = "Track";
    Session session;
    bool haveSession = false;
    std::filesystem::file_time_type sessionTime {};

    std::unique_ptr<Vst3Host> vst;
    std::unique_ptr<AraDocument> doc;
    const ARA::ARAPlugInExtensionInstance* araInstance = nullptr;   // the plug-in's ARA side, once bound
    std::vector<uint8_t> archive;     // Melodyne's edits from the saved project, until a document takes them
    std::mutex audio;                 // held by the main thread while it rebuilds
    std::atomic<bool> ready { false };

    void* parent = nullptr;           // the window Bitwig gave the bridge's editor
    bool editorOpen = false;

    // ------------------------------------------------------------------ session

    void readTrackName () {
        if (!hostTrack)
            return;
        clap_track_info_t info {};
        if (hostTrack->get (host, &info) && (info.flags & CLAP_TRACK_INFO_HAS_TRACK_NAME) && info.name[0])
            track = info.name;
    }

    /** Re-reads this track's session file; rebuilds when it changed. Main thread. */
    void checkSession (bool force = false) {
        auto path = sessionPath (track);
        std::error_code ec;
        if (!std::filesystem::exists (path, ec)) {
            if (haveSession) {
                haveSession = false;
                teardown ();
            }
            return;
        }
        auto t = std::filesystem::last_write_time (path, ec);
        if (!force && haveSession && t == sessionTime)
            return;
        Session s;
        if (!readSession (path, s))
            return;
        sessionTime = t;
        if (s.plugin.empty ())
            s.plugin = defaultPlugin ();
        if (!force && haveSession && s == session)
            return;
        session = s;
        haveSession = true;
        rebuild ();
    }

    void teardown () {
        std::lock_guard<std::mutex> g (audio);
        ready = false;
        if (doc) {   // keep Melodyne's edits for the next document
            auto a = doc->store ();
            if (!a.empty ())
                archive = std::move (a);
        }
        if (vst) {
            vst->closeEditor ();
            vst->stopProcessing ();
        }
        assignRegion (false);
        vst.reset ();   // the plug-in instance goes before the document it is bound to
        doc.reset ();
    }

    void rebuild () {
        bool reopen = editorOpen;
        if (editorOpen && vst)
            vst->closeEditor ();
        editorOpen = false;
        teardown ();
        if (!haveSession)
            return;
        if (session.plugin.empty ()) {
            log ("no ARA plug-in set for track " + track + " (Melodyne not found)");
            return;
        }
        std::lock_guard<std::mutex> g (audio);
        std::string error;
        auto v = std::make_unique<Vst3Host> ();
        v->onResizeRequest = [this] (uint32_t w, uint32_t h) {
            return hostGui && hostGui->request_resize (host, w, h);
        };
        if (!v->load (session.plugin, error)) {
            log ("could not load " + session.plugin + ": " + error);
            return;
        }
        if (!v->araFactory ()) {
            log (v->name () + " has no ARA support");
            return;
        }
        auto d = std::make_unique<AraDocument> (v->araFactory ());
        d->setArchive (archive);
        if (!d->build (session, track, error)) {
            log ("could not set up the clip: " + error);
            return;
        }
        constexpr ARA::ARAPlugInInstanceRoleFlags roles =
            ARA::kARAPlaybackRendererRole | ARA::kARAEditorRendererRole | ARA::kARAEditorViewRole;
        araInstance = v->bind (d->controller (), roles);
        if (!araInstance) {
            log (v->name () + " did not accept the ARA document");
            return;
        }
        doc = std::move (d);
        assignRegion (true);   // ARA: regions are handed over while the plug-in isn't rendering
        if (active && !v->startProcessing (sampleRate, (int32_t) maxFrames))
            log (v->name () + " could not start processing");
        vst = std::move (v);
        ready = active;
        log ("ready: " + vst->name () + " on " + session.file + " at " + std::to_string (session.start) + " s");
        if (reopen && parent)
            openEditor ();
    }

    /** Hands the clip (playback region) to the plug-in's renderers, or takes it back. ARA 2 needs both. */
    void assignRegion (bool add) {
        if (!araInstance || !doc || !doc->region ()) {
            if (!add)
                araInstance = nullptr;
            return;
        }
        auto* pr = araInstance->playbackRendererInterface;
        auto* er = araInstance->editorRendererInterface;
        if (add) {
            if (pr) pr->addPlaybackRegion (araInstance->playbackRendererRef, doc->region ());
            if (er) er->addPlaybackRegion (araInstance->editorRendererRef, doc->region ());
        } else {
            if (pr) pr->removePlaybackRegion (araInstance->playbackRendererRef, doc->region ());
            if (er) er->removePlaybackRegion (araInstance->editorRendererRef, doc->region ());
            araInstance = nullptr;
        }
    }

    /** No session yet: ask for the clip's audio file (and the ARA plug-in, if Melodyne isn't where it usually is). */
    void askForFile () {
        const char* wav[] = { "*.wav" };
        const char* f = tinyfd_openFileDialog ("ARA Bridge: the clip's audio file", "", 1, wav, "WAV files", 0);
        if (!f)
            return;
        Session s;
        s.file = f;
        s.plugin = defaultPlugin ();
        if (s.plugin.empty ()) {
            const char* vst3[] = { "*.vst3" };
            const char* p = tinyfd_openFileDialog ("ARA Bridge: the ARA plug-in (Melodyne.vst3)", "", 1, vst3, "VST3 plug-ins", 0);
            if (!p)
                return;
            s.plugin = p;
        }
        writeSession (sessionPath (track), s);   // starts at 0 s: move the clip to bar 1, or let the mod say where
        checkSession (true);
    }

    // ------------------------------------------------------------------ editor

    void openEditor () {
        uint32_t w = 0, h = 0;
        if (vst && parent && vst->openEditor (parent, w, h)) {
            editorOpen = true;
            if (hostGui)
                hostGui->request_resize (host, w, h);
        }
    }
};

Bridge* self (const clap_plugin_t* p) {
    return (Bridge*) p->plugin_data;
}

// ---------------------------------------------------------------------- audio ports

uint32_t portsCount (const clap_plugin_t*, bool) {
    return 1;
}

bool portsGet (const clap_plugin_t*, uint32_t index, bool isInput, clap_audio_port_info_t* info) {
    if (index != 0)
        return false;
    info->id = isInput ? 0 : 1;
    std::snprintf (info->name, sizeof (info->name), "%s", isInput ? "In" : "Out");
    info->flags = CLAP_AUDIO_PORT_IS_MAIN;
    info->channel_count = 2;
    info->port_type = CLAP_PORT_STEREO;
    info->in_place_pair = CLAP_INVALID_ID;
    return true;
}

const clap_plugin_audio_ports_t audioPorts { portsCount, portsGet };

// ---------------------------------------------------------------------- timer: watch the session file

void onTimer (const clap_plugin_t* p, clap_id) {
    self (p)->checkSession ();
}

const clap_plugin_timer_support_t timerSupport { onTimer };

// ---------------------------------------------------------------------- track info: which track the bridge is on

void trackChanged (const clap_plugin_t* p) {
    auto* b = self (p);
    std::string old = b->track;
    b->readTrackName ();
    if (b->track != old)
        b->checkSession (true);
}

const clap_plugin_track_info_t trackInfo { trackChanged };

// ---------------------------------------------------------------------- state: Melodyne's edits

bool stateSave (const clap_plugin_t* p, const clap_ostream_t* out) {
    auto* b = self (p);
    std::vector<uint8_t> a = b->doc ? b->doc->store () : b->archive;
    if (a.empty ())
        a = b->archive;
    const char magic[4] = { 'N', 'A', 'R', '1' };
    uint64_t n = a.size ();
    if (out->write (out, magic, 4) != 4 || out->write (out, &n, sizeof (n)) != (int64_t) sizeof (n))
        return false;
    size_t done = 0;
    while (done < a.size ()) {
        auto w = out->write (out, a.data () + done, a.size () - done);
        if (w <= 0)
            return false;
        done += (size_t) w;
    }
    return true;
}

bool readAll (const clap_istream_t* in, void* buf, size_t n) {
    size_t done = 0;
    while (done < n) {
        auto r = in->read (in, (uint8_t*) buf + done, n - done);
        if (r <= 0)
            return false;
        done += (size_t) r;
    }
    return true;
}

bool stateLoad (const clap_plugin_t* p, const clap_istream_t* in) {
    auto* b = self (p);
    char magic[4];
    uint64_t n = 0;
    if (!readAll (in, magic, 4) || std::memcmp (magic, "NAR1", 4) != 0 || !readAll (in, &n, sizeof (n)) || n > (1ull << 31))
        return false;
    std::vector<uint8_t> a ((size_t) n);
    if (n && !readAll (in, a.data (), (size_t) n))
        return false;
    b->archive = std::move (a);
    b->checkSession (true);
    return true;
}

const clap_plugin_state_t state { stateSave, stateLoad };

// ---------------------------------------------------------------------- gui: Melodyne's editor inside the bridge's window

#if defined (_WIN32)
const char* const guiApi = CLAP_WINDOW_API_WIN32;
#elif defined (__APPLE__)
const char* const guiApi = CLAP_WINDOW_API_COCOA;
#else
const char* const guiApi = CLAP_WINDOW_API_X11;
#endif

bool guiSupported (const clap_plugin_t*, const char* api, bool floating) {
    return !floating && std::strcmp (api, guiApi) == 0;
}
bool guiPreferred (const clap_plugin_t*, const char** api, bool* floating) {
    *api = guiApi;
    *floating = false;
    return true;
}
bool guiCreate (const clap_plugin_t* p, const char* api, bool floating) {
    return guiSupported (p, api, floating);
}
void guiDestroy (const clap_plugin_t* p) {
    auto* b = self (p);
    if (b->vst)
        b->vst->closeEditor ();
    b->editorOpen = false;
    b->parent = nullptr;
}
bool guiSetScale (const clap_plugin_t*, double) {
    return false;
}
bool guiGetSize (const clap_plugin_t* p, uint32_t* w, uint32_t* h) {
    auto* b = self (p);
    if (b->vst && b->editorOpen && b->vst->editorSize (*w, *h))
        return true;
    *w = 640;
    *h = 360;
    return true;
}
bool guiCanResize (const clap_plugin_t*) {
    return true;
}
bool guiResizeHints (const clap_plugin_t*, clap_gui_resize_hints_t*) {
    return false;
}
bool guiAdjust (const clap_plugin_t*, uint32_t*, uint32_t*) {
    return true;
}
bool guiSetSize (const clap_plugin_t* p, uint32_t w, uint32_t h) {
    auto* b = self (p);
    return !b->vst || !b->editorOpen || b->vst->resizeEditor (w, h);
}
bool guiSetParent (const clap_plugin_t* p, const clap_window_t* window) {
    auto* b = self (p);
#if defined (_WIN32)
    b->parent = window->win32;
#elif defined (__APPLE__)
    b->parent = window->cocoa;
#else
    b->parent = (void*) window->x11;
#endif
    return true;
}
bool guiSetTransient (const clap_plugin_t*, const clap_window_t*) {
    return false;
}
void guiSuggestTitle (const clap_plugin_t*, const char*) {}
bool guiShow (const clap_plugin_t* p) {
    auto* b = self (p);
    if (!b->haveSession)
        b->askForFile ();
    if (!b->editorOpen)
        b->openEditor ();
    return true;
}
bool guiHide (const clap_plugin_t*) {
    return true;
}

const clap_plugin_gui_t gui { guiSupported, guiPreferred, guiCreate, guiDestroy, guiSetScale, guiGetSize, guiCanResize,
                              guiResizeHints, guiAdjust, guiSetSize, guiSetParent, guiSetTransient, guiSuggestTitle,
                              guiShow, guiHide };

// ---------------------------------------------------------------------- plugin

bool pluginInit (const clap_plugin_t* p) {
    auto* b = self (p);
    b->hostTimer = (const clap_host_timer_support_t*) b->host->get_extension (b->host, CLAP_EXT_TIMER_SUPPORT);
    b->hostTrack = (const clap_host_track_info_t*) b->host->get_extension (b->host, CLAP_EXT_TRACK_INFO);
    b->hostGui = (const clap_host_gui_t*) b->host->get_extension (b->host, CLAP_EXT_GUI);
    b->readTrackName ();
    if (b->hostTimer)
        b->hostTimer->register_timer (b->host, 500, &b->timer);
    b->checkSession (true);
    return true;
}

void pluginDestroy (const clap_plugin_t* p) {
    auto* b = self (p);
    if (b->hostTimer && b->timer != CLAP_INVALID_ID)
        b->hostTimer->unregister_timer (b->host, b->timer);
    b->teardown ();
    delete b;
}

bool pluginActivate (const clap_plugin_t* p, double sr, uint32_t, uint32_t maxFrames) {
    auto* b = self (p);
    b->sampleRate = sr;
    b->maxFrames = maxFrames;
    b->active = true;
    if (b->vst && b->doc) {
        std::lock_guard<std::mutex> g (b->audio);
        b->vst->startProcessing (sr, (int32_t) maxFrames);
        b->ready = true;
    }
    return true;
}

void pluginDeactivate (const clap_plugin_t* p) {
    auto* b = self (p);
    std::lock_guard<std::mutex> g (b->audio);
    b->ready = false;
    b->active = false;
    if (b->vst)
        b->vst->stopProcessing ();
}

bool pluginStartProcessing (const clap_plugin_t*) {
    return true;
}
void pluginStopProcessing (const clap_plugin_t*) {}
void pluginReset (const clap_plugin_t*) {}

clap_process_status pluginProcess (const clap_plugin_t* p, const clap_process_t* proc) {
    auto* b = self (p);
    const uint32_t n = proc->frames_count;
    if (proc->audio_outputs_count < 1 || proc->audio_outputs[0].channel_count < 2)
        return CLAP_PROCESS_CONTINUE;
    float** out = proc->audio_outputs[0].data32;
    float* const* in = (proc->audio_inputs_count > 0 && proc->audio_inputs[0].channel_count >= 2) ? proc->audio_inputs[0].data32 : nullptr;

    std::unique_lock<std::mutex> g (b->audio, std::try_to_lock);
    if (!g.owns_lock () || !b->ready || !b->vst) {
        // Not set up (yet): the clip passes through unchanged.
        for (int c = 0; c < 2; ++c) {
            if (in && in[c] != out[c])
                std::memcpy (out[c], in[c], n * sizeof (float));
            else if (!in)
                std::memset (out[c], 0, n * sizeof (float));
        }
        return CLAP_PROCESS_CONTINUE;
    }

    const clap_event_transport_t* t = proc->transport;
    bool playing = t && (t->flags & CLAP_TRANSPORT_IS_PLAYING);
    double seconds = (t && (t->flags & CLAP_TRANSPORT_HAS_SECONDS_TIMELINE)) ? (double) t->song_pos_seconds / CLAP_SECTIME_FACTOR : 0;
    double beats = (t && (t->flags & CLAP_TRANSPORT_HAS_BEATS_TIMELINE)) ? (double) t->song_pos_beats / CLAP_BEATTIME_FACTOR : 0;
    double tempo = (t && (t->flags & CLAP_TRANSPORT_HAS_TEMPO)) ? t->tempo : 120;
    int64_t songSamples = (int64_t) (seconds * b->sampleRate + 0.5);

    // Melodyne renders the clip from the file itself; Bitwig's own playback of the clip (the input) is replaced.
    b->vst->process ((const float* const*) in, out, (int32_t) n, songSamples, playing, tempo, beats);
    return CLAP_PROCESS_CONTINUE;
}

const void* pluginExtension (const clap_plugin_t*, const char* id) {
    if (!std::strcmp (id, CLAP_EXT_AUDIO_PORTS)) return &audioPorts;
    if (!std::strcmp (id, CLAP_EXT_STATE)) return &state;
    if (!std::strcmp (id, CLAP_EXT_GUI)) return &gui;
    if (!std::strcmp (id, CLAP_EXT_TIMER_SUPPORT)) return &timerSupport;
    if (!std::strcmp (id, CLAP_EXT_TRACK_INFO)) return &trackInfo;
    return nullptr;
}

void pluginOnMainThread (const clap_plugin_t*) {}

const clap_plugin_t* createPlugin (const clap_host_t* host) {
    auto* b = new Bridge ();
    b->host = host;
    b->plugin = { &descriptor, b, pluginInit, pluginDestroy, pluginActivate, pluginDeactivate, pluginStartProcessing,
                  pluginStopProcessing, pluginReset, pluginProcess, pluginExtension, pluginOnMainThread };
    return &b->plugin;
}

// ---------------------------------------------------------------------- factory and entry

uint32_t factoryCount (const clap_plugin_factory_t*) {
    return 1;
}
const clap_plugin_descriptor_t* factoryDescriptor (const clap_plugin_factory_t*, uint32_t i) {
    return i == 0 ? &descriptor : nullptr;
}
const clap_plugin_t* factoryCreate (const clap_plugin_factory_t*, const clap_host_t* host, const char* id) {
    if (!clap_version_is_compatible (host->clap_version) || std::strcmp (id, descriptor.id) != 0)
        return nullptr;
    return createPlugin (host);
}

const clap_plugin_factory_t factory { factoryCount, factoryDescriptor, factoryCreate };

bool entryInit (const char*) {
    return true;
}
void entryDeinit () {}
const void* entryFactory (const char* id) {
    return std::strcmp (id, CLAP_PLUGIN_FACTORY_ID) == 0 ? &factory : nullptr;
}

}  // namespace

extern "C" CLAP_EXPORT const clap_plugin_entry_t clap_entry = { CLAP_VERSION_INIT, entryInit, entryDeinit, entryFactory };
