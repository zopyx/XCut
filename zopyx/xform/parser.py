from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional, Tuple

from . import ast

KEYWORDS = {
    "xform",
    "version",
    "import",
    "as",
    "ns",
    "def",
    "var",
    "let",
    "in",
    "for",
    "where",
    "return",
    "if",
    "then",
    "else",
    "match",
    "case",
    "default",
    "and",
    "or",
    "not",
    "div",
    "mod",
    "rule",
}

OPERATORS = {"=", "!=", "<", "<=", ">", ">=", "+", "-", "*", ":="}
PUNCT = {"(", ")", "{", "}", "[", "]", ",", ";", ":"}


@dataclass
class Token:
    kind: str
    value: str
    pos: int


class Lexer:
    def __init__(self, text: str) -> None:
        self.text = text
        self.pos = 0
        self._buffer: Optional[Token] = None

    def peek(self) -> Token:
        if self._buffer is None:
            self._buffer = self._next_token()
        return self._buffer

    def next(self) -> Token:
        if self._buffer is not None:
            tok = self._buffer
            self._buffer = None
            return tok
        return self._next_token()

    def expect(self, kind: str, value: Optional[str] = None) -> Token:
        tok = self.next()
        if tok.kind != kind or (value is not None and tok.value != value):
            raise SyntaxError(f"Expected {kind} {value or ''} at {tok.pos}")
        return tok

    def _skip_ws_comments(self) -> None:
        text = self.text
        while self.pos < len(text):
            ch = text[self.pos]
            if ch.isspace():
                self.pos += 1
                continue
            if ch == "#":
                while self.pos < len(text) and text[self.pos] != "\n":
                    self.pos += 1
                continue
            break

    def _next_token(self) -> Token:
        self._skip_ws_comments()
        if self.pos >= len(self.text):
            return Token("EOF", "", self.pos)

        ch = self.text[self.pos]

        if ch == ":" and self.pos + 1 < len(self.text) and self.text[self.pos + 1] == "=":
            start = self.pos
            self.pos += 2
            return Token("OP", ":=", start)
        if ch in "(){}[],:;":
            self.pos += 1
            return Token("PUNCT", ch, self.pos - 1)

        if ch == ".":
            start = self.pos
            if self.text[self.pos : self.pos + 2] == "..":
                self.pos += 2
                return Token("DOT", "..", start)
            if self.text[self.pos : self.pos + 3] == ".//":
                self.pos += 3
                return Token("DOT", ".//", start)
            self.pos += 1
            return Token("DOT", ".", start)

        if ch == "/":
            start = self.pos
            if self.text[self.pos : self.pos + 2] == "//":
                self.pos += 2
                return Token("SLASH", "//", start)
            self.pos += 1
            return Token("SLASH", "/", start)

        if ch in "<>=!+-*":
            start = self.pos
            self.pos += 1
            if self.pos < len(self.text) and self.text[self.pos] == "=":
                self.pos += 1
                return Token("OP", self.text[start:self.pos], start)
            return Token("OP", ch, start)

        if ch in "'\"":
            quote = ch
            start = self.pos
            self.pos += 1
            out = []
            while self.pos < len(self.text):
                ch = self.text[self.pos]
                if ch == "\\":
                    self.pos += 1
                    if self.pos >= len(self.text):
                        break
                    esc = self.text[self.pos]
                    if esc == "n":
                        out.append("\n")
                    elif esc == "t":
                        out.append("\t")
                    elif esc == "r":
                        out.append("\r")
                    elif esc == "u":
                        hexval = self.text[self.pos + 1 : self.pos + 5]
                        out.append(chr(int(hexval, 16)))
                        self.pos += 4
                    else:
                        out.append(esc)
                    self.pos += 1
                    continue
                if ch == quote:
                    self.pos += 1
                    return Token("STRING", "".join(out), start)
                out.append(ch)
                self.pos += 1
            raise SyntaxError(f"Unterminated string at {start}")

        if ch.isdigit():
            start = self.pos
            while self.pos < len(self.text) and (
                self.text[self.pos].isdigit() or self.text[self.pos] == "."
            ):
                self.pos += 1
            return Token("NUMBER", self.text[start:self.pos], start)

        if ch.isalpha() or ch == "_":
            start = self.pos
            while self.pos < len(self.text):
                c = self.text[self.pos]
                if c == ":":
                    if self.pos + 1 < len(self.text) and (
                        self.text[self.pos + 1].isalnum() or self.text[self.pos + 1] in "_-"
                    ):
                        self.pos += 1
                        continue
                    break
                if not (c.isalnum() or c in "_-"):
                    break
                self.pos += 1
            val = self.text[start:self.pos]
            if val in KEYWORDS:
                return Token("KW", val, start)
            return Token("IDENT", val, start)

        if ch == "@":
            self.pos += 1
            return Token("AT", "@", self.pos - 1)

        raise SyntaxError(f"Unexpected character {ch!r} at {self.pos}")


class Parser:
    def __init__(self, text: str) -> None:
        self.text = text
        self.lexer = Lexer(text)

    def parse_module(self) -> ast.Module:
        functions = {}
        rules: dict[str, List[ast.RuleDef]] = {}
        vars_decl = {}
        namespaces: dict[str, str] = {}
        imports: List[Tuple[str, Optional[str]]] = []

        tok = self.lexer.peek()
        if tok.kind == "KW" and tok.value == "xform":
            self.lexer.next()
            self.lexer.expect("KW", "version")
            version = self.lexer.expect("STRING").value
            if version != "2.0":
                raise SyntaxError("XFST0005: unsupported version")
            self.lexer.expect("PUNCT", ";")

        while True:
            tok = self.lexer.peek()
            if tok.kind == "KW" and tok.value == "ns":
                self._parse_ns(namespaces)
                continue
            if tok.kind == "KW" and tok.value == "import":
                self._parse_import(imports)
                continue
            if tok.kind == "KW" and tok.value == "var":
                name, value = self._parse_var()
                vars_decl[name] = value
                continue
            if tok.kind == "KW" and tok.value == "def":
                self._parse_def(functions)
                continue
            if tok.kind == "KW" and tok.value == "rule":
                self._parse_rule(rules)
                continue
            break

        expr = None
        if self.lexer.peek().kind != "EOF":
            expr = self.parse_expr()
            if self.lexer.peek().kind != "EOF":
                raise SyntaxError(f"Unexpected token at {self.lexer.peek().pos}")
        return ast.Module(
            functions=functions,
            rules=rules,
            vars=vars_decl,
            namespaces=namespaces,
            imports=imports,
            expr=expr,
        )

    def _parse_ns(self, namespaces: dict) -> None:
        self.lexer.expect("KW", "ns")
        prefix = self.lexer.expect("STRING").value
        self.lexer.expect("OP", "=")
        uri = self.lexer.expect("STRING").value
        self.lexer.expect("PUNCT", ";")
        namespaces[prefix] = uri

    def _parse_import(self, imports: list) -> None:
        self.lexer.expect("KW", "import")
        iri = self.lexer.expect("STRING").value
        alias = None
        if self.lexer.peek().kind == "KW" and self.lexer.peek().value == "as":
            self.lexer.next()
            alias = self.lexer.expect("IDENT").value
        self.lexer.expect("PUNCT", ";")
        imports.append((iri, alias))

    def _parse_var(self) -> Tuple[str, ast.Expr]:
        self.lexer.expect("KW", "var")
        name = self.lexer.expect("IDENT").value
        self.lexer.expect("OP", ":=")
        value = self.parse_expr()
        self.lexer.expect("PUNCT", ";")
        return name, value

    def _parse_def(self, functions: dict) -> None:
        self.lexer.expect("KW", "def")
        name = self._parse_qname()
        self.lexer.expect("PUNCT", "(")
        params: List[ast.Param] = []
        if self.lexer.peek().kind != "PUNCT" or self.lexer.peek().value != ")":
            params.append(self._parse_param())
            while self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == ",":
                self.lexer.next()
                params.append(self._parse_param())
        self.lexer.expect("PUNCT", ")")
        self.lexer.expect("OP", ":=")
        body = self.parse_expr()
        self.lexer.expect("PUNCT", ";")
        functions[name] = ast.FunctionDef(params, body)

    def _parse_param(self) -> ast.Param:
        name = self.lexer.expect("IDENT").value
        type_ref = None
        default = None
        if self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == ":":
            self.lexer.next()
            type_ref = self._parse_type_ref()
        if self.lexer.peek().kind == "OP" and self.lexer.peek().value == ":=":
            self.lexer.next()
            default = self.parse_expr()
        return ast.Param(name, type_ref, default)

    def _parse_type_ref(self) -> str:
        tok = self.lexer.peek()
        if tok.kind == "IDENT" and tok.value in ("string", "number", "boolean", "null", "map"):
            return self.lexer.next().value
        return self._parse_qname()

    def _parse_rule(self, rules: dict) -> None:
        self.lexer.expect("KW", "rule")
        name = self._parse_qname()
        self.lexer.expect("KW", "match")
        pattern = self._parse_pattern()
        self.lexer.expect("OP", ":=")
        body = self.parse_expr()
        self.lexer.expect("PUNCT", ";")
        rules.setdefault(name, []).append(ast.RuleDef(pattern, body))

    def parse_expr(self) -> ast.Expr:
        tok = self.lexer.peek()
        if tok.kind == "KW" and tok.value == "if":
            return self._parse_if()
        if tok.kind == "KW" and tok.value == "let":
            return self._parse_let()
        if tok.kind == "KW" and tok.value == "for":
            return self._parse_for()
        if tok.kind == "KW" and tok.value == "match":
            return self._parse_match()
        return self._parse_or()

    def _parse_if(self) -> ast.Expr:
        self.lexer.expect("KW", "if")
        cond = self.parse_expr()
        self.lexer.expect("KW", "then")
        then_expr = self.parse_expr()
        self.lexer.expect("KW", "else")
        else_expr = self.parse_expr()
        return ast.IfExpr(cond, then_expr, else_expr)

    def _parse_let(self) -> ast.Expr:
        self.lexer.expect("KW", "let")
        name = self.lexer.expect("IDENT").value
        self.lexer.expect("OP", ":=")
        value = self.parse_expr()
        self.lexer.expect("KW", "in")
        body = self.parse_expr()
        return ast.LetExpr(name, value, body)

    def _parse_for(self) -> ast.Expr:
        self.lexer.expect("KW", "for")
        name = self.lexer.expect("IDENT").value
        self.lexer.expect("KW", "in")
        seq = self.parse_expr()
        where = None
        if self.lexer.peek().kind == "KW" and self.lexer.peek().value == "where":
            self.lexer.next()
            where = self.parse_expr()
        self.lexer.expect("KW", "return")
        body = self.parse_expr()
        return ast.ForExpr(name, seq, where, body)

    def _parse_match(self) -> ast.Expr:
        self.lexer.expect("KW", "match")
        target = self.parse_expr()
        self.lexer.expect("PUNCT", ":")
        cases = []
        default = None
        while True:
            tok = self.lexer.peek()
            if tok.kind == "KW" and tok.value == "case":
                self.lexer.next()
                pattern = self._parse_pattern()
                self.lexer.expect("OP", "=")
                self.lexer.expect("OP", ">")
                expr = self.parse_expr()
                self.lexer.expect("PUNCT", ";")
                cases.append((pattern, expr))
                continue
            if tok.kind == "KW" and tok.value == "default":
                self.lexer.next()
                self.lexer.expect("OP", "=")
                self.lexer.expect("OP", ">")
                default = self.parse_expr()
                self.lexer.expect("PUNCT", ";")
                break
            break
        return ast.MatchExpr(target, cases, default)

    def _parse_or(self) -> ast.Expr:
        expr = self._parse_and()
        while self.lexer.peek().kind == "KW" and self.lexer.peek().value == "or":
            self.lexer.next()
            right = self._parse_and()
            expr = ast.BinaryOp("or", expr, right)
        return expr

    def _parse_and(self) -> ast.Expr:
        expr = self._parse_eq()
        while self.lexer.peek().kind == "KW" and self.lexer.peek().value == "and":
            self.lexer.next()
            right = self._parse_eq()
            expr = ast.BinaryOp("and", expr, right)
        return expr

    def _parse_eq(self) -> ast.Expr:
        expr = self._parse_rel()
        while self.lexer.peek().kind == "OP" and self.lexer.peek().value in ("=", "!="):
            op = self.lexer.next().value
            right = self._parse_rel()
            expr = ast.BinaryOp(op, expr, right)
        return expr

    def _parse_rel(self) -> ast.Expr:
        expr = self._parse_add()
        while self.lexer.peek().kind == "OP" and self.lexer.peek().value in ("<", "<=", ">", ">="):
            op = self.lexer.next().value
            right = self._parse_add()
            expr = ast.BinaryOp(op, expr, right)
        return expr

    def _parse_add(self) -> ast.Expr:
        expr = self._parse_mul()
        while self.lexer.peek().kind == "OP" and self.lexer.peek().value in ("+", "-"):
            op = self.lexer.next().value
            right = self._parse_mul()
            expr = ast.BinaryOp(op, expr, right)
        return expr

    def _parse_mul(self) -> ast.Expr:
        expr = self._parse_unary()
        while True:
            tok = self.lexer.peek()
            if tok.kind == "OP" and tok.value == "*":
                self.lexer.next()
                right = self._parse_unary()
                expr = ast.BinaryOp("*", expr, right)
                continue
            if tok.kind == "KW" and tok.value in ("div", "mod"):
                op = self.lexer.next().value
                right = self._parse_unary()
                expr = ast.BinaryOp(op, expr, right)
                continue
            break
        return expr

    def _parse_unary(self) -> ast.Expr:
        tok = self.lexer.peek()
        if tok.kind == "OP" and tok.value == "-":
            self.lexer.next()
            return ast.UnaryOp("-", self._parse_unary())
        if tok.kind == "KW" and tok.value == "not":
            self.lexer.next()
            return ast.UnaryOp("not", self._parse_unary())
        return self._parse_primary()

    def _parse_primary(self) -> ast.Expr:
        tok = self.lexer.peek()
        if tok.kind == "NUMBER":
            self.lexer.next()
            return ast.Literal(float(tok.value))
        if tok.kind == "STRING":
            self.lexer.next()
            return ast.Literal(tok.value)
        if tok.kind == "PUNCT" and tok.value == "(":
            self.lexer.next()
            expr = self.parse_expr()
            self.lexer.expect("PUNCT", ")")
            return expr
        if tok.kind == "IDENT" and tok.value == "text":
            saved_pos = self.lexer.pos
            saved_buf = self.lexer._buffer
            self.lexer.next()
            if self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == "{":
                self.lexer.next()
                expr = self.parse_expr()
                self.lexer.expect("PUNCT", "}")
                return ast.TextConstructor(expr)
            self.lexer.pos = saved_pos
            self.lexer._buffer = saved_buf
        if tok.kind == "OP" and tok.value == "<":
            return self._parse_constructor()
        if tok.kind in ("DOT", "SLASH"):
            return self._parse_path()
        if tok.kind == "IDENT":
            name = self.lexer.next().value
            if self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == "(":
                return self._parse_func_call(name)
            if self._path_continues():
                return self._parse_path(start=ast.PathStart("var", name))
            return ast.VarRef(name)
        raise SyntaxError(f"Unexpected token at {tok.pos}")

    def _parse_func_call(self, name: str) -> ast.FuncCall:
        self.lexer.expect("PUNCT", "(")
        args = []
        if self.lexer.peek().kind != "PUNCT" or self.lexer.peek().value != ")":
            args.append(self.parse_expr())
            while self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == ",":
                self.lexer.next()
                args.append(self.parse_expr())
        self.lexer.expect("PUNCT", ")")
        return ast.FuncCall(name, args)

    def _path_continues(self) -> bool:
        tok = self.lexer.peek()
        return tok.kind in ("SLASH", "DOT", "AT")

    def _parse_path(self, start: Optional[ast.PathStart] = None) -> ast.PathExpr:
        if start is None:
            tok = self.lexer.next()
            if tok.kind == "DOT":
                if tok.value == ".//":
                    start = ast.PathStart("desc")
                else:
                    start = ast.PathStart("context")
            elif tok.kind == "SLASH":
                if tok.value == "//":
                    start = ast.PathStart("desc_root")
                else:
                    start = ast.PathStart("root")
            else:
                raise SyntaxError(f"Invalid path start at {tok.pos}")

        steps: List[ast.PathStep] = []
        if start.kind in ("root", "context", "var"):
            tok = self.lexer.peek()
            if tok.kind == "AT":
                self.lexer.next()
                test = ast.StepTest("name", self._parse_qname())
                steps.append(ast.PathStep("attr", test, []))
            elif tok.kind == "OP" and tok.value == "*":
                test = self._parse_step_test()
                predicates = self._parse_predicates()
                steps.append(ast.PathStep("child", test, predicates))
            elif tok.kind == "IDENT":
                test = self._parse_step_test()
                predicates = self._parse_predicates()
                steps.append(ast.PathStep("child", test, predicates))
        if start.kind in ("desc", "desc_root"):
            tok = self.lexer.peek()
            if tok.kind in ("IDENT", "OP") or (tok.kind == "IDENT" and tok.value in ("text", "node", "comment", "pi")):
                test = self._parse_step_test()
                predicates = self._parse_predicates()
                steps.append(ast.PathStep("desc_or_self", test, predicates))
        while True:
            tok = self.lexer.peek()
            if tok.kind == "SLASH":
                axis = "child" if tok.value == "/" else "desc"
                self.lexer.next()
                if self.lexer.peek().kind == "AT":
                    self.lexer.next()
                    test = ast.StepTest("name", self._parse_qname())
                    axis = "attr"
                    predicates = []
                else:
                    test = self._parse_step_test()
                    predicates = self._parse_predicates()
                steps.append(ast.PathStep(axis, test, predicates))
                continue
            if tok.kind == "DOT":
                if tok.value == ".":
                    self.lexer.next()
                    if self.lexer.peek().kind == "AT":
                        self.lexer.next()
                        test = ast.StepTest("name", self._parse_qname())
                        steps.append(ast.PathStep("attr", test, []))
                    else:
                        steps.append(ast.PathStep("self", ast.StepTest("node"), []))
                    continue
                if tok.value == "..":
                    self.lexer.next()
                    steps.append(ast.PathStep("parent", ast.StepTest("node"), []))
                    continue
            if tok.kind == "AT":
                self.lexer.next()
                test = ast.StepTest("name", self._parse_qname())
                steps.append(ast.PathStep("attr", test, []))
                continue
            break
        return ast.PathExpr(start, steps)

    def _parse_step_test(self) -> ast.StepTest:
        tok = self.lexer.peek()
        if tok.kind == "OP" and tok.value == "*":
            self.lexer.next()
            return ast.StepTest("wildcard")
        if tok.kind == "IDENT":
            if tok.value in ("text", "node", "comment", "pi"):
                self.lexer.next()
                self.lexer.expect("PUNCT", "(")
                self.lexer.expect("PUNCT", ")")
                return ast.StepTest(tok.value)
            name = self._parse_qname()
            return ast.StepTest("name", name)
        raise SyntaxError(f"Invalid step test at {tok.pos}")

    def _parse_predicates(self) -> List[ast.Expr]:
        preds = []
        while self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == "[":
            self.lexer.next()
            preds.append(self.parse_expr())
            self.lexer.expect("PUNCT", "]")
        return preds

    def _parse_qname(self) -> str:
        return self.lexer.expect("IDENT").value

    def _parse_pattern(self) -> ast.Pattern:
        tok = self.lexer.peek()
        if tok.kind == "AT":
            self.lexer.next()
            name = self._parse_qname()
            return ast.AttributePattern(name)
        if tok.kind == "IDENT" and tok.value in ("node", "text", "comment"):
            self.lexer.next()
            self.lexer.expect("PUNCT", "(")
            self.lexer.expect("PUNCT", ")")
            return ast.TypedPattern(tok.value)
        if tok.kind == "IDENT" and tok.value == "_":
            self.lexer.next()
            return ast.WildcardPattern()
        if tok.kind == "OP" and tok.value == "<":
            self.lexer.next()
            name = self._parse_qname()
            self.lexer.expect("OP", ">")
            var: Optional[str] = None
            child: Optional[ast.Pattern] = None
            if self.lexer.peek().kind == "PUNCT" and self.lexer.peek().value == "{":
                self.lexer.next()
                var = self.lexer.expect("IDENT").value
                self.lexer.expect("PUNCT", "}")
            elif self.lexer.peek().kind == "OP" and self.lexer.peek().value == "<":
                child = self._parse_pattern()
            else:
                raise SyntaxError("Invalid element pattern content")
            self.lexer.expect("OP", "<")
            self.lexer.expect("SLASH", "/")
            end = self._parse_qname()
            if end != name:
                raise SyntaxError("Mismatched pattern end tag")
            self.lexer.expect("OP", ">")
            return ast.ElementPattern(name, var=var, child=child)
        raise SyntaxError(f"Invalid pattern at {tok.pos}")

    def _parse_constructor(self) -> ast.Constructor:
        self.lexer.expect("OP", "<")
        name = self._parse_qname()
        attrs: List[Tuple[str, ast.Expr]] = []
        while True:
            tok = self.lexer.peek()
            if tok.kind == "OP" and tok.value == ">":
                self.lexer.next()
                break
            if tok.kind == "SLASH" and tok.value == "/":
                self.lexer.next()
                self.lexer.expect("OP", ">")
                return ast.Constructor(name, attrs, [])
            attr_name = self._parse_qname()
            self.lexer.expect("OP", "=")
            self.lexer.expect("PUNCT", "{")
            expr = self.parse_expr()
            self.lexer.expect("PUNCT", "}")
            attrs.append((attr_name, expr))
        contents: List[ast.Content] = []
        self.lexer._buffer = None
        while True:
            if self.lexer.pos >= len(self.text):
                raise SyntaxError("Unterminated constructor")
            if self.text.startswith("</", self.lexer.pos):
                end_name, new_pos = self._read_end_tag()
                if end_name != name:
                    raise SyntaxError("Mismatched end tag")
                self.lexer.pos = new_pos
                self.lexer._buffer = None
                break
            if self.text.startswith("text{", self.lexer.pos):
                self.lexer.pos += 4
                self.lexer._buffer = None
                self.lexer.expect("PUNCT", "{")
                expr = self.parse_expr()
                self.lexer.expect("PUNCT", "}")
                contents.append(ast.TextConstructor(expr))
                continue
            ch = self.text[self.lexer.pos]
            if ch == "<":
                self.lexer._buffer = None
                contents.append(self._parse_constructor())
                continue
            if ch == "{":
                self.lexer.pos += 1
                self.lexer._buffer = None
                expr = self.parse_expr()
                self.lexer.expect("PUNCT", "}")
                contents.append(ast.Interp(expr))
                continue
            text = self._parse_chardata()
            if text and text.strip():
                contents.append(ast.Text(text))
        return ast.Constructor(name, attrs, contents)

    def _parse_chardata(self) -> str:
        text = []
        while True:
            if self.lexer.pos >= len(self.text):
                break
            ch = self.text[self.lexer.pos]
            if ch == "<" or ch == "{":
                break
            text.append(ch)
            self.lexer.pos += 1
        return "".join(text)

    def _read_end_tag(self) -> Tuple[str, int]:
        pos = self.lexer.pos
        if not self.text.startswith("</", pos):
            raise SyntaxError("Expected end tag")
        pos += 2
        start = pos
        while pos < len(self.text) and (self.text[pos].isalnum() or self.text[pos] in "_:-"):
            pos += 1
        name = self.text[start:pos]
        while pos < len(self.text) and self.text[pos].isspace():
            pos += 1
        if pos >= len(self.text) or self.text[pos] != ">":
            raise SyntaxError("Unterminated end tag")
        return name, pos + 1
