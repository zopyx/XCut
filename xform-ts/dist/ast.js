"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RuleDef = exports.FunctionDef = exports.Param = exports.AttributePattern = exports.TypedPattern = exports.ElementPattern = exports.WildcardPattern = exports.StepTest = exports.PathStep = exports.PathStart = exports.Interp = exports.Text = exports.TextConstructor = exports.Constructor = exports.PathExpr = exports.BinaryOp = exports.UnaryOp = exports.FuncCall = exports.MatchExpr = exports.ForExpr = exports.LetExpr = exports.IfExpr = exports.VarRef = exports.Literal = exports.Module = void 0;
class Module {
    constructor(opts) {
        this.functions = opts.functions;
        this.rules = opts.rules;
        this.vars = opts.vars;
        this.namespaces = opts.namespaces;
        this.imports = opts.imports;
        this.expr = opts.expr;
    }
}
exports.Module = Module;
class Literal {
    constructor(value) {
        this.value = value;
    }
}
exports.Literal = Literal;
class VarRef {
    constructor(name) {
        this.name = name;
    }
}
exports.VarRef = VarRef;
class IfExpr {
    constructor(cond, thenExpr, elseExpr) {
        this.cond = cond;
        this.then_expr = thenExpr;
        this.else_expr = elseExpr;
    }
}
exports.IfExpr = IfExpr;
class LetExpr {
    constructor(name, value, body) {
        this.name = name;
        this.value = value;
        this.body = body;
    }
}
exports.LetExpr = LetExpr;
class ForExpr {
    constructor(name, seq, where, body) {
        this.name = name;
        this.seq = seq;
        this.where = where;
        this.body = body;
    }
}
exports.ForExpr = ForExpr;
class MatchExpr {
    constructor(target, cases, defaultExpr) {
        this.target = target;
        this.cases = cases;
        this.defaultExpr = defaultExpr;
    }
}
exports.MatchExpr = MatchExpr;
class FuncCall {
    constructor(name, args) {
        this.name = name;
        this.args = args;
    }
}
exports.FuncCall = FuncCall;
class UnaryOp {
    constructor(op, expr) {
        this.op = op;
        this.expr = expr;
    }
}
exports.UnaryOp = UnaryOp;
class BinaryOp {
    constructor(op, left, right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }
}
exports.BinaryOp = BinaryOp;
class PathExpr {
    constructor(start, steps) {
        this.start = start;
        this.steps = steps;
    }
}
exports.PathExpr = PathExpr;
class Constructor {
    constructor(name, attrs, contents) {
        this.name = name;
        this.attrs = attrs;
        this.contents = contents;
    }
}
exports.Constructor = Constructor;
class TextConstructor {
    constructor(expr) {
        this.expr = expr;
    }
}
exports.TextConstructor = TextConstructor;
class Text {
    constructor(value) {
        this.value = value;
    }
}
exports.Text = Text;
class Interp {
    constructor(expr) {
        this.expr = expr;
    }
}
exports.Interp = Interp;
class PathStart {
    constructor(kind, name = null) {
        this.kind = kind;
        this.name = name;
    }
}
exports.PathStart = PathStart;
class PathStep {
    constructor(axis, test, predicates) {
        this.axis = axis;
        this.test = test;
        this.predicates = predicates;
    }
}
exports.PathStep = PathStep;
class StepTest {
    constructor(kind, name = null) {
        this.kind = kind;
        this.name = name;
    }
}
exports.StepTest = StepTest;
class WildcardPattern {
}
exports.WildcardPattern = WildcardPattern;
class ElementPattern {
    constructor(name, varName = null, child = null) {
        this.name = name;
        this.varName = varName;
        this.child = child;
    }
}
exports.ElementPattern = ElementPattern;
class TypedPattern {
    constructor(kind) {
        this.kind = kind;
    }
}
exports.TypedPattern = TypedPattern;
class AttributePattern {
    constructor(name) {
        this.name = name;
    }
}
exports.AttributePattern = AttributePattern;
class Param {
    constructor(name, typeRef = null, defaultExpr = null) {
        this.name = name;
        this.type_ref = typeRef;
        this.defaultExpr = defaultExpr;
    }
}
exports.Param = Param;
class FunctionDef {
    constructor(params, body) {
        this.params = params;
        this.body = body;
    }
}
exports.FunctionDef = FunctionDef;
class RuleDef {
    constructor(pattern, body) {
        this.pattern = pattern;
        this.body = body;
    }
}
exports.RuleDef = RuleDef;
