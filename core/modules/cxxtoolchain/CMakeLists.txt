add_library(cxxtoolchain SHARED
  src/cxxtoolchain.cpp
  src/cxxtoolchain_jni.cpp
)

target_include_directories(cxxtoolchain PUBLIC include)
target_link_libraries(cxxtoolchain PUBLIC pystudio_core)
target_compile_features(cxxtoolchain PUBLIC cxx_std_20)

install(TARGETS cxxtoolchain
  LIBRARY DESTINATION lib
  ARCHIVE DESTINATION lib
)
install(DIRECTORY include/ DESTINATION include)
