add_library(pyembed SHARED
    src/pyembed.cpp
)

# default ABI if not set
if(NOT ANDROID_ABI)
    set(ANDROID_ABI "arm64-v8a")
endif()

set(PYTHON_RUNTIME_DIR "${CMAKE_SOURCE_DIR}/../runtimes/${ANDROID_ABI}")

if(NOT EXISTS "${PYTHON_RUNTIME_DIR}/lib/libpython3.14.so")
    message(WARNING "[pyembed] libpython3.14.so not found for ABI ${ANDROID_ABI} at ${PYTHON_RUNTIME_DIR}")
    return()
endif()

target_include_directories(pyembed PUBLIC 
    include
    "${PYTHON_RUNTIME_DIR}/include/python3.14"
)

target_link_libraries(pyembed PUBLIC
    pystudio_core
    "${PYTHON_RUNTIME_DIR}/lib/libpython3.14.so"
)

# ─── runner_jni: JNI bridge for RunnerService (isolated process) ──────────
add_library(runner_jni SHARED
    src/runner_jni.cpp
)

target_include_directories(runner_jni PRIVATE
    include
    "${PYTHON_RUNTIME_DIR}/include/python3.14"
)

target_link_libraries(runner_jni PRIVATE
    pyembed
    pystudio_core
)

install(TARGETS pyembed runner_jni
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
