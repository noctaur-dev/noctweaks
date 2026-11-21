// NocTweaks ARA Bridge: hosting the VST3 plug-in, with the VST3 SDK's hosting helpers (MIT).
#include "vst3_host.h"

#include "ARA_API/ARAVST3.h"
#include "pluginterfaces/base/funknown.h"
#include "pluginterfaces/gui/iplugview.h"
#include "pluginterfaces/vst/ivstaudioprocessor.h"
#include "pluginterfaces/vst/ivsteditcontroller.h"
#include "pluginterfaces/vst/ivstprocesscontext.h"
#include "public.sdk/source/vst/hosting/hostclasses.h"
#include "public.sdk/source/vst/hosting/module.h"
#include "public.sdk/source/vst/hosting/plugprovider.h"

// The ARA interfaces' IDs are defined once, here (the VST3 SDK defines its own in vstinitiids.cpp).
DEF_CLASS_IID (ARA::IMainFactory)
DEF_CLASS_IID (ARA::IPlugInEntryPoint)
DEF_CLASS_IID (ARA::IPlugInEntryPoint2)

using namespace Steinberg;
using namespace Steinberg::Vst;

namespace bridge {

namespace {

/** The window frame the plug-in's editor asks to resize through. */
class PlugFrame : public IPlugFrame {
public:
    std::function<bool (uint32_t, uint32_t)>* onResize = nullptr;

    tresult PLUGIN_API resizeView (IPlugView* view, ViewRect* newSize) override {
        if (!view || !newSize)
            return kInvalidArgument;
        uint32_t w = (uint32_t) newSize->getWidth (), h = (uint32_t) newSize->getHeight ();
        if (onResize && *onResize && (*onResize) (w, h)) {
            view->onSize (newSize);
            return kResultTrue;
        }
        return kResultFalse;
    }
    tresult PLUGIN_API queryInterface (const TUID _iid, void** obj) override {
        QUERY_INTERFACE (_iid, obj, FUnknown::iid, IPlugFrame)
        QUERY_INTERFACE (_iid, obj, IPlugFrame::iid, IPlugFrame)
        *obj = nullptr;
        return kNoInterface;
    }
    uint32 PLUGIN_API addRef () override { return 1; }   // lives as long as the host that owns it
    uint32 PLUGIN_API release () override { return 1; }
};

HostApplication* hostApplication () {
    static HostApplication* app = [] {
        auto* a = new HostApplication ();
        PluginContextFactory::instance ().setPluginContext (a);
        return a;
    }();
    return app;
}

const char* platformType () {
#if defined (_WIN32)
    return kPlatformTypeHWND;
#elif defined (__APPLE__)
    return kPlatformTypeNSView;
#else
    return kPlatformTypeX11EmbedWindowID;
#endif
}

}  // namespace

struct Vst3Host::Impl {
    VST3::Hosting::Module::Ptr module;
    IPtr<PlugProvider> provider;
    IPtr<IComponent> component;
    IPtr<IAudioProcessor> processor;
    IPtr<IEditController> controller;
    IPtr<ARA::IMainFactory> araMain;
    const ARA::ARAFactory* ara = nullptr;
    IPtr<IPlugView> view;
    PlugFrame frame;
    bool processing = false;
    double sampleRate = 44100;
    std::string name;

    ~Impl () {
        if (view) {
            view->setFrame (nullptr);
            view->removed ();
        }
        view = nullptr;
        if (processing && processor && component) {
            processor->setProcessing (false);
            component->setActive (false);
        }
        processor = nullptr;
        controller = nullptr;
        component = nullptr;
        provider = nullptr;   // terminates and disconnects component and controller
        araMain = nullptr;
        module = nullptr;     // unloads the binary last
    }
};

Vst3Host::Vst3Host () : impl (new Impl) {
    impl->frame.onResize = &onResizeRequest;
}

Vst3Host::~Vst3Host () = default;

bool Vst3Host::load (const std::string& path, std::string& error) {
    hostApplication ();
    impl->module = VST3::Hosting::Module::create (path, error);
    if (!impl->module)
        return false;
    const auto& factory = impl->module->getFactory ();
    factory.setHostContext (hostApplication ());

    VST3::Hosting::ClassInfo effect;
    bool haveEffect = false;
    for (const auto& ci : factory.classInfos ()) {
        if (ci.category () == kVstAudioEffectClass && !haveEffect) {
            effect = ci;
            haveEffect = true;
        } else if (ci.category () == kARAMainFactoryClass && !impl->araMain) {
            impl->araMain = factory.createInstance<ARA::IMainFactory> (ci.ID ());
        }
    }
    if (!haveEffect) {
        error = "no audio effect in " + path;
        return false;
    }
    impl->name = effect.name ();
    if (impl->araMain)
        impl->ara = impl->araMain->getFactory ();

    impl->provider = owned (new PlugProvider (factory, effect, true));
    if (!impl->provider->initialize ()) {
        error = "could not start " + impl->name;
        return false;
    }
    impl->component = impl->provider->getComponentPtr ();
    impl->controller = impl->provider->getControllerPtr ();
    impl->processor = FUnknownPtr<IAudioProcessor> (impl->component);
    if (!impl->processor) {
        error = impl->name + " has no audio processor";
        return false;
    }
    return true;
}

const ARA::ARAFactory* Vst3Host::araFactory () const {
    return impl->ara;
}

std::string Vst3Host::name () const {
    return impl->name;
}

const ARA::ARAPlugInExtensionInstance* Vst3Host::bind (ARA::ARADocumentControllerRef controller, ARA::ARAPlugInInstanceRoleFlags roles) {
    constexpr ARA::ARAPlugInInstanceRoleFlags known = ARA::kARAPlaybackRendererRole | ARA::kARAEditorRendererRole | ARA::kARAEditorViewRole;
    if (FUnknownPtr<ARA::IPlugInEntryPoint2> entry2 { impl->component })
        return entry2->bindToDocumentControllerWithRoles (controller, known, roles);
    if (FUnknownPtr<ARA::IPlugInEntryPoint> entry { impl->component })
        return entry->bindToDocumentController (controller);
    return nullptr;
}

bool Vst3Host::startProcessing (double sampleRate, int32_t maxBlock) {
    if (!impl->processor)
        return false;
    impl->sampleRate = sampleRate;
    SpeakerArrangement stereo = SpeakerArr::kStereo;
    int32 ins = impl->component->getBusCount (kAudio, kInput);
    int32 outs = impl->component->getBusCount (kAudio, kOutput);
    impl->processor->setBusArrangements (ins > 0 ? &stereo : nullptr, ins > 0 ? 1 : 0, &stereo, outs > 0 ? 1 : 0);
    if (ins > 0)
        impl->component->activateBus (kAudio, kInput, 0, true);
    if (outs > 0)
        impl->component->activateBus (kAudio, kOutput, 0, true);
    ProcessSetup setup { kRealtime, kSample32, maxBlock, sampleRate };
    if (impl->processor->setupProcessing (setup) != kResultOk)
        return false;
    impl->component->setActive (true);
    impl->processor->setProcessing (true);
    impl->processing = true;
    return true;
}

void Vst3Host::stopProcessing () {
    if (impl->processing) {
        impl->processor->setProcessing (false);
        impl->component->setActive (false);
        impl->processing = false;
    }
}

void Vst3Host::process (const float* const* in, float* const* out, int32_t frames, int64_t songSamples,
                        bool playing, double tempo, double beats) {
    if (!impl->processing)
        return;
    AudioBusBuffers inBus {}, outBus {};
    inBus.numChannels = 2;
    inBus.channelBuffers32 = const_cast<float**> (in);
    outBus.numChannels = 2;
    outBus.channelBuffers32 = const_cast<float**> (out);

    ProcessContext ctx {};
    ctx.state = (playing ? ProcessContext::kPlaying : 0) | ProcessContext::kProjectTimeMusicValid | ProcessContext::kTempoValid;
    ctx.sampleRate = impl->sampleRate;
    ctx.projectTimeSamples = songSamples;
    ctx.projectTimeMusic = beats;
    ctx.tempo = tempo;

    ProcessData data;
    data.processMode = kRealtime;
    data.symbolicSampleSize = kSample32;
    data.numSamples = frames;
    data.numInputs = in ? 1 : 0;
    data.numOutputs = 1;
    data.inputs = in ? &inBus : nullptr;
    data.outputs = &outBus;
    data.processContext = &ctx;
    impl->processor->process (data);
}

bool Vst3Host::hasEditor () {
    if (!impl->controller)
        return false;
    if (impl->view)
        return true;
    IPtr<IPlugView> v = owned (impl->controller->createView (ViewType::kEditor));
    return v && v->isPlatformTypeSupported (platformType ()) == kResultTrue;
}

bool Vst3Host::openEditor (void* parent, uint32_t& width, uint32_t& height) {
    if (!impl->controller)
        return false;
    if (!impl->view)
        impl->view = owned (impl->controller->createView (ViewType::kEditor));
    if (!impl->view || impl->view->isPlatformTypeSupported (platformType ()) != kResultTrue)
        return false;
    impl->view->setFrame (&impl->frame);
    if (impl->view->attached (parent, platformType ()) != kResultOk)
        return false;
    return editorSize (width, height);
}

bool Vst3Host::editorSize (uint32_t& width, uint32_t& height) {
    if (!impl->view)
        return false;
    ViewRect r;
    if (impl->view->getSize (&r) != kResultOk)
        return false;
    width = (uint32_t) r.getWidth ();
    height = (uint32_t) r.getHeight ();
    return true;
}

bool Vst3Host::resizeEditor (uint32_t width, uint32_t height) {
    if (!impl->view)
        return false;
    ViewRect r (0, 0, (int32) width, (int32) height);
    return impl->view->onSize (&r) == kResultOk;
}

void Vst3Host::closeEditor () {
    if (impl->view) {
        impl->view->setFrame (nullptr);
        impl->view->removed ();
        impl->view = nullptr;
    }
}

}  // namespace bridge
