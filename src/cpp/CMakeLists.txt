cmake_minimum_required(VERSION 3.16)
project("audit_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -Wall -Wextra")

# WASM build configuration
if(EMSCRIPTEN)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s WASM=1 -s MODULARIZE=1 -s EXPORT_NAME='AuditEngineModule' -s ALLOW_MEMORY_GROWTH=1")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -s EXPORTED_RUNTIME_METHODS=['ccall','cwrap']")
    
    add_library(audit_engine STATIC
        audit_engine.cpp
    )
    
    # Export C functions for WASM
    target_compile_definitions(audit_engine PRIVATE
        __EMSCRIPTEN__
    )
    
    # Explicitly export WASM functions
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/emscripten_export.config")
        include(${CMAKE_CURRENT_SOURCE_DIR}/emscripten_export.config)
    endif()
else()
    add_library(audit_engine STATIC
        audit_engine.cpp
    )
endif()

# Include directory
target_include_directories(audit_engine PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
