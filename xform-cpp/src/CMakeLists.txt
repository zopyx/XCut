add_executable(xform
    main.cpp
    lexer.cpp
    parser.cpp
    ast.cpp
    eval.cpp
    xmlmodel.cpp
    string_builder.cpp
    hashmap.cpp
    ast.hpp
    eval.hpp
    hashmap.hpp
    lexer.hpp
    parser.hpp
    string_builder.hpp
    xmlmodel.hpp
)

target_link_libraries(xform ${LIBXML2_LIBRARIES} m)
target_compile_options(xform PRIVATE ${LIBXML2_CFLAGS_OTHER} -fpermissive)
