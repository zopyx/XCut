cmake_minimum_required(VERSION 3.10)
project(xform-cpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(LIBXML2 REQUIRED libxml-2.0)

include_directories(${LIBXML2_INCLUDE_DIRS})
link_directories(${LIBXML2_LIBRARY_DIRS})

add_subdirectory(src)
