from __future__ import annotations

import pytest

from zopyx.xform.parser import Lexer, Parser
from zopyx.xform import ast


def test_lexer_basic_tokens_and_comments() -> None:
    text = " # comment\nxform version '2.0';"
    lexer = Lexer(text)
    assert lexer.next().value == "xform"
    assert lexer.next().value == "version"
    assert lexer.next().value == "2.0"
    assert lexer.next().value == ";"
    assert lexer.next().kind == "EOF"


def test_lexer_string_escapes_and_numbers() -> None:
    lexer = Lexer(r"'a\nb\u0041' 12.5")
    assert lexer.next().value == "a\nbA"
    assert lexer.next().value == "12.5"


def test_lexer_unterminated_string_raises() -> None:
    lexer = Lexer("'nope")
    with pytest.raises(SyntaxError):
        lexer.next()


def test_lexer_invalid_character_raises() -> None:
    lexer = Lexer("$")
    with pytest.raises(SyntaxError):
        lexer.next()


def test_parse_module_with_decls_and_expr() -> None:
    source = """
    xform version "2.0";
    ns "ex" = "urn:ex";
    import "lib.xf" as lib;
    var greeting := "hi";
    def ex:echo(x: string := "x") := x;
    rule main match <item>{v}</item> := v;
    <out>{greeting}</out>
    """
    module = Parser(source).parse_module()
    assert module.namespaces["ex"] == "urn:ex"
    assert module.imports == [("lib.xf", "lib")]
    assert "greeting" in module.vars
    assert "ex:echo" in module.functions
    assert "main" in module.rules
    assert isinstance(module.expr, ast.Constructor)


def test_parse_version_mismatch_raises() -> None:
    source = "xform version '1.0';"
    with pytest.raises(SyntaxError, match="XFST0005"):
        Parser(source).parse_module()


def test_parse_unexpected_trailing_tokens_raises() -> None:
    source = "xform version '2.0'; 1 2"
    with pytest.raises(SyntaxError):
        Parser(source).parse_module()


def test_parse_text_constructor_vs_text_function() -> None:
    module = Parser("xform version '2.0'; text{'a'}").parse_module()
    assert isinstance(module.expr, ast.TextConstructor)
    module2 = Parser("xform version '2.0'; text()").parse_module()
    assert isinstance(module2.expr, ast.FuncCall)


def test_parse_constructor_contents_and_attributes() -> None:
    source = "xform version '2.0'; <a b={'1'}>hi{2}<c/></a>"
    module = Parser(source).parse_module()
    expr = module.expr
    assert isinstance(expr, ast.Constructor)
    assert expr.attrs[0][0] == "b"
    assert any(isinstance(c, ast.Text) for c in expr.contents)
    assert any(isinstance(c, ast.Interp) for c in expr.contents)


def test_parse_path_variants_and_predicates() -> None:
    source = "xform version '2.0'; /root/child[position()=1]"
    module = Parser(source).parse_module()
    expr = module.expr
    assert isinstance(expr, ast.PathExpr)
    assert expr.start.kind == "root"
    assert expr.steps[-1].predicates


def test_parse_dot_attr_and_desc_or_self() -> None:
    expr = Parser("xform version '2.0'; .@id").parse_module().expr
    assert isinstance(expr, ast.PathExpr)
    assert expr.steps[0].axis == "attr"
    expr2 = Parser("xform version '2.0'; .//child").parse_module().expr
    assert isinstance(expr2, ast.PathExpr)
    assert expr2.start.kind == "desc"


def test_parse_patterns() -> None:
    module = Parser(
        "xform version '2.0'; match .: case _ => 1; default => 2;"
    ).parse_module()
    assert isinstance(module.expr, ast.MatchExpr)
    assert isinstance(module.expr.cases[0][0], ast.WildcardPattern)
    module = Parser(
        "xform version '2.0'; match .: case node() => 1; default => 2;"
    ).parse_module()
    assert isinstance(module.expr.cases[0][0], ast.TypedPattern)
    module = Parser(
        "xform version '2.0'; match .: case @id => 1; default => 2;"
    ).parse_module()
    assert isinstance(module.expr.cases[0][0], ast.AttributePattern)
    module = Parser(
        "xform version '2.0'; match .: case <a>{v}</a> => v; default => 2;"
    ).parse_module()
    assert isinstance(module.expr.cases[0][0], ast.ElementPattern)


def test_parse_pattern_child_and_errors() -> None:
    source = "xform version '2.0'; match .: case <a><b>{v}</b></a> => v; default => 0;"
    module = Parser(source).parse_module()
    pat = module.expr.cases[0][0]
    assert isinstance(pat, ast.ElementPattern)
    assert pat.child is not None

    with pytest.raises(SyntaxError, match="Invalid element pattern content"):
        Parser("xform version '2.0'; match .: case <a>_</a> => 1;").parse_module()

    with pytest.raises(SyntaxError, match="Mismatched pattern end tag"):
        Parser("xform version '2.0'; match .: case <a>{v}</b> => 1;").parse_module()


def test_parse_constructor_mismatched_end_tag_raises() -> None:
    source = "xform version '2.0'; <a></b>"
    with pytest.raises(SyntaxError, match="Mismatched end tag"):
        Parser(source).parse_module()


def test_parse_invalid_step_test_raises() -> None:
    source = "xform version '2.0'; /?"
    with pytest.raises(SyntaxError):
        Parser(source).parse_module()
