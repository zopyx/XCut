from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional, Tuple


@dataclass
class Module:
    functions: dict
    rules: dict
    vars: dict
    namespaces: dict
    imports: list
    expr: "Expr | None"
    version: str = "2.0"


class Expr:
    pass


@dataclass
class Literal(Expr):
    value: object


@dataclass
class VarRef(Expr):
    name: str


@dataclass
class IfExpr(Expr):
    cond: Expr
    then_expr: Expr
    else_expr: Expr


@dataclass
class LetExpr(Expr):
    name: str
    value: Expr
    body: Expr


@dataclass
class ForExpr(Expr):
    name: str
    seq: Expr
    where: Optional[Expr]
    body: Expr


@dataclass
class MatchExpr(Expr):
    target: Expr
    cases: List[Tuple["Pattern", Optional[Expr], Expr]]
    default: Optional[Expr]

    def __post_init__(self):
        normalized = []
        for case in self.cases:
            if len(case) == 2:
                pattern, body = case  # type: ignore[misc]
                normalized.append((pattern, None, body))
            else:
                normalized.append(case)
        self.cases = normalized


@dataclass
class FuncCall(Expr):
    name: str
    args: List[Expr] = field(default_factory=list)
    named_args: List[Tuple[str, Expr]] = field(default_factory=list)


@dataclass
class ApplyExpr(Expr):
    expr: Expr
    ruleset: Optional[str] = None


@dataclass
class UnaryOp(Expr):
    op: str
    expr: Expr


@dataclass
class BinaryOp(Expr):
    op: str
    left: Expr
    right: Expr


@dataclass
class PathExpr(Expr):
    start: "PathStart"
    steps: List["PathStep"]


@dataclass
class Constructor(Expr):
    name: str
    attrs: List[Tuple[str, Expr]]
    contents: List["Content"]


@dataclass
class TextConstructor(Expr):
    expr: Expr


@dataclass
class CommentConstructor(Expr):
    expr: Expr


@dataclass
class PIConstructor(Expr):
    target: Expr
    value: Expr


@dataclass
class Text(Expr):
    value: str


@dataclass
class Interp(Expr):
    expr: Expr


Content = Expr


@dataclass
class PathStart:
    kind: str  # 'context', 'root', 'desc', 'desc_root', 'var', 'attr'
    name: Optional[str] = None


@dataclass
class PathStep:
    axis: str  # 'child', 'desc', 'self', 'parent', 'attr', 'desc_or_self'
    test: "StepTest"
    predicates: List[Expr]


@dataclass
class StepTest:
    kind: str  # 'name', 'wildcard', 'text', 'node', 'element', 'comment', 'pi', 'document'
    name: Optional[str] = None


class Pattern:
    pass


@dataclass
class WildcardPattern(Pattern):
    pass


@dataclass
class ElementPattern(Pattern):
    name: str
    var: Optional[str] = None
    child: Optional[Pattern] = None
    children: List[Pattern] = field(default_factory=list)
    attrs: List[Tuple[str, Optional[Literal]]] = field(default_factory=list)

    def __post_init__(self):
        if self.child is not None and not self.children:
            self.children = [self.child]
            self.child = None


@dataclass
class TypedPattern(Pattern):
    kind: str  # 'node', 'element', 'text', 'comment', 'pi', 'document'


@dataclass
class AttributePattern(Pattern):
    name: str
    value: Optional[Literal] = None


@dataclass
class LiteralPattern(Pattern):
    value: str


@dataclass
class Param:
    name: str
    type_ref: str | None = None
    default: Expr | None = None


@dataclass
class FunctionDef:
    params: List[Param]
    body: Expr


@dataclass
class RuleDef:
    pattern: Pattern
    body: Expr
