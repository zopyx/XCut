#include "lexer.hpp"
#include "string_builder.hpp"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

static const char* KEYWORDS[] = {
    "xform", "version", "import", "as", "ns", "def", "var", "let", "in",
    "for", "where", "return", "if", "then", "else", "match", "case",
    "default", "and", "or", "not", "div", "mod", "rule",
    NULL
};

static int is_keyword(const char *s) {
    for (int i = 0; KEYWORDS[i]; i++) {
        if (strcmp(KEYWORDS[i], s) == 0) return 1;
    }
    return 0;
}

Lexer* lexer_new(const char *text) {
    Lexer *lex = (Lexer*)malloc(sizeof(Lexer));
    if (!lex) return NULL;
    lex->text = text;
    lex->len = strlen(text);
    lex->pos = 0;
    lex->buffer = NULL;
    return lex;
}

void lexer_free(Lexer *lex) {
    if (lex) {
        if (lex->buffer) {
            token_free(lex->buffer);
            lex->buffer = NULL;
        }
        free(lex);
    }
}

static void skip_ws(Lexer *lex) {
    while (lex->pos < lex->len) {
        char c = lex->text[lex->pos];
        if (isspace((unsigned char)c)) {
            lex->pos++;
        } else if (c == '#') {
            while (lex->pos < lex->len && lex->text[lex->pos] != '\n') {
                lex->pos++;
            }
        } else {
            break;
        }
    }
}

static Token* make_token(TokenKind kind, const char *value, size_t pos) {
    Token *tok = (Token*)malloc(sizeof(Token));
    if (!tok) return NULL;
    tok->kind = kind;
    tok->value = value ? strdup(value) : NULL;
    tok->pos = pos;
    return tok;
}

static char peek_char(Lexer *lex, size_t offset) {
    if (lex->pos + offset < lex->len) {
        return lex->text[lex->pos + offset];
    }
    return '\0';
}

Token* lexer_next(Lexer *lex) {
    if (lex->buffer) {
        Token *tok = lex->buffer;
        lex->buffer = NULL;
        return tok;
    }
    
    skip_ws(lex);
    
    if (lex->pos >= lex->len) {
        return make_token(TK_EOF, "", lex->pos);
    }
    
    size_t start = lex->pos;
    char c = lex->text[lex->pos];
    
    /* := */
    if (c == ':' && peek_char(lex, 1) == '=') {
        lex->pos += 2;
        return make_token(TK_OP, ":=", start);
    }
    
    /* Punctuation */
    if (strchr("(){}[],:;", c)) {
        char s[2] = {c, '\0'};
        lex->pos++;
        return make_token(TK_PUNCT, s, start);
    }
    
    /* Dot variants */
    if (c == '.') {
        if (peek_char(lex, 1) == '/' && peek_char(lex, 2) == '/') {
            lex->pos += 3;
            return make_token(TK_DOT, ".//", start);
        }
        if (peek_char(lex, 1) == '.') {
            lex->pos += 2;
            return make_token(TK_DOT, "..", start);
        }
        lex->pos++;
        return make_token(TK_DOT, ".", start);
    }
    
    /* Slash variants */
    if (c == '/') {
        if (peek_char(lex, 1) == '/') {
            lex->pos += 2;
            return make_token(TK_SLASH, "//", start);
        }
        lex->pos++;
        return make_token(TK_SLASH, "/", start);
    }
    
    /* Operators */
    if (strchr("<>=!+-*", c)) {
        lex->pos++;
        if (peek_char(lex, 0) == '=') {
            char s[3] = {c, '=', '\0'};
            lex->pos++;
            return make_token(TK_OP, s, start);
        }
        char s[2] = {c, '\0'};
        return make_token(TK_OP, s, start);
    }
    
    /* Strings */
    if (c == '\'' || c == '"') {
        char quote = c;
        lex->pos++;
        StringBuilder *sb = sb_new();
        while (lex->pos < lex->len) {
            char ch = lex->text[lex->pos];
            if (ch == '\\') {
                lex->pos++;
                if (lex->pos < lex->len) {
                    char esc = lex->text[lex->pos];
                    switch (esc) {
                        case 'n': sb_append(sb, '\n'); break;
                        case 't': sb_append(sb, '\t'); break;
                        case 'r': sb_append(sb, '\r'); break;
                        case 'u': {
                            /* Unicode escape \uXXXX */
                            if (lex->pos + 4 < lex->len) {
                                char hex[5];
                                memcpy(hex, lex->text + lex->pos + 1, 4);
                                hex[4] = '\0';
                                unsigned int code;
                                if (sscanf(hex, "%4x", &code) == 1) {
                                    /* Simple handling for ASCII range */
                                    if (code < 128) {
                                        sb_append(sb, (char)code);
                                    }
                                }
                                lex->pos += 4;
                            }
                            break;
                        }
                        default: sb_append(sb, esc); break;
                    }
                    lex->pos++;
                }
            } else if (ch == quote) {
                lex->pos++;
                char *result = sb_to_string(sb);
                sb_free(sb);
                return make_token(TK_STR, result, start);
            } else {
                sb_append(sb, ch);
                lex->pos++;
            }
        }
        sb_free(sb);
        return make_token(TK_STR, "", start);  /* unterminated */
    }
    
    /* Numbers */
    if (isdigit((unsigned char)c)) {
        while (lex->pos < lex->len && 
               (isdigit((unsigned char)lex->text[lex->pos]) || lex->text[lex->pos] == '.')) {
            lex->pos++;
        }
        size_t len = lex->pos - start;
        char *s = (char*)malloc(len + 1);
        memcpy(s, lex->text + start, len);
        s[len] = '\0';
        return make_token(TK_NUM, s, start);
    }
    
    /* Identifiers / Keywords */
    if (isalpha((unsigned char)c) || c == '_') {
        while (lex->pos < lex->len) {
            char ch = lex->text[lex->pos];
            if (ch == ':') {
                /* Check for QName separator */
                if (lex->pos + 1 < lex->len && 
                    (isalnum((unsigned char)lex->text[lex->pos + 1]) || 
                     lex->text[lex->pos + 1] == '_' || 
                     lex->text[lex->pos + 1] == '-')) {
                    lex->pos++;
                    continue;
                }
                break;
            }
            if (isalnum((unsigned char)ch) || ch == '_' || ch == '-') {
                lex->pos++;
            } else {
                break;
            }
        }
        size_t len = lex->pos - start;
        char *s = (char*)malloc(len + 1);
        memcpy(s, lex->text + start, len);
        s[len] = '\0';
        TokenKind kind = is_keyword(s) ? TK_KW : TK_IDENT;
        return make_token(kind, s, start);
    }
    
    /* @ */
    if (c == '@') {
        lex->pos++;
        return make_token(TK_AT, "@", start);
    }
    
    /* Fallback */
    char s[2] = {c, '\0'};
    lex->pos++;
    return make_token(TK_IDENT, s, start);
}

Token* lexer_peek(Lexer *lex) {
    if (!lex->buffer) {
        lex->buffer = lexer_next(lex);
    }
    return lex->buffer;
}

Token* lexer_expect(Lexer *lex, TokenKind kind, const char *value) {
    Token *tok = lexer_next(lex);
    if (!tok) return NULL;
    if (tok->kind != kind || (value && strcmp(tok->value, value) != 0)) {
        free(tok->value);
        tok->value = NULL;
        return tok;
    }
    return tok;
}

void token_free(Token *tok) {
    if (tok) {
        free(tok->value);
        free(tok);
    }
}
