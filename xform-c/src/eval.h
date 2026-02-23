#ifndef EVAL_H
#define EVAL_H

#include "ast.h"
#include "xmlmodel.h"
#include "hashmap.h"
#include "string_builder.h"

/* Item types */
typedef enum {
    ITEM_NODE,
    ITEM_STR,
    ITEM_NUM,
    ITEM_BOOL,
    ITEM_NULL,
    ITEM_MAP,
    ITEM_FUNC_REF
} ItemKind;

/* Forward declaration */
typedef struct Item Item;

typedef struct {
    HashMap *data;  /* string -> Item** (null-terminated array) */
} XMap;

struct Item {
    ItemKind kind;
    union {
        XmlNode *node;
        char *str;
        double num;
        int bool_val;
        XMap *map;
        char *func_ref;
    } data;
};

/* Sequence is an array of item pointers */
typedef struct {
    Item **items;
    size_t count;
    size_t capacity;
} Seq;

/* Context for evaluation */
typedef struct Context {
    Item *context_item;  /* NULL if none */
    XmlNode *root;
    HashMap *variables;  /* name -> Seq* */
    HashMap *functions;  /* name -> FunctionDef* */
    HashMap *rules;      /* name -> RuleDef** (null-terminated) */
    double position;     /* 0 if not set */
    double last;         /* 0 if not set */
    int has_position;
    int has_last;
} Context;

/* Item functions */
Item* item_new(ItemKind kind);
Item* item_new_node(XmlNode *node);
Item* item_new_str(const char *s);
Item* item_new_num(double n);
Item* item_new_bool(bool b);
Item* item_new_null(void);
Item* item_new_func_ref(const char *name);
void item_free(Item *item);
Item* item_copy(Item *item);

/* Seq functions */
Seq* seq_new(void);
void seq_free(Seq *seq);
void seq_append(Seq *seq, Item *item);  /* takes ownership */
void seq_extend(Seq *seq, Seq *other);  /* copies items */
Seq* seq_copy(Seq *seq);
Item* seq_first(Seq *seq);

/* XMap functions */
XMap* xmap_new(void);
void xmap_free(XMap *map);
void xmap_put(XMap *map, const char *key, Seq *value);
Seq* xmap_get(XMap *map, const char *key);

/* Context functions */
Context* ctx_new(XmlNode *root);
void ctx_free(Context *ctx);
Context* ctx_with_item(Context *ctx, Item *item);
Context* ctx_with_vars(Context *ctx, HashMap *vars);

/* Main evaluation functions */
Seq* eval_module(Module *mod, XmlNode *doc);
Seq* eval_expr(Expr *expr, Context *ctx);

/* Specific evaluators */
Seq* eval_path(PathExpr *pe, Context *ctx);
Seq* apply_step(Seq *items, PathStep *step, Context *ctx);
XmlNode* eval_constructor(Constructor *c, Context *ctx);
bool matches_test(XmlNode *node, StepTest *test);

/* Pattern matching - returns new hashmap of bindings or NULL */
HashMap* match_pattern(Pattern *pat, Item *item);

/* Coercions */
bool to_boolean(Seq *seq);
double to_number(Seq *seq, int *error);
char* to_string(Seq *seq);  /* caller frees */
bool value_equal(Seq *a, Seq *b);

/* Built-in functions */
Seq* call_function(const char *name, Seq **args, size_t arg_count, Context *ctx);

/* Serialization */
char* serialize_items(Seq *items);  /* caller frees */

#endif
