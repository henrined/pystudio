# CMakeLists.txt pour gitengine
add_library(gitengine SHARED
    src/gitengine.cpp
    src/gitengine_jni.cpp
)

target_include_directories(gitengine PUBLIC include)
target_link_libraries(gitengine pystudio_core)

# Optionnel: lier libgit2 si disponible
find_package(libgit2 QUIET)
if(libgit2_FOUND)
    target_link_libraries(gitengine git2)
    target_compile_definitions(gitengine PUBLIC HAS_LIBGIT2)
endif()

install(TARGETS gitengine
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
