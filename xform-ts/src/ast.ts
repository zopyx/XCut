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
  | ApplyExpr
  | UnaryOp
  | BinaryOp
  | PathExpr
  | Constructor
  | TextConstructor
  | CommentConstructor
  | PIConstructor
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
  namedArgs: Array<[string, Expr]>;
  constructor(name: string, args: Expr[], namedArgs: Array<[string, Expr]> = []) {
    this.name = name;
    this.args = args;
    this.namedArgs = namedArgs;
  }
}

export class ApplyExpr {
  expr: Expr;
  ruleset: string | null;
  constructor(expr: Expr, ruleset: string | null = null) {
    this.expr = expr;
    this.ruleset = ruleset;
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

export class CommentConstructor {
  expr: Expr;
  constructor(expr: Expr) {
    this.expr = expr;
  }
}

export class PIConstructor {
  target: Expr;
  value: Expr;
  constructor(target: Expr, value: Expr) {
    this.target = target;
    this.value = value;
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
  kind: string; // context, root, desc, desc_root, var, attr
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
  kind: string; // name, wildcard, text, node, comment, pi, document
  name: string | null;
  constructor(kind: string, name: string | null = null) {
    this.kind = kind;
    this.name = name;
  }
}

export type Pattern = WildcardPattern | ElementPattern | TypedPattern | AttributePattern | LiteralPattern;

export class WildcardPattern {}

export class ElementPattern {
  name: string;
  varName: string | null;
  child: Pattern | null;
  attrs: Array<[string, Literal | null]>;
  children: Pattern[];
  constructor(
    name: string,
    varName: string | null = null,
    child: Pattern | null = null,
    attrs: Array<[string, Literal | null]> = [],
    children: Pattern[] = [],
  ) {
    this.name = name;
    this.varName = varName;
    this.child = child;
    this.attrs = attrs;
    this.children = children;
    // Normalize legacy child into children
    if (this.child !== null && this.children.length === 0) {
      this.children = [this.child];
      this.child = null;
    }
  }
}

export class TypedPattern {
  kind: string; // node, text, comment, pi, document
  constructor(kind: string) {
    this.kind = kind;
  }
}

export class AttributePattern {
  name: string;
  value: Literal | null;
  constructor(name: string, value: Literal | null = null) {
    this.name = name;
    this.value = value;
  }
}

export class LiteralPattern {
  value: string;
  constructor(value: string) {
    this.value = value;
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
