import * as ast from "./ast";
import { Node, deepCopy, iterDescendants, serialize } from "./xmlmodel";

export class Context {
  contextItem: any;
  variables: Record<string, any[]>;
  functions: Record<string, ast.FunctionDef>;
  rules: Record<string, ast.RuleDef[]>;
  position: number | null;
  last: number | null;

  constructor(
    contextItem: any,
    variables: Record<string, any[]>,
    functions: Record<string, ast.FunctionDef>,
    rules: Record<string, ast.RuleDef[]>,
    position: number | null = null,
    last: number | null = null,
  ) {
    this.contextItem = contextItem;
    this.variables = variables;
    this.functions = functions;
    this.rules = rules;
    this.position = position;
    this.last = last;
  }
}

export function evalModule(module: ast.Module, doc: Node): any[] {
  const functions = { ...module.functions };
  const rules = { ...module.rules };
  const variables: Record<string, any[]> = {};
  const ctx = new Context(doc, variables, functions, rules);
  for (const [name, expr] of Object.entries(module.vars)) {
    variables[name] = evalExpr(expr, ctx);
  }
  if (module.expr === null) return [];
  return evalExpr(module.expr, ctx);
}

export function evalExpr(expr: ast.Expr, ctx: Context): any[] {
  if (expr instanceof ast.Literal) {
    return [expr.value];
  }
  if (expr instanceof ast.VarRef) {
    if (expr.name in ctx.variables) return ctx.variables[expr.name];
    if (expr.name in ctx.functions) return [new FunctionRef(expr.name)];
    if (ctx.contextItem instanceof Node) {
      return ctx.contextItem.children.filter(
        (c) => c.kind === "element" && c.name === expr.name,
      );
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
    return evalExpr(expr.body, new Context(ctx.contextItem, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last));
  }
  if (expr instanceof ast.ForExpr) {
    const seq = evalExpr(expr.seq, ctx);
    const out: any[] = [];
    const total = seq.length;
    seq.forEach((item, idx) => {
      const newVars = { ...ctx.variables, [expr.name]: [item] };
      const newCtx = new Context(item, newVars, ctx.functions, ctx.rules, idx + 1, total);
      if (expr.where) {
        if (!toBoolean(evalExpr(expr.where, newCtx))) return;
      }
      out.push(...evalExpr(expr.body, newCtx));
    });
    return out;
  }
  if (expr instanceof ast.MatchExpr) {
    const targetSeq = evalExpr(expr.target, ctx);
    const out: any[] = [];
    for (const target of targetSeq) {
      let matchedAny = false;
      for (const [pattern, body] of expr.cases) {
        const [matched, bindings] = matchPattern(pattern, target);
        if (matched) {
          matchedAny = true;
          const newVars = { ...ctx.variables, ...bindings };
          out.push(
            ...evalExpr(body, new Context(target, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last)),
          );
          break;
        }
      }
      if (!matchedAny) {
        if (!expr.defaultExpr) throw new Error("XFDY0001: no matching case");
        out.push(
          ...evalExpr(expr.defaultExpr, new Context(target, { ...ctx.variables }, ctx.functions, ctx.rules, ctx.position, ctx.last)),
        );
      }
    }
    return out;
  }
  if (expr instanceof ast.FuncCall) {
    const args = expr.args.map((a) => evalExpr(a, ctx));
    return callFunction(expr.name, args, ctx);
  }
  if (expr instanceof ast.UnaryOp) {
    const val = evalExpr(expr.expr, ctx);
    if (expr.op === "-") return [-toNumber(val)];
    if (expr.op === "not") return [!toBoolean(val)];
  }
  if (expr instanceof ast.BinaryOp) {
    if (expr.op === "and") {
      const left = evalExpr(expr.left, ctx);
      if (!toBoolean(left)) return [false];
      const right = evalExpr(expr.right, ctx);
      return [toBoolean(right)];
    }
    if (expr.op === "or") {
      const left = evalExpr(expr.left, ctx);
      if (toBoolean(left)) return [true];
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
    return [new Node({ kind: "text", value: toString(evalExpr(expr.expr, ctx)) })];
  }
  if (expr instanceof ast.Text) {
    return [expr.value];
  }
  if (expr instanceof ast.Interp) {
    return evalExpr(expr.expr, ctx);
  }
  throw new Error(`Unknown expr ${String(expr)}`);
}

export function evalBinary(op: string, left: any[], right: any[]): any {
  if (op === "and") return toBoolean(left) && toBoolean(right);
  if (op === "or") return toBoolean(left) || toBoolean(right);
  if (op === "=") return valueEqual(left, right);
  if (op === "!=") return !valueEqual(left, right);
  const lnum = toNumber(left);
  const rnum = toNumber(right);
  if (op === "+") return lnum + rnum;
  if (op === "-") return lnum - rnum;
  if (op === "*") return lnum * rnum;
  if (op === "div") return lnum / rnum;
  if (op === "mod") return lnum % rnum;
  if (op === "<") return lnum < rnum;
  if (op === "<=") return lnum <= rnum;
  if (op === ">") return lnum > rnum;
  if (op === ">=") return lnum >= rnum;
  throw new Error(`Unknown operator ${op}`);
}

export function evalPath(expr: ast.PathExpr, ctx: Context): any[] {
  let steps = [...expr.steps];
  let base: any[] = [];
  if (expr.start.kind === "context") {
    base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
  } else if (expr.start.kind === "root") {
    base = rootOf(ctx.contextItem);
  } else if (expr.start.kind === "desc") {
    base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
  } else if (expr.start.kind === "desc_root") {
    base = rootOf(ctx.contextItem);
  } else if (expr.start.kind === "var") {
    if (expr.start.name && expr.start.name in ctx.variables) {
      base = ctx.variables[expr.start.name];
    } else {
      base = ctx.contextItem !== null && ctx.contextItem !== undefined ? [ctx.contextItem] : [];
      if (expr.start.name) {
        steps = [new ast.PathStep("child", new ast.StepTest("name", expr.start.name), []), ...steps];
      }
    }
  }

  let current = base;
  for (const step of steps) {
    current = applyStep(current, step, ctx);
  }
  return current;
}

function rootOf(item: any): any[] {
  if (item instanceof Node) {
    let node: Node = item;
    while (node.parent) node = node.parent;
    return [node];
  }
  return [];
}

function descOrSelf(items: any[]): any[] {
  const out: any[] = [];
  for (const item of items) {
    if (item instanceof Node) {
      out.push(item);
      for (const d of iterDescendants(item)) out.push(d);
    }
  }
  return out;
}

export function applyStep(items: any[], step: ast.PathStep, ctx: Context): any[] {
  const out: any[] = [];
  for (const item of items) {
    if (!(item instanceof Node)) continue;
    let candidates: Node[] = [];
    if (step.axis === "self") {
      candidates = [item];
    } else if (step.axis === "parent") {
      candidates = item.parent ? [item.parent] : [];
    } else if (step.axis === "desc_or_self") {
      candidates = [item, ...Array.from(iterDescendants(item))];
    } else if (step.axis === "desc") {
      candidates = Array.from(iterDescendants(item));
    } else if (step.axis === "attr") {
      if (item.kind === "element") {
        if (step.test.kind === "name") {
          const name = step.test.name as string;
          if (name in item.attrs) {
            candidates = [new Node({ kind: "attribute", name, value: item.attrs[name] })];
          } else {
            candidates = [];
          }
        } else if (step.test.kind === "wildcard") {
          candidates = Object.entries(item.attrs).map(
            ([k, v]) => new Node({ kind: "attribute", name: k, value: v }),
          );
        } else {
          candidates = [];
        }
      }
    } else if (step.axis === "child") {
      candidates = item.children;
    }

    let filtered = candidates.filter((c) => matchesStepTest(step.test, c));
    for (const pred of step.predicates) {
      const predOut: Node[] = [];
      for (let i = 0; i < filtered.length; i += 1) {
        const child = filtered[i];
        const predCtx = new Context(child, ctx.variables, ctx.functions, ctx.rules, i + 1, filtered.length);
        if (toBoolean(evalExpr(pred, predCtx))) predOut.push(child);
      }
      filtered = predOut;
    }
    out.push(...filtered);
  }
  return out;
}

function matchesStepTest(test: ast.StepTest, node: Node): boolean {
  if (test.kind === "wildcard") return node.kind === "element";
  if (test.kind === "text") return node.kind === "text";
  if (test.kind === "node") return true;
  if (test.kind === "comment") return node.kind === "comment";
  if (test.kind === "pi") return node.kind === "pi";
  if (test.kind === "name") return node.name === test.name;
  return false;
}

export function evalConstructor(expr: ast.Constructor, ctx: Context): Node {
  const node = new Node({ kind: "element", name: expr.name });
  for (const [name, aexpr] of expr.attrs) {
    const val = evalExpr(aexpr, ctx);
    node.attrs[name] = toString(val);
  }
  const children: Node[] = [];
  for (const content of expr.contents) {
    if (content instanceof ast.Text) {
      children.push(new Node({ kind: "text", value: content.value }));
      continue;
    }
    const seq = evalExpr(content, ctx);
    for (const item of seq) {
      if (item instanceof Node) {
        children.push(deepCopy(item, true));
      } else {
        children.push(new Node({ kind: "text", value: toString([item]) }));
      }
    }
  }
  for (const child of children) child.parent = node;
  node.children = children;
  return node;
}

export class FunctionRef {
  name: string;
  constructor(name: string) {
    this.name = name;
  }
}

export function callFunction(name: string, args: any[][], ctx: Context): any[] {
  if (name in ctx.functions) {
    const func = ctx.functions[name];
    const params = func.params;
    if (args.length > params.length) throw new Error("XFDY0002: wrong arity");
    const newVars: Record<string, any[]> = { ...ctx.variables };
    for (let i = 0; i < args.length; i += 1) {
      newVars[params[i].name] = args[i];
    }
    if (args.length < params.length) {
      for (let i = args.length; i < params.length; i += 1) {
        const param = params[i];
        if (!param.defaultExpr) throw new Error("XFDY0002: wrong arity");
        newVars[param.name] = evalExpr(param.defaultExpr, ctx);
      }
    }
    const newCtx = new Context(ctx.contextItem, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last);
    return evalExpr(func.body, newCtx);
  }

  const fn = BUILTINS[name];
  if (!fn) throw new Error(`XFST0003: unknown function ${name}`);
  return fn(args, ctx);
}

export function toBoolean(seq: any[]): boolean {
  if (!seq || seq.length === 0) return false;
  if (seq.some((i) => i instanceof Node)) return true;
  for (const item of seq) {
    if (![false, 0, 0.0, "", null, undefined].includes(item)) return true;
  }
  return false;
}

export function toString(seq: any[]): string {
  if (!seq || seq.length === 0) return "";
  const item = seq[0];
  if (item instanceof Node) return item.stringValue();
  if (item === null || item === undefined) return "";
  if (typeof item === "boolean") return item ? "true" : "false";
  if (typeof item === "number") {
    return Number.isInteger(item) ? String(item) : String(item);
  }
  return String(item);
}

export function toNumber(seq: any[]): number {
  if (!seq || seq.length === 0) return 0.0;
  let item: any = seq[0];
  if (item instanceof Node) item = item.stringValue();
  if (typeof item === "boolean") return item ? 1.0 : 0.0;
  const num = Number(item);
  if (Number.isNaN(num)) throw new Error("XFDY0002: number conversion");
  return num;
}

export function valueEqual(left: any[], right: any[]): boolean {
  return toString(left) === toString(right);
}

export function matchPattern(pattern: ast.Pattern, item: any): [boolean, Record<string, any[]>] {
  if (pattern instanceof ast.WildcardPattern) return [true, {}];
  if (pattern instanceof ast.AttributePattern) {
    if (item instanceof Node && item.kind === "attribute" && item.name === pattern.name) return [true, {}];
    return [false, {}];
  }
  if (pattern instanceof ast.TypedPattern) {
    if (item === null || item === undefined) return [false, {}];
    if (pattern.kind === "node") return [item instanceof Node, {}];
    if (pattern.kind === "text") return [item instanceof Node && item.kind === "text", {}];
    if (pattern.kind === "comment") return [item instanceof Node && item.kind === "comment", {}];
    return [false, {}];
  }
  if (pattern instanceof ast.ElementPattern) {
    if (item instanceof Node && item.kind === "element" && item.name === pattern.name) {
      const bindings: Record<string, any[]> = {};
      if (pattern.varName) {
        bindings[pattern.varName] = [...item.children];
        return [true, bindings];
      }
      if (pattern.child) {
        for (const child of item.children) {
          const [matched, childBindings] = matchPattern(pattern.child, child);
          if (matched) {
            Object.assign(bindings, childBindings);
            return [true, bindings];
          }
        }
        return [false, {}];
      }
      return [true, {}];
    }
    return [false, {}];
  }
  return [false, {}];
}

// Builtins

type BuiltinFn = (args: any[][], ctx: Context) => any[];

function fnString(args: any[][]): any[] {
  return [toString(args[0] ?? [])];
}

function fnNumber(args: any[][]): any[] {
  return [toNumber(args[0] ?? [])];
}

function fnBoolean(args: any[][]): any[] {
  return [toBoolean(args[0] ?? [])];
}

function fnTypeOf(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return ["null"];
  const item = args[0][0];
  if (item instanceof Node) return ["node"];
  if (item && typeof item === "object" && !Array.isArray(item)) return ["map"];
  if (typeof item === "boolean") return ["boolean"];
  if (typeof item === "number") return ["number"];
  if (item === null || item === undefined) return ["null"];
  return ["string"];
}

function fnName(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [""];
  const item = args[0][0];
  if (item instanceof Node) return [item.name ?? ""];
  return [""];
}

function fnAttr(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [""];
  const node = args[0][0];
  if (!(node instanceof Node) || node.kind !== "element") return [""];
  if (args.length < 2) return [""];
  const key = toString(args[1]);
  return [node.attrs[key] ?? ""];
}

function fnText(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [""];
  const node = args[0][0];
  if (node instanceof Node) {
    let deep = true;
    if (args.length > 1) deep = toBoolean(args[1]);
    if (deep) return [node.stringValue()];
    if (node.kind === "element" || node.kind === "document") {
      const direct = node.children
        .filter((c) => c.kind === "text")
        .map((c) => c.value ?? "")
        .join("");
      return [direct];
    }
    return [node.stringValue()];
  }
  return [toString(args[0])];
}

function fnChildren(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [];
  const node = args[0][0];
  if (node instanceof Node) return [...node.children];
  return [];
}

function fnElements(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [];
  const node = args[0][0];
  if (!(node instanceof Node) || (node.kind !== "element" && node.kind !== "document")) return [];
  const nameTest = args.length > 1 ? toString(args[1]) : null;
  let out = node.children.filter((c) => c.kind === "element");
  if (nameTest) out = out.filter((c) => c.name === nameTest);
  return out;
}

function fnCopy(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [];
  const node = args[0][0];
  if (!(node instanceof Node)) return [];
  let recurse = true;
  if (args.length > 1) recurse = toBoolean(args[1]);
  return [deepCopy(node, recurse)];
}

function fnCount(args: any[][]): any[] {
  return [Number(args && args[0] ? args[0].length : 0)];
}

function fnEmpty(args: any[][]): any[] {
  return [!(args && args[0] && args[0].length > 0)];
}

function fnDistinct(args: any[][]): any[] {
  if (!args || args.length === 0) return [];
  const seen = new Set<string>();
  const out: any[] = [];
  for (const item of args[0]) {
    const key = toString([item]);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

function fnSort(args: any[][], ctx: Context): any[] {
  if (!args || args.length === 0) return [];
  const seq = args[0];
  let keyFn: string | null = null;
  if (args.length > 1 && args[1] && args[1][0] instanceof FunctionRef) {
    keyFn = (args[1][0] as FunctionRef).name;
  }
  if (keyFn) {
    return [...seq].sort((a, b) => {
      const ka = toString(callFunction(keyFn as string, [[a]], ctx));
      const kb = toString(callFunction(keyFn as string, [[b]], ctx));
      return ka.localeCompare(kb);
    });
  }
  return [...seq].sort((a, b) => toString([a]).localeCompare(toString([b])));
}

function fnConcat(args: any[][]): any[] {
  const out: any[] = [];
  for (const seq of args) out.push(...seq);
  return out;
}

function fnHead(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [];
  return [args[0][0]];
}

function fnTail(args: any[][]): any[] {
  if (!args || args.length === 0 || args[0].length === 0) return [];
  return [...args[0].slice(1)];
}

function fnLast(args: any[][], ctx: Context): any[] {
  if (!args || args.length === 0 || args[0].length === 0) {
    if (ctx.last === null) return [];
    return [ctx.last];
  }
  const seq = args[0];
  if (seq.length === 0) return [];
  return [seq[seq.length - 1]];
}

function fnIndex(args: any[][], ctx: Context): any[] {
  if (!args || args.length === 0) return [];
  const seq = args[0];
  let keyFn: string | null = null;
  if (args.length > 1 && args[1] && args[1][0] instanceof FunctionRef) {
    keyFn = (args[1][0] as FunctionRef).name;
  }
  const index: Record<string, any[]> = {};
  for (const item of seq) {
    const key = keyFn ? toString(callFunction(keyFn, [[item]], ctx)) : toString([item]);
    if (!index[key]) index[key] = [];
    index[key].push(item);
  }
  return [index];
}

function fnLookup(args: any[][]): any[] {
  if (args.length < 2) return [];
  if (!args[0] || args[0].length === 0) return [];
  const mapping = args[0][0];
  if (!mapping || typeof mapping !== "object" || Array.isArray(mapping)) return [];
  const key = toString(args[1]);
  return mapping[key] ?? [];
}

function fnGroupBy(args: any[][], ctx: Context): any[] {
  if (args.length < 2) return [];
  const seq = args[0];
  let keyFn: string | null = null;
  if (args[1] && args[1][0] instanceof FunctionRef) {
    keyFn = (args[1][0] as FunctionRef).name;
  }
  const groups: Record<string, any[]> = {};
  for (const item of seq) {
    const key = keyFn ? toString(callFunction(keyFn, [[item]], ctx)) : toString([item]);
    if (!groups[key]) groups[key] = [];
    groups[key].push(item);
  }
  return Object.entries(groups).map(([key, items]) => ({ key, items }));
}

function fnSeq(args: any[][]): any[] {
  const out: any[] = [];
  for (const seq of args) out.push(...seq);
  return out;
}

function fnPosition(_: any[][], ctx: Context): any[] {
  if (ctx.position === null) return [];
  return [ctx.position];
}

function fnApply(args: any[][], ctx: Context): any[] {
  if (!args || args.length === 0) return [];
  const seq = args[0];
  let ruleset = "main";
  if (args.length > 1 && args[1] && args[1].length > 0) {
    ruleset = toString(args[1]);
  }
  const rules = ctx.rules[ruleset] ?? [];
  const out: any[] = [];
  for (const item of seq) {
    let matched = false;
    for (const rule of rules) {
      const [ok, bindings] = matchPattern(rule.pattern, item);
      if (ok) {
        matched = true;
        const newVars = { ...ctx.variables, ...bindings };
        out.push(
          ...evalExpr(rule.body, new Context(item, newVars, ctx.functions, ctx.rules, ctx.position, ctx.last)),
        );
        break;
      }
    }
    if (!matched) throw new Error("XFDY0001: no matching rule");
  }
  return out;
}

function fnSum(args: any[][]): any[] {
  if (!args || args.length === 0) return [0.0];
  let total = 0.0;
  for (const item of args[0]) total += toNumber([item]);
  return [total];
}

const BUILTINS: Record<string, BuiltinFn> = {
  string: (args) => fnString(args),
  number: (args) => fnNumber(args),
  boolean: (args) => fnBoolean(args),
  typeOf: (args) => fnTypeOf(args),
  name: (args) => fnName(args),
  attr: (args) => fnAttr(args),
  text: (args) => fnText(args),
  children: (args) => fnChildren(args),
  elements: (args) => fnElements(args),
  copy: (args) => fnCopy(args),
  count: (args) => fnCount(args),
  empty: (args) => fnEmpty(args),
  distinct: (args) => fnDistinct(args),
  sort: (args, ctx) => fnSort(args, ctx),
  concat: (args) => fnConcat(args),
  index: (args, ctx) => fnIndex(args, ctx),
  lookup: (args) => fnLookup(args),
  groupBy: (args, ctx) => fnGroupBy(args, ctx),
  seq: (args) => fnSeq(args),
  sum: (args) => fnSum(args),
  head: (args) => fnHead(args),
  tail: (args) => fnTail(args),
  last: (args, ctx) => fnLast(args, ctx),
  position: (args, ctx) => fnPosition(args, ctx),
  apply: (args, ctx) => fnApply(args, ctx),
};

export function serializeItem(item: any): string {
  if (item instanceof Node) return serialize(item);
  return toString([item]);
}
