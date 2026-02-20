use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct Module {
    pub functions: HashMap<String, FunctionDef>,
    pub rules: HashMap<String, Vec<RuleDef>>,
    pub vars: HashMap<String, Expr>,
    pub namespaces: HashMap<String, String>,
    pub imports: Vec<(String, Option<String>)>,
    pub expr: Option<Expr>,
}

#[derive(Debug, Clone)]
pub struct FunctionDef {
    pub params: Vec<Param>,
    pub body: Expr,
}

#[derive(Debug, Clone)]
pub struct Param {
    pub name: String,
    pub type_ref: Option<String>,
    pub default: Option<Expr>,
}

#[derive(Debug, Clone)]
pub struct RuleDef {
    pub pattern: Pattern,
    pub body: Expr,
}

#[derive(Debug, Clone)]
pub enum Expr {
    Literal(LiteralValue),
    VarRef(String),
    IfExpr(Box<IfExpr>),
    LetExpr(Box<LetExpr>),
    ForExpr(Box<ForExpr>),
    MatchExpr(Box<MatchExpr>),
    FuncCall(Box<FuncCall>),
    UnaryOp { op: String, expr: Box<Expr> },
    BinaryOp { op: String, left: Box<Expr>, right: Box<Expr> },
    PathExpr(Box<PathExpr>),
    Constructor(Box<Constructor>),
    TextConstructor(Box<Expr>),
    CharData(String),
    Interp(Box<Expr>),
}

#[derive(Debug, Clone)]
pub enum LiteralValue {
    Str(String),
    Num(f64),
    Bool(bool),
    Null,
}

#[derive(Debug, Clone)]
pub struct IfExpr {
    pub cond: Expr,
    pub then_expr: Expr,
    pub else_expr: Expr,
}

#[derive(Debug, Clone)]
pub struct LetExpr {
    pub name: String,
    pub value: Expr,
    pub body: Expr,
}

#[derive(Debug, Clone)]
pub struct ForExpr {
    pub name: String,
    pub seq: Expr,
    pub where_clause: Option<Expr>,
    pub body: Expr,
}

#[derive(Debug, Clone)]
pub struct MatchExpr {
    pub target: Expr,
    pub cases: Vec<(Pattern, Expr)>,
    pub default: Option<Expr>,
}

#[derive(Debug, Clone)]
pub struct FuncCall {
    pub name: String,
    pub args: Vec<Expr>,
}

#[derive(Debug, Clone)]
pub struct PathExpr {
    pub start: PathStart,
    pub steps: Vec<PathStep>,
}

#[derive(Debug, Clone)]
pub struct Constructor {
    pub name: String,
    pub attrs: Vec<(String, Expr)>,
    pub contents: Vec<Expr>,
}

#[derive(Debug, Clone)]
pub struct PathStart {
    pub kind: PathStartKind,
    pub name: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PathStartKind {
    Context,
    Root,
    Desc,
    DescRoot,
    Var,
}

#[derive(Debug, Clone)]
pub struct PathStep {
    pub axis: PathAxis,
    pub test: StepTest,
    pub predicates: Vec<Expr>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PathAxis {
    Child,
    Desc,
    DescOrSelf,
    SelfAxis,
    Parent,
    Attr,
}

#[derive(Debug, Clone)]
pub struct StepTest {
    pub kind: StepTestKind,
    pub name: Option<String>,
}

impl StepTest {
    pub fn named(n: &str) -> Self {
        StepTest { kind: StepTestKind::Name, name: Some(n.to_string()) }
    }
    pub fn wildcard() -> Self { StepTest { kind: StepTestKind::Wildcard, name: None } }
    pub fn text() -> Self { StepTest { kind: StepTestKind::Text, name: None } }
    pub fn node() -> Self { StepTest { kind: StepTestKind::Node, name: None } }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StepTestKind {
    Name,
    Wildcard,
    Text,
    Node,
    Comment,
    Pi,
}

#[derive(Debug, Clone)]
pub enum Pattern {
    Wildcard,
    Element(ElementPattern),
    Attribute(String),
    Typed(String),
}

#[derive(Debug, Clone)]
pub struct ElementPattern {
    pub name: String,
    pub var: Option<String>,
    pub child: Option<Box<Pattern>>,
}
