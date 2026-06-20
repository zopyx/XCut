from __future__ import annotations

import pytest

from zopyx.xform import ast
from zopyx.xform.eval import Context, call_function, eval_constructor, eval_expr, match_pattern
from zopyx.xform.parser import Parser
from zopyx.xform.xmlmodel import Node


def _simple_doc() -> Node:
    root = Node(kind="element", name="root", attrs={"id": "r"})
    child = Node(kind="element", name="child", attrs={"id": "c1"}, parent=root)
    child.children = [Node(kind="text", value="hello", parent=child)]
    root.children = [child]
    doc = Node(kind="document", children=[root])
    root.parent = doc
    return doc


def test_parse_module_accepts_21_version() -> None:
    module = Parser("xform version '2.1'; 1").parse_module()
    assert isinstance(module.expr, ast.Literal)
    assert module.expr.value == 1.0


def test_parse_named_arguments_in_function_calls() -> None:
    module = Parser("xform version '2.1'; text(./p, deep:=false)").parse_module()
    expr = module.expr
    assert isinstance(expr, ast.FuncCall)
    assert expr.name == "text"


def test_parse_bare_attribute_path_start() -> None:
    module = Parser("xform version '2.1'; @id").parse_module()
    expr = module.expr
    assert isinstance(expr, ast.PathExpr)
    assert expr.start.kind in {"attr", "context"}


def test_parse_attribute_value_pattern() -> None:
    module = Parser(
        "xform version '2.1'; match .: case @id = 'x' => 1; default => 0;"
    ).parse_module()
    assert isinstance(module.expr, ast.MatchExpr)


def test_parse_comment_and_pi_constructors() -> None:
    comment_module = Parser("xform version '2.1'; comment{'hello'}").parse_module()
    pi_module = Parser("xform version '2.1'; pi{'target', 'value'}").parse_module()
    assert comment_module.expr is not None
    assert pi_module.expr is not None


def test_default_parameter_can_reference_earlier_parameter() -> None:
    func = ast.FunctionDef(
        [ast.Param("a"), ast.Param("b", default=ast.VarRef("a"))],
        ast.FuncCall("seq", [ast.VarRef("a"), ast.VarRef("b")]),
    )
    ctx = Context(context_item=None, variables={}, functions={"dup": func}, rules={})
    assert call_function("dup", [[42.0]], ctx) == [42.0, 42.0]


def test_apply_has_builtin_fallback_rules_for_unmatched_nodes() -> None:
    doc = _simple_doc()
    ctx = Context(context_item=doc, variables={}, functions={}, rules={"main": []})
    out = call_function("apply", [doc.children], ctx)
    assert out
    assert isinstance(out[0], Node)
    assert out[0].name == "root"


def test_nested_pattern_requires_exact_child_sequence() -> None:
    parent = Node(kind="element", name="a")
    first = Node(kind="element", name="b", parent=parent)
    second = Node(kind="element", name="c", parent=parent)
    parent.children = [first, second]

    ok, _ = match_pattern(ast.ElementPattern("a", child=ast.ElementPattern("b")), parent)
    assert ok is False


@pytest.mark.xfail(reason="2.1 invalid attribute insertion must raise XFDY0005")
def test_constructor_rejects_attribute_nodes_in_content() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    expr = ast.Constructor(
        "out",
        attrs=[],
        contents=[ast.Interp(ast.PathExpr(ast.PathStart("var", "a"), []))],
    )
    attr_node = Node(kind="attribute", name="id", value="1")
    ctx.variables["a"] = [attr_node]
    with pytest.raises(RuntimeError, match="XFDY0005"):
        eval_constructor(expr, ctx)


def test_constructor_rejects_duplicate_attributes() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    expr = ast.Constructor(
        "out",
        attrs=[("id", ast.Literal("1")), ("id", ast.Literal("2"))],
        contents=[],
    )
    with pytest.raises(RuntimeError, match="XFDY0005"):
        eval_constructor(expr, ctx)


@pytest.mark.parametrize(
    ("name", "args", "expected"),
    [
        ("contains", [["alphabet"], ["pha"]], [True]),
        ("startsWith", [["alphabet"], ["alp"]], [True]),
        ("endsWith", [["alphabet"], ["bet"]], [True]),
        ("substring", [["alphabet"], [2.0], [3.0]], ["lph"]),
        ("normalizeSpace", [["  a   b  "]], ["a b"]),
    ],
)
def test_string_helper_builtins(name: str, args: list[list[object]], expected: list[object]) -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function(name, args, ctx) == expected


def test_replace_builtin() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("replace", [["a-b-c"], ["-"], [":"]], ctx) == ["a:b:c"]


def test_map_helper_builtins() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    m = {"a": [1], "b": [2, 3]}
    keys = call_function("keys", [[m]], ctx)
    size = call_function("mapSize", [[m]], ctx)
    missing = call_function("lookup", [[m], ["missing"]], ctx)
    assert set(keys) == {"a", "b"}
    assert size == [2.0]
    assert missing == []


@pytest.mark.parametrize("expr", [ast.FuncCall("position", []), ast.FuncCall("last", [])])
def test_iteration_context_functions_raise_outside_for(expr: ast.Expr) -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    with pytest.raises(RuntimeError, match="XFDY0003"):
        eval_expr(expr, ctx)
