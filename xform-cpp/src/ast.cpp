#include "ast.hpp"
#include "hashmap.hpp"
#include <stdlib.h>
#include <string.h>

void literal_free(LiteralValue *lit) {
    if (!lit) return;
    if (lit->kind == LIT_STR && lit->value.str) {
        free(lit->value.str);
    }
}

void param_free(Param *param) {
    if (!param) return;
    free(param->name);
    free(param->type_ref);
    if (param->default_value) {
        expr_free(param->default_value);
    }
}

void function_def_free(FunctionDef *fd) {
    if (!fd) return;
    for (size_t i = 0; i < fd->param_count; i++) {
        param_free(&fd->params[i]);
    }
    free(fd->params);
    expr_free(fd->body);
    free(fd);
}

void rule_def_free(RuleDef *rd) {
    if (!rd) return;
    pattern_free(rd->pattern);
    expr_free(rd->body);
}

void expr_free(Expr *expr) {
    if (!expr) return;
    
    switch (expr->kind) {
        case EXPR_LITERAL:
            literal_free(&expr->data.literal);
            break;
        case EXPR_VAR_REF:
            free(expr->data.var_ref);
            break;
        case EXPR_IF:
            if (expr->data.if_expr) {
                expr_free(expr->data.if_expr->cond);
                expr_free(expr->data.if_expr->then_expr);
                expr_free(expr->data.if_expr->else_expr);
                free(expr->data.if_expr);
            }
            break;
        case EXPR_LET:
            if (expr->data.let_expr) {
                free(expr->data.let_expr->name);
                expr_free(expr->data.let_expr->value);
                expr_free(expr->data.let_expr->body);
                free(expr->data.let_expr);
            }
            break;
        case EXPR_FOR:
            if (expr->data.for_expr) {
                free(expr->data.for_expr->name);
                expr_free(expr->data.for_expr->seq);
                expr_free(expr->data.for_expr->where_clause);
                expr_free(expr->data.for_expr->body);
                free(expr->data.for_expr);
            }
            break;
        case EXPR_MATCH:
            if (expr->data.match_expr) {
                expr_free(expr->data.match_expr->target);
                for (size_t i = 0; i < expr->data.match_expr->case_count; i++) {
                    pattern_free(expr->data.match_expr->patterns[i]);
                    expr_free(expr->data.match_expr->exprs[i]);
                }
                free(expr->data.match_expr->patterns);
                free(expr->data.match_expr->exprs);
                expr_free(expr->data.match_expr->default_expr);
                free(expr->data.match_expr);
            }
            break;
        case EXPR_FUNC_CALL:
            if (expr->data.func_call) {
                free(expr->data.func_call->name);
                for (size_t i = 0; i < expr->data.func_call->arg_count; i++) {
                    expr_free(expr->data.func_call->args[i]);
                }
                free(expr->data.func_call->args);
                free(expr->data.func_call);
            }
            break;
        case EXPR_UNARY_OP:
            if (expr->data.unary_op) {
                free(expr->data.unary_op->op);
                expr_free(expr->data.unary_op->expr);
                free(expr->data.unary_op);
            }
            break;
        case EXPR_BINARY_OP:
            if (expr->data.binary_op) {
                free(expr->data.binary_op->op);
                expr_free(expr->data.binary_op->left);
                expr_free(expr->data.binary_op->right);
                free(expr->data.binary_op);
            }
            break;
        case EXPR_PATH:
            path_expr_free(expr->data.path);
            break;
        case EXPR_CONSTRUCTOR:
            constructor_free(expr->data.constructor);
            break;
        case EXPR_TEXT_CONSTRUCTOR:
            expr_free(expr->data.text_constructor);
            break;
        case EXPR_CHAR_DATA:
            free(expr->data.char_data);
            break;
        case EXPR_INTERP:
            expr_free(expr->data.interp);
            break;
    }
    free(expr);
}

void pattern_free(Pattern *pat) {
    if (!pat) return;
    switch (pat->kind) {
        case PAT_ELEMENT:
            if (pat->data.element) {
                free(pat->data.element->name);
                free(pat->data.element->var);
                pattern_free(pat->data.element->child);
                free(pat->data.element);
            }
            break;
        case PAT_ATTRIBUTE:
            free(pat->data.attribute);
            break;
        case PAT_TYPED:
            free(pat->data.typed);
            break;
        case PAT_WILDCARD:
            break;
    }
    free(pat);
}

void constructor_free(Constructor *c) {
    if (!c) return;
    free(c->name);
    for (size_t i = 0; i < c->attr_count; i++) {
        free(c->attrs[i].name);
        expr_free(c->attrs[i].expr);
    }
    free(c->attrs);
    for (size_t i = 0; i < c->content_count; i++) {
        expr_free(c->contents[i]);
    }
    free(c->contents);
    free(c);
}

void path_expr_free(PathExpr *pe) {
    if (!pe) return;
    free(pe->start.name);
    for (size_t i = 0; i < pe->step_count; i++) {
        free(pe->steps[i].test.name);
        for (size_t j = 0; j < pe->steps[i].predicate_count; j++) {
            expr_free(pe->steps[i].predicates[j]);
        }
        free(pe->steps[i].predicates);
    }
    free(pe->steps);
    free(pe);
}

void module_free(Module *mod) {
    if (!mod) return;
    hm_free_with_values(mod->functions, (void(*)(void*))function_def_free);
    
    /* Rules hashmap contains arrays of RuleDef* */
    if (mod->rules) {
        size_t count;
        HMEntry *entries = hm_entries(mod->rules, &count);
        for (size_t i = 0; i < count; i++) {
            RuleDef **rules = entries[i].value;
            for (size_t j = 0; rules[j]; j++) {
                rule_def_free(rules[j]);
                free(rules[j]);
            }
            free(rules);
        }
        free(entries);
        hm_free(mod->rules);
    }
    
    hm_free_with_values(mod->vars, (void(*)(void*))expr_free);
    hm_free(mod->namespaces);
    
    for (size_t i = 0; i < mod->import_count; i++) {
        free(mod->imports[i].iri);
        free(mod->imports[i].alias);
    }
    free(mod->imports);
    
    expr_free(mod->expr);
    free(mod);
}

StepTest step_test_named(const char *name) {
    StepTest st;
    st.kind = TEST_NAME;
    st.name = strdup(name);
    return st;
}

StepTest step_test_wildcard(void) {
    StepTest st;
    st.kind = TEST_WILDCARD;
    st.name = NULL;
    return st;
}

StepTest step_test_text(void) {
    StepTest st;
    st.kind = TEST_TEXT;
    st.name = NULL;
    return st;
}

StepTest step_test_node(void) {
    StepTest st;
    st.kind = TEST_NODE;
    st.name = NULL;
    return st;
}

Expr* expr_new(ExprKind kind) {
    Expr *e = calloc(1, sizeof(Expr));
    if (e) e->kind = kind;
    return e;
}

Pattern* pattern_new(PatternKind kind) {
    Pattern *p = calloc(1, sizeof(Pattern));
    if (p) p->kind = kind;
    return p;
}
