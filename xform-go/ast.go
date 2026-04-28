package xform

type Module struct {
	Functions  map[string]FunctionDef
	Rules      map[string][]RuleDef
	Vars       map[string]Expr
	Namespaces map[string]string
	Imports    [][2]*string
	Expr       Expr
}

type Expr interface{}

type Literal struct{ Value any }

type VarRef struct{ Name string }

type IfExpr struct {
	Cond     Expr
	ThenExpr Expr
	ElseExpr Expr
}

type LetExpr struct {
	Name  string
	Value Expr
	Body  Expr
}

type ForExpr struct {
	Name  string
	Seq   Expr
	Where Expr
	Body  Expr
}

type MatchExpr struct {
	Target  Expr
	Cases   []MatchCase
	Default Expr
}

type MatchCase struct {
	Pattern Pattern
	Expr    Expr
}

type FuncCall struct {
	Name      string
	Args      []Expr
	NamedArgs []NamedArg
}

type NamedArg struct {
	Name string
	Expr Expr
}

type ApplyExpr struct {
	Expr    Expr
	Ruleset *string
}

type UnaryOp struct {
	Op   string
	Expr Expr
}

type BinaryOp struct {
	Op    string
	Left  Expr
	Right Expr
}

type PathExpr struct {
	Start PathStart
	Steps []PathStep
}

type Constructor struct {
	Name     string
	Attrs    []AttrConstructor
	Contents []Expr
}

type AttrConstructor struct {
	Name string
	Expr Expr
}

type TextConstructor struct{ Expr Expr }
type CommentConstructor struct{ Expr Expr }
type PIConstructor struct {
	Target Expr
	Value  Expr
}

type Text struct{ Value string }

type Interp struct{ Expr Expr }

type PathStart struct {
	Kind string
	Name *string
}

type PathStep struct {
	Axis       string
	Test       StepTest
	Predicates []Expr
}

type StepTest struct {
	Kind string
	Name *string
}

type Pattern interface{}

type WildcardPattern struct{}

type ElementPattern struct {
	Name     string
	Var      *string
	Child    Pattern
	Attrs    []PatternAttr
	Children []Pattern
}

type PatternAttr struct {
	Name  string
	Value *Literal
}

type TypedPattern struct{ Kind string }

type AttributePattern struct {
	Name  string
	Value *Literal
}

type LiteralPattern struct{ Value string }

type Param struct {
	Name    string
	TypeRef *string
	Default Expr
}

type FunctionDef struct {
	Params []Param
	Body   Expr
}

type RuleDef struct {
	Pattern Pattern
	Body    Expr
}
