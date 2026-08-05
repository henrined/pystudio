cmake_minimum_required(VERSION 3.22)

# ─── Test executable ─────────────────────────────────────────────────────────
set(TEST_SOURCES
  test_service_registry.cpp
  test_logger.cpp
  test_result.cpp
)

if(PYSTUDIO_MODULE_PYEMBED)
  list(APPEND TEST_SOURCES test_pyembed.cpp)
endif()

if(PYSTUDIO_MODULE_CXXTOOLCHAIN)
  list(APPEND TEST_SOURCES test_cxxtoolchain.cpp)
endif()

if(PYSTUDIO_MODULE_MLRUNTIME)
  list(APPEND TEST_SOURCES test_mlruntime.cpp)
endif()

if(PYSTUDIO_MODULE_GITENGINE)
  list(APPEND TEST_SOURCES test_gitengine.cpp)
endif()

if(PYSTUDIO_MODULE_DBGBRIDGE)
  list(APPEND TEST_SOURCES test_dbgbridge.cpp)
endif()

add_executable(pystudio_core_tests ${TEST_SOURCES})

target_link_libraries(pystudio_core_tests
  PRIVATE
    pystudio_core
    GTest::gtest_main
)

if(PYSTUDIO_MODULE_PYEMBED)
  target_link_libraries(pystudio_core_tests PRIVATE pyembed)
  target_include_directories(pystudio_core_tests PRIVATE ${CMAKE_SOURCE_DIR}/modules/pyembed/include)
endif()

if(PYSTUDIO_MODULE_CXXTOOLCHAIN)
  target_link_libraries(pystudio_core_tests PRIVATE cxxtoolchain)
  target_include_directories(pystudio_core_tests PRIVATE ${CMAKE_SOURCE_DIR}/modules/cxxtoolchain/include)
endif()

if(PYSTUDIO_MODULE_MLRUNTIME)
  target_link_libraries(pystudio_core_tests PRIVATE mlruntime)
  target_include_directories(pystudio_core_tests PRIVATE ${CMAKE_SOURCE_DIR}/modules/mlruntime/include)
endif()

if(PYSTUDIO_MODULE_GITENGINE)
  target_link_libraries(pystudio_core_tests PRIVATE gitengine)
  target_include_directories(pystudio_core_tests PRIVATE ${CMAKE_SOURCE_DIR}/modules/gitengine/include)
endif()

if(PYSTUDIO_MODULE_DBGBRIDGE)
  target_link_libraries(pystudio_core_tests PRIVATE dbgbridge)
  target_include_directories(pystudio_core_tests PRIVATE ${CMAKE_SOURCE_DIR}/modules/dbgbridge/include)
endif()

target_include_directories(pystudio_core_tests PRIVATE
  ${CMAKE_SOURCE_DIR}/include
)

include(GoogleTest)
gtest_discover_tests(pystudio_core_tests)
