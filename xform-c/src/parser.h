#ifndef PARSER_H
#define PARSER_H

#include "lexer.h"
#include "ast.h"

typedef struct {
    Lexer *lexer;
} Parser;

Parser* parser_new(const char *text);
void parser_free(Parser *p);
Module* parser_parse_module(Parser *p);  /* NULL on error, check parser_error() */
Expr* parser_parse_expr(Parser *p);      /* For testing */

/* Get error message if parsing failed */
const char* parser_error(void);
void parser_clear_error(void);

#endif
