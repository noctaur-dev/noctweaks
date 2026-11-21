# NocTweaks ARA Bridge (prototype): a CLAP plug-in that hosts an ARA plug-in (Melodyne) for Bitwig.
#
#   cmake -B build -DCMAKE_BUILD_TYPE=Release
#   cmake --build build --config Release
#
# Gives build/<config>/NocTweaks ARA Bridge.clap (Windows: copy it to C:\Program Files\Common Files\CLAP;
# macOS: ~/Library/Audio/Plug-Ins/CLAP). The SDKs are downloaded on the first configure; to use copies you
# already have, pass -DCLAP_SDK=... -DARA_SDK=... -DVST3_SDK=... (VST3_SDK holds pluginterfaces, base and
# public.sdk), -DDR_LIBS=... and -DTINYFD=....
cmake_minimum_required(VERSION 3.20)
project(noctweaks_ara_bridge VERSION 0.1.0 LANGUAGES C CXX)
if(APPLE)
    enable_language(OBJCXX)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "11.0" CACHE STRING "")
endif()
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
macro(sdk var name repo tag)
    if(NOT ${var})
        FetchContent_Declare(${name} GIT_REPOSITORY ${repo} GIT_TAG ${tag} GIT_SHALLOW TRUE)
        FetchContent_GetProperties(${name})
        if(NOT ${name}_POPULATED)
            FetchContent_Populate(${name})
        endif()
        set(${var} ${${name}_SOURCE_DIR})
    endif()
endmacro()

sdk(CLAP_SDK clap https://github.com/free-audio/clap.git main)
sdk(ARA_API_DIR ara_api https://github.com/Celemony/ARA_API.git main)
sdk(DR_LIBS dr_libs https://github.com/mackron/dr_libs.git master)
sdk(TINYFD tinyfd https://github.com/native-toolkit/libtinyfiledialogs.git master)
if(ARA_SDK)
    set(ARA_API_DIR ${ARA_SDK}/ARA_API)
endif()
if(NOT VST3_SDK)
    sdk(VST3_IF vst3_pluginterfaces https://github.com/steinbergmedia/vst3_pluginterfaces.git master)
    sdk(VST3_BASE vst3_base https://github.com/steinbergmedia/vst3_base.git master)
    sdk(VST3_PUBLIC vst3_public_sdk https://github.com/steinbergmedia/vst3_public_sdk.git master)
    # the SDK's sources include each other as pluginterfaces/..., base/..., public.sdk/...
    set(VST3_SDK ${CMAKE_BINARY_DIR}/vst3sdk)
    file(MAKE_DIRECTORY ${VST3_SDK})
    foreach(pair "pluginterfaces;${VST3_IF}" "base;${VST3_BASE}" "public.sdk;${VST3_PUBLIC}")
        list(GET pair 0 link)
        list(GET pair 1 target)
        if(NOT EXISTS ${VST3_SDK}/${link})
            file(COPY ${target}/ DESTINATION ${VST3_SDK}/${link} PATTERN ".git" EXCLUDE)
        endif()
    endforeach()
endif()
# ARA_API's headers are included as "ARA_API/...": its parent folder goes on the include path
get_filename_component(ARA_INCLUDE ${ARA_API_DIR}/.. ABSOLUTE)
if(NOT EXISTS ${ARA_INCLUDE}/ARA_API/ARAInterface.h)
    set(ARA_INCLUDE ${CMAKE_BINARY_DIR}/ara)
    file(MAKE_DIRECTORY ${ARA_INCLUDE})
    file(COPY ${ARA_API_DIR}/ DESTINATION ${ARA_INCLUDE}/ARA_API PATTERN ".git" EXCLUDE)
endif()

set(VST3_HOSTING
    ${VST3_SDK}/public.sdk/source/vst/hosting/module.cpp
    ${VST3_SDK}/public.sdk/source/vst/hosting/plugprovider.cpp
    ${VST3_SDK}/public.sdk/source/vst/hosting/hostclasses.cpp
    ${VST3_SDK}/public.sdk/source/vst/hosting/connectionproxy.cpp
    ${VST3_SDK}/public.sdk/source/vst/hosting/pluginterfacesupport.cpp
    ${VST3_SDK}/public.sdk/source/vst/utility/stringconvert.cpp
    ${VST3_SDK}/public.sdk/source/common/commoniids.cpp
    ${VST3_SDK}/public.sdk/source/common/commonstringconvert.cpp
    ${VST3_SDK}/public.sdk/source/vst/vstinitiids.cpp
    ${VST3_SDK}/pluginterfaces/base/funknown.cpp
    ${VST3_SDK}/pluginterfaces/base/coreiids.cpp
    ${VST3_SDK}/pluginterfaces/base/ustring.cpp
    ${VST3_SDK}/base/source/baseiids.cpp
    ${VST3_SDK}/base/source/fobject.cpp
    ${VST3_SDK}/base/source/fstring.cpp
    ${VST3_SDK}/base/source/fdebug.cpp
    ${VST3_SDK}/base/source/updatehandler.cpp
    ${VST3_SDK}/base/thread/source/flock.cpp
)
if(WIN32)
    list(APPEND VST3_HOSTING ${VST3_SDK}/public.sdk/source/vst/hosting/module_win32.cpp
                             ${VST3_SDK}/public.sdk/source/common/threadchecker_win32.cpp)
elseif(APPLE)
    list(APPEND VST3_HOSTING ${VST3_SDK}/public.sdk/source/vst/hosting/module_mac.mm
                             ${VST3_SDK}/public.sdk/source/common/threadchecker_mac.mm)
else()
    list(APPEND VST3_HOSTING ${VST3_SDK}/public.sdk/source/vst/hosting/module_linux.cpp
                             ${VST3_SDK}/public.sdk/source/common/threadchecker_linux.cpp)
endif()
foreach(f IN LISTS VST3_HOSTING)
    if(NOT EXISTS ${f})
        list(REMOVE_ITEM VST3_HOSTING ${f})
        message(STATUS "VST3 SDK: no ${f}, skipped")
    endif()
endforeach()

add_library(ara_bridge MODULE
    src/bridge.cpp
    src/vst3_host.cpp
    src/ara_document.cpp
    src/dr_wav.c
    ${TINYFD}/tinyfiledialogs.c
    ${VST3_HOSTING}
)
target_include_directories(ara_bridge PRIVATE src ${CLAP_SDK}/include ${ARA_INCLUDE} ${VST3_SDK} ${DR_LIBS} ${TINYFD})
target_compile_definitions(ara_bridge PRIVATE $<$<CONFIG:Debug>:DEVELOPMENT=1> $<$<NOT:$<CONFIG:Debug>>:RELEASE=1>)
set_target_properties(ara_bridge PROPERTIES OUTPUT_NAME "NocTweaks ARA Bridge" PREFIX "" SUFFIX ".clap")
if(WIN32)
    target_link_libraries(ara_bridge PRIVATE ole32 comdlg32 shell32 user32)
    if(MINGW)   # one self-contained file, no MinGW runtime DLLs beside it
        target_link_options(ara_bridge PRIVATE -static -static-libgcc -static-libstdc++)
    endif()
elseif(APPLE)
    set_target_properties(ara_bridge PROPERTIES BUNDLE TRUE BUNDLE_EXTENSION clap MACOSX_BUNDLE_GUI_IDENTIFIER io.github.noctaur.ara-bridge)
    target_link_libraries(ara_bridge PRIVATE "-framework CoreFoundation" "-framework Foundation" "-framework Cocoa")
else()
    target_link_libraries(ara_bridge PRIVATE dl pthread stdc++fs)
endif()
