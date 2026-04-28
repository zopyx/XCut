#include "parser.hpp"
#include "string_builder.hpp"
#include "hashmap.hpp"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <ctype.h>

static __thread char error_buf[1024];

/* Forward declarations */
static Expr* parse_constructor(Parser *p);

const char* parser_error(void) {
    return error_buf;
}

void parser_clear_error(void) {
    error_buf[0] = '\0';
}

static void set_error(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(error_buf, sizeof(error_buf), fmt, args);
    va_end(args);
}

Parser* parser_new(const char *text) {
    Parser *p = (Parser*)malloc(sizeof(Parser));
    if (!p) return NULL;
    p->lexer = lexer_new(text);
    if (!p->lexer) {
        free(p);
        return NULL;
    }
    parser_clear_error();
    return p;
}

void parser_free(Parser *p) {
    if (p) {
        lexer_free(p->lexer);
        free(p);
    }
}

static int peek_is(Parser *p, TokenKind kind, const char *value) {
    Token *tok = lexer_peek(p->lexer);
    if (!tok) return 0;
    if (tok->kind != kind) return 0;
    if (value && strcmp(tok->value, value) != 0) return 0;
    return 1;
}

static int accept(Parser *p, TokenKind kind, const char *value) {
    if (peek_is(p, kind, value)) {
        Token *tok = lexer_next(p->lexer);
        token_free(tok);
        return 1;
    }
    return 0;
}

static int expect(Parser *p, TokenKind kind, const char *value) {
    Token *tok = lexer_peek(p->lexer);
    if (!tok) {
        set_error("Unexpected end of input");
        return 0;
    }
    if (tok->kind != kind || (value && strcmp(tok->value, value) != 0)) {
        set_error("Expected %s at position %zu, got %s", 
                  value ? value : "token", tok->pos, tok->value);
        return 0;
    }
    lexer_next(p->lexer);
    token_free(tok);
    return 1;
}

static char* expect_ident(Parser *p) {
    Token *tok = lexer_next(p->lexer);
    if (!tok || (tok->kind != TK_IDENT && tok->kind != TK_KW)) {
        set_error("Expected identifier");
        if (tok) token_free(tok);
        return NULL;
    }
    char *result = tok->value;
    tok->value = NULL;
    token_free(tok);
    return result;
}

/* Forward declarations */
static Expr* parse_expr_internal(Parser *p);
static Pattern* parse_pattern(Parser *p);
static Expr* parse_primary(Parser *p);
static Expr* parse_path(Parser *p, PathStart *start);

static void parse_ns(Parser *p, Module *mod) {
    expect(p, TK_KW, "ns");
    char *prefix = expect_ident(p);
    expect(p, TK_OP, "=");
    Token *uri_tok = lexer_next(p->lexer);
    if (uri_tok && uri_tok->kind == TK_STR) {
        hm_set(mod->namespaces, prefix, uri_tok->value);
        free(uri_tok->value);
        uri_tok->value = NULL;
    }
    token_free(uri_tok);
    expect(p, TK_PUNCT, ";");
    free(prefix);
}

static void parse_import(Parser *p, Module *mod) {
    expect(p, TK_KW, "import");
    Token *iri_tok = lexer_next(p->lexer);
    char *alias = NULL;
    if (peek_is(p, TK_KW, "as")) {
        accept(p, TK_KW, "as");
        alias = expect_ident(p);
    }
    expect(p, TK_PUNCT, ";");
    
    /* Grow imports array */
    mod->imports = (decltype(mod->imports))realloc(mod->imports, 
        (mod->import_count + 1) * sizeof(*mod->imports));
    mod->imports[mod->import_count].iri = 
        (iri_tok && iri_tok->value) ? strdup(iri_tok->value) : NULL;
    mod->imports[mod->import_count].alias = alias;
    mod->import_count++;
    token_free(iri_tok);
}

static void parse_var(Parser *p, Module *mod) {
    expect(p, TK_KW, "var");
    char *name = expect_ident(p);
    expect(p, TK_OP, ":=");
    Expr *expr = parse_expr_internal(p);
    expect(p, TK_PUNCT, ";");
    if (name && expr) {
        hm_set(mod->vars, name, expr);
    }
    free(name);
}

static Param parse_param(Parser *p) {
    Param param = {0};
    param.name = expect_ident(p);
    if (accept(p, TK_PUNCT, ":")) {
        param.type_ref = expect_ident(p);
    }
    if (accept(p, TK_OP, ":=")) {
        param.default_value = parse_expr_internal(p);
    }
    return param;
}

static void parse_def(Parser *p, Module *mod) {
    expect(p, TK_KW, "def");
    char *name = expect_ident(p);
    expect(p, TK_PUNCT, "(");
    
    FunctionDef *fd = (FunctionDef*)calloc(1, sizeof(FunctionDef));
    
    if (!peek_is(p, TK_PUNCT, ")")) {
        /* Parse first param */
        fd->params = (Param*)realloc(fd->params, (fd->param_count + 1) * sizeof(Param));
        fd->params[fd->param_count++] = parse_param(p);
        
        while (accept(p, TK_PUNCT, ",")) {
            fd->params = (Param*)realloc(fd->params, (fd->param_count + 1) * sizeof(Param));
            fd->params[fd->param_count++] = parse_param(p);
        }
    }
    
    expect(p, TK_PUNCT, ")");
    expect(p, TK_OP, ":=");
    fd->body = parse_expr_internal(p);
    expect(p, TK_PUNCT, ";");
    
    if (name && fd->body) {
        hm_set(mod->functions, name, fd);
    }
    free(name);
}

static void parse_rule(Parser *p, Module *mod) {
    expect(p, TK_KW, "rule");
    char *name = expect_ident(p);
    expect(p, TK_KW, "match");
    Pattern *pattern = parse_pattern(p);
    expect(p, TK_OP, ":=");
    Expr *body = parse_expr_internal(p);
    expect(p, TK_PUNCT, ";");
    
    if (name && pattern && body) {
        RuleDef *rd = (RuleDef*)malloc(sizeof(RuleDef));
        rd->pattern = pattern;
        rd->body = body;
        
        /* Get or create rules array */
        RuleDef **rules = (RuleDef**)hm_get(mod->rules, name);
        size_t count = 0;
        if (rules) {
            while (rules[count]) count++;
        }
        rules = (RuleDef**)realloc(rules, (count + 2) * sizeof(RuleDef*));
        rules[count] = rd;
        rules[count + 1] = NULL;
        hm_set(mod->rules, name, rules);
    }
    free(name);
}

static Expr* parse_if(Parser *p) {
    accept(p, TK_KW, "if");
    Expr *cond = parse_expr_internal(p);
    expect(p, TK_KW, "then");
    Expr *then_expr = parse_expr_internal(p);
    expect(p, TK_KW, "else");
    Expr *else_expr = parse_expr_internal(p);
    
    IfExpr *ie = (IfExpr*)malloc(sizeof(IfExpr));
    ie->cond = cond;
    ie->then_expr = then_expr;
    ie->else_expr = else_expr;
    
    Expr *e = expr_new(EXPR_IF);
    e->data.if_expr = ie;
    return e;
}

static Expr* parse_let(Parser *p) {
    accept(p, TK_KW, "let");
    char *name = expect_ident(p);
    expect(p, TK_OP, ":=");
    Expr *value = parse_expr_internal(p);
    expect(p, TK_KW, "in");
    Expr *body = parse_expr_internal(p);
    
    LetExpr *le = (LetExpr*)malloc(sizeof(LetExpr));
    le->name = name;
    le->value = value;
    le->body = body;
    
    Expr *e = expr_new(EXPR_LET);
    e->data.let_expr = le;
    return e;
}

static Expr* parse_for(Parser *p) {
    accept(p, TK_KW, "for");
    char *name = expect_ident(p);
    expect(p, TK_KW, "in");
    Expr *seq = parse_expr_internal(p);
    
    Expr *where = NULL;
    if (accept(p, TK_KW, "where")) {
        where = parse_expr_internal(p);
    }
    
    expect(p, TK_KW, "return");
    Expr *body = parse_expr_internal(p);
    
    ForExpr *fe = (ForExpr*)malloc(sizeof(ForExpr));
    fe->name = name;
    fe->seq = seq;
    fe->where_clause = where;
    fe->body = body;
    
    Expr *e = expr_new(EXPR_FOR);
    e->data.for_expr = fe;
    return e;
}

static Expr* parse_match(Parser *p) {
    accept(p, TK_KW, "match");
    Expr *target = parse_expr_internal(p);
    expect(p, TK_PUNCT, ":");
    
    MatchExpr *me = (MatchExpr*)calloc(1, sizeof(MatchExpr));
    me->target = target;
    
    while (1) {
        if (peek_is(p, TK_KW, "case")) {
            accept(p, TK_KW, "case");
            Pattern *pat = parse_pattern(p);
            expect(p, TK_OP, "=");
            expect(p, TK_OP, ">");
            Expr *expr = parse_expr_internal(p);
            expect(p, TK_PUNCT, ";");
            
            me->patterns = (Pattern**)realloc(me->patterns, 
                (me->case_count + 1) * sizeof(Pattern*));
            me->exprs = (Expr**)realloc(me->exprs, 
                (me->case_count + 1) * sizeof(Expr*));
            me->patterns[me->case_count] = pat;
            me->exprs[me->case_count] = expr;
            me->case_count++;
        } else if (peek_is(p, TK_KW, "default")) {
            accept(p, TK_KW, "default");
            expect(p, TK_OP, "=");
            expect(p, TK_OP, ">");
            me->default_expr = parse_expr_internal(p);
            expect(p, TK_PUNCT, ";");
            break;
        } else {
            break;
        }
    }
    
    Expr *e = expr_new(EXPR_MATCH);
    e->data.match_expr = me;
    return e;
}

static Expr* parse_or(Parser *p);

static Expr* parse_primary(Parser *p) {
    if (peek_is(p, TK_NUM, NULL)) {
        Token *tok = lexer_next(p->lexer);
        Expr *e = expr_new(EXPR_LITERAL);
        e->data.literal.kind = LIT_NUM;
        e->data.literal.value.num = strtod(tok->value, NULL);
        token_free(tok);
        return e;
    }
    
    if (peek_is(p, TK_STR, NULL)) {
        Token *tok = lexer_next(p->lexer);
        Expr *e = expr_new(EXPR_LITERAL);
        e->data.literal.kind = LIT_STR;
        e->data.literal.value.str = strdup(tok->value);
        token_free(tok);
        return e;
    }
    
    if (accept(p, TK_PUNCT, "(")) {
        Expr *e = parse_expr_internal(p);
        expect(p, TK_PUNCT, ")");
        return e;
    }
    
    if (peek_is(p, TK_KW, "if")) {
        return parse_if(p);
    }
    
    if (peek_is(p, TK_KW, "let")) {
        return parse_let(p);
    }
    
    if (peek_is(p, TK_KW, "for")) {
        return parse_for(p);
    }
    
    if (peek_is(p, TK_KW, "match")) {
        return parse_match(p);
    }
    
    /* text{...} constructor */
    if (peek_is(p, TK_IDENT, "text")) {
        size_t saved_pos = p->lexer->pos;
        Token *saved_buf = p->lexer->buffer;
        if (p->lexer->buffer) {
            p->lexer->buffer = NULL;
        }
        
        Token *tok = lexer_next(p->lexer);
        if (peek_is(p, TK_PUNCT, "{")) {
            token_free(tok);
            accept(p, TK_PUNCT, "{");
            Expr *inner = parse_expr_internal(p);
            expect(p, TK_PUNCT, "}");
            Expr *e = expr_new(EXPR_TEXT_CONSTRUCTOR);
            e->data.text_constructor = inner;
            return e;
        }
        /* Restore */
        p->lexer->pos = saved_pos;
        p->lexer->buffer = saved_buf;
        /* Fall through to identifier handling */
    }
    
    /* Element constructor */
    if (peek_is(p, TK_OP, "<")) {
        return parse_constructor(p);
    }
    
    /* Path starting with . or / */
    if (peek_is(p, TK_DOT, NULL) || peek_is(p, TK_SLASH, NULL)) {
        return parse_path(p, NULL);
    }
    
    /* Identifier: variable, function call, or path start */
    if (peek_is(p, TK_IDENT, NULL)) {
        char *name = expect_ident(p);
        if (peek_is(p, TK_PUNCT, "(")) {
            /* Function call */
            accept(p, TK_PUNCT, "(");
            FuncCall *fc = (FuncCall*)calloc(1, sizeof(FuncCall));
            fc->name = name;
            
            if (!peek_is(p, TK_PUNCT, ")")) {
                fc->args = (Expr**)realloc(fc->args, (fc->arg_count + 1) * sizeof(Expr*));
                fc->args[fc->arg_count++] = parse_expr_internal(p);
                while (accept(p, TK_PUNCT, ",")) {
                    fc->args = (Expr**)realloc(fc->args, (fc->arg_count + 1) * sizeof(Expr*));
                    fc->args[fc->arg_count++] = parse_expr_internal(p);
                }
            }
            expect(p, TK_PUNCT, ")");
            
            Expr *e = expr_new(EXPR_FUNC_CALL);
            e->data.func_call = fc;
            return e;
        }
        
        /* Check if path continues */
        if (peek_is(p, TK_SLASH, NULL) || peek_is(p, TK_DOT, NULL) || 
            peek_is(p, TK_AT, NULL)) {
            PathStart start = {PS_VAR, name};
            return parse_path(p, &start);
        }
        
        /* Simple variable reference */
        Expr *e = expr_new(EXPR_VAR_REF);
        e->data.var_ref = name;
        return e;
    }
    
    set_error("Unexpected token in primary expression");
    return NULL;
}

static StepTest parse_step_test(Parser *p) {
    if (accept(p, TK_OP, "*")) {
        return step_test_wildcard();
    }
    
    if (peek_is(p, TK_IDENT, NULL)) {
        char *name = expect_ident(p);
        if (strcmp(name, "text") == 0 || strcmp(name, "node") == 0 ||
            strcmp(name, "comment") == 0 || strcmp(name, "pi") == 0) {
            accept(p, TK_PUNCT, "(");
            expect(p, TK_PUNCT, ")");
            StepTest st;
            if (strcmp(name, "text") == 0) st = step_test_text();
            else if (strcmp(name, "node") == 0) st = step_test_node();
            else {
                st.kind = strcmp(name, "comment") == 0 ? TEST_COMMENT : TEST_PI;
                st.name = NULL;
            }
            free(name);
            return st;
        }
        return step_test_named(name);
    }
    
    return step_test_node();
}

static Expr** parse_predicates(Parser *p, size_t *count) {
    *count = 0;
    Expr **preds = NULL;
    
    while (accept(p, TK_PUNCT, "[")) {
        preds = (Expr**)realloc(preds, (*count + 1) * sizeof(Expr*));
        preds[(*count)++] = parse_expr_internal(p);
        expect(p, TK_PUNCT, "]");
    }
    
    return preds;
}

static Expr* parse_path(Parser *p, PathStart *start) {
    PathExpr *pe = (PathExpr*)calloc(1, sizeof(PathExpr));
    
    if (start) {
        pe->start = *start;
        if (start->name) pe->start.name = strdup(start->name);
    } else {
        Token *tok = lexer_next(p->lexer);
        if (tok->kind == TK_DOT) {
            if (strcmp(tok->value, ".//") == 0) {
                pe->start.kind = PS_DESC;
            } else {
                pe->start.kind = PS_CONTEXT;
            }
        } else if (tok->kind == TK_SLASH) {
            if (strcmp(tok->value, "//") == 0) {
                pe->start.kind = PS_DESC_ROOT;
            } else {
                pe->start.kind = PS_ROOT;
            }
        }
        token_free(tok);
    }
    
    /* Handle special starts */
    if (pe->start.kind == PS_DESC || pe->start.kind == PS_DESC_ROOT) {
        if (peek_is(p, TK_IDENT, NULL) || peek_is(p, TK_OP, "*")) {
            PathStep step = {};
            step.axis = AXIS_DESC_OR_SELF;
            step.test = parse_step_test(p);
            step.predicates = parse_predicates(p, &step.predicate_count);
            
            pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
            pe->steps[pe->step_count++] = step;
        }
    }
    
    if (pe->start.kind == PS_ROOT) {
        if (peek_is(p, TK_AT, NULL)) {
            accept(p, TK_AT, NULL);
            char *name = expect_ident(p);
            PathStep step = {};
            step.axis = AXIS_ATTR;
            step.test = step_test_named(name);
            free(name);
            pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
            pe->steps[pe->step_count++] = step;
        } else if (peek_is(p, TK_IDENT, NULL) || peek_is(p, TK_OP, "*")) {
            PathStep step = {};
            step.axis = AXIS_CHILD;
            step.test = parse_step_test(p);
            step.predicates = parse_predicates(p, &step.predicate_count);
            pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
            pe->steps[pe->step_count++] = step;
        }
    }
    
    /* Parse remaining steps */
    while (1) {
        if (peek_is(p, TK_SLASH, NULL)) {
            Token *tok = lexer_next(p->lexer);
            PathAxis axis = (strcmp(tok->value, "//") == 0) ? AXIS_DESC : AXIS_CHILD;
            token_free(tok);
            
            if (accept(p, TK_AT, NULL)) {
                char *name = expect_ident(p);
                PathStep step = {};
                step.axis = AXIS_ATTR;
                step.test = step_test_named(name);
                free(name);
                pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
                pe->steps[pe->step_count++] = step;
            } else {
                PathStep step = {};
                step.axis = axis;
                step.test = parse_step_test(p);
                step.predicates = parse_predicates(p, &step.predicate_count);
                pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
                pe->steps[pe->step_count++] = step;
            }
        } else if (peek_is(p, TK_DOT, NULL)) {
            Token *tok = lexer_next(p->lexer);
            if (strcmp(tok->value, ".") == 0) {
                token_free(tok);
                PathStep step = {};
                step.axis = AXIS_SELF;
                step.test = step_test_node();
                
                if (accept(p, TK_AT, NULL)) {
                    char *name = expect_ident(p);
                    step.axis = AXIS_ATTR;
                    step.test = step_test_named(name);
                    free(name);
                }
                
                pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
                pe->steps[pe->step_count++] = step;
            } else if (strcmp(tok->value, "..") == 0) {
                token_free(tok);
                PathStep step = {};
                step.axis = AXIS_PARENT;
                step.test = step_test_node();
                pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
                pe->steps[pe->step_count++] = step;
            } else {
                token_free(tok);
                break;
            }
        } else if (accept(p, TK_AT, NULL)) {
            char *name = expect_ident(p);
            PathStep step = {};
            step.axis = AXIS_ATTR;
            step.test = step_test_named(name);
            free(name);
            pe->steps = (PathStep*)realloc(pe->steps, (pe->step_count + 1) * sizeof(PathStep));
            pe->steps[pe->step_count++] = step;
        } else {
            break;
        }
    }
    
    Expr *e = expr_new(EXPR_PATH);
    e->data.path = pe;
    return e;
}

static Pattern* parse_pattern(Parser *p) {
    if (accept(p, TK_AT, NULL)) {
        char *name = expect_ident(p);
        Pattern *pat = pattern_new(PAT_ATTRIBUTE);
        pat->data.attribute = name;
        return pat;
    }
    
    if (peek_is(p, TK_IDENT, NULL)) {
        char *name = expect_ident(p);
        if (strcmp(name, "node") == 0 || strcmp(name, "text") == 0 || 
            strcmp(name, "comment") == 0) {
            accept(p, TK_PUNCT, "(");
            expect(p, TK_PUNCT, ")");
            Pattern *pat = pattern_new(PAT_TYPED);
            pat->data.typed = name;
            return pat;
        }
        if (strcmp(name, "_") == 0) {
            free(name);
            return pattern_new(PAT_WILDCARD);
        }
        /* Element pattern */
        expect(p, TK_PUNCT, "(");
        expect(p, TK_PUNCT, ")");
        Pattern *pat = pattern_new(PAT_ELEMENT);
        pat->data.element = (ElementPattern*)calloc(1, sizeof(ElementPattern));
        pat->data.element->name = name;
        return pat;
    }
    
    if (accept(p, TK_OP, "<")) {
        /* Element pattern with content */
        char *name = expect_ident(p);
        expect(p, TK_OP, ">");
        
        Pattern *pat = pattern_new(PAT_ELEMENT);
        pat->data.element = (ElementPattern*)calloc(1, sizeof(ElementPattern));
        pat->data.element->name = name;
        
        if (peek_is(p, TK_PUNCT, "{")) {
            accept(p, TK_PUNCT, "{");
            pat->data.element->var = expect_ident(p);
            expect(p, TK_PUNCT, "}");
        } else if (peek_is(p, TK_OP, "<")) {
            pat->data.element->child = parse_pattern(p);
        }
        
        expect(p, TK_OP, "<");
        expect(p, TK_SLASH, "/");
        char *end_name = expect_ident(p);
        free(end_name);
        expect(p, TK_OP, ">");
        return pat;
    }
    
    set_error("Invalid pattern");
    return NULL;
}

static Expr* parse_unary(Parser *p) {
    if (accept(p, TK_OP, "-")) {
        Expr *operand = parse_unary(p);
        UnaryOp *uo = (UnaryOp*)malloc(sizeof(UnaryOp));
        uo->op = strdup("-");
        uo->expr = operand;
        Expr *e = expr_new(EXPR_UNARY_OP);
        e->data.unary_op = uo;
        return e;
    }
    
    if (accept(p, TK_KW, "not")) {
        Expr *operand = parse_unary(p);
        UnaryOp *uo = (UnaryOp*)malloc(sizeof(UnaryOp));
        uo->op = strdup("not");
        uo->expr = operand;
        Expr *e = expr_new(EXPR_UNARY_OP);
        e->data.unary_op = uo;
        return e;
    }
    
    return parse_primary(p);
}

static Expr* parse_mul(Parser *p) {
    Expr *left = parse_unary(p);
    
    while (1) {
        if (accept(p, TK_OP, "*")) {
            Expr *right = parse_unary(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup("*");
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
        } else if (accept(p, TK_KW, "div")) {
            Expr *right = parse_unary(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup("div");
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
        } else if (accept(p, TK_KW, "mod")) {
            Expr *right = parse_unary(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup("mod");
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
        } else {
            break;
        }
    }
    
    return left;
}

static Expr* parse_add(Parser *p) {
    Expr *left = parse_mul(p);
    
    while (1) {
        if (accept(p, TK_OP, "+")) {
            Expr *right = parse_mul(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup("+");
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
        } else if (accept(p, TK_OP, "-")) {
            Expr *right = parse_mul(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup("-");
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
        } else {
            break;
        }
    }
    
    return left;
}

static Expr* parse_rel(Parser *p) {
    Expr *left = parse_add(p);
    
    while (1) {
        if (peek_is(p, TK_OP, "<") || peek_is(p, TK_OP, "<=") ||
            peek_is(p, TK_OP, ">") || peek_is(p, TK_OP, ">=")) {
            Token *tok = lexer_next(p->lexer);
            Expr *right = parse_add(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup(tok->value);
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
            token_free(tok);
        } else {
            break;
        }
    }
    
    return left;
}

static Expr* parse_eq(Parser *p) {
    Expr *left = parse_rel(p);
    
    while (1) {
        if (peek_is(p, TK_OP, "=") || peek_is(p, TK_OP, "!=")) {
            Token *tok = lexer_next(p->lexer);
            Expr *right = parse_rel(p);
            BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
            bo->op = strdup(tok->value);
            bo->left = left;
            bo->right = right;
            Expr *e = expr_new(EXPR_BINARY_OP);
            e->data.binary_op = bo;
            left = e;
            token_free(tok);
        } else {
            break;
        }
    }
    
    return left;
}

static Expr* parse_and(Parser *p) {
    Expr *left = parse_eq(p);
    
    while (accept(p, TK_KW, "and")) {
        Expr *right = parse_eq(p);
        BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
        bo->op = strdup("and");
        bo->left = left;
        bo->right = right;
        Expr *e = expr_new(EXPR_BINARY_OP);
        e->data.binary_op = bo;
        left = e;
    }
    
    return left;
}

static Expr* parse_or(Parser *p) {
    Expr *left = parse_and(p);
    
    while (accept(p, TK_KW, "or")) {
        Expr *right = parse_and(p);
        BinaryOp *bo = (BinaryOp*)malloc(sizeof(BinaryOp));
        bo->op = strdup("or");
        bo->left = left;
        bo->right = right;
        Expr *e = expr_new(EXPR_BINARY_OP);
        e->data.binary_op = bo;
        left = e;
    }
    
    return left;
}

static Expr* parse_expr_internal(Parser *p) {
    return parse_or(p);
}

/* Constructor parsing */
static char* read_end_tag(Parser *p, size_t *new_pos);

static Expr* parse_constructor(Parser *p) {
    accept(p, TK_OP, "<");
    char *name = expect_ident(p);
    
    Constructor *c = (Constructor*)calloc(1, sizeof(Constructor));
    c->name = name;
    
    /* Parse attributes */
    while (1) {
        if (peek_is(p, TK_OP, ">")) {
            accept(p, TK_OP, ">");
            break;
        }
        if (peek_is(p, TK_SLASH, "/")) {
            accept(p, TK_SLASH, "/");
            expect(p, TK_OP, ">");
            Expr *e = expr_new(EXPR_CONSTRUCTOR);
            e->data.constructor = c;
            return e;
        }
        
        char *aname = expect_ident(p);
        expect(p, TK_OP, "=");
        expect(p, TK_PUNCT, "{");
        Expr *aexpr = parse_expr_internal(p);
        expect(p, TK_PUNCT, "}");
        
        c->attrs = (decltype(c->attrs))realloc(c->attrs, (c->attr_count + 1) * sizeof(*c->attrs));
        c->attrs[c->attr_count].name = aname;
        c->attrs[c->attr_count].expr = aexpr;
        c->attr_count++;
    }
    
    /* Parse contents */
    p->lexer->buffer = NULL;
    while (1) {
        /* Check for end tag */
        if (p->lexer->pos + 1 < p->lexer->len &&
            p->lexer->text[p->lexer->pos] == '<' &&
            p->lexer->text[p->lexer->pos + 1] == '/') {
            size_t new_pos;
            char *end_name = read_end_tag(p, &new_pos);
            if (end_name) {
                if (strcmp(end_name, name) != 0) {
                    set_error("Mismatched end tag");
                    free(end_name);
                    constructor_free(c);
                    return NULL;
                }
                free(end_name);
                p->lexer->pos = new_pos;
                p->lexer->buffer = NULL;
                break;
            }
        }
        
        /* text{ constructor */
        if (p->lexer->pos + 5 <= p->lexer->len &&
            strncmp(p->lexer->text + p->lexer->pos, "text{", 5) == 0) {
            p->lexer->pos += 4;
            p->lexer->buffer = NULL;
            expect(p, TK_PUNCT, "{");
            Expr *inner = parse_expr_internal(p);
            expect(p, TK_PUNCT, "}");
            Expr *e = expr_new(EXPR_TEXT_CONSTRUCTOR);
            e->data.text_constructor = inner;
            c->contents = (Expr**)realloc(c->contents, (c->content_count + 1) * sizeof(Expr*));
            c->contents[c->content_count++] = e;
            continue;
        }
        
        /* Nested element */
        if (p->lexer->pos < p->lexer->len &&
            p->lexer->text[p->lexer->pos] == '<') {
            p->lexer->buffer = NULL;
            Expr *child = parse_constructor(p);
            c->contents = (Expr**)realloc(c->contents, (c->content_count + 1) * sizeof(Expr*));
            c->contents[c->content_count++] = child;
            continue;
        }
        
        /* Interpolation */
        if (p->lexer->pos < p->lexer->len &&
            p->lexer->text[p->lexer->pos] == '{') {
            p->lexer->pos++;
            p->lexer->buffer = NULL;
            Expr *interp = parse_expr_internal(p);
            expect(p, TK_PUNCT, "}");
            Expr *e = expr_new(EXPR_INTERP);
            e->data.interp = interp;
            c->contents = (Expr**)realloc(c->contents, (c->content_count + 1) * sizeof(Expr*));
            c->contents[c->content_count++] = e;
            continue;
        }
        
        /* Character data */
        StringBuilder *sb = sb_new();
        while (p->lexer->pos < p->lexer->len) {
            char ch = p->lexer->text[p->lexer->pos];
            if (ch == '<' || ch == '{') break;
            sb_append(sb, ch);
            p->lexer->pos++;
        }
        char *cd = sb_to_string(sb);
        if (cd && strlen(cd) > 0) {
            Expr *e = expr_new(EXPR_CHAR_DATA);
            e->data.char_data = cd;
            c->contents = (Expr**)realloc(c->contents, (c->content_count + 1) * sizeof(Expr*));
            c->contents[c->content_count++] = e;
        } else {
            free(cd);
        }
    }
    
    Expr *e = expr_new(EXPR_CONSTRUCTOR);
    e->data.constructor = c;
    return e;
}

static char* read_end_tag(Parser *p, size_t *new_pos) {
    size_t pos = p->lexer->pos;
    if (pos + 1 >= p->lexer->len ||
        p->lexer->text[pos] != '<' ||
        p->lexer->text[pos + 1] != '/') {
        return NULL;
    }
    pos += 2;
    size_t start = pos;
    while (pos < p->lexer->len &&
           (isalnum((unsigned char)p->lexer->text[pos]) ||
            p->lexer->text[pos] == '_' ||
            p->lexer->text[pos] == ':' ||
            p->lexer->text[pos] == '-')) {
        pos++;
    }
    
    if (start == pos) return NULL;
    
    size_t len = pos - start;
    char *name = (char*)malloc(len + 1);
    memcpy(name, p->lexer->text + start, len);
    name[len] = '\0';
    
    while (pos < p->lexer->len && isspace((unsigned char)p->lexer->text[pos])) {
        pos++;
    }
    
    if (pos >= p->lexer->len || p->lexer->text[pos] != '>') {
        free(name);
        return NULL;
    }
    
    *new_pos = pos + 1;
    return name;
}

Module* parser_parse_module(Parser *p) {
    parser_clear_error();
    
    Module *mod = (Module*)calloc(1, sizeof(Module));
    mod->functions = hm_new();
    mod->rules = hm_new();
    mod->vars = hm_new();
    mod->namespaces = hm_new();
    
    /* Optional prolog */
    if (peek_is(p, TK_KW, "xform")) {
        accept(p, TK_KW, "xform");
        expect(p, TK_KW, "version");
        Token *ver = lexer_next(p->lexer);
        if (ver && strcmp(ver->value, "2.0") != 0) {
            set_error("XFST0005: unsupported version");
            token_free(ver);
            module_free(mod);
            return NULL;
        }
        token_free(ver);
        expect(p, TK_PUNCT, ";");
    }
    
    /* Parse declarations */
    while (1) {
        if (peek_is(p, TK_KW, "ns")) {
            parse_ns(p, mod);
        } else if (peek_is(p, TK_KW, "import")) {
            parse_import(p, mod);
        } else if (peek_is(p, TK_KW, "var")) {
            parse_var(p, mod);
        } else if (peek_is(p, TK_KW, "def")) {
            parse_def(p, mod);
        } else if (peek_is(p, TK_KW, "rule")) {
            parse_rule(p, mod);
        } else {
            break;
        }
    }
    
    /* Parse main expression if present */
    if (!peek_is(p, TK_EOF, NULL)) {
        mod->expr = parse_expr_internal(p);
    }
    
    return mod;
}

Expr* parser_parse_expr(Parser *p) {
    parser_clear_error();
    return parse_expr_internal(p);
}
