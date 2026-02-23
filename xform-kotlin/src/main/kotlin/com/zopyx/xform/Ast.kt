package com.zopyx.xform

// AST Expressions
sealed interface Expr

data class Literal(val value: Any?) : Expr
data class VarRef(val name: String) : Expr
data class IfExpr(val cond: Expr, val thenExpr: Expr, val elseExpr: Expr) : Expr
data class LetExpr(val name: String, val value: Expr, val body: Expr) : Expr
data class ForExpr(val name: String, val seq: Expr, val where: Expr?, val body: Expr) : Expr
data class MatchExpr(val target: Expr, val cases: List<MatchCase>, val default: Expr?) : Expr
data class MatchCase(val pattern: Pattern, val expr: Expr)
data class FuncCall(val name: String, val args: List<Expr>) : Expr
data class UnaryOp(val op: String, val expr: Expr) : Expr
data class BinaryOp(val op: String, val left: Expr, val right: Expr) : Expr
data class PathExpr(val start: PathStart, val steps: List<PathStep>) : Expr
data class Constructor(val name: String, val attrs: List<AttrConstructor>, val contents: List<Expr>) : Expr
data class AttrConstructor(val name: String, val expr: Expr)
data class TextConstructor(val expr: Expr) : Expr
data class Text(val value: String) : Expr
data class Interp(val expr: Expr) : Expr

// Path expressions
data class PathStart(val kind: String, val name: String? = null)
data class PathStep(val axis: String, val test: StepTest, val predicates: List<Expr> = emptyList())
data class StepTest(val kind: String, val name: String? = null)

// Patterns
sealed interface Pattern
object WildcardPattern : Pattern
data class ElementPattern(val name: String, val variable: String? = null, val child: Pattern? = null) : Pattern
data class TypedPattern(val kind: String) : Pattern
data class AttributePattern(val name: String) : Pattern

// Module declarations
data class Module(
    val functions: MutableMap<String, FunctionDef> = mutableMapOf(),
    val rules: MutableMap<String, MutableList<RuleDef>> = mutableMapOf(),
    val vars: MutableMap<String, Expr> = mutableMapOf(),
    val namespaces: MutableMap<String, String> = mutableMapOf(),
    val imports: MutableList<ImportDecl> = mutableListOf(),
    var expr: Expr? = null
)

data class ImportDecl(val iri: String, val alias: String? = null)
data class Param(val name: String, val typeRef: String? = null, val default: Expr? = null)
data class FunctionDef(val params: List<Param>, val body: Expr)
data class RuleDef(val pattern: Pattern, val body: Expr)

// Function reference (for passing functions as arguments)
data class FunctionRef(val name: String)
