cmake_minimum_required(VERSION 3.22.1)
project("lifeos_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+simd -ffast-math")

add_library(
    lifeos_security
    SHARED
    src/main/cpp/blackbox_memory_wipe.cpp
    src/main/cpp/neon_phase_math.cpp
    src/main/cpp/panic_wipe_hook.cpp
)

find_library(log-lib log)
target_link_libraries(lifeos_security ${log-lib})
