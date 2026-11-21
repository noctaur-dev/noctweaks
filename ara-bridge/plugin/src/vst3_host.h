// NocTweaks ARA Bridge: hosts one VST3 plug-in (Melodyne) inside the bridge, with its ARA extension.
#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>

#include "ARA_API/ARAInterface.h"

namespace bridge {

class Vst3Host {
public:
    Vst3Host ();
    ~Vst3Host ();

    /** Loads the .vst3 and makes its audio effect (component + controller). Main thread. */
    bool load (const std::string& path, std::string& error);

    /** The plug-in's ARA factory, or null if it has none. */
    const ARA::ARAFactory* araFactory () const;

    /** Binds the plug-in instance to an ARA document controller (before processing starts). */
    const ARA::ARAPlugInExtensionInstance* bind (ARA::ARADocumentControllerRef controller, ARA::ARAPlugInInstanceRoleFlags roles);

    /** Stereo in/out, 32-bit float. Main thread, after bind. */
    bool startProcessing (double sampleRate, int32_t maxBlock);
    void stopProcessing ();

    /** Audio thread. The ARA plug-in renders its playback regions at this timeline position (samples). */
    void process (const float* const* in, float* const* out, int32_t frames, int64_t songSamples,
                  bool playing, double tempo, double beats);

    // Editor (main thread). parent: HWND on Windows, NSView* on macOS, X11 window id on Linux.
    bool hasEditor ();
    bool openEditor (void* parent, uint32_t& width, uint32_t& height);
    void closeEditor ();
    bool resizeEditor (uint32_t width, uint32_t height);
    bool editorSize (uint32_t& width, uint32_t& height);
    /** Called when the plug-in asks to resize its window. */
    std::function<bool (uint32_t, uint32_t)> onResizeRequest;

    std::string name () const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};

}  // namespace bridge
