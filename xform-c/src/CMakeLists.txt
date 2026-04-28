add_executable(xform
    main.c
    lexer.c
    parser.c
    ast.c
    eval.c
    xmlmodel.c
    string_builder.c
    hashmap.c
)

target_link_libraries(xform ${LIBXML2_LIBRARIES} m)
target_compile_options(xform PRIVATE ${LIBXML2_CFLAGS_OTHER} -Wall -Wextra -Wpedantic)
