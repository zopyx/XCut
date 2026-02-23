#ifndef LEXER_H
#define LEXER_H

#include <stddef.h>
#include <stdbool.h>

typedef enum {
    TK_KW,
    TK_IDENT,
    TK_STR,
    TK_NUM,
    TK_OP,
    TK_PUNCT,
    TK_DOT,
    TK_SLASH,
    TK_AT,
    TK_EOF
} TokenKind;

typedef struct {
    TokenKind kind;
    char *value;
    size_t pos;
} Token;

typedef struct {
    const char *text;
    size_t len;
    size_t pos;
    Token *buffer;  /* for peek */
} Lexer;

Lexer* lexer_new(const char *text);
void lexer_free(Lexer *lex);
Token* lexer_peek(Lexer *lex);
Token* lexer_next(Lexer *lex);
Token* lexer_expect(Lexer *lex, TokenKind kind, const char *value);
void token_free(Token *tok);

#endif
