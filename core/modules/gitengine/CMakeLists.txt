# CMakeLists.txt pour gitengine
add_library(gitengine SHARED
    src/gitengine.cpp
    src/gitengine_jni.cpp
)

target_include_directories(gitengine PUBLIC include)
target_link_libraries(gitengine pystudio_core)

# Obligatoire: lier libgit2
find_package(libgit2 REQUIRED)
target_link_libraries(gitengine git2)

install(TARGETS gitengine
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
