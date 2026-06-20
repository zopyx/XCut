#ifndef AST_H
#define AST_H

#include <stddef.h>
#include <stdbool.h>

/* Forward declarations */
struct Expr;
struct Pattern;

/* Literal values */
typedef enum {
    LIT_STR,
    LIT_NUM,
    LIT_BOOL,
    LIT_NULL
} LiteralKind;

typedef struct {
    LiteralKind kind;
    union {
        char *str;
        double num;
        bool boolean;
    } value;
} LiteralValue;

/* Parameter definition */
typedef struct {
    char *name;
    char *type_ref;
    struct Expr *default_value;
} Param;

/* Function definition */
typedef struct {
    Param *params;
    size_t param_count;
    struct Expr *body;
} FunctionDef;

/* Rule definition */
typedef struct {
    struct Pattern *pattern;
    struct Expr *body;
} RuleDef;

/* Path start kinds */
typedef enum {
    PS_CONTEXT,
    PS_ROOT,
    PS_DESC,
    PS_DESC_ROOT,
    PS_VAR,
    PS_ATTR
} PathStartKind;

typedef struct {
    PathStartKind kind;
    char *name;  /* NULL for non-var starts */
} PathStart;

/* Path axis */
typedef enum {
    AXIS_CHILD,
    AXIS_DESC,
    AXIS_DESC_OR_SELF,
    AXIS_SELF,
    AXIS_PARENT,
    AXIS_ATTR
} PathAxis;

/* Step test kind */
typedef enum {
    TEST_NAME,
    TEST_WILDCARD,
    TEST_TEXT,
    TEST_NODE,
    TEST_ELEMENT,
    TEST_COMMENT,
    TEST_PI,
    TEST_DOCUMENT
} StepTestKind;

typedef struct {
    StepTestKind kind;
    char *name;  /* NULL for non-name tests */
} StepTest;

/* Path step */
typedef struct {
    PathAxis axis;
    StepTest test;
    struct Expr **predicates;
    size_t predicate_count;
} PathStep;

/* Path expression */
typedef struct {
    PathStart start;
    PathStep *steps;
    size_t step_count;
} PathExpr;

/* Constructor */
typedef struct {
    char *name;
    struct {
        char *name;
        struct Expr *expr;
    } *attrs;
    size_t attr_count;
    struct Expr **contents;
    size_t content_count;
} Constructor;

/* Expression kinds */
typedef enum {
    EXPR_LITERAL,
    EXPR_VAR_REF,
    EXPR_IF,
    EXPR_LET,
    EXPR_FOR,
    EXPR_MATCH,
    EXPR_FUNC_CALL,
    EXPR_UNARY_OP,
    EXPR_BINARY_OP,
    EXPR_PATH,
    EXPR_CONSTRUCTOR,
    EXPR_TEXT_CONSTRUCTOR,
    EXPR_CHAR_DATA,
    EXPR_INTERP,
    EXPR_APPLY,
    EXPR_COMMENT_CONSTRUCTOR,
    EXPR_PI_CONSTRUCTOR
} ExprKind;

/* Forward declarations for expression types */
typedef struct IfExpr {
    struct Expr *cond;
    struct Expr *then_expr;
    struct Expr *else_expr;
} IfExpr;

typedef struct LetExpr {
    char *name;
    struct Expr *value;
    struct Expr *body;
} LetExpr;

typedef struct ForExpr {
    char *name;
    struct Expr *seq;
    struct Expr *where_clause;
    struct Expr *body;
} ForExpr;

typedef struct MatchExpr {
    struct Expr *target;
    struct Pattern **patterns;
    struct Expr **exprs;
    size_t case_count;
    struct Expr *default_expr;
} MatchExpr;

typedef struct NamedArg {
    char *name;
    struct Expr *expr;
} NamedArg;

typedef struct FuncCall {
    char *name;
    struct Expr **args;
    size_t arg_count;
    NamedArg *named_args;
    size_t named_arg_count;
} FuncCall;

typedef struct UnaryOp {
    char *op;
    struct Expr *expr;
} UnaryOp;

typedef struct BinaryOp {
    char *op;
    struct Expr *left;
    struct Expr *right;
} BinaryOp;

typedef struct ApplyExpr {
    struct Expr *expr;
    char *ruleset;  /* NULL for default */
} ApplyExpr;

typedef struct CommentConstructor {
    struct Expr *expr;
} CommentConstructor;

typedef struct PIConstructor {
    struct Expr *target;
    struct Expr *value;
} PIConstructor;

/* Expression */
typedef struct Expr {
    ExprKind kind;
    union {
        LiteralValue literal;
        char *var_ref;
        IfExpr *if_expr;
        LetExpr *let_expr;
        ForExpr *for_expr;
        MatchExpr *match_expr;
        FuncCall *func_call;
        UnaryOp *unary_op;
        BinaryOp *binary_op;
        PathExpr *path;
        Constructor *constructor;
        struct Expr *text_constructor;  /* EXPR_TEXT_CONSTRUCTOR */
        char *char_data;                /* EXPR_CHAR_DATA */
        struct Expr *interp;            /* EXPR_INTERP */
        ApplyExpr *apply_expr;          /* EXPR_APPLY */
        CommentConstructor *comment_constructor; /* EXPR_COMMENT_CONSTRUCTOR */
        PIConstructor *pi_constructor;  /* EXPR_PI_CONSTRUCTOR */
    } data;
} Expr;

/* Pattern kinds */
typedef enum {
    PAT_WILDCARD,
    PAT_ELEMENT,
    PAT_ATTRIBUTE,
    PAT_TYPED
} PatternKind;

typedef struct AttributePattern {
    char *name;
    LiteralValue *value;  /* NULL if no value comparison */
} AttributePattern;

typedef struct ElementPattern {
    char *name;
    char *var;           /* NULL if no var */
    struct Pattern *child;  /* NULL if no child pattern */
    struct Pattern **children; /* NULL if no exact children */
    size_t child_count;
} ElementPattern;

typedef struct Pattern {
    PatternKind kind;
    union {
        ElementPattern *element;
        AttributePattern *attribute;
        char *typed;      /* type name */
    } data;
} Pattern;

/* Module - top level */
typedef struct {
    /* Functions: name -> FunctionDef* */
    struct HashMap *functions;
    /* Rules: name -> RuleDef** (null-terminated array) */
    struct HashMap *rules;
    /* Variables: name -> Expr* */
    struct HashMap *vars;
    /* Namespaces: prefix -> uri */
    struct HashMap *namespaces;
    /* Imports: array of (iri, alias) pairs */
    struct ImportDecl {
        char *iri;
        char *alias;
    } *imports;
    size_t import_count;
    /* Main expression */
    Expr *expr;
} Module;

/* Memory management functions */
void literal_free(LiteralValue *lit);
void param_free(Param *param);
void function_def_free(FunctionDef *fd);
void rule_def_free(RuleDef *rd);
void expr_free(Expr *expr);
void pattern_free(Pattern *pat);
void constructor_free(Constructor *c);
void path_expr_free(PathExpr *pe);
void module_free(Module *mod);

/* Helper constructors */
StepTest step_test_named(const char *name);
StepTest step_test_wildcard(void);
StepTest step_test_text(void);
StepTest step_test_node(void);
StepTest step_test_document(void);
Expr* expr_new(ExprKind kind);
Pattern* pattern_new(PatternKind kind);

#endif
