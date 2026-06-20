from __future__ import annotations

import pytest

from zopyx.xform import ast
from zopyx.xform.eval import Context, call_function, eval_expr, match_pattern
from zopyx.xform.parser import Parser
from zopyx.xform.xmlmodel import Node


@pytest.mark.parametrize("name", ["apply", "text", "comment", "pi", "null"])
def test_reserved_words_cannot_be_used_as_identifiers(name: str) -> None:
    with pytest.raises(SyntaxError, match="XFST0006"):
        Parser(f"xform version '2.1'; var {name} := 1;").parse_module()


def test_parse_element_pattern_with_attribute_constraint() -> None:
    module = Parser(
        "xform version '2.1'; match .: case <item @type='product'>{v}</item> => v; default => 0;"
    ).parse_module()
    assert isinstance(module.expr, ast.MatchExpr)


def test_parse_document_typed_pattern() -> None:
    module = Parser(
        "xform version '2.1'; match .: case document() => 1; default => 0;"
    ).parse_module()
    assert isinstance(module.expr, ast.MatchExpr)


def test_eval_comment_constructor_produces_comment_node() -> None:
    module = Parser("xform version '2.1'; comment{'hi'}").parse_module()
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    out = eval_expr(module.expr, ctx)
    assert isinstance(out[0], Node)
    assert out[0].kind == "comment"


def test_eval_pi_constructor_produces_pi_node() -> None:
    module = Parser("xform version '2.1'; pi{'xml-stylesheet', 'href=\"x.css\"'}").parse_module()
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    out = eval_expr(module.expr, ctx)
    assert isinstance(out[0], Node)
    assert out[0].kind == "pi"


def test_match_pattern_attribute_value_constraint() -> None:
    attr = Node(kind="attribute", name="type", value="product")
    ok, _ = match_pattern(ast.AttributePattern("type", "product"), attr)  # type: ignore[arg-type]
    assert ok


def test_match_pattern_exact_child_sequence_success() -> None:
    parent = Node(kind="element", name="a")
    child = Node(kind="element", name="b", parent=parent)
    parent.children = [child]
    pattern = ast.ElementPattern("a", child=ast.ElementPattern("b"))
    ok, _ = match_pattern(pattern, parent)
    assert ok


def test_apply_unknown_ruleset_raises_xfst0007() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    with pytest.raises(RuntimeError, match="XFST0007"):
        call_function("apply", [[Node(kind="element", name="a")], ["detail"]], ctx)


def test_contains_builtin_false_case() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("contains", [["alpha"], ["z"]], ctx) == [False]


def test_startswith_builtin_false_case() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("startsWith", [["alpha"], ["pha"]], ctx) == [False]


def test_endswith_builtin_false_case() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("endsWith", [["alpha"], ["alp"]], ctx) == [False]


def test_normalizespace_collapses_whitespace() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("normalizeSpace", [["  a \n  b\t c  "]], ctx) == ["a b c"]


def test_substring_builtin() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("substring", [["abcdef"], [2.0], [3.0]], ctx) == ["bcd"]


def test_mapsize_counts_keys() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("mapSize", [[{"a": [1], "b": [2]}]], ctx) == [2.0]


def test_keys_returns_all_keys() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    keys = call_function("keys", [[{"a": [1], "b": [2]}]], ctx)
    assert set(keys) == {"a", "b"}


def test_lookup_missing_key_returns_empty_sequence() -> None:
    ctx = Context(context_item=None, variables={}, functions={}, rules={})
    assert call_function("lookup", [[{"a": [1]}], ["missing"]], ctx) == []
