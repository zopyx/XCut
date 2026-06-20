"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.FunctionRef = exports.Context = void 0;
exports.evalModule = evalModule;
exports.evalExpr = evalExpr;
exports.evalBinary = evalBinary;
exports.evalPath = evalPath;
exports.applyStep = applyStep;
exports.evalConstructor = evalConstructor;
exports.callFunction = callFunction;
exports.toBoolean = toBoolean;
exports.toString = toString;
exports.toNumber = toNumber;
exports.valueEqual = valueEqual;
exports.matchPattern = matchPattern;
exports.serializeItem = serializeItem;
const ast = __importStar(require("./ast"));
const xmlmodel_1 = require("./xmlmodel");
const MAX_RECURSION_DEPTH = 10000;
class Context {
    constructor(contextItem, variables, functions, rules, position = null, last = null, recursionDepth = 0, version = "2.0") {
        this.contextItem = contextItem;
        this.variables = variables;
        this.functions = functions;
        this.rules = rules;
        this.position = position;
        this.last = last;
        this.recursionDepth = recursionDepth;
        this.version = version;
    }
}
exports.Context = Context;
function evalModule(module, doc) {
    const functions = { ...module.functions };
    const rules = { ...module.rules };
    const variables = {};
    const ctx = new Context(doc, variables, functions, rules, null, null, 0, module.version);
    for (const [name, expr] of Object.entries(module.vars)) {
        variables[name] = evalExpr(expr, ctx);
    }
    if (module.expr === null)
        return [];
    return evalExpr(module.expr, ctx);
}
function evalExpr(expr, ctx) {
    if (expr instanceof ast.Literal) {
        return [expr.value];
    }
    if (expr instanceof ast.VarRef) {
        if (expr.name in ctx.variables)
            return ctx.variables[expr.name];
        if (expr.name in ctx.functions)
            return [new FunctionRef(expr.name)];
        if (ctx.contextItem instanceof xmlmodel_1.Node) {
            return ctx.contextItem.children.filter((c) => c.kind === "element" && c.name === expr.name);
        }
        return [];
    }
    if (expr instanceof ast.IfExpr) {
        const cond = toBoolean(evalExpr(expr.cond, ctx));
        return cond ? evalExpr(expr.then_expr, ctx) : evalExpr(expr.else_expr, ctx);
    }
    if (expr instanceof ast.LetExpr) {
        const value = evalExpr(expr.value, ctx);
        const newVars = { ...ctx.variables, [expr.name]: value };
        return evalExpr(expr.body, new Context(ctx.contextItem, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth, ctx.version));
    }
    if (expr instanceof ast.ForExpr) {
        const seq = evalExpr(expr.seq, ctx);
        const out = [];
        const total = seq.length;
        seq.forEach((item, idx) => {
            const newVars = { ...ctx.variables, [expr.name]: [item] };
            const newCtx = new Context(item, newVars, ctx.functions, ctx.rules, idx + 1, total, ctx.recursionDepth, ctx.version);
            if (expr.where) {
                if (!toBoolean(evalExpr(expr.where, newCtx)))
                    return;
            }
            out.push(...evalExpr(expr.body, newCtx));
        });
        return out;
    }
    if (expr instanceof ast.MatchExpr) {
        const targetSeq = evalExpr(expr.target, ctx);
        const out = [];
        for (const target of targetSeq) {
            let matchedAny = false;
            for (const [pattern, guard, body] of expr.cases) {
                const [matched, bindings] = matchPattern(pattern, target);
                if (matched) {
                    const newVars = { ...ctx.variables, ...bindings };
                    const matchCtx = new Context(target, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth, ctx.version);
                    if (guard && !toBoolean(evalExpr(guard, matchCtx)))
                        continue;
                    matchedAny = true;
                    out.push(...evalExpr(body, matchCtx));
                    break;
                }
            }
            if (!matchedAny) {
                if (!expr.defaultExpr)
                    throw new Error("XFDY0001: no matching case");
                out.push(...evalExpr(expr.defaultExpr, new Context(target, { ...ctx.variables }, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth, ctx.version)));
            }
        }
        return out;
    }
    if (expr instanceof ast.FuncCall) {
        const args = expr.args.map((a) => evalExpr(a, ctx));
        const named = {};
        const namedRaw = {};
        for (const [name, valExpr] of expr.namedArgs) {
            named[name] = evalExpr(valExpr, ctx);
            namedRaw[name] = valExpr;
        }
        return callFunction(expr.name, args, ctx, named, namedRaw);
    }
    if (expr instanceof ast.ApplyExpr) {
        const seq = evalExpr(expr.expr, ctx);
        const ruleset = expr.ruleset || "main";
        return doApply(seq, ruleset, ctx);
    }
    if (expr instanceof ast.UnaryOp) {
        const val = evalExpr(expr.expr, ctx);
        if (expr.op === "-")
            return [-toNumber(val)];
        if (expr.op === "not")
            return [!toBoolean(val)];
    }
    if (expr instanceof ast.BinaryOp) {
        if (expr.op === "and") {
            const left = evalExpr(expr.left, ctx);
            if (!toBoolean(left))
                return [false];
            const right = evalExpr(expr.right, ctx);
            return [toBoolean(right)];
        }
        if (expr.op === "or") {
            const left = evalExpr(expr.left, ctx);
            if (toBoolean(left))
                return [true];
            const right = evalExpr(expr.right, ctx);
            return [toBoolean(right)];
        }
        const left = evalExpr(expr.left, ctx);
        const right = evalExpr(expr.right, ctx);
        return [evalBinary(expr.op, left, right)];
    }
    if (expr instanceof ast.PathExpr) {
        return evalPath(expr, ctx);
    }
    if (expr instanceof ast.Constructor) {
        return [evalConstructor(expr, ctx)];
    }
    if (expr instanceof ast.TextConstructor) {
        return [new xmlmodel_1.Node({ kind: "text", value: toString(evalExpr(expr.expr, ctx)) })];
    }
    if (expr instanceof ast.CommentConstructor) {
        return [new xmlmodel_1.Node({ kind: "comment", value: toString(evalExpr(expr.expr, ctx)) })];
    }
    if (expr instanceof ast.PIConstructor) {
        const target = toString(evalExpr(expr.target, ctx));
        const value = toString(evalExpr(expr.value, ctx));
        return [new xmlmodel_1.Node({ kind: "pi", name: target, value })];
    }
    if (expr instanceof ast.Text) {
        return [expr.value];
    }
    if (expr instanceof ast.Interp) {
        return evalExpr(expr.expr, ctx);
    }
    throw new Error(`Unknown expr ${String(expr)}`);
}
function evalBinary(op, left, right) {
    if (op === "and")
        return toBoolean(left) && toBoolean(right);
    if (op === "or")
        return toBoolean(left) || toBoolean(right);
    if (op === "=")
        return valueEqual(left, right);
    if (op === "!=")
        return !valueEqual(left, right);
    const lnum = toNumber(left);
    const rnum = toNumber(right);
    if (op === "+")
        return lnum + rnum;
    if (op === "-")
        return lnum - rnum;
    if (op === "*")
        return lnum * rnum;
    if (op === "div")
        return lnum / rnum;
    if (op === "mod")
        return lnum % rnum;
    if (op === "<")
        return lnum < rnum;
    if (op === "<=")
        return lnum <= rnum;
    if (op === ">")
        return lnum > rnum;
    if (op === ">=")
        return lnum >= rnum;
    throw new Error(`Unknown operator ${op}`);
}
function evalPath(expr, ctx) {
    let steps = [...expr.steps];
    let base = [];
    if (expr.start.kind === "context") {
        base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
    }
    else if (expr.start.kind === "root") {
        base = rootOf(ctx.contextItem);
    }
    else if (expr.start.kind === "desc") {
        base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
    }
    else if (expr.start.kind === "desc_root") {
        base = rootOf(ctx.contextItem);
    }
    else if (expr.start.kind === "var") {
        if (expr.start.name && expr.start.name in ctx.variables) {
            base = ctx.variables[expr.start.name];
        }
        else {
            base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
            if (expr.start.name) {
                steps = [new ast.PathStep("child", new ast.StepTest("name", expr.start.name), []), ...steps];
            }
        }
    }
    else if (expr.start.kind === "attr") {
        base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
        if (expr.start.name) {
            steps = [new ast.PathStep("attr", new ast.StepTest("name", expr.start.name), []), ...steps];
        }
    }
    let current = base;
    for (const step of steps) {
        current = applyStep(current, step, ctx);
    }
    return current;
}
function rootOf(item) {
    if (item instanceof xmlmodel_1.Node) {
        let node = item;
        while (node.parent)
            node = node.parent;
        return [node];
    }
    return [];
}
function descOrSelf(items) {
    const out = [];
    for (const item of items) {
        if (item instanceof xmlmodel_1.Node) {
            out.push(item);
            for (const d of (0, xmlmodel_1.iterDescendants)(item))
                out.push(d);
        }
    }
    return out;
}
function applyStep(items, step, ctx) {
    const out = [];
    for (const item of items) {
        if (!(item instanceof xmlmodel_1.Node))
            continue;
        let candidates = [];
        if (step.axis === "self") {
            candidates = [item];
        }
        else if (step.axis === "parent") {
            candidates = item.parent ? [item.parent] : [];
        }
        else if (step.axis === "desc_or_self") {
            candidates = [item, ...Array.from((0, xmlmodel_1.iterDescendants)(item))];
        }
        else if (step.axis === "desc") {
            candidates = Array.from((0, xmlmodel_1.iterDescendants)(item));
        }
        else if (step.axis === "attr") {
            if (item.kind === "element") {
                if (step.test.kind === "name") {
                    const name = step.test.name;
                    if (name in item.attrs) {
                        candidates = [new xmlmodel_1.Node({ kind: "attribute", name, value: item.attrs[name] })];
                    }
                    else {
                        candidates = [];
                    }
                }
                else if (step.test.kind === "wildcard") {
                    candidates = Object.entries(item.attrs).map(([k, v]) => new xmlmodel_1.Node({ kind: "attribute", name: k, value: v }));
                }
                else {
                    candidates = [];
                }
            }
        }
        else if (step.axis === "child") {
            candidates = item.kind === "element" || item.kind === "document" ? item.children : [];
        }
        let filtered = candidates.filter((c) => matchesStepTest(step.test, c));
        for (const pred of step.predicates) {
            const predOut = [];
            for (let i = 0; i < filtered.length; i += 1) {
                const child = filtered[i];
                const predCtx = new Context(child, ctx.variables, ctx.functions, ctx.rules, i + 1, filtered.length, ctx.recursionDepth, ctx.version);
                if (toBoolean(evalExpr(pred, predCtx)))
                    predOut.push(child);
            }
            filtered = predOut;
        }
        out.push(...filtered);
    }
    return out;
}
function matchesStepTest(test, node) {
    if (test.kind === "node")
        return true;
    if (test.kind === "wildcard")
        return node.kind === "element" || node.kind === "attribute";
    if (test.kind === "element")
        return node.kind === "element";
    if (test.kind === "text")
        return node.kind === "text";
    if (test.kind === "comment")
        return node.kind === "comment";
    if (test.kind === "pi")
        return node.kind === "pi";
    if (test.kind === "document")
        return node.kind === "document";
    if (test.kind === "name")
        return node.name === test.name;
    return false;
}
function evalConstructor(expr, ctx) {
    const node = new xmlmodel_1.Node({ kind: "element", name: expr.name });
    const seenAttrs = new Set();
    for (const [name, aexpr] of expr.attrs) {
        if (seenAttrs.has(name)) {
            throw new Error("XFDY0005");
        }
        seenAttrs.add(name);
        const val = evalExpr(aexpr, ctx);
        node.attrs[name] = toString(val);
    }
    const children = [];
    for (const content of expr.contents) {
        if (content instanceof ast.Text) {
            children.push(new xmlmodel_1.Node({ kind: "text", value: content.value }));
            continue;
        }
        const seq = evalExpr(content, ctx);
        for (const item of seq) {
            if (item instanceof xmlmodel_1.Node) {
                if (item.kind === "attribute") {
                    if (ctx.version >= "2.2")
                        throw new Error("XFDY0005");
                    children.push(new xmlmodel_1.Node({ kind: "text", value: item.value || "" }));
                }
                else {
                    children.push((0, xmlmodel_1.deepCopy)(item, true));
                }
            }
            else {
                children.push(new xmlmodel_1.Node({ kind: "text", value: toString([item]) }));
            }
        }
    }
    const merged = [];
    for (const child of children) {
        if (child.kind === "text" && merged.length > 0 && merged[merged.length - 1].kind === "text") {
            merged[merged.length - 1].value = (merged[merged.length - 1].value || "") + (child.value || "");
        }
        else {
            merged.push(child);
        }
    }
    for (const child of merged)
        child.parent = node;
    node.children = merged;
    return node;
}
class FunctionRef {
    constructor(name) {
        this.name = name;
    }
}
exports.FunctionRef = FunctionRef;
function callFunction(name, args, ctx, namedArgs = {}, namedRaw = {}) {
    if (name in ctx.functions) {
        const func = ctx.functions[name];
        const params = func.params;
        if (args.length > params.length) {
            throw new Error("XFDY0008: too many arguments");
        }
        const newVars = { ...ctx.variables };
        const bound = new Set();
        for (let i = 0; i < args.length; i += 1) {
            newVars[params[i].name] = args[i];
            bound.add(params[i].name);
        }
        for (const [paramName, value] of Object.entries(namedArgs)) {
            if (bound.has(paramName)) {
                throw new Error("XFDY0008: duplicate argument");
            }
            const matching = params.filter((p) => p.name === paramName);
            if (matching.length === 0) {
                throw new Error("XFDY0008: unknown parameter");
            }
            newVars[paramName] = value;
            bound.add(paramName);
        }
        const newCtx = new Context(ctx.contextItem, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth + 1, ctx.version);
        if (newCtx.recursionDepth >= MAX_RECURSION_DEPTH)
            throw new Error("XFDY0099");
        for (const param of params) {
            if (!bound.has(param.name)) {
                if (!param.defaultExpr) {
                    throw new Error("XFDY0008: missing required parameter");
                }
                newVars[param.name] = evalExpr(param.defaultExpr, newCtx);
                bound.add(param.name);
            }
        }
        return evalExpr(func.body, newCtx);
    }
    const fn = BUILTINS[name];
    if (!fn)
        throw new Error(`XFST0003: unknown function ${name}`);
    return fn(args, ctx, namedArgs, namedRaw);
}
function toBoolean(seq) {
    if (!seq || seq.length === 0)
        return false;
    if (seq.some((i) => i instanceof xmlmodel_1.Node))
        return true;
    for (const item of seq) {
        if (![false, 0, 0.0, "", null, undefined].includes(item))
            return true;
    }
    return false;
}
function toString(seq) {
    if (!seq || seq.length === 0)
        return "";
    const item = seq[0];
    if (item instanceof xmlmodel_1.Node)
        return item.stringValue();
    if (item === null || item === undefined)
        return "";
    if (typeof item === "boolean")
        return item ? "true" : "false";
    if (typeof item === "number") {
        if (Number.isInteger(item))
            return String(item);
        return String(item);
    }
    return String(item);
}
function toNumber(seq) {
    if (!seq || seq.length === 0)
        return NaN;
    let item = seq[0];
    if (item instanceof xmlmodel_1.Node)
        item = item.stringValue();
    if (typeof item === "boolean")
        return item ? 1.0 : 0.0;
    const num = Number(item);
    if (Number.isNaN(num))
        return NaN;
    return num;
}
function valueEqual(left, right) {
    return toString(left) === toString(right);
}
function matchPattern(pattern, item) {
    if (pattern instanceof ast.WildcardPattern)
        return [true, {}];
    if (pattern instanceof ast.AttributePattern) {
        if (item instanceof xmlmodel_1.Node && item.kind === "attribute" && item.name === pattern.name) {
            if (pattern.value !== null) {
                if (item.value === pattern.value.value) {
                    return [true, {}];
                }
                return [false, {}];
            }
            return [true, {}];
        }
        return [false, {}];
    }
    if (pattern instanceof ast.TypedPattern) {
        if (item === null || item === undefined)
            return [false, {}];
        if (pattern.kind === "node")
            return [item instanceof xmlmodel_1.Node, {}];
        if (pattern.kind === "element")
            return [item instanceof xmlmodel_1.Node && item.kind === "element", {}];
        if (pattern.kind === "text")
            return [item instanceof xmlmodel_1.Node && item.kind === "text", {}];
        if (pattern.kind === "comment")
            return [item instanceof xmlmodel_1.Node && item.kind === "comment", {}];
        if (pattern.kind === "pi")
            return [item instanceof xmlmodel_1.Node && item.kind === "pi", {}];
        if (pattern.kind === "document")
            return [item instanceof xmlmodel_1.Node && item.kind === "document", {}];
        return [false, {}];
    }
    if (pattern instanceof ast.LiteralPattern) {
        if (item instanceof xmlmodel_1.Node && item.kind === "text" && item.value === pattern.value) {
            return [true, {}];
        }
        return [false, {}];
    }
    if (pattern instanceof ast.ElementPattern) {
        if (item instanceof xmlmodel_1.Node && item.kind === "element" && item.name === pattern.name) {
            const bindings = {};
            for (const [attrName, attrValue] of pattern.attrs) {
                if (!(attrName in item.attrs)) {
                    return [false, {}];
                }
                if (attrValue !== null) {
                    if (item.attrs[attrName] !== attrValue.value) {
                        return [false, {}];
                    }
                }
            }
            if (pattern.varName !== null) {
                bindings[pattern.varName] = [...item.children];
                return [true, bindings];
            }
            if (pattern.children.length > 0) {
                if (item.children.length !== pattern.children.length) {
                    return [false, {}];
                }
                for (let i = 0; i < pattern.children.length; i += 1) {
                    const [matched, childBindings] = matchPattern(pattern.children[i], item.children[i]);
                    if (!matched) {
                        return [false, {}];
                    }
                    Object.assign(bindings, childBindings);
                }
                return [true, bindings];
            }
            return [true, {}];
        }
        return [false, {}];
    }
    return [false, {}];
}
function doApply(seq, ruleset, ctx) {
    if (ctx.recursionDepth >= MAX_RECURSION_DEPTH)
        throw new Error("XFDY0099");
    if (ruleset !== "main" && !(ruleset in ctx.rules)) {
        throw new Error("XFST0007");
    }
    const rules = ctx.rules[ruleset] ?? [];
    const out = [];
    for (const item of seq) {
        let matched = false;
        for (const rule of rules) {
            const [ok, bindings] = matchPattern(rule.pattern, item);
            if (ok) {
                matched = true;
                const newVars = { ...ctx.variables, ...bindings };
                out.push(...evalExpr(rule.body, new Context(item, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth + 1, ctx.version)));
                break;
            }
        }
        if (!matched) {
            out.push(...applyBuiltin(item, ruleset, ctx));
        }
    }
    return out;
}
function applyBuiltin(item, ruleset, ctx) {
    if (!(item instanceof xmlmodel_1.Node))
        return [];
    if (item.kind === "document") {
        return doApply([...item.children], ruleset, ctx);
    }
    if (item.kind === "element") {
        const newEl = new xmlmodel_1.Node({ kind: "element", name: item.name, attrs: { ...item.attrs } });
        const children = doApply([...item.children], ruleset, ctx);
        for (const c of children) {
            if (c instanceof xmlmodel_1.Node) {
                c.parent = newEl;
            }
        }
        newEl.children = children.filter((c) => c instanceof xmlmodel_1.Node);
        return [newEl];
    }
    if (item.kind === "attribute" || item.kind === "text" || item.kind === "comment" || item.kind === "pi") {
        return [(0, xmlmodel_1.deepCopy)(item, true)];
    }
    return [];
}
function fnString(args) {
    var _a;
    return [toString((_a = args[0]) !== null && _a !== void 0 ? _a : [])];
}
function fnNumber(args) {
    var _a;
    return [toNumber((_a = args[0]) !== null && _a !== void 0 ? _a : [])];
}
function fnBoolean(args) {
    var _a;
    return [toBoolean((_a = args[0]) !== null && _a !== void 0 ? _a : [])];
}
function fnTypeOf(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return ["null"];
    const item = args[0][0];
    if (item instanceof xmlmodel_1.Node)
        return ["node"];
    if (item instanceof FunctionRef)
        return ["function"];
    if (item && typeof item === "object" && !Array.isArray(item))
        return ["map"];
    if (typeof item === "boolean")
        return ["boolean"];
    if (typeof item === "number")
        return ["number"];
    if (item === null || item === undefined)
        return ["null"];
    return ["string"];
}
function fnName(args) {
    var _a;
    if (!args || args.length === 0 || args[0].length === 0)
        return [""];
    const item = args[0][0];
    if (!(item instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    return [(_a = item.name) !== null && _a !== void 0 ? _a : ""];
}
function fnAttr(args) {
    var _a;
    if (!args || args.length === 0 || args[0].length === 0)
        return [""];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    if (node.kind !== "element")
        return [""];
    if (args.length < 2)
        return [""];
    const key = toString(args[1]);
    return [(_a = node.attrs[key]) !== null && _a !== void 0 ? _a : ""];
}
function fnText(args, ctx, named) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [""];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    let deep = true;
    if (args.length > 1) {
        deep = toBoolean(args[1]);
    }
    else if (named && "deep" in named) {
        deep = toBoolean(named["deep"]);
    }
    if (deep)
        return [node.stringValue()];
    if (node.kind === "element" || node.kind === "document") {
        const direct = node.children
            .filter((c) => c.kind === "text")
            .map((c) => { var _a; return (_a = c.value) !== null && _a !== void 0 ? _a : ""; })
            .join("");
        return [direct];
    }
    return [node.stringValue()];
}
function fnChildren(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    return [...node.children];
}
function fnElements(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    if (node.kind !== "element" && node.kind !== "document")
        return [];
    const nameTest = args.length > 1 ? toString(args[1]) : null;
    let out = node.children.filter((c) => c.kind === "element");
    if (nameTest)
        out = out.filter((c) => c.name === nameTest);
    return out;
}
function fnAttributes(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    if (node.kind !== "element")
        return [];
    return Object.entries(node.attrs).map(([k, v]) => new xmlmodel_1.Node({ kind: "attribute", name: k, value: v }));
}
function fnCopy(args, ctx, named) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    const node = args[0][0];
    if (!(node instanceof xmlmodel_1.Node))
        throw new Error("XFDY0003");
    let recurse = true;
    if (args.length > 1) {
        recurse = toBoolean(args[1]);
    }
    else if (named && "recurse" in named) {
        recurse = toBoolean(named["recurse"]);
    }
    return [(0, xmlmodel_1.deepCopy)(node, recurse)];
}
function fnCount(args) {
    return [Number(args && args[0] ? args[0].length : 0)];
}
function fnEmpty(args) {
    return [!(args && args[0] && args[0].length > 0)];
}
function fnDistinct(args) {
    if (!args || args.length === 0)
        return [];
    const seen = new Set();
    const out = [];
    for (const item of args[0]) {
        const key = toString([item]);
        if (seen.has(key))
            continue;
        seen.add(key);
        out.push(item);
    }
    return out;
}
function fnSort(args, ctx) {
    if (!args || args.length === 0)
        return [];
    const seq = args[0];
    let keyFn = null;
    if (args.length > 1 && args[1] && args[1][0] instanceof FunctionRef) {
        keyFn = args[1][0].name;
    }
    if (keyFn) {
        return [...seq].sort((a, b) => {
            const ka = toString(callFunction(keyFn, [[a]], ctx));
            const kb = toString(callFunction(keyFn, [[b]], ctx));
            return ka.localeCompare(kb);
        });
    }
    return [...seq].sort((a, b) => toString([a]).localeCompare(toString([b])));
}
function fnConcat(args) {
    const out = [];
    for (const seq of args)
        out.push(...seq);
    return out;
}
function fnHead(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    return [args[0][0]];
}
function fnTail(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    return [...args[0].slice(1)];
}
function fnLast(args, ctx) {
    if (!args || args.length === 0 || args[0].length === 0) {
        if (ctx.last === null) {
            throw new Error("XFDY0003");
        }
        return [ctx.last];
    }
    const seq = args[0];
    if (seq.length === 0)
        return [];
    return [seq[seq.length - 1]];
}
function fnIndex(args, ctx, named, namedRaw) {
    if (!args || args.length === 0)
        return [];
    const seq = args[0];
    let keyFn = null;
    let keyExpr = null;
    if (args.length > 1 && args[1] && args[1][0] instanceof FunctionRef) {
        keyFn = args[1][0].name;
    }
    if (named && "key" in named) {
        const candidate = named["key"];
        if (candidate && candidate[0] instanceof FunctionRef) {
            keyFn = candidate[0].name;
        }
        else if (namedRaw && "key" in namedRaw) {
            keyExpr = namedRaw["key"];
        }
    }
    const index = {};
    for (const item of seq) {
        let key;
        if (keyFn) {
            key = toString(callFunction(keyFn, [[item]], ctx));
        }
        else if (keyExpr !== null) {
            const itemCtx = new Context(item, { ...ctx.variables }, ctx.functions, ctx.rules, ctx.position, ctx.last, ctx.recursionDepth, ctx.version);
            key = toString(evalExpr(keyExpr, itemCtx));
        }
        else {
            key = toString([item]);
        }
        if (!index[key])
            index[key] = [];
        index[key].push(item);
    }
    return [index];
}
function fnLookup(args) {
    var _a;
    if (args.length < 2)
        return [];
    if (!args[0] || args[0].length === 0)
        return [];
    const mapping = args[0][0];
    if (!mapping || typeof mapping !== "object" || Array.isArray(mapping))
        return [];
    const key = toString(args[1]);
    return (_a = mapping[key]) !== null && _a !== void 0 ? _a : [];
}
function fnGroupBy(args, ctx) {
    if (args.length < 2)
        return [];
    const seq = args[0];
    let keyFn = null;
    if (args[1] && args[1][0] instanceof FunctionRef) {
        keyFn = args[1][0].name;
    }
    const groups = {};
    for (const item of seq) {
        const key = keyFn ? toString(callFunction(keyFn, [[item]], ctx)) : toString([item]);
        if (!groups[key])
            groups[key] = [];
        groups[key].push(item);
    }
    return Object.entries(groups).map(([key, items]) => ({ key, items }));
}
function fnSeq(args) {
    const out = [];
    for (const seq of args)
        out.push(...seq);
    return out;
}
function fnPosition(_, ctx) {
    if (ctx.position === null) {
        throw new Error("XFDY0003");
    }
    return [ctx.position];
}
function fnApply(args, ctx) {
    if (!args || args.length === 0)
        return [];
    const seq = args[0];
    let ruleset = "main";
    if (args.length > 1 && args[1] && args[1].length > 0) {
        ruleset = toString(args[1]);
    }
    return doApply(seq, ruleset, ctx);
}
function fnSum(args) {
    if (!args || args.length === 0)
        return [0.0];
    let total = 0.0;
    for (const item of args[0])
        total += toNumber([item]);
    return [total];
}
function fnContains(args) {
    if (args.length < 2)
        return [false];
    return [toString(args[0]).indexOf(toString(args[1])) >= 0];
}
function fnStartsWith(args) {
    if (args.length < 2)
        return [false];
    return [toString(args[0]).startsWith(toString(args[1]))];
}
function fnEndsWith(args) {
    if (args.length < 2)
        return [false];
    return [toString(args[0]).endsWith(toString(args[1]))];
}
function fnSubstring(args) {
    const s = toString(args[0] ?? []);
    if (args.length < 2)
        return [""];
    const start = Math.floor(toNumber(args[1]));
    if (args.length > 2) {
        const length = Math.floor(toNumber(args[2]));
        return [s.substring(start - 1, start - 1 + length)];
    }
    return [s.substring(start - 1)];
}
function fnNormalizeSpace(args) {
    const s = toString(args[0] ?? []);
    return [s.trim().replace(/\s+/g, " ")];
}
function fnReplace(args) {
    if (args.length < 3)
        return [""];
    const s = toString(args[0]);
    const pattern = toString(args[1]);
    const replacement = toString(args[2]);
    return [s.split(pattern).join(replacement)];
}
function fnKeys(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [];
    const mapping = args[0][0];
    if (!mapping || typeof mapping !== "object" || Array.isArray(mapping))
        return [];
    return Object.keys(mapping);
}
function fnMapSize(args) {
    if (!args || args.length === 0 || args[0].length === 0)
        return [0.0];
    const mapping = args[0][0];
    if (!mapping || typeof mapping !== "object" || Array.isArray(mapping))
        return [0.0];
    return [Object.keys(mapping).length];
}
function fnStringLength(args) {
    const s = toString(args[0] ?? []);
    return [s.length];
}
function fnUpperCase(args) {
    const s = toString(args[0] ?? []);
    return [s.toUpperCase()];
}
function fnLowerCase(args) {
    const s = toString(args[0] ?? []);
    return [s.toLowerCase()];
}
function fnMatches(args) {
    if (!args || args.length < 2)
        return [false];
    const s = toString(args[0]);
    const pattern = toString(args[1]);
    return [s.indexOf(pattern) >= 0];
}
const BUILTINS = {
    string: (args) => fnString(args),
    number: (args) => fnNumber(args),
    boolean: (args) => fnBoolean(args),
    typeOf: (args) => fnTypeOf(args),
    name: (args) => fnName(args),
    attr: (args) => fnAttr(args),
    text: (args, ctx, named) => fnText(args, ctx, named),
    children: (args) => fnChildren(args),
    elements: (args) => fnElements(args),
    attributes: (args) => fnAttributes(args),
    copy: (args, ctx, named) => fnCopy(args, ctx, named),
    count: (args) => fnCount(args),
    empty: (args) => fnEmpty(args),
    distinct: (args) => fnDistinct(args),
    sort: (args, ctx) => fnSort(args, ctx),
    concat: (args) => fnConcat(args),
    index: (args, ctx, named, namedRaw) => fnIndex(args, ctx, named, namedRaw),
    lookup: (args) => fnLookup(args),
    groupBy: (args, ctx) => fnGroupBy(args, ctx),
    seq: (args) => fnSeq(args),
    sum: (args) => fnSum(args),
    head: (args) => fnHead(args),
    tail: (args) => fnTail(args),
    last: (args, ctx) => fnLast(args, ctx),
    position: (args, ctx) => fnPosition(args, ctx),
    apply: (args, ctx) => fnApply(args, ctx),
    contains: (args) => fnContains(args),
    startsWith: (args) => fnStartsWith(args),
    endsWith: (args) => fnEndsWith(args),
    substring: (args) => fnSubstring(args),
    normalizeSpace: (args) => fnNormalizeSpace(args),
    replace: (args) => fnReplace(args),
    keys: (args) => fnKeys(args),
    mapSize: (args) => fnMapSize(args),
    stringLength: (args) => fnStringLength(args),
    upperCase: (args) => fnUpperCase(args),
    lowerCase: (args) => fnLowerCase(args),
    matches: (args) => fnMatches(args),
};
function serializeItem(item) {
    if (item instanceof xmlmodel_1.Node)
        return (0, xmlmodel_1.serialize)(item);
    return toString([item]);
}
