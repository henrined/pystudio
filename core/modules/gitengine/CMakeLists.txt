# ─── gitengine: libgit2 wrapper ───────────────────────────────────────────────
# This module requires libgit2 headers and library to be pre-installed.
# If not found, the module is silently skipped (CI-safe).

find_path(LIBGIT2_INCLUDE_DIR NAMES git2.h)
find_library(LIBGIT2_LIBRARY NAMES git2)

if(NOT LIBGIT2_INCLUDE_DIR OR NOT LIBGIT2_LIBRARY)
    message(STATUS "[gitengine] libgit2 not found — skipping gitengine module")
    return()
endif()

message(STATUS "[gitengine] libgit2 found: ${LIBGIT2_INCLUDE_DIR}, ${LIBGIT2_LIBRARY}")

add_library(gitengine SHARED
    src/gitengine.cpp
    src/gitengine_jni.cpp
)

target_include_directories(gitengine PUBLIC include)
target_include_directories(gitengine PRIVATE ${LIBGIT2_INCLUDE_DIR})
target_link_libraries(gitengine PUBLIC pystudio_core)
target_link_libraries(gitengine PRIVATE ${LIBGIT2_LIBRARY})
target_compile_definitions(gitengine PUBLIC HAS_LIBGIT2)

install(TARGETS gitengine
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
