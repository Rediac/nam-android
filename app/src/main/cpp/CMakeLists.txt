cmake_minimum_required(VERSION 3.22.1)
project(namplayer CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Paths ─────────────────────────────────────────────────────────────────────
set(NAM_CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/NeuralAmpModelerCore)
set(NAM_DIR      ${NAM_CORE_DIR}/NAM)
set(DEPS_DIR     ${NAM_CORE_DIR}/Dependencies)

# ── NeuralAmpModelerCore source files ─────────────────────────────────────────
# Real filenames verified from the repository
set(NAM_SOURCES
    ${NAM_DIR}/activations.cpp
    ${NAM_DIR}/convnet.cpp
    ${NAM_DIR}/dsp.cpp
    ${NAM_DIR}/get_dsp.cpp
    ${NAM_DIR}/lstm.cpp
    ${NAM_DIR}/util.cpp
    ${NAM_DIR}/wavenet.cpp
)

# ── Include paths ─────────────────────────────────────────────────────────────
# nlohmann/json is header-only, lives inside the repo (no separate submodule)
# Eigen is a submodule under Dependencies/Eigen
include_directories(
    ${NAM_CORE_DIR}
    ${NAM_DIR}
    ${DEPS_DIR}/nlohmann
    ${DEPS_DIR}/Eigen
)

# ── Eigen alignment fix for Android (important!) ──────────────────────────────
# Prevents crashes from misaligned Eigen members on some Android compilers
add_definitions(
    -DEIGEN_MAX_ALIGN_BYTES=0
    -DEIGEN_DONT_VECTORIZE
)

# ── Shared library: JNI wrapper + NAM core ────────────────────────────────────
add_library(
    namplayer
    SHARED
    nam_jni.cpp
    ${NAM_SOURCES}
)

# ── Android system libraries ──────────────────────────────────────────────────
find_library(log-lib    log)
find_library(aaudio-lib aaudio)

target_link_libraries(
    namplayer
    ${log-lib}
    ${aaudio-lib}
)

# ── Compile options ───────────────────────────────────────────────────────────
target_compile_options(namplayer PRIVATE
    -O3
    -ffast-math
)
