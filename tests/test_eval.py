from __future__ import annotations

import pytest

from zopyx.xform import ast
from zopyx.xform.eval import (
    Context,
    FunctionRef,
    apply_step,
    call_function,
    eval_binary,
    eval_constructor,
    eval_expr,
    eval_module,
    eval_path,
    match_pattern,
    to_boolean,
    to_number,
    to_string,
    value_equal,
)
from zopyx.xform.xmlmodel import Node


def _doc_with_children() -> Node:
    root = Node(kind="element", name="root", attrs={"id": "r"})
    child1 = Node(kind="element", name="child", attrs={"id": "c1"}, parent=root)
    child1.children = [Node(kind="text", value="hi", parent=child1)]
    child2 = Node(kind="element", name="child", parent=root)
    sub = Node(kind="element", name="sub", parent=child2)
    child2.children = [sub]
    root.children = [child1, child2]
    doc = Node(kind="document", children=[root])
    root.parent = doc
    return doc


def test_eval_module_no_expr_returns_empty() -> None:
    module = ast.Module(functions={}, rules={}, vars={}, namespaces={}, imports=[], expr=None)
    doc = _doc_with_children()
    assert eval_module(module, doc) == []


def test_varref_resolution_order() -> None:
    doc = _doc_with_children()
    ctx = Context(context_item=doc.children[0], variables={"x": [1]}, functions={"f": ast.FunctionDef([], ast.Literal(1))}, rules={})
    assert eval_expr(ast.VarRef("x"), ctx) == [1]
    assert isinstance(eval_expr(ast.VarRef("f"), ctx)[0], FunctionRef)
    children = eval_expr(ast.VarRef("child"), ctx)
    assert len(children) == 2
    ctx2 = Context(context_item="not-node", variables={}, functions={}, rules={})
    assert eval_expr(ast.VarRef("missing"), ctx2) == []


def test_if_let_for_and_where_position_last() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    seq = ast.FuncCall("seq", [ast.Literal(1.0), ast.Literal(2.0), ast.Literal(3.0)])
    expr = ast.ForExpr(
        name="n",
        seq=seq,
        where=ast.BinaryOp(">", ast.VarRef("n"), ast.Literal(1.0)),
        body=ast.FuncCall("seq", [ast.FuncCall("position", []), ast.FuncCall("last", [])]),
    )
    out = eval_expr(expr, ctx)
    assert out == [2.0, 3.0, 3.0, 3.0]


def test_match_expr_default_and_error() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    target = ast.FuncCall("seq", [ast.Literal("a"), ast.Literal("b")])
    expr = ast.MatchExpr(target, [(ast.WildcardPattern(), ast.Literal("ok"))], ast.Literal("x"))
    assert eval_expr(expr, ctx) == ["ok", "ok"]
    expr2 = ast.MatchExpr(target, [(ast.TypedPattern("node"), ast.Literal("ok"))], None)
    with pytest.raises(RuntimeError, match="XFDY0001"):
        eval_expr(expr2, ctx)


def test_eval_binary_ops_and_errors() -> None:
    assert eval_binary("+", [1.0], [2.0]) == 3.0
    assert eval_binary("div", [4.0], [2.0]) == 2.0
    assert eval_binary("mod", [5.0], [2.0]) == 1.0
    assert eval_binary("=", ["a"], ["a"]) is True
    assert eval_binary("!=", ["a"], ["b"]) is True
    with pytest.raises(RuntimeError):
        eval_binary("?", [1.0], [1.0])


def test_boolean_short_circuiting() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    expr = ast.BinaryOp(
        "and",
        ast.Literal(False),
        ast.FuncCall("unknown", []),
    )
    assert eval_expr(expr, ctx) == [False]
    expr = ast.BinaryOp(
        "or",
        ast.Literal(True),
        ast.FuncCall("unknown", []),
    )
    assert eval_expr(expr, ctx) == [True]


def test_eval_path_axes_and_predicates() -> None:
    doc = _doc_with_children()
    ctx = Context(context_item=doc, variables={}, functions={}, rules={})
    expr = ast.PathExpr(
        start=ast.PathStart("root"),
        steps=[
            ast.PathStep("child", ast.StepTest("name", "root"), []),
            ast.PathStep("child", ast.StepTest("name", "child"), [
                ast.BinaryOp(
                    "=",
                    ast.FuncCall(
                        "attr",
                        [ast.PathExpr(ast.PathStart("context"), []), ast.Literal("id")],
                    ),
                    ast.Literal("c1"),
                )
            ]),
        ],
    )
    out = eval_path(expr, ctx)
    assert len(out) == 1

    desc_expr = ast.PathExpr(
        start=ast.PathStart("desc"),
        steps=[ast.PathStep("desc_or_self", ast.StepTest("name", "sub"), [])],
    )
    out = eval_path(desc_expr, Context(doc.children[0], {}, {}, {}))
    assert len(out) == 1

    attr_expr = ast.PathExpr(
        start=ast.PathStart("context"),
        steps=[ast.PathStep("attr", ast.StepTest("name", "missing"), [])],
    )
    assert eval_path(attr_expr, Context(doc.children[0], {}, {}, {})) == []


def test_apply_step_attr_wildcard_and_parent() -> None:
    root = Node(kind="element", name="root", attrs={"a": "1", "b": "2"})
    ctx = Context(context_item=root, variables={}, functions={}, rules={})
    step = ast.PathStep("attr", ast.StepTest("wildcard"), [])
    attrs = apply_step([root], step, ctx)
    assert {a.name for a in attrs} == {"a", "b"}
    step2 = ast.PathStep("parent", ast.StepTest("node"), [])
    child = Node(kind="element", name="child", parent=root)
    assert apply_step([child], step2, ctx) == [root]


def test_eval_path_var_start_unbound_falls_back_to_child() -> None:
    doc = _doc_with_children()
    ctx = Context(context_item=doc.children[0], variables={}, functions={}, rules={})
    expr = ast.PathExpr(
        start=ast.PathStart("var", "child"),
        steps=[],
    )
    out = eval_path(expr, ctx)
    assert len(out) == 2


def test_eval_path_comment_and_pi_tests() -> None:
    root = Node(kind="element", name="root")
    comment = Node(kind="comment", value="c", parent=root)
    pi = Node(kind="pi", value="p", parent=root)
    root.children = [comment, pi]
    ctx = Context(context_item=root, variables={}, functions={}, rules={})
    step = ast.PathStep("child", ast.StepTest("comment"), [])
    assert apply_step([root], step, ctx) == [comment]
    step = ast.PathStep("child", ast.StepTest("pi"), [])
    assert apply_step([root], step, ctx) == [pi]


def test_eval_constructor_and_text_constructor() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    expr = ast.Constructor(
        "a",
        attrs=[("id", ast.Literal("1"))],
        contents=[ast.Text("x"), ast.Interp(ast.Literal(2.0)), ast.TextConstructor(ast.Literal("y"))],
    )
    node = eval_constructor(expr, ctx)
    assert node.attrs["id"] == "1"
    assert node.string_value() == "x2y"


def test_unary_ops_and_text_interp() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert eval_expr(ast.UnaryOp("-", ast.Literal(2.0)), ctx) == [-2.0]
    assert eval_expr(ast.UnaryOp("not", ast.Literal(True)), ctx) == [False]
    assert eval_expr(ast.Text("hi"), ctx) == ["hi"]
    assert eval_expr(ast.Interp(ast.Literal("x")), ctx) == ["x"]


def test_call_function_user_defined_and_defaults() -> None:
    func = ast.FunctionDef([ast.Param("a"), ast.Param("b", default=ast.Literal(2.0))], ast.BinaryOp("+", ast.VarRef("a"), ast.VarRef("b")))
    ctx = Context(context_item=None, variables={}, functions={"add": func}, rules={})
    assert call_function("add", [[1.0]], ctx) == [3.0]
    with pytest.raises(RuntimeError, match="XFDY0002"):
        call_function("add", [[1.0], [2.0], [3.0]], ctx)


def test_unknown_function_raises() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    with pytest.raises(RuntimeError, match="XFST0003"):
        call_function("nope", [], ctx)


def test_to_boolean_string_number_and_node() -> None:
    assert to_boolean([]) is False
    assert to_boolean([0]) is False
    assert to_boolean([""]) is False
    assert to_boolean([Node(kind="text", value="x")]) is True


def test_to_string_number_boolean_and_node() -> None:
    assert to_string([]) == ""
    assert to_string([None]) == ""
    assert to_string([True]) == "true"
    assert to_string([1.0]) == "1"
    assert to_string([1.5]) == "1.5"
    assert to_string([Node(kind="text", value="x")]) == "x"


def test_to_number_conversions_and_error() -> None:
    assert to_number([]) == 0.0
    assert to_number([True]) == 1.0
    assert to_number(["2.5"]) == 2.5
    assert to_number([Node(kind="text", value="3")]) == 3.0
    with pytest.raises(RuntimeError, match="XFDY0002"):
        to_number(["nope"])


def test_value_equal_uses_string_value() -> None:
    assert value_equal([1.0], [1.0]) is True
    assert value_equal([Node(kind="text", value="x")], ["x"]) is True


def test_match_pattern_variants() -> None:
    node = Node(kind="element", name="a")
    assert match_pattern(ast.WildcardPattern(), node)[0] is True
    assert match_pattern(ast.AttributePattern("id"), Node(kind="attribute", name="id"))[0] is True
    assert match_pattern(ast.TypedPattern("text"), Node(kind="text"))[0] is True
    ok, bindings = match_pattern(ast.ElementPattern("a", var="v"), node)
    assert ok and "v" in bindings
    child = Node(kind="element", name="b", parent=node)
    node.children = [child]
    ok, _ = match_pattern(ast.ElementPattern("a", child=ast.ElementPattern("b")), node)
    assert ok


def test_builtin_helpers() -> None:
    doc = _doc_with_children()
    root = doc.children[0]
    ctx = Context(context_item=root, variables={}, functions={}, rules={})
    assert call_function("string", [[]], ctx) == [""]
    assert call_function("number", [[Node(kind="text", value="4")]], ctx) == [4.0]
    assert call_function("boolean", [[1]], ctx) == [True]
    assert call_function("typeOf", [[]], ctx) == ["null"]
    assert call_function("typeOf", [[{"a": 1}]], ctx) == ["map"]
    assert call_function("name", [[root]], ctx) == ["root"]
    assert call_function("attr", [[root], ["id"]], ctx) == ["r"]
    assert call_function("attr", [[root], ["missing"]], ctx) == [""]
    assert call_function("attr", [[Node(kind="text", value="x")], ["id"]], ctx) == [""]
    assert call_function("text", [[root], [False]], ctx) == [""]
    assert call_function("text", [["plain"]], ctx) == ["plain"]
    assert call_function("children", [[root]], ctx)[0].name == "child"
    assert call_function("elements", [[root], ["child"]], ctx)
    assert call_function("elements", [[Node(kind="text", value="x")]], ctx) == []
    assert call_function("copy", [[root], [False]], ctx)[0].children == []
    assert call_function("count", [[1, 2, 3]], ctx) == [3.0]
    assert call_function("empty", [[]], ctx) == [True]
    assert call_function("distinct", [[1, 1, 2]], ctx) == [1, 2]
    assert call_function("concat", [[1], [2, 3]], ctx) == [1, 2, 3]
    assert call_function("head", [[1, 2]], ctx) == [1]
    assert call_function("tail", [[1, 2]], ctx) == [2]
    assert call_function("last", [[1, 2]], ctx) == [2]
    assert call_function("seq", [[1], [2]], ctx) == [1, 2]
    assert call_function("sum", [[1, 2, 3]], ctx) == [6.0]


def test_sort_index_groupby_lookup_and_apply() -> None:
    func = ast.FunctionDef([ast.Param("x")], ast.VarRef("x"))
    rule = ast.RuleDef(ast.ElementPattern("child"), ast.Literal("ok"))
    doc = _doc_with_children()
    ctx = Context(context_item=doc, variables={}, functions={"key": func}, rules={"main": [rule]})

    seq = [3, 1, 2]
    assert call_function("sort", [seq, [FunctionRef("key")]], ctx) == [1, 2, 3]
    index = call_function("index", [[1, 1, 2]], ctx)[0]
    assert index["1"] == [1, 1]
    grouped = call_function("groupBy", [[1, 2, 2], [FunctionRef("key")]], ctx)
    assert {g["key"] for g in grouped} == {"1", "2"}
    assert call_function("lookup", [[index], ["2"]], ctx) == [2]
    assert call_function("apply", [doc.children[0].children], ctx) == ["ok", "ok"]


def test_apply_raises_when_no_rule_matches() -> None:
    doc = _doc_with_children()
    ctx = Context(context_item=doc, variables={}, functions={}, rules={"main": []})
    with pytest.raises(RuntimeError, match="XFDY0001"):
        call_function("apply", [doc.children], ctx)
