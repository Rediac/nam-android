cmake_minimum_required(VERSION 3.22.1)
project(namplayer CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Paths ─────────────────────────────────────────────────────────────────────
set(NAM_CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/NeuralAmpModelerCore)
set(NAM_DIR      ${NAM_CORE_DIR}/NAM)

# ── NeuralAmpModelerCore source files ─────────────────────────────────────────
# Based on actual file listing from 2024-06-24
set(NAM_SOURCES
    ${NAM_DIR}/activations.cpp
    ${NAM_DIR}/container.cpp
    ${NAM_DIR}/conv1d.cpp
    ${NAM_DIR}/convnet.cpp
    ${NAM_DIR}/dsp.cpp
    ${NAM_DIR}/get_dsp.cpp
    ${NAM_DIR}/linear.cpp
    ${NAM_DIR}/lstm.cpp
    ${NAM_DIR}/ring_buffer.cpp
    ${NAM_DIR}/util.cpp
    ${NAM_DIR}/wavenet/wavenet.cpp
)

# ── Include paths ─────────────────────────────────────────────────────────────
include_directories(
    ${NAM_CORE_DIR}
    ${NAM_DIR}
    ${NAM_DIR}/wavenet
    ${NAM_CORE_DIR}/Dependencies/nlohmann
    ${NAM_CORE_DIR}/Dependencies/eigen
    ${NAM_CORE_DIR}/Dependencies/AudioDSPTools/include
)

# ── Eigen alignment fix for Android ───────────────────────────────────────────
add_definitions(
    -DEIGEN_MAX_ALIGN_BYTES=0
    -DEIGEN_DONT_VECTORIZE
)

# ── Shared library ────────────────────────────────────────────────────────────
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
