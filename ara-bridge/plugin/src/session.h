// NocTweaks ARA Bridge: the session file that says which audio file this track's bridge works on.
//
// One small text file per track, written by the Patchbay mod (or by the bridge itself, when you pick a file):
//   <data>/NocTweaks/ara-bridge/<track name>.txt
//     plugin=<path to the ARA plug-in's .vst3>      (Melodyne by default)
//     file=<path to the clip's audio file>
//     start=<seconds on Bitwig's timeline where the clip begins>
//     offset=<seconds into the file where the clip begins>
//     length=<seconds the clip plays, 0 = to the end of the file>
#pragma once

#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <string>

namespace bridge {

namespace fs = std::filesystem;

struct Session {
    std::string plugin;
    std::string file;
    double start = 0, offset = 0, length = 0;

    bool operator== (const Session& o) const {
        return plugin == o.plugin && file == o.file && start == o.start && offset == o.offset && length == o.length;
    }
    bool operator!= (const Session& o) const { return !(*this == o); }
};

inline fs::path dataFolder () {
#if defined (_WIN32)
    const char* base = std::getenv ("LOCALAPPDATA");
    fs::path p = base ? fs::path (base) : fs::path (".");
#elif defined (__APPLE__)
    const char* home = std::getenv ("HOME");
    fs::path p = fs::path (home ? home : ".") / "Library" / "Application Support";
#else
    const char* xdg = std::getenv ("XDG_CONFIG_HOME");
    const char* home = std::getenv ("HOME");
    fs::path p = xdg ? fs::path (xdg) : fs::path (home ? home : ".") / ".config";
#endif
    return p / "NocTweaks" / "ara-bridge";
}

inline std::string safeName (std::string n) {
    for (auto& c : n)
        if (std::string ("<>:\"/\\|?*").find (c) != std::string::npos || (unsigned char) c < 32)
            c = '_';
    while (!n.empty () && (n.back () == ' ' || n.back () == '.'))
        n.pop_back ();
    return n.empty () ? "Track" : n;
}

inline fs::path sessionPath (const std::string& track) {
    return dataFolder () / (safeName (track) + ".txt");
}

inline bool readSession (const fs::path& path, Session& s) {
    std::ifstream in (path);
    if (!in)
        return false;
    Session r;
    std::string line;
    while (std::getline (in, line)) {
        if (!line.empty () && line.back () == '\r')
            line.pop_back ();
        auto eq = line.find ('=');
        if (eq == std::string::npos)
            continue;
        auto k = line.substr (0, eq), v = line.substr (eq + 1);
        if (k == "plugin") r.plugin = v;
        else if (k == "file") r.file = v;
        else if (k == "start") r.start = std::atof (v.c_str ());
        else if (k == "offset") r.offset = std::atof (v.c_str ());
        else if (k == "length") r.length = std::atof (v.c_str ());
    }
    s = r;
    return true;
}

inline bool writeSession (const fs::path& path, const Session& s) {
    std::error_code ec;
    fs::create_directories (path.parent_path (), ec);
    std::ofstream out (path, std::ios::trunc);
    if (!out)
        return false;
    out << "plugin=" << s.plugin << "\nfile=" << s.file << "\nstart=" << s.start
        << "\noffset=" << s.offset << "\nlength=" << s.length << "\n";
    return true;
}

/** Where the ARA plug-in usually is (Melodyne), if the session doesn't say. */
inline std::string defaultPlugin () {
#if defined (_WIN32)
    const char* cf = std::getenv ("CommonProgramFiles");
    fs::path p = fs::path (cf ? cf : "C:\\Program Files\\Common Files") / "VST3" / "Melodyne.vst3";
#elif defined (__APPLE__)
    fs::path p = "/Library/Audio/Plug-Ins/VST3/Melodyne.vst3";
#else
    fs::path p = "/usr/lib/vst3/Melodyne.vst3";
#endif
    return fs::exists (p) ? p.string () : std::string ();
}

}  // namespace bridge
