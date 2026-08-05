add_library(mlruntime SHARED
    src/mlruntime.cpp
)

option(PYSTUDIO_USE_TFLITE "Use TFLite" OFF)
option(PYSTUDIO_USE_OPENCV "Use OpenCV" OFF)
option(PYSTUDIO_USE_LIBTORCH "Use LibTorch" OFF)
option(PYSTUDIO_MLRUNTIME_USE_STUBS "Use dependency stubs for testing (deps_stubs.cpp)" OFF)

target_include_directories(mlruntime PUBLIC include)
target_link_libraries(mlruntime pystudio_core)

# ─── Stubs mode (for unit testing without real libraries) ────────────────────
if(PYSTUDIO_MLRUNTIME_USE_STUBS)
    target_sources(mlruntime PRIVATE src/deps_stubs.cpp)
    target_include_directories(mlruntime PUBLIC deps/include)
    target_compile_definitions(mlruntime PUBLIC
        PYSTUDIO_HAS_TFLITE
        PYSTUDIO_HAS_OPENCV
        PYSTUDIO_MLRUNTIME_STUBS
    )
    # Note: LibTorch stubs are also available if needed
    if(PYSTUDIO_USE_LIBTORCH)
        target_compile_definitions(mlruntime PUBLIC PYSTUDIO_HAS_LIBTORCH)
    endif()
    message(STATUS "[mlruntime] Building with STUBS (testing mode)")
    return()
endif()

# ─── Real TFLite ─────────────────────────────────────────────────────────────
if(PYSTUDIO_USE_TFLITE)
    find_package(TensorFlowLite QUIET)
    if(TensorFlowLite_FOUND)
        target_link_libraries(mlruntime PUBLIC TensorFlowLite::TensorFlowLite)
        target_compile_definitions(mlruntime PUBLIC PYSTUDIO_HAS_TFLITE)
        message(STATUS "[mlruntime] TFLite: FOUND and enabled")
    else()
        message(WARNING "[mlruntime] TFLite: requested but NOT found — feature disabled")
    endif()
endif()

# ─── Real OpenCV ─────────────────────────────────────────────────────────────
if(PYSTUDIO_USE_OPENCV)
    find_package(OpenCV QUIET)
    if(OpenCV_FOUND)
        target_link_libraries(mlruntime PUBLIC ${OpenCV_LIBS})
        target_compile_definitions(mlruntime PUBLIC PYSTUDIO_HAS_OPENCV)
        message(STATUS "[mlruntime] OpenCV: FOUND and enabled")
    else()
        message(WARNING "[mlruntime] OpenCV: requested but NOT found — feature disabled")
    endif()
endif()

# ─── Real LibTorch ───────────────────────────────────────────────────────────
if(PYSTUDIO_USE_LIBTORCH)
    find_package(Torch QUIET)
    if(Torch_FOUND)
        target_link_libraries(mlruntime PUBLIC ${TORCH_LIBRARIES})
        target_compile_definitions(mlruntime PUBLIC PYSTUDIO_HAS_LIBTORCH)
        message(STATUS "[mlruntime] LibTorch: FOUND and enabled")
    else()
        message(WARNING "[mlruntime] LibTorch: requested but NOT found — feature disabled")
    endif()
endif()

# ─── Install ─────────────────────────────────────────────────────────────────
install(TARGETS mlruntime
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)
