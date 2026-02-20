from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional, Tuple


@dataclass
class Module:
    functions: dict
    vars: dict
    expr: "Expr"


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
    cases: List[Tuple["Pattern", Expr]]
    default: Optional[Expr]


@dataclass
class FuncCall(Expr):
    name: str
    args: List[Expr]


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
class Text(Expr):
    value: str


@dataclass
class Interp(Expr):
    expr: Expr


Content = Expr


@dataclass
class PathStart:
    kind: str  # 'context', 'root', 'desc', 'desc_root', 'var'
    name: Optional[str] = None


@dataclass
class PathStep:
    axis: str  # 'child', 'desc', 'self', 'parent', 'attr'
    test: "StepTest"
    predicates: List[Expr]


@dataclass
class StepTest:
    kind: str  # 'name', 'wildcard', 'text', 'node', 'comment', 'pi'
    name: Optional[str] = None


class Pattern:
    pass


@dataclass
class WildcardPattern(Pattern):
    pass


@dataclass
class ElementPattern(Pattern):
    name: str
    var: str


@dataclass
class TypedPattern(Pattern):
    kind: str  # 'node', 'text', 'comment'
