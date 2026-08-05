add_library(dbgbridge SHARED
  src/dbgbridge.cpp
)

add_library(dbgbridge_jni SHARED
  src/dbgbridge_jni.cpp
)

target_include_directories(dbgbridge PUBLIC include)
target_link_libraries(dbgbridge PUBLIC pystudio_core)
target_compile_features(dbgbridge PUBLIC cxx_std_20)

target_include_directories(dbgbridge_jni PRIVATE include)
target_link_libraries(dbgbridge_jni PRIVATE dbgbridge pystudio_core pthread)

install(TARGETS dbgbridge dbgbridge_jni
  LIBRARY DESTINATION lib
  ARCHIVE DESTINATION lib
)
install(DIRECTORY include/ DESTINATION include)
