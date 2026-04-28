#include "eval.hpp"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>
#include <ctype.h>

/* Item functions */
Item* item_new(ItemKind kind) {
    Item *item = (Item*)calloc(1, sizeof(Item));
    item->kind = kind;
    return item;
}

Item* item_new_node(XmlNode *node) {
    Item *item = item_new(ITEM_NODE);
    item->data.node = node_ref(node);
    return item;
}

Item* item_new_str(const char *s) {
    Item *item = item_new(ITEM_STR);
    item->data.str = strdup(s);
    return item;
}

Item* item_new_num(double n) {
    Item *item = item_new(ITEM_NUM);
    item->data.num = n;
    return item;
}

Item* item_new_bool(bool b) {
    Item *item = item_new(ITEM_BOOL);
    item->data.bool_val = b;
    return item;
}

Item* item_new_null(void) {
    return item_new(ITEM_NULL);
}

Item* item_new_func_ref(const char *name) {
    Item *item = item_new(ITEM_FUNC_REF);
    item->data.func_ref = strdup(name);
    return item;
}

void item_free(Item *item) {
    if (!item) return;
    switch (item->kind) {
        case ITEM_NODE:
            node_unref(item->data.node);
            break;
        case ITEM_STR:
            free(item->data.str);
            break;
        case ITEM_FUNC_REF:
            free(item->data.func_ref);
            break;
        case ITEM_MAP:
            xmap_free(item->data.map);
            break;
        default:
            break;
    }
    free(item);
}

Item* item_copy(Item *item) {
    if (!item) return NULL;
    switch (item->kind) {
        case ITEM_NODE:
            return item_new_node(item->data.node);
        case ITEM_STR:
            return item_new_str(item->data.str);
        case ITEM_NUM:
            return item_new_num(item->data.num);
        case ITEM_BOOL:
            return item_new_bool(item->data.bool_val);
        case ITEM_NULL:
            return item_new_null();
        case ITEM_FUNC_REF:
            return item_new_func_ref(item->data.func_ref);
        case ITEM_MAP:
            {
                Item *copy = item_new(ITEM_MAP);
                copy->data.map = xmap_new();
                size_t count = 0;
                HMEntry *entries = hm_entries(item->data.map->data, &count);
                for (size_t i = 0; i < count; i++) {
                    xmap_put(copy->data.map, entries[i].key, seq_copy((Seq*)entries[i].value));
                }
                free(entries);
                return copy;
            }
    }
    return NULL;
}

/* Seq functions */
Seq* seq_new(void) {
    return (Seq*)calloc(1, sizeof(Seq));
}

void seq_free(Seq *seq) {
    if (!seq) return;
    for (size_t i = 0; i < seq->count; i++) {
        item_free(seq->items[i]);
    }
    free(seq->items);
    free(seq);
}

void seq_append(Seq *seq, Item *item) {
    if (!seq || !item) return;
    if (seq->count >= seq->capacity) {
        seq->capacity = seq->capacity ? seq->capacity * 2 : 4;
        seq->items = (Item**)realloc(seq->items, seq->capacity * sizeof(Item*));
    }
    seq->items[seq->count++] = item;
}

void seq_extend(Seq *seq, Seq *other) {
    if (!seq || !other) return;
    for (size_t i = 0; i < other->count; i++) {
        seq_append(seq, item_copy(other->items[i]));
    }
}

Seq* seq_copy(Seq *seq) {
    if (!seq) return seq_new();
    Seq *copy = seq_new();
    for (size_t i = 0; i < seq->count; i++) {
        seq_append(copy, item_copy(seq->items[i]));
    }
    return copy;
}

Item* seq_first(Seq *seq) {
    if (!seq || seq->count == 0) return NULL;
    return seq->items[0];
}

/* XMap functions */
XMap* xmap_new(void) {
    XMap *map = (XMap*)malloc(sizeof(XMap));
    map->data = hm_new();
    return map;
}

void xmap_free(XMap *map) {
    if (!map) return;
    /* Free all sequences in map */
    size_t count;
    HMEntry *entries = hm_entries(map->data, &count);
    for (size_t i = 0; i < count; i++) {
        seq_free((Seq*)entries[i].value);
    }
    free(entries);
    hm_free(map->data);
    free(map);
}

void xmap_put(XMap *map, const char *key, Seq *value) {
    if (!map || !key) return;
    hm_set(map->data, key, value);
}

Seq* xmap_get(XMap *map, const char *key) {
    if (!map || !key) return NULL;
    return (Seq*)hm_get(map->data, key);
}

/* Context functions */
Context* ctx_new(XmlNode *root) {
    Context *ctx = (Context*)calloc(1, sizeof(Context));
    ctx->root = node_ref(root);
    ctx->variables = hm_new();
    ctx->functions = hm_new();
    ctx->rules = hm_new();
    return ctx;
}

void ctx_free(Context *ctx) {
    if (!ctx) return;
    node_unref(ctx->root);
    if (ctx->context_item) item_free(ctx->context_item);
    
    if (ctx->variables) hm_free_with_values(ctx->variables, (void(*)(void*))seq_free);
    if (ctx->functions) hm_free(ctx->functions);  /* FunctionDefs owned by Module */
    
    /* Rules */
    if (ctx->rules) {
        size_t count;
        HMEntry *entries = hm_entries(ctx->rules, &count);
        for (size_t i = 0; i < count; i++) {
            free(entries[i].value);  /* Just the array, RuleDefs owned by Module */
        }
        free(entries);
        hm_free(ctx->rules);
    }
    
    free(ctx);
}

Context* ctx_with_item(Context *ctx, Item *item) {
    Context *new_ctx = (Context*)malloc(sizeof(Context));
    *new_ctx = *ctx;
    new_ctx->context_item = item_copy(item);
    new_ctx->variables = ctx->variables;
    new_ctx->functions = ctx->functions;
    new_ctx->rules = ctx->rules;
    return new_ctx;
}

Context* ctx_with_vars(Context *ctx, HashMap *vars) {
    Context *new_ctx = (Context*)malloc(sizeof(Context));
    *new_ctx = *ctx;
    new_ctx->context_item = ctx->context_item ? item_copy(ctx->context_item) : NULL;
    new_ctx->variables = vars;
    new_ctx->functions = ctx->functions;
    new_ctx->rules = ctx->rules;
    return new_ctx;
}

/* Coercions */
bool to_boolean(Seq *seq) {
    if (!seq || seq->count == 0) return false;
    
    for (size_t i = 0; i < seq->count; i++) {
        if (seq->items[i]->kind == ITEM_NODE) return true;
    }
    
    for (size_t i = 0; i < seq->count; i++) {
        Item *item = seq->items[i];
        switch (item->kind) {
            case ITEM_BOOL:
                if (item->data.bool_val) return true;
                break;
            case ITEM_NUM:
                if (item->data.num != 0.0) return true;
                break;
            case ITEM_STR:
                if (item->data.str && strlen(item->data.str) > 0) return true;
                break;
            case ITEM_NULL:
                break;
            case ITEM_MAP:
            case ITEM_FUNC_REF:
            case ITEM_NODE:
                return true;
        }
    }
    return false;
}

double to_number(Seq *seq, int *error) {
    if (error) *error = 0;
    if (!seq || seq->count == 0) return 0.0;
    
    Item *item = seq->items[0];
    switch (item->kind) {
        case ITEM_NUM:
            return item->data.num;
        case ITEM_BOOL:
            return item->data.bool_val ? 1.0 : 0.0;
        case ITEM_STR: {
            char *endptr;
            double val = strtod(item->data.str, &endptr);
            if (endptr == item->data.str || *endptr != '\0') {
                if (error) *error = 1;
                return 0.0;
            }
            return val;
        }
        case ITEM_NODE: {
            char *sv = node_string_value(item->data.node);
            char *endptr;
            double val = strtod(sv, &endptr);
            int err = (endptr == sv || *endptr != '\0');
            free(sv);
            if (error) *error = err;
            return err ? 0.0 : val;
        }
        case ITEM_NULL:
            return 0.0;
        default:
            if (error) *error = 1;
            return 0.0;
    }
}

char* to_string(Seq *seq) {
    if (!seq || seq->count == 0) return strdup("");
    
    Item *item = seq->items[0];
    switch (item->kind) {
        case ITEM_STR:
            return strdup(item->data.str);
        case ITEM_NUM: {
            char buf[64];
            if (item->data.num == floor(item->data.num) && 
                fabs(item->data.num) < 1e15) {
                snprintf(buf, sizeof(buf), "%.0f", item->data.num);
            } else {
                snprintf(buf, sizeof(buf), "%g", item->data.num);
            }
            return strdup(buf);
        }
        case ITEM_BOOL:
            return strdup(item->data.bool_val ? "true" : "false");
        case ITEM_NULL:
            return strdup("");
        case ITEM_NODE:
            return node_string_value(item->data.node);
        case ITEM_MAP:
            return strdup("[map]");
        case ITEM_FUNC_REF:
            return strdup(item->data.func_ref);
    }
    return strdup("");
}

bool value_equal(Seq *a, Seq *b) {
    char *sa = to_string(a);
    char *sb = to_string(b);
    bool result = strcmp(sa, sb) == 0;
    free(sa);
    free(sb);
    return result;
}

/* Helper for literal to item */
static Item* lit_to_item(LiteralValue *lit) {
    switch (lit->kind) {
        case LIT_STR:
            return item_new_str(lit->value.str);
        case LIT_NUM:
            return item_new_num(lit->value.num);
        case LIT_BOOL:
            return item_new_bool(lit->value.boolean);
        case LIT_NULL:
            return item_new_null();
    }
    return item_new_null();
}

/* Forward declarations */
static Seq* call_builtin(const char *name, Seq **args, size_t arg_count, Context *ctx);
static char* sort_item_key(Item *item, const char *key_func_name, Context *ctx);

Seq* eval_expr(Expr *expr, Context *ctx);

static char* sort_item_key(Item *item, const char *key_func_name, Context *ctx) {
    Seq *temp = seq_new();
    seq_append(temp, item_copy(item));
    char *key = NULL;
    if (key_func_name && *key_func_name) {
        Seq *fn_args[1] = { temp };
        Seq *key_seq = call_function(key_func_name, fn_args, 1, ctx);
        key = to_string(key_seq);
        seq_free(key_seq);
    } else {
        key = to_string(temp);
    }
    seq_free(temp);
    return key;
}

static Seq* eval_binary_op(const char *op, Seq *left, Seq *right) {
    Seq *result = seq_new();
    
    if (strcmp(op, "=") == 0) {
        seq_append(result, item_new_bool(value_equal(left, right)));
    } else if (strcmp(op, "!=") == 0) {
        seq_append(result, item_new_bool(!value_equal(left, right)));
    } else if (strcmp(op, "+") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_num(l + r));
    } else if (strcmp(op, "-") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_num(l - r));
    } else if (strcmp(op, "*") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_num(l * r));
    } else if (strcmp(op, "div") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_num(l / r));
    } else if (strcmp(op, "mod") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_num(fmod(l, r)));
    } else if (strcmp(op, "<") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_bool(l < r));
    } else if (strcmp(op, "<=") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_bool(l <= r));
    } else if (strcmp(op, ">") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_bool(l > r));
    } else if (strcmp(op, ">=") == 0) {
        int err;
        double l = to_number(left, &err);
        double r = to_number(right, &err);
        seq_append(result, item_new_bool(l >= r));
    } else if (strcmp(op, "and") == 0) {
        seq_append(result, item_new_bool(to_boolean(left) && to_boolean(right)));
    } else if (strcmp(op, "or") == 0) {
        seq_append(result, item_new_bool(to_boolean(left) || to_boolean(right)));
    }
    
    return result;
}

Seq* eval_path(PathExpr *pe, Context *ctx);

Seq* eval_expr(Expr *expr, Context *ctx) {
    if (!expr) return seq_new();
    
    switch (expr->kind) {
        case EXPR_LITERAL: {
            Seq *s = seq_new();
            seq_append(s, lit_to_item(&expr->data.literal));
            return s;
        }
        
        case EXPR_VAR_REF: {
            /* Check variables first */
            Seq *val = (Seq*)hm_get(ctx->variables, expr->data.var_ref);
            if (val) {
                return seq_copy(val);
            }
            
            /* Check functions */
            if (hm_contains(ctx->functions, expr->data.var_ref)) {
                Seq *s = seq_new();
                seq_append(s, item_new_func_ref(expr->data.var_ref));
                return s;
            }
            
            /* Fallback to child axis from context */
            if (ctx->context_item && ctx->context_item->kind == ITEM_NODE) {
                XmlNode *node = ctx->context_item->data.node;
                if (node->kind == NODE_ELEMENT || node->kind == NODE_DOCUMENT) {
                    Seq *s = seq_new();
                    for (size_t i = 0; i < node->child_count; i++) {
                        XmlNode *child = node->children[i];
                        if (child->kind == NODE_ELEMENT &&
                            child->name &&
                            strcmp(child->name, expr->data.var_ref) == 0) {
                            seq_append(s, item_new_node(child));
                        }
                    }
                    return s;
                }
            }
            return seq_new();
        }
        
        case EXPR_IF: {
            Seq *cond = eval_expr(expr->data.if_expr->cond, ctx);
            bool b = to_boolean(cond);
            seq_free(cond);
            if (b) {
                return eval_expr(expr->data.if_expr->then_expr, ctx);
            } else {
                return eval_expr(expr->data.if_expr->else_expr, ctx);
            }
        }
        
        case EXPR_LET: {
            Seq *val = eval_expr(expr->data.let_expr->value, ctx);
            HashMap *vars = hm_new();
            /* Copy existing vars */
            size_t count;
            HMEntry *entries = hm_entries(ctx->variables, &count);
            for (size_t i = 0; i < count; i++) {
                hm_set(vars, entries[i].key, entries[i].value);
            }
            free(entries);
            hm_set(vars, expr->data.let_expr->name, val);
            Context *new_ctx = ctx_with_vars(ctx, vars);
            Seq *result = eval_expr(expr->data.let_expr->body, new_ctx);
            /* Don't free vars or val, they may be used elsewhere */
            free(new_ctx);
            return result;
        }
        
        case EXPR_FOR: {
            Seq *seq = eval_expr(expr->data.for_expr->seq, ctx);
            Seq *result = seq_new();
            
            for (size_t i = 0; i < seq->count; i++) {
                HashMap *vars = hm_new();
                /* Copy existing vars */
                size_t count;
                HMEntry *entries = hm_entries(ctx->variables, &count);
                for (size_t j = 0; j < count; j++) {
                    hm_set(vars, entries[j].key, entries[j].value);
                }
                free(entries);
                
                Seq *item_seq = seq_new();
                seq_append(item_seq, item_copy(seq->items[i]));
                hm_set(vars, expr->data.for_expr->name, item_seq);
                
                Context *new_ctx = ctx_with_vars(ctx, vars);
                new_ctx->context_item = seq->items[i];
                new_ctx->position = (double)(i + 1);
                new_ctx->last = (double)seq->count;
                new_ctx->has_position = 1;
                new_ctx->has_last = 1;
                
                /* Check where clause */
                if (expr->data.for_expr->where_clause) {
                    Seq *w = eval_expr(expr->data.for_expr->where_clause, new_ctx);
                    bool ok = to_boolean(w);
                    seq_free(w);
                    if (!ok) {
                        free(new_ctx);
                        continue;
                    }
                }
                
                Seq *body_result = eval_expr(expr->data.for_expr->body, new_ctx);
                seq_extend(result, body_result);
                seq_free(body_result);
                
                free(new_ctx);
            }
            
            seq_free(seq);
            return result;
        }
        
        case EXPR_MATCH: {
            Seq *target = eval_expr(expr->data.match_expr->target, ctx);
            Seq *result = seq_new();
            
            for (size_t i = 0; i < target->count; i++) {
                Item *item = target->items[i];
                int matched = 0;
                
                for (size_t j = 0; j < expr->data.match_expr->case_count; j++) {
                    HashMap *bindings = match_pattern(expr->data.match_expr->patterns[j], item);
                    if (bindings) {
                        matched = 1;
                        HashMap *vars = hm_new();
                        /* Copy existing vars */
                        size_t count;
                        HMEntry *entries = hm_entries(ctx->variables, &count);
                        for (size_t k = 0; k < count; k++) {
                            hm_set(vars, entries[k].key, entries[k].value);
                        }
                        free(entries);
                        /* Add bindings */
                        entries = hm_entries(bindings, &count);
                        for (size_t k = 0; k < count; k++) {
                            hm_set(vars, entries[k].key, entries[k].value);
                        }
                        free(entries);
                        hm_free(bindings);
                        
                        Context *new_ctx = ctx_with_vars(ctx, vars);
                        new_ctx->context_item = item;
                        Seq *case_result = eval_expr(expr->data.match_expr->exprs[j], new_ctx);
                        seq_extend(result, case_result);
                        seq_free(case_result);
                        free(new_ctx);
                        break;
                    }
                }
                
                if (!matched) {
                    if (expr->data.match_expr->default_expr) {
                        Context *new_ctx = ctx_with_item(ctx, item);
                        Seq *def_result = eval_expr(expr->data.match_expr->default_expr, new_ctx);
                        seq_extend(result, def_result);
                        seq_free(def_result);
                        item_free(new_ctx->context_item);
                        free(new_ctx);
                    } else {
                        /* Error - no matching case */
                        fprintf(stderr, "XFDY0001: no matching case\n");
                    }
                }
            }
            
            seq_free(target);
            return result;
        }
        
        case EXPR_FUNC_CALL: {
            Seq **args = (Seq**)malloc(expr->data.func_call->arg_count * sizeof(Seq*));
            for (size_t i = 0; i < expr->data.func_call->arg_count; i++) {
                args[i] = eval_expr(expr->data.func_call->args[i], ctx);
            }
            Seq *result = call_function(expr->data.func_call->name, args, 
                                        expr->data.func_call->arg_count, ctx);
            for (size_t i = 0; i < expr->data.func_call->arg_count; i++) {
                seq_free(args[i]);
            }
            free(args);
            return result;
        }
        
        case EXPR_UNARY_OP: {
            Seq *val = eval_expr(expr->data.unary_op->expr, ctx);
            Seq *result = seq_new();
            
            if (strcmp(expr->data.unary_op->op, "-") == 0) {
                int err;
                double n = to_number(val, &err);
                seq_append(result, item_new_num(-n));
            } else if (strcmp(expr->data.unary_op->op, "not") == 0) {
                seq_append(result, item_new_bool(!to_boolean(val)));
            }
            
            seq_free(val);
            return result;
        }
        
        case EXPR_BINARY_OP: {
            /* Short-circuit for and/or */
            if (strcmp(expr->data.binary_op->op, "and") == 0) {
                Seq *left = eval_expr(expr->data.binary_op->left, ctx);
                if (!to_boolean(left)) {
                    seq_free(left);
                    Seq *r = seq_new();
                    seq_append(r, item_new_bool(0));
                    return r;
                }
                seq_free(left);
                Seq *right = eval_expr(expr->data.binary_op->right, ctx);
                Seq *r = seq_new();
                seq_append(r, item_new_bool(to_boolean(right)));
                seq_free(right);
                return r;
            }
            
            if (strcmp(expr->data.binary_op->op, "or") == 0) {
                Seq *left = eval_expr(expr->data.binary_op->left, ctx);
                if (to_boolean(left)) {
                    seq_free(left);
                    Seq *r = seq_new();
                    seq_append(r, item_new_bool(1));
                    return r;
                }
                seq_free(left);
                Seq *right = eval_expr(expr->data.binary_op->right, ctx);
                Seq *r = seq_new();
                seq_append(r, item_new_bool(to_boolean(right)));
                seq_free(right);
                return r;
            }
            
            Seq *left = eval_expr(expr->data.binary_op->left, ctx);
            Seq *right = eval_expr(expr->data.binary_op->right, ctx);
            Seq *result = eval_binary_op(expr->data.binary_op->op, left, right);
            seq_free(left);
            seq_free(right);
            return result;
        }
        
        case EXPR_PATH:
            return eval_path(expr->data.path, ctx);
        
        case EXPR_CONSTRUCTOR: {
            XmlNode *node = eval_constructor(expr->data.constructor, ctx);
            Seq *s = seq_new();
            seq_append(s, item_new_node(node));
            node_unref(node);
            return s;
        }
        
        case EXPR_TEXT_CONSTRUCTOR: {
            Seq *val = eval_expr(expr->data.text_constructor, ctx);
            char *str = to_string(val);
            XmlNode *node = node_new_text(str);
            Seq *s = seq_new();
            seq_append(s, item_new_node(node));
            node_unref(node);
            free(str);
            seq_free(val);
            return s;
        }
        
        case EXPR_CHAR_DATA: {
            Seq *s = seq_new();
            seq_append(s, item_new_str(expr->data.char_data));
            return s;
        }
        
        case EXPR_INTERP:
            return eval_expr(expr->data.interp, ctx);
    }
    
    return seq_new();
}

/* Path evaluation */
Seq* eval_path(PathExpr *pe, Context *ctx) {
    Seq *base = seq_new();
    
    /* Determine base sequence */
    switch (pe->start.kind) {
        case PS_CONTEXT:
            if (ctx->context_item) {
                seq_append(base, item_copy(ctx->context_item));
            }
            break;
        case PS_ROOT:
            seq_append(base, item_new_node(ctx->root));
            break;
        case PS_DESC:
            if (ctx->context_item) {
                seq_append(base, item_copy(ctx->context_item));
            }
            break;
        case PS_DESC_ROOT:
            seq_append(base, item_new_node(ctx->root));
            break;
        case PS_VAR: {
            Seq *val = (Seq*)hm_get(ctx->variables, pe->start.name);
            if (val) {
                seq_extend(base, val);
            } else {
                /* Treat as child axis from context */
                if (ctx->context_item && ctx->context_item->kind == ITEM_NODE) {
                    XmlNode *node = ctx->context_item->data.node;
                    for (size_t i = 0; i < node->child_count; i++) {
                        XmlNode *child = node->children[i];
                        if (child->kind == NODE_ELEMENT &&
                            child->name &&
                            strcmp(child->name, pe->start.name) == 0) {
                            seq_append(base, item_new_node(child));
                        }
                    }
                }
            }
            break;
        }
    }
    
    /* Apply steps */
    for (size_t i = 0; i < pe->step_count; i++) {
        Seq *next = apply_step(base, &pe->steps[i], ctx);
        seq_free(base);
        base = next;
    }
    
    return base;
}

bool matches_test(XmlNode *node, StepTest *test) {
    switch (test->kind) {
        case TEST_NODE:
            return true;
        case TEST_WILDCARD:
            return node->kind == NODE_ELEMENT;
        case TEST_TEXT:
            return node->kind == NODE_TEXT;
        case TEST_COMMENT:
            return node->kind == NODE_COMMENT;
        case TEST_PI:
            return node->kind == NODE_PI;
        case TEST_NAME:
            return (node->kind == NODE_ELEMENT || node->kind == NODE_ATTRIBUTE) &&
                   node->name &&
                   strcmp(node->name, test->name) == 0;
    }
    return false;
}

Seq* apply_step(Seq *items, PathStep *step, Context *ctx) {
    Seq *result = seq_new();
    
    for (size_t i = 0; i < items->count; i++) {
        Item *item = items->items[i];
        if (item->kind != ITEM_NODE) continue;
        
        XmlNode *node = item->data.node;
        XmlNode **candidates = NULL;
        size_t candidate_count = 0;
        
        switch (step->axis) {
            case AXIS_SELF:
                candidates = (XmlNode**)malloc(sizeof(XmlNode*));
                candidates[0] = node_ref(node);
                candidate_count = 1;
                break;
                
            case AXIS_PARENT:
                /* Not implemented - no parent tracking */
                break;
                
            case AXIS_DESC_OR_SELF:
                candidates = (XmlNode**)malloc((node->child_count + 1) * sizeof(XmlNode*));
                candidates[0] = node_ref(node);
                candidate_count = 1;
                for (size_t j = 0; j < node->child_count; j++) {
                    candidates[candidate_count++] = node_ref(node->children[j]);
                    size_t subcount;
                    XmlNode **sub = node_descendants(node->children[j], &subcount);
                    candidates = (XmlNode**)realloc(candidates, 
                        (candidate_count + subcount) * sizeof(XmlNode*));
                    for (size_t k = 0; k < subcount; k++) {
                        candidates[candidate_count++] = sub[k];
                    }
                    free(sub);
                }
                break;
                
            case AXIS_DESC:
                candidates = node_descendants(node, &candidate_count);
                break;
                
            case AXIS_ATTR:
                if (node->kind == NODE_ELEMENT) {
                    if (step->test.kind == TEST_WILDCARD) {
                        candidates = (XmlNode**)malloc(node->attr_count * sizeof(XmlNode*));
                        for (size_t j = 0; j < node->attr_count; j++) {
                            candidates[j] = node_new_attribute(
                                node->attrs[j].name, 
                                node->attrs[j].value);
                        }
                        candidate_count = node->attr_count;
                    } else if (step->test.kind == TEST_NAME) {
                        candidates = (XmlNode**)malloc(sizeof(XmlNode*));
                        for (size_t j = 0; j < node->attr_count; j++) {
                            if (strcmp(node->attrs[j].name, step->test.name) == 0) {
                                candidates[0] = node_new_attribute(
                                    node->attrs[j].name,
                                    node->attrs[j].value);
                                candidate_count = 1;
                                break;
                            }
                        }
                    }
                }
                break;
                
            case AXIS_CHILD:
                if (node->kind == NODE_ELEMENT || node->kind == NODE_DOCUMENT) {
                    candidates = (XmlNode**)malloc(node->child_count * sizeof(XmlNode*));
                    for (size_t j = 0; j < node->child_count; j++) {
                        candidates[j] = node_ref(node->children[j]);
                    }
                    candidate_count = node->child_count;
                }
                break;
        }
        
        /* Filter by test and predicates */
        for (size_t j = 0; j < candidate_count; j++) {
            XmlNode *cand = candidates[j];
            if (!matches_test(cand, &step->test)) {
                node_unref(cand);
                continue;
            }
            
            /* Apply predicates */
            int ok = 1;
            Context *pred_ctx = ctx_with_item(ctx, item_new_node(cand));
            
            for (size_t k = 0; k < step->predicate_count && ok; k++) {
                Seq *pred_result = eval_expr(step->predicates[k], pred_ctx);
                if (!to_boolean(pred_result)) {
                    ok = 0;
                }
                seq_free(pred_result);
            }
            
            item_free(pred_ctx->context_item);
            free(pred_ctx);
            
            if (ok) {
                seq_append(result, item_new_node(cand));
            }
            node_unref(cand);
        }
        
        free(candidates);
    }
    
    return result;
}

XmlNode* eval_constructor(Constructor *c, Context *ctx) {
    XmlNode *elem = node_new_element(c->name);
    
    /* Evaluate attributes */
    for (size_t i = 0; i < c->attr_count; i++) {
        Seq *val = eval_expr(c->attrs[i].expr, ctx);
        char *str = to_string(val);
        node_add_attr(elem, c->attrs[i].name, str);
        free(str);
        seq_free(val);
    }
    
    /* Evaluate contents */
    for (size_t i = 0; i < c->content_count; i++) {
        Expr *content = c->contents[i];
        
        if (content->kind == EXPR_CHAR_DATA) {
            char *text = content->data.char_data;
            /* Trim and add if not empty */
            char *start = text;
            while (isspace((unsigned char)*start)) start++;
            char *end = text + strlen(text) - 1;
            while (end > start && isspace((unsigned char)*end)) end--;
            if (end >= start) {
                size_t len = end - start + 1;
                char *trimmed = (char*)malloc(len + 1);
                memcpy(trimmed, start, len);
                trimmed[len] = '\0';
                if (strlen(trimmed) > 0) {
                    XmlNode *text_node = node_new_text(trimmed);
                    node_add_child(elem, text_node);
                    node_unref(text_node);
                }
                free(trimmed);
            }
        } else {
            Seq *val = eval_expr(content, ctx);
            for (size_t j = 0; j < val->count; j++) {
                Item *item = val->items[j];
                if (item->kind == ITEM_NODE) {
                    XmlNode *copy = node_deep_copy(item->data.node);
                    node_add_child(elem, copy);
                    node_unref(copy);
                } else {
                    char *str = to_string(seq_new());
                    /* Create a temp seq for this item */
                    Seq *temp = seq_new();
                    seq_append(temp, item_copy(item));
                    char *str2 = to_string(temp);
                    seq_free(temp);
                    XmlNode *text_node = node_new_text(str2);
                    node_add_child(elem, text_node);
                    node_unref(text_node);
                    free(str2);
                    free(str);
                }
            }
            seq_free(val);
        }
    }
    
    return elem;
}

/* Pattern matching */
HashMap* match_pattern(Pattern *pat, Item *item) {
    switch (pat->kind) {
        case PAT_WILDCARD:
            return hm_new();
            
        case PAT_ATTRIBUTE: {
            if (item->kind == ITEM_NODE && 
                item->data.node->kind == NODE_ATTRIBUTE &&
                item->data.node->name &&
                strcmp(item->data.node->name, pat->data.attribute) == 0) {
                return hm_new();
            }
            return NULL;
        }
        
        case PAT_TYPED: {
            if (item->kind != ITEM_NODE) return NULL;
            XmlNode *node = item->data.node;
            int matches = 0;
            
            if (strcmp(pat->data.typed, "node") == 0) {
                matches = 1;
            } else if (strcmp(pat->data.typed, "text") == 0) {
                matches = (node->kind == NODE_TEXT);
            } else if (strcmp(pat->data.typed, "comment") == 0) {
                matches = (node->kind == NODE_COMMENT);
            }
            
            if (matches) return hm_new();
            return NULL;
        }
        
        case PAT_ELEMENT: {
            if (item->kind != ITEM_NODE || 
                item->data.node->kind != NODE_ELEMENT) {
                return NULL;
            }
            
            XmlNode *node = item->data.node;
            if (!node->name || strcmp(node->name, pat->data.element->name) != 0) {
                return NULL;
            }
            
            HashMap *bindings = hm_new();
            
            if (pat->data.element->var) {
                Seq *seq = seq_new();
                for (size_t i = 0; i < node->child_count; i++) {
                    seq_append(seq, item_new_node(node->children[i]));
                }
                hm_set(bindings, pat->data.element->var, seq);
                return bindings;
            }
            
            if (pat->data.element->child) {
                for (size_t i = 0; i < node->child_count; i++) {
                    Item *child = item_new_node(node->children[i]);
                    HashMap *child_bindings = match_pattern(pat->data.element->child, child);
                    item_free(child);
                    if (child_bindings) {
                        /* Merge bindings */
                        size_t count;
                        HMEntry *entries = hm_entries(child_bindings, &count);
                        for (size_t j = 0; j < count; j++) {
                            hm_set(bindings, entries[j].key, entries[j].value);
                        }
                        free(entries);
                        hm_free(child_bindings);
                        return bindings;
                    }
                }
                hm_free(bindings);
                return NULL;
            }
            
            return bindings;
        }
    }
    
    return NULL;
}

/* Function calling */
Seq* call_function(const char *name, Seq **args, size_t arg_count, Context *ctx) {
    /* Check user-defined functions */
    FunctionDef *fd = (FunctionDef*)hm_get(ctx->functions, name);
    if (fd) {
        HashMap *vars = hm_new();
        /* Copy existing vars */
        size_t count;
        HMEntry *entries = hm_entries(ctx->variables, &count);
        for (size_t i = 0; i < count; i++) {
            hm_set(vars, entries[i].key, entries[i].value);
        }
        free(entries);
        
        /* Bind parameters */
        for (size_t i = 0; i < fd->param_count; i++) {
            if (i < arg_count) {
                hm_set(vars, fd->params[i].name, seq_copy(args[i]));
            } else if (fd->params[i].default_value) {
                Seq *def = eval_expr(fd->params[i].default_value, ctx);
                hm_set(vars, fd->params[i].name, def);
            }
        }
        
        Context *new_ctx = ctx_with_vars(ctx, vars);
        Seq *result = eval_expr(fd->body, new_ctx);
        free(new_ctx);
        /* Don't free vars, it may contain refs to shared sequences */
        hm_free(vars);
        return result;
    }
    
    return call_builtin(name, args, arg_count, ctx);
}

static Seq* call_builtin(const char *name, Seq **args, size_t arg_count, Context *ctx) {
    Seq *result = seq_new();
    
    if (strcmp(name, "string") == 0) {
        char *s = arg_count > 0 ? to_string(args[0]) : strdup("");
        seq_append(result, item_new_str(s));
        free(s);
    } else if (strcmp(name, "number") == 0) {
        int err;
        double n = arg_count > 0 ? to_number(args[0], &err) : 0.0;
        seq_append(result, item_new_num(n));
    } else if (strcmp(name, "boolean") == 0) {
        int b = arg_count > 0 ? to_boolean(args[0]) : 0;
        seq_append(result, item_new_bool(b));
    } else if (strcmp(name, "typeOf") == 0) {
        const char *t = "null";
        if (arg_count > 0 && args[0]->count > 0) {
            switch (args[0]->items[0]->kind) {
                case ITEM_NODE: t = "node"; break;
                case ITEM_MAP: t = "map"; break;
                case ITEM_BOOL: t = "boolean"; break;
                case ITEM_NUM: t = "number"; break;
                case ITEM_NULL: t = "null"; break;
                case ITEM_STR: t = "string"; break;
                case ITEM_FUNC_REF: t = "function"; break;
            }
        }
        seq_append(result, item_new_str(t));
    } else if (strcmp(name, "name") == 0) {
        char *s = "";
        if (arg_count > 0 && args[0]->count > 0 && 
            args[0]->items[0]->kind == ITEM_NODE) {
            XmlNode *n = args[0]->items[0]->data.node;
            if (n->name) s = n->name;
        }
        seq_append(result, item_new_str(s));
    } else if (strcmp(name, "attr") == 0) {
        char *val = "";
        if (arg_count >= 2 && args[0]->count > 0 && 
            args[0]->items[0]->kind == ITEM_NODE) {
            XmlNode *n = args[0]->items[0]->data.node;
            char *key = to_string(args[1]);
            if (n->kind == NODE_ELEMENT) {
                for (size_t i = 0; i < n->attr_count; i++) {
                    if (strcmp(n->attrs[i].name, key) == 0) {
                        val = n->attrs[i].value;
                        break;
                    }
                }
            }
            free(key);
        }
        seq_append(result, item_new_str(val));
    } else if (strcmp(name, "text") == 0) {
        if (arg_count > 0 && args[0]->count > 0) {
            Item *item = args[0]->items[0];
            if (item->kind == ITEM_NODE) {
                char *s = node_string_value(item->data.node);
                seq_append(result, item_new_str(s));
                free(s);
            } else {
                char *s = to_string(args[0]);
                seq_append(result, item_new_str(s));
                free(s);
            }
        } else {
            seq_append(result, item_new_str(""));
        }
    } else if (strcmp(name, "children") == 0) {
        if (arg_count > 0 && args[0]->count > 0 &&
            args[0]->items[0]->kind == ITEM_NODE) {
            XmlNode *n = args[0]->items[0]->data.node;
            for (size_t i = 0; i < n->child_count; i++) {
                seq_append(result, item_new_node(n->children[i]));
            }
        }
    } else if (strcmp(name, "elements") == 0) {
        char *filter = NULL;
        if (arg_count > 1) {
            filter = to_string(args[1]);
        }
        if (arg_count > 0 && args[0]->count > 0 &&
            args[0]->items[0]->kind == ITEM_NODE) {
            XmlNode *n = args[0]->items[0]->data.node;
            if (n->kind == NODE_ELEMENT || n->kind == NODE_DOCUMENT) {
                for (size_t i = 0; i < n->child_count; i++) {
                    XmlNode *child = n->children[i];
                    if (child->kind == NODE_ELEMENT) {
                        if (!filter || strlen(filter) == 0 ||
                            (child->name && strcmp(child->name, filter) == 0)) {
                            seq_append(result, item_new_node(child));
                        }
                    }
                }
            }
        }
        free(filter);
    } else if (strcmp(name, "copy") == 0) {
        if (arg_count > 0 && args[0]->count > 0 &&
            args[0]->items[0]->kind == ITEM_NODE) {
            XmlNode *copy = node_deep_copy(args[0]->items[0]->data.node);
            seq_append(result, item_new_node(copy));
            node_unref(copy);
        }
    } else if (strcmp(name, "count") == 0) {
        size_t n = (arg_count > 0) ? args[0]->count : 0;
        seq_append(result, item_new_num((double)n));
    } else if (strcmp(name, "empty") == 0) {
        int e = (arg_count == 0 || args[0]->count == 0);
        seq_append(result, item_new_bool(e));
    } else if (strcmp(name, "head") == 0) {
        if (arg_count > 0 && args[0]->count > 0) {
            seq_append(result, item_copy(args[0]->items[0]));
        }
    } else if (strcmp(name, "tail") == 0) {
        if (arg_count > 0 && args[0]->count > 1) {
            for (size_t i = 1; i < args[0]->count; i++) {
                seq_append(result, item_copy(args[0]->items[i]));
            }
        }
    } else if (strcmp(name, "last") == 0) {
        if (arg_count > 0 && args[0]->count > 0) {
            seq_append(result, item_copy(args[0]->items[args[0]->count - 1]));
        } else if (ctx->has_last) {
            seq_append(result, item_new_num(ctx->last));
        }
    } else if (strcmp(name, "position") == 0) {
        if (ctx->has_position) {
            seq_append(result, item_new_num(ctx->position));
        }
    } else if (strcmp(name, "sum") == 0) {
        double total = 0.0;
        if (arg_count > 0) {
            for (size_t i = 0; i < args[0]->count; i++) {
                Seq *temp = seq_new();
                seq_append(temp, item_copy(args[0]->items[i]));
                int err;
                total += to_number(temp, &err);
                seq_free(temp);
            }
        }
        seq_append(result, item_new_num(total));
    } else if (strcmp(name, "concat") == 0 || strcmp(name, "seq") == 0) {
        for (size_t i = 0; i < arg_count; i++) {
            seq_extend(result, args[i]);
        }
    } else if (strcmp(name, "lookup") == 0) {
        if (arg_count >= 2 && args[0]->count > 0 && args[0]->items[0]->kind == ITEM_MAP) {
            char *key = to_string(args[1]);
            Seq *val = xmap_get(args[0]->items[0]->data.map, key);
            if (val) {
                seq_extend(result, val);
            }
            free(key);
        }
    } else if (strcmp(name, "groupBy") == 0) {
        const char *key_func = NULL;
        if (arg_count >= 2 && args[1]->count > 0 && args[1]->items[0]->kind == ITEM_FUNC_REF) {
            key_func = args[1]->items[0]->data.func_ref;
        }
        if (arg_count > 0) {
            HashMap *groups = hm_new(); /* key -> Item*(map) */
            for (size_t i = 0; i < args[0]->count; i++) {
                Item *input_item = args[0]->items[i];
                char *key = sort_item_key(input_item, key_func, ctx);
                Item *group_item = (Item*)hm_get(groups, key);
                if (!group_item) {
                    group_item = item_new(ITEM_MAP);
                    group_item->data.map = xmap_new();

                    Seq *key_seq = seq_new();
                    seq_append(key_seq, item_new_str(key));
                    xmap_put(group_item->data.map, "key", key_seq);

                    Seq *items_seq = seq_new();
                    xmap_put(group_item->data.map, "items", items_seq);

                    hm_set(groups, key, group_item);
                    seq_append(result, group_item);
                }

                Seq *items_seq = xmap_get(group_item->data.map, "items");
                if (items_seq) {
                    seq_append(items_seq, item_copy(input_item));
                }
                free(key);
            }
            hm_free(groups);
        }
    } else if (strcmp(name, "distinct") == 0) {
        if (arg_count > 0) {
            /* Use a simple hash set approach */
            HashMap *seen = hm_new();
            for (size_t i = 0; i < args[0]->count; i++) {
                Seq *temp = seq_new();
                seq_append(temp, item_copy(args[0]->items[i]));
                char *key = to_string(temp);
                seq_free(temp);
                if (!hm_contains(seen, key)) {
                    hm_set(seen, key, (void*)1);
                    seq_append(result, item_copy(args[0]->items[i]));
                }
                free(key);
            }
            hm_free(seen);
        }
    } else if (strcmp(name, "sort") == 0) {
        if (arg_count > 0) {
            const char *key_func = NULL;
            if (arg_count > 1 && args[1]->count > 0 &&
                args[1]->items[0]->kind == ITEM_FUNC_REF) {
                key_func = args[1]->items[0]->data.func_ref;
            }
            /* Simple insertion sort */
            Seq *input = seq_copy(args[0]);
            for (size_t i = 0; i < input->count; i++) {
                for (size_t j = i + 1; j < input->count; j++) {
                    char *si = sort_item_key(input->items[i], key_func, ctx);
                    char *sj = sort_item_key(input->items[j], key_func, ctx);
                    if (strcmp(si, sj) > 0) {
                        Item *tmp = input->items[i];
                        input->items[i] = input->items[j];
                        input->items[j] = tmp;
                    }
                    free(si);
                    free(sj);
                }
            }
            seq_extend(result, input);
            seq_free(input);
        }
    } else {
        fprintf(stderr, "XFST0003: unknown function %s\n", name);
    }
    
    return result;
}

/* Main entry point */
Seq* eval_module(Module *mod, XmlNode *doc) {
    Context *ctx = ctx_new(doc);
    ctx->context_item = item_new_node(doc);
    
    /* Copy module functions and rules */
    ctx->functions = mod->functions;
    ctx->rules = mod->rules;
    
    /* Evaluate module vars */
    size_t count;
    HMEntry *entries = hm_entries(mod->vars, &count);
    for (size_t i = 0; i < count; i++) {
        Seq *val = eval_expr((Expr*)entries[i].value, ctx);
        hm_set(ctx->variables, entries[i].key, val);
    }
    free(entries);
    
    Seq *result;
    if (mod->expr) {
        result = eval_expr(mod->expr, ctx);
    } else {
        result = seq_new();
    }
    
    /* Detach functions/rules from ctx so they don't get freed */
    ctx->functions = NULL;
    ctx->rules = NULL;
    
    ctx_free(ctx);
    return result;
}

char* serialize_items(Seq *items) {
    StringBuilder *sb = sb_new();
    
    for (size_t i = 0; i < items->count; i++) {
        Item *item = items->items[i];
        switch (item->kind) {
            case ITEM_NODE: {
                char *xml = serialize_xml(item->data.node);
                sb_append_str(sb, xml);
                free(xml);
                break;
            }
            case ITEM_STR:
                sb_append_str(sb, item->data.str);
                break;
            case ITEM_NUM: {
                char buf[64];
                if (item->data.num == floor(item->data.num) && 
                    fabs(item->data.num) < 1e15) {
                    snprintf(buf, sizeof(buf), "%.0f", item->data.num);
                } else {
                    snprintf(buf, sizeof(buf), "%g", item->data.num);
                }
                sb_append_str(sb, buf);
                break;
            }
            case ITEM_BOOL:
                sb_append_str(sb, item->data.bool_val ? "true" : "false");
                break;
            case ITEM_NULL:
                break;
            default:
                break;
        }
    }
    
    return sb_to_string(sb);
}
