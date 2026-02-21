export class Module {
  functions: Record<string, FunctionDef>;
  rules: Record<string, RuleDef[]>;
  vars: Record<string, Expr>;
  namespaces: Record<string, string>;
  imports: Array<[string, string | null]>;
  expr: Expr | null;

  constructor(opts: {
    functions: Record<string, FunctionDef>;
    rules: Record<string, RuleDef[]>;
    vars: Record<string, Expr>;
    namespaces: Record<string, string>;
    imports: Array<[string, string | null]>;
    expr: Expr | null;
  }) {
    this.functions = opts.functions;
    this.rules = opts.rules;
    this.vars = opts.vars;
    this.namespaces = opts.namespaces;
    this.imports = opts.imports;
    this.expr = opts.expr;
  }
}

export type Expr =
  | Literal
  | VarRef
  | IfExpr
  | LetExpr
  | ForExpr
  | MatchExpr
  | FuncCall
  | UnaryOp
  | BinaryOp
  | PathExpr
  | Constructor
  | TextConstructor
  | Text
  | Interp;

export class Literal {
  value: any;
  constructor(value: any) {
    this.value = value;
  }
}

export class VarRef {
  name: string;
  constructor(name: string) {
    this.name = name;
  }
}

export class IfExpr {
  cond: Expr;
  then_expr: Expr;
  else_expr: Expr;
  constructor(cond: Expr, thenExpr: Expr, elseExpr: Expr) {
    this.cond = cond;
    this.then_expr = thenExpr;
    this.else_expr = elseExpr;
  }
}

export class LetExpr {
  name: string;
  value: Expr;
  body: Expr;
  constructor(name: string, value: Expr, body: Expr) {
    this.name = name;
    this.value = value;
    this.body = body;
  }
}

export class ForExpr {
  name: string;
  seq: Expr;
  where: Expr | null;
  body: Expr;
  constructor(name: string, seq: Expr, where: Expr | null, body: Expr) {
    this.name = name;
    this.seq = seq;
    this.where = where;
    this.body = body;
  }
}

export class MatchExpr {
  target: Expr;
  cases: Array<[Pattern, Expr]>;
  defaultExpr: Expr | null;
  constructor(target: Expr, cases: Array<[Pattern, Expr]>, defaultExpr: Expr | null) {
    this.target = target;
    this.cases = cases;
    this.defaultExpr = defaultExpr;
  }
}

export class FuncCall {
  name: string;
  args: Expr[];
  constructor(name: string, args: Expr[]) {
    this.name = name;
    this.args = args;
  }
}

export class UnaryOp {
  op: string;
  expr: Expr;
  constructor(op: string, expr: Expr) {
    this.op = op;
    this.expr = expr;
  }
}

export class BinaryOp {
  op: string;
  left: Expr;
  right: Expr;
  constructor(op: string, left: Expr, right: Expr) {
    this.op = op;
    this.left = left;
    this.right = right;
  }
}

export class PathExpr {
  start: PathStart;
  steps: PathStep[];
  constructor(start: PathStart, steps: PathStep[]) {
    this.start = start;
    this.steps = steps;
  }
}

export class Constructor {
  name: string;
  attrs: Array<[string, Expr]>;
  contents: Expr[];
  constructor(name: string, attrs: Array<[string, Expr]>, contents: Expr[]) {
    this.name = name;
    this.attrs = attrs;
    this.contents = contents;
  }
}

export class TextConstructor {
  expr: Expr;
  constructor(expr: Expr) {
    this.expr = expr;
  }
}

export class Text {
  value: string;
  constructor(value: string) {
    this.value = value;
  }
}

export class Interp {
  expr: Expr;
  constructor(expr: Expr) {
    this.expr = expr;
  }
}

export class PathStart {
  kind: string; // context, root, desc, desc_root, var
  name: string | null;
  constructor(kind: string, name: string | null = null) {
    this.kind = kind;
    this.name = name;
  }
}

export class PathStep {
  axis: string; // child, desc, desc_or_self, self, parent, attr
  test: StepTest;
  predicates: Expr[];
  constructor(axis: string, test: StepTest, predicates: Expr[]) {
    this.axis = axis;
    this.test = test;
    this.predicates = predicates;
  }
}

export class StepTest {
  kind: string; // name, wildcard, text, node, comment, pi
  name: string | null;
  constructor(kind: string, name: string | null = null) {
    this.kind = kind;
    this.name = name;
  }
}

export type Pattern = WildcardPattern | ElementPattern | TypedPattern | AttributePattern;

export class WildcardPattern {}

export class ElementPattern {
  name: string;
  varName: string | null;
  child: Pattern | null;
  constructor(name: string, varName: string | null = null, child: Pattern | null = null) {
    this.name = name;
    this.varName = varName;
    this.child = child;
  }
}

export class TypedPattern {
  kind: string; // node, text, comment
  constructor(kind: string) {
    this.kind = kind;
  }
}

export class AttributePattern {
  name: string;
  constructor(name: string) {
    this.name = name;
  }
}

export class Param {
  name: string;
  type_ref: string | null;
  defaultExpr: Expr | null;
  constructor(name: string, typeRef: string | null = null, defaultExpr: Expr | null = null) {
    this.name = name;
    this.type_ref = typeRef;
    this.defaultExpr = defaultExpr;
  }
}

export class FunctionDef {
  params: Param[];
  body: Expr;
  constructor(params: Param[], body: Expr) {
    this.params = params;
    this.body = body;
  }
}

export class RuleDef {
  pattern: Pattern;
  body: Expr;
  constructor(pattern: Pattern, body: Expr) {
    this.pattern = pattern;
    this.body = body;
  }
}
