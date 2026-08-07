cmake_minimum_required(VERSION 3.22)
project(pystudio_core VERSION 0.1.0 LANGUAGES C CXX)

# ─── Standards ────────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_C_STANDARD 11)

# ─── ABI Filters ──────────────────────────────────────────────────────────────
if(ANDROID AND NOT CMAKE_ANDROID_ARCH_ABI)
  set(CMAKE_ANDROID_ARCH_ABI "arm64-v8a;x86_64" CACHE STRING "Android ABIs")
endif()

# ─── Build type ───────────────────────────────────────────────────────────────
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Debug CACHE STRING "Build type" FORCE)
endif()

# ─── Options ──────────────────────────────────────────────────────────────────
option(PYSTUDIO_BUILD_TESTS    "Build unit tests"               ON)
option(PYSTUDIO_MODULE_PYEMBED "Build pyembed module"           ON)
option(PYSTUDIO_ASAN           "Enable AddressSanitizer"        OFF)

# ─── Compiler warnings & hardening ────────────────────────────────────────────
add_compile_options(
  -Wall -Wextra -Wpedantic
  -Wno-unused-parameter
  -fstack-protector-strong
)
if(PYSTUDIO_ASAN)
  add_compile_options(-fsanitize=address,undefined)
  add_link_options(-fsanitize=address,undefined)
endif()

# ─── Global include ───────────────────────────────────────────────────────────
include_directories(include)

# ─── Core library (orchestrateur) ─────────────────────────────────────────────
add_library(pystudio_core SHARED
  src/core.cpp
  src/service_registry.cpp
  src/logger.cpp
  src/jni_utils.cpp
)
target_include_directories(pystudio_core PUBLIC include)
target_compile_features(pystudio_core PUBLIC cxx_std_20)

if(ANDROID OR CMAKE_SYSTEM_NAME STREQUAL "Android" OR DEFINED ENV{PREFIX})
  # In Termux (which is Android but doesn't always set CMAKE_SYSTEM_NAME=Android out of the box),
  # we must link liblog.so explicitly.
  target_link_libraries(pystudio_core PUBLIC log)
endif()

option(PYSTUDIO_MODULE_CXXTOOLCHAIN "Build cxxtoolchain module"     ON)
option(PYSTUDIO_MODULE_DBGBRIDGE    "Build dbgbridge module"        ON)
option(PYSTUDIO_MODULE_GITENGINE    "Build gitengine module"        ON)
option(PYSTUDIO_MODULE_MLRUNTIME    "Build mlruntime module"        ON)

# ─── Modules optionnels ───────────────────────────────────────────────────────
if(PYSTUDIO_MODULE_PYEMBED)
  if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../runtimes")
    add_subdirectory(modules/pyembed)
  else()
    message(STATUS "[pyembed] Python runtimes directory not present, skipping pyembed build")
  endif()
endif()

if(PYSTUDIO_MODULE_CXXTOOLCHAIN)
  add_subdirectory(modules/cxxtoolchain)
endif()

if(PYSTUDIO_MODULE_DBGBRIDGE)
  add_subdirectory(modules/dbgbridge)
endif()

if(PYSTUDIO_MODULE_GITENGINE)
  add_subdirectory(modules/gitengine)
endif()

if(PYSTUDIO_MODULE_MLRUNTIME)
  add_subdirectory(modules/mlruntime)
endif()

# ─── Tests ────────────────────────────────────────────────────────────────────
if(PYSTUDIO_BUILD_TESTS)
  enable_testing()
  
  # ─── GoogleTest via FetchContent ─────────────────────────────────────────────
  include(FetchContent)
  FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG        v1.15.2
    GIT_SHALLOW    TRUE
  )
  # Prevent overriding the parent project's compiler/linker settings on Windows
  set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
  FetchContent_MakeAvailable(googletest)
  
  add_subdirectory(tests)
endif()

# ─── Install ──────────────────────────────────────────────────────────────────
install(TARGETS pystudio_core
  LIBRARY DESTINATION lib
  ARCHIVE DESTINATION lib
)
install(DIRECTORY include/ DESTINATION include)
