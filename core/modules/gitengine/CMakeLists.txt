find_package(libgit2 QUIET)

if(NOT libgit2_FOUND)
    include(FetchContent)
    FetchContent_Declare(
        libgit2
        GIT_REPOSITORY https://github.com/libgit2/libgit2.git
        GIT_TAG        v1.8.1
        GIT_SHALLOW    TRUE
    )
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_CLI OFF CACHE BOOL "" FORCE)
    set(USE_SSH OFF CACHE BOOL "" FORCE)
    set(USE_HTTPS OFF CACHE BOOL "" FORCE)
    set(USE_BUNDLED_ZLIB ON CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(libgit2)
endif()

add_library(gitengine SHARED
    src/gitengine.cpp
    src/gitengine_jni.cpp
)

target_include_directories(gitengine PUBLIC include)
target_link_libraries(gitengine PUBLIC pystudio_core git2)
target_compile_definitions(gitengine PUBLIC HAS_LIBGIT2)

install(TARGETS gitengine
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
