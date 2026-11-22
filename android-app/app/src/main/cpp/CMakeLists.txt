cmake_minimum_required(VERSION 3.22.1)
project("monnixcode_pty")

add_library(monnixcode_pty SHARED pty_bridge.cpp)

target_compile_features(monnixcode_pty PRIVATE cxx_std_17)
target_compile_options(monnixcode_pty PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(monnixcode_pty android log)
