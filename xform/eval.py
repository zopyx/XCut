from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, Iterable, List, Optional

from . import ast
from .xmlmodel import Node, deep_copy, iter_descendants, serialize


@dataclass
class Context:
    context_item: Optional[Any]
    variables: Dict[str, List[Any]]
    functions: Dict[str, ast.FunctionDef]
    rules: Dict[str, List[ast.RuleDef]]
    position: Optional[int] = None
    last: Optional[int] = None


def eval_module(module: ast.Module, doc: Node) -> List[Any]:
    functions = dict(module.functions)
    rules = dict(module.rules)
    variables: Dict[str, List[Any]] = {}
    ctx = Context(context_item=doc, variables=variables, functions=functions, rules=rules)
    for name, expr in module.vars.items():
        variables[name] = eval_expr(expr, ctx)
    if module.expr is None:
        return []
    return eval_expr(module.expr, ctx)


def eval_expr(expr: ast.Expr, ctx: Context) -> List[Any]:
    if isinstance(expr, ast.Literal):
        return [expr.value]
    if isinstance(expr, ast.VarRef):
        if expr.name in ctx.variables:
            return ctx.variables[expr.name]
        if expr.name in ctx.functions:
            return [FunctionRef(expr.name)]
        if isinstance(ctx.context_item, Node):
            return [
                child
                for child in ctx.context_item.children
                if child.kind == "element" and child.name == expr.name
            ]
        return []
    if isinstance(expr, ast.IfExpr):
        cond = to_boolean(eval_expr(expr.cond, ctx))
        return eval_expr(expr.then_expr, ctx) if cond else eval_expr(expr.else_expr, ctx)
    if isinstance(expr, ast.LetExpr):
        value = eval_expr(expr.value, ctx)
        new_vars = dict(ctx.variables)
        new_vars[expr.name] = value
        return eval_expr(
            expr.body,
            Context(ctx.context_item, new_vars, ctx.functions, ctx.rules, ctx.position, ctx.last),
        )
    if isinstance(expr, ast.ForExpr):
        seq = eval_expr(expr.seq, ctx)
        out: List[Any] = []
        total = len(seq)
        for idx, item in enumerate(seq, start=1):
            new_vars = dict(ctx.variables)
            new_vars[expr.name] = [item]
            new_ctx = Context(
                context_item=item,
                variables=new_vars,
                functions=ctx.functions,
                rules=ctx.rules,
                position=idx,
                last=total,
            )
            if expr.where is not None:
                if not to_boolean(eval_expr(expr.where, new_ctx)):
                    continue
            out.extend(eval_expr(expr.body, new_ctx))
        return out
    if isinstance(expr, ast.MatchExpr):
        target_seq = eval_expr(expr.target, ctx)
        out: List[Any] = []
        for target in target_seq:
            matched_any = False
            for pattern, body in expr.cases:
                matched, bindings = match_pattern(pattern, target)
                if matched:
                    matched_any = True
                    new_vars = dict(ctx.variables)
                    new_vars.update(bindings)
                    out.extend(
                        eval_expr(
                            body,
                            Context(
                                target,
                                new_vars,
                                ctx.functions,
                                ctx.rules,
                                ctx.position,
                                ctx.last,
                            ),
                        )
                    )
                    break
            if not matched_any:
                if expr.default is None:
                    raise RuntimeError("XFDY0001: no matching case")
                out.extend(
                    eval_expr(
                        expr.default,
                        Context(
                            target,
                            dict(ctx.variables),
                            ctx.functions,
                            ctx.rules,
                            ctx.position,
                            ctx.last,
                        ),
                    )
                )
        return out
    if isinstance(expr, ast.FuncCall):
        args = [eval_expr(a, ctx) for a in expr.args]
        return call_function(expr.name, args, ctx)
    if isinstance(expr, ast.UnaryOp):
        val = eval_expr(expr.expr, ctx)
        if expr.op == "-":
            return [-to_number(val)]
        if expr.op == "not":
            return [not to_boolean(val)]
    if isinstance(expr, ast.BinaryOp):
        if expr.op == "and":
            left = eval_expr(expr.left, ctx)
            if not to_boolean(left):
                return [False]
            right = eval_expr(expr.right, ctx)
            return [to_boolean(right)]
        if expr.op == "or":
            left = eval_expr(expr.left, ctx)
            if to_boolean(left):
                return [True]
            right = eval_expr(expr.right, ctx)
            return [to_boolean(right)]
        left = eval_expr(expr.left, ctx)
        right = eval_expr(expr.right, ctx)
        return [eval_binary(expr.op, left, right)]
    if isinstance(expr, ast.PathExpr):
        return eval_path(expr, ctx)
    if isinstance(expr, ast.Constructor):
        return [eval_constructor(expr, ctx)]
    if isinstance(expr, ast.TextConstructor):
        return [Node(kind="text", value=to_string(eval_expr(expr.expr, ctx)))]
    if isinstance(expr, ast.Text):
        return [expr.value]
    if isinstance(expr, ast.Interp):
        return eval_expr(expr.expr, ctx)
    raise RuntimeError(f"Unknown expr {expr}")


def eval_binary(op: str, left: List[Any], right: List[Any]) -> Any:
    if op == "and":
        return to_boolean(left) and to_boolean(right)
    if op == "or":
        return to_boolean(left) or to_boolean(right)
    lnum = to_number(left)
    rnum = to_number(right)
    if op == "+":
        return lnum + rnum
    if op == "-":
        return lnum - rnum
    if op == "*":
        return lnum * rnum
    if op == "div":
        return lnum / rnum
    if op == "mod":
        return lnum % rnum
    if op == "=":
        return value_equal(left, right)
    if op == "!=":
        return not value_equal(left, right)
    if op == "<":
        return lnum < rnum
    if op == "<=":
        return lnum <= rnum
    if op == ">":
        return lnum > rnum
    if op == ">=":
        return lnum >= rnum
    raise RuntimeError(f"Unknown operator {op}")


def eval_path(expr: ast.PathExpr, ctx: Context) -> List[Any]:
    steps = list(expr.steps)
    if expr.start.kind == "context":
        base = [ctx.context_item] if ctx.context_item is not None else []
    elif expr.start.kind == "root":
        base = _root_of(ctx.context_item)
    elif expr.start.kind == "desc":
        base = [ctx.context_item] if ctx.context_item is not None else []
    elif expr.start.kind == "desc_root":
        base = _root_of(ctx.context_item)
    elif expr.start.kind == "var":
        if expr.start.name in ctx.variables:
            base = ctx.variables.get(expr.start.name, [])
        else:
            base = [ctx.context_item] if ctx.context_item is not None else []
            steps = [ast.PathStep("child", ast.StepTest("name", expr.start.name), [])] + steps
    else:
        base = []

    current = base
    for step in steps:
        current = apply_step(current, step, ctx)
    return current


def _root_of(item: Optional[Any]) -> List[Any]:
    if isinstance(item, Node):
        node = item
        while node.parent is not None:
            node = node.parent
        return [node]
    return []


def _desc_or_self(items: List[Any]) -> List[Any]:
    out: List[Any] = []
    for item in items:
        if isinstance(item, Node):
            out.append(item)
            out.extend(list(iter_descendants(item)))
    return out


def apply_step(items: List[Any], step: ast.PathStep, ctx: Context) -> List[Any]:
    out: List[Any] = []
    for item in items:
        if not isinstance(item, Node):
            continue
        if step.axis == "self":
            candidates = [item]
        elif step.axis == "parent":
            candidates = [item.parent] if item.parent is not None else []
        elif step.axis == "desc_or_self":
            candidates = [item]
            candidates.extend(list(iter_descendants(item)))
        elif step.axis == "desc":
            candidates = list(iter_descendants(item))
        elif step.axis == "attr":
            if item.kind == "element":
                if step.test.kind == "name":
                    name = step.test.name
                    if name in item.attrs:
                        candidates = [Node(kind="attribute", name=name, value=item.attrs[name])]
                    else:
                        candidates = []
                elif step.test.kind == "wildcard":
                    candidates = [
                        Node(kind="attribute", name=k, value=v)
                        for k, v in item.attrs.items()
                    ]
                else:
                    candidates = []
            else:
                candidates = []
        else:
            candidates = item.children if item.kind in ("element", "document") else []

        matched: List[Any] = []
        for cand in candidates:
            if step.axis in ("attr", "self", "parent"):
                if _matches_test(cand, step.test):
                    matched.append(cand)
                continue
            if _matches_test(cand, step.test):
                matched.append(cand)
        for cand in matched:
            if all(
                to_boolean(
                    eval_expr(
                        pred,
                        Context(cand, dict(ctx.variables), ctx.functions, ctx.rules, ctx.position, ctx.last),
                    )
                )
                for pred in step.predicates
            ):
                out.append(cand)
    return out


def _matches_test(node: Node, test: ast.StepTest) -> bool:
    if test.kind == "node":
        return True
    if test.kind == "wildcard":
        return node.kind == "element"
    if test.kind == "text":
        return node.kind == "text"
    if test.kind == "comment":
        return node.kind == "comment"
    if test.kind == "pi":
        return node.kind == "pi"
    if test.kind == "name":
        return node.name == test.name
    return False


def eval_constructor(expr: ast.Constructor, ctx: Context) -> Node:
    node = Node(kind="element", name=expr.name)
    for name, aexpr in expr.attrs:
        val = eval_expr(aexpr, ctx)
        node.attrs[name] = to_string(val)
    children: List[Node] = []
    for content in expr.contents:
        if isinstance(content, ast.Text):
            children.append(Node(kind="text", value=content.value))
            continue
        seq = eval_expr(content, ctx)
        for item in seq:
            if isinstance(item, Node):
                children.append(deep_copy(item))
            else:
                children.append(Node(kind="text", value=to_string([item])))
    for child in children:
        child.parent = node
    node.children = children
    return node


@dataclass
class FunctionRef:
    name: str


def call_function(name: str, args: List[List[Any]], ctx: Context) -> List[Any]:
    if name in ctx.functions:
        func = ctx.functions[name]
        params = func.params
        body = func.body
        if len(args) > len(params):
            raise RuntimeError("XFDY0002: wrong arity")
        new_vars = dict(ctx.variables)
        for param, value in zip(params, args):
            new_vars[param.name] = value
        if len(args) < len(params):
            for param in params[len(args) :]:
                if param.default is None:
                    raise RuntimeError("XFDY0002: wrong arity")
                new_vars[param.name] = eval_expr(param.default, ctx)
        new_ctx = Context(ctx.context_item, new_vars, ctx.functions, ctx.rules, ctx.position, ctx.last)
        return eval_expr(body, new_ctx)

    fn = BUILTINS.get(name)
    if fn is None:
        raise RuntimeError(f"XFST0003: unknown function {name}")
    return fn(args, ctx)


def to_boolean(seq: List[Any]) -> bool:
    if not seq:
        return False
    if any(isinstance(i, Node) for i in seq):
        return True
    for item in seq:
        if item not in (False, 0, 0.0, "", None):
            return True
    return False


def to_string(seq: List[Any]) -> str:
    if not seq:
        return ""
    item = seq[0]
    if isinstance(item, Node):
        return item.string_value()
    if item is None:
        return ""
    if isinstance(item, bool):
        return "true" if item else "false"
    if isinstance(item, float):
        if item.is_integer():
            return str(int(item))
        return str(item)
    return str(item)


def to_number(seq: List[Any]) -> float:
    if not seq:
        return 0.0
    item = seq[0]
    if isinstance(item, Node):
        item = item.string_value()
    if isinstance(item, bool):
        return 1.0 if item else 0.0
    try:
        return float(item)
    except Exception as exc:
        raise RuntimeError("XFDY0002: number conversion") from exc


def value_equal(left: List[Any], right: List[Any]) -> bool:
    return to_string(left) == to_string(right)


def match_pattern(pattern: ast.Pattern, item: Any) -> tuple[bool, Dict[str, List[Any]]]:
    if isinstance(pattern, ast.WildcardPattern):
        return True, {}
    if isinstance(pattern, ast.AttributePattern):
        if isinstance(item, Node) and item.kind == "attribute" and item.name == pattern.name:
            return True, {}
        return False, {}
    if isinstance(pattern, ast.TypedPattern):
        if item is None:
            return False, {}
        if pattern.kind == "node":
            return isinstance(item, Node), {}
        if pattern.kind == "text":
            return isinstance(item, Node) and item.kind == "text", {}
        if pattern.kind == "comment":
            return isinstance(item, Node) and item.kind == "comment", {}
        return False, {}
    if isinstance(pattern, ast.ElementPattern):
        if isinstance(item, Node) and item.kind == "element" and item.name == pattern.name:
            bindings: Dict[str, List[Any]] = {}
            if pattern.var is not None:
                bindings[pattern.var] = list(item.children)
                return True, bindings
            if pattern.child is not None:
                for child in item.children:
                    matched, child_bindings = match_pattern(pattern.child, child)
                    if matched:
                        bindings.update(child_bindings)
                        return True, bindings
                return False, {}
            return True, {}
        return False, {}
    return False, {}


# Built-in functions

def _fn_string(args: List[List[Any]], ctx: Context) -> List[Any]:
    return [to_string(args[0] if args else [])]


def _fn_number(args: List[List[Any]], ctx: Context) -> List[Any]:
    return [to_number(args[0] if args else [])]


def _fn_boolean(args: List[List[Any]], ctx: Context) -> List[Any]:
    return [to_boolean(args[0] if args else [])]


def _fn_typeof(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return ["null"]
    item = args[0][0]
    if isinstance(item, Node):
        return ["node"]
    if isinstance(item, dict):
        return ["map"]
    if isinstance(item, bool):
        return ["boolean"]
    if isinstance(item, (int, float)):
        return ["number"]
    if item is None:
        return ["null"]
    return ["string"]


def _fn_name(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return [""]
    item = args[0][0]
    if isinstance(item, Node):
        return [item.name or ""]
    return [""]


def _fn_attr(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return [""]
    node = args[0][0]
    if not isinstance(node, Node) or node.kind != "element":
        return [""]
    if len(args) < 2:
        return [""]
    key = to_string(args[1])
    return [node.attrs.get(key, "")]


def _fn_text(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return [""]
    node = args[0][0]
    if isinstance(node, Node):
        deep = True
        if len(args) > 1:
            deep = to_boolean(args[1])
        if deep:
            return [node.string_value()]
        if node.kind in ("element", "document"):
            direct = "".join(
                child.value or ""
                for child in node.children
                if isinstance(child, Node) and child.kind == "text"
            )
            return [direct]
        return [node.string_value()]
    return [to_string(args[0])]


def _fn_children(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return []
    node = args[0][0]
    if isinstance(node, Node):
        return list(node.children)
    return []


def _fn_elements(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return []
    node = args[0][0]
    if not isinstance(node, Node) or node.kind not in ("element", "document"):
        return []
    name_test = to_string(args[1]) if len(args) > 1 else None
    out = [c for c in node.children if isinstance(c, Node) and c.kind == "element"]
    if name_test:
        out = [c for c in out if c.name == name_test]
    return out


def _fn_copy(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return []
    node = args[0][0]
    if not isinstance(node, Node):
        return []
    recurse = True
    if len(args) > 1:
        recurse = to_boolean(args[1])
    return [deep_copy(node, recurse=recurse)]


def _fn_count(args: List[List[Any]], ctx: Context) -> List[Any]:
    return [float(len(args[0]) if args else 0)]


def _fn_empty(args: List[List[Any]], ctx: Context) -> List[Any]:
    return [len(args[0]) == 0 if args else True]


def _fn_distinct(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args:
        return []
    seen = set()
    out = []
    for item in args[0]:
        key = to_string([item])
        if key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out


def _fn_sort(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args:
        return []
    seq = args[0]
    key_fn = None
    if len(args) > 1 and args[1]:
        candidate = args[1][0]
        if isinstance(candidate, FunctionRef):
            key_fn = candidate.name
    if key_fn:
        return sorted(seq, key=lambda i: to_string(call_function(key_fn, [[i]], ctx)))
    return sorted(seq, key=lambda i: to_string([i]))


def _fn_concat(args: List[List[Any]], ctx: Context) -> List[Any]:
    out: List[Any] = []
    for seq in args:
        out.extend(seq)
    return out


def _fn_head(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return []
    return [args[0][0]]


def _fn_tail(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        return []
    return list(args[0][1:])


def _fn_last(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args or not args[0]:
        if ctx.last is None:
            return []
        return [float(ctx.last)]
    seq = args[0]
    if not seq:
        return []
    return [seq[-1]]


def _fn_index(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args:
        return []
    seq = args[0]
    key_fn = None
    if len(args) > 1 and args[1]:
        candidate = args[1][0]
        if isinstance(candidate, FunctionRef):
            key_fn = candidate.name
    index: Dict[str, List[Any]] = {}
    for item in seq:
        if key_fn:
            key = to_string(call_function(key_fn, [[item]], ctx))
        else:
            key = to_string([item])
        index.setdefault(key, []).append(item)
    return [index]


def _fn_lookup(args: List[List[Any]], ctx: Context) -> List[Any]:
    if len(args) < 2:
        return []
    if not args[0]:
        return []
    mapping = args[0][0]
    if not isinstance(mapping, dict):
        return []
    key = to_string(args[1])
    return mapping.get(key, [])


def _fn_group_by(args: List[List[Any]], ctx: Context) -> List[Any]:
    if len(args) < 2:
        return []
    seq = args[0]
    key_fn = None
    if args[1]:
        candidate = args[1][0]
        if isinstance(candidate, FunctionRef):
            key_fn = candidate.name
    groups = {}
    for item in seq:
        if key_fn:
            key = to_string(call_function(key_fn, [[item]], ctx))
        else:
            key = to_string([item])
        groups.setdefault(key, []).append(item)
    return [{"key": k, "items": v} for k, v in groups.items()]


def _fn_seq(args: List[List[Any]], ctx: Context) -> List[Any]:
    out: List[Any] = []
    for seq in args:
        out.extend(seq)
    return out


def _fn_position(args: List[List[Any]], ctx: Context) -> List[Any]:
    if ctx.position is None:
        return []
    return [float(ctx.position)]


def _fn_apply(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args:
        return []
    seq = args[0]
    ruleset = "main"
    if len(args) > 1 and args[1]:
        ruleset = to_string(args[1])
    rules = ctx.rules.get(ruleset, [])
    out: List[Any] = []
    for item in seq:
        matched = False
        for rule in rules:
            ok, bindings = match_pattern(rule.pattern, item)
            if ok:
                matched = True
                new_vars = dict(ctx.variables)
                new_vars.update(bindings)
                out.extend(
                    eval_expr(
                        rule.body,
                        Context(item, new_vars, ctx.functions, ctx.rules, ctx.position, ctx.last),
                    )
                )
                break
        if not matched:
            raise RuntimeError("XFDY0001: no matching rule")
    return out


def _fn_sum(args: List[List[Any]], ctx: Context) -> List[Any]:
    if not args:
        return [0.0]
    total = 0.0
    for item in args[0]:
        total += to_number([item])
    return [total]


BUILTINS: Dict[str, Callable[[List[List[Any]], Context], List[Any]]] = {
    "string": _fn_string,
    "number": _fn_number,
    "boolean": _fn_boolean,
    "typeOf": _fn_typeof,
    "name": _fn_name,
    "attr": _fn_attr,
    "text": _fn_text,
    "children": _fn_children,
    "elements": _fn_elements,
    "copy": _fn_copy,
    "count": _fn_count,
    "empty": _fn_empty,
    "distinct": _fn_distinct,
    "sort": _fn_sort,
    "concat": _fn_concat,
    "index": _fn_index,
    "lookup": _fn_lookup,
    "groupBy": _fn_group_by,
    "seq": _fn_seq,
    "sum": _fn_sum,
    "head": _fn_head,
    "tail": _fn_tail,
    "last": _fn_last,
    "position": _fn_position,
    "apply": _fn_apply,
}
