package com.zopyx.xform

import kotlin.math.floor

object Evaluator {
    fun evalModule(module: Module, doc: XmlNode): XFormValue {
        val ctx = EvalContext(
            contextItem = doc,
            functions = module.functions,
            rules = module.rules
        )
        for ((name, expr) in module.vars) {
            ctx.variables[name] = evalExpr(expr, ctx)
        }
        return module.expr?.let { evalExpr(it, ctx) } ?: emptyList()
    }

    fun evalExpr(expr: Expr, ctx: EvalContext): XFormValue {
        return when (expr) {
            is Literal -> listOf(expr.value)
            is VarRef -> evalVarRef(expr, ctx)
            is IfExpr -> if (toBoolean(evalExpr(expr.cond, ctx))) {
                evalExpr(expr.thenExpr, ctx)
            } else {
                evalExpr(expr.elseExpr, ctx)
            }
            is LetExpr -> {
                val newCtx = ctx.copy()
                newCtx.variables[expr.name] = evalExpr(expr.value, ctx)
                evalExpr(expr.body, newCtx)
            }
            is ForExpr -> evalFor(expr, ctx)
            is MatchExpr -> evalMatch(expr, ctx)
            is FuncCall -> {
                val args = expr.args.map { evalExpr(it, ctx) }
                Builtins.callFunction(expr.name, args, ctx)
            }
            is UnaryOp -> {
                val v = evalExpr(expr.expr, ctx)
                when (expr.op) {
                    "-" -> listOf(-toNumber(v))
                    "not" -> listOf(!toBoolean(v))
                    else -> throw XFormException("unknown unary op ${expr.op}")
                }
            }
            is BinaryOp -> evalBinaryOp(expr, ctx)
            is PathExpr -> evalPath(expr, ctx)
            is Constructor -> listOf(evalConstructor(expr, ctx))
            is TextConstructor -> {
                val text = toString(evalExpr(expr.expr, ctx))
                listOf(XmlNode("text", value = text))
            }
            is Text -> listOf(expr.value)
            is Interp -> evalExpr(expr.expr, ctx)
        }
    }

    private fun evalVarRef(expr: VarRef, ctx: EvalContext): XFormValue {
        ctx.variables[expr.name]?.let { return it }
        if (expr.name in ctx.functions) {
            return listOf(FunctionRef(expr.name))
        }
        val node = ctx.contextItem as? XmlNode
        if (node != null) {
            return node.children.filter { it.kind == "element" && it.name == expr.name }
        }
        return emptyList()
    }

    private fun evalFor(expr: ForExpr, ctx: EvalContext): XFormValue {
        val source = evalExpr(expr.seq, ctx)
        val out = mutableListOf<Any?>()
        val total = source.size
        for ((idx, item) in source.withIndex()) {
            val newCtx = ctx.copy().apply {
                contextItem = item
                variables[expr.name] = listOf(item)
                position = idx + 1
                last = total
            }
            if (expr.where != null && !toBoolean(evalExpr(expr.where, newCtx))) {
                continue
            }
            out.addAll(evalExpr(expr.body, newCtx))
        }
        return out
    }

    private fun evalMatch(expr: MatchExpr, ctx: EvalContext): XFormValue {
        val target = evalExpr(expr.target, ctx)
        val out = mutableListOf<Any?>()
        for (item in target) {
            var matched = false
            for (c in expr.cases) {
                val (ok, bindings) = matchPattern(c.pattern, item)
                if (ok) {
                    matched = true
                    val newCtx = ctx.copy().apply {
                        contextItem = item
                        variables.putAll(bindings)
                    }
                    out.addAll(evalExpr(c.expr, newCtx))
                    break
                }
            }
            if (!matched) {
                if (expr.default == null) {
                    throw XFormException("XFDY0001: no matching case")
                }
                val newCtx = ctx.copy().apply { contextItem = item }
                out.addAll(evalExpr(expr.default, newCtx))
            }
        }
        return out
    }

    private fun evalBinaryOp(expr: BinaryOp, ctx: EvalContext): XFormValue {
        return when (expr.op) {
            "and" -> {
                val left = evalExpr(expr.left, ctx)
                if (!toBoolean(left)) return listOf(false)
                listOf(toBoolean(evalExpr(expr.right, ctx)))
            }
            "or" -> {
                val left = evalExpr(expr.left, ctx)
                if (toBoolean(left)) return listOf(true)
                listOf(toBoolean(evalExpr(expr.right, ctx)))
            }
            else -> {
                val left = evalExpr(expr.left, ctx)
                val right = evalExpr(expr.right, ctx)
                listOf(evalBinary(expr.op, left, right))
            }
        }
    }

    private fun evalBinary(op: String, left: XFormValue, right: XFormValue): Any? {
        return when (op) {
            "=" -> valueEqual(left, right)
            "!=" -> !valueEqual(left, right)
            "+" -> toNumber(left) + toNumber(right)
            "-" -> toNumber(left) - toNumber(right)
            "*" -> toNumber(left) * toNumber(right)
            "div" -> toNumber(left) / toNumber(right)
            "mod" -> toNumber(left) % toNumber(right)
            "<" -> toNumber(left) < toNumber(right)
            "<=" -> toNumber(left) <= toNumber(right)
            ">" -> toNumber(left) > toNumber(right)
            ">=" -> toNumber(left) >= toNumber(right)
            else -> throw XFormException("unknown operator $op")
        }
    }

    private fun evalPath(expr: PathExpr, ctx: EvalContext): XFormValue {
        val steps = expr.steps.toMutableList()
        val base = when (expr.start.kind) {
            "context" -> ctx.contextItem?.let { listOf(it) } ?: emptyList()
            "root" -> rootOf(ctx.contextItem)
            "desc" -> ctx.contextItem?.let { listOf(it) } ?: emptyList()
            "desc_root" -> rootOf(ctx.contextItem)
            "var" -> {
                val name = expr.start.name
                if (name != null && name in ctx.variables) {
                    ctx.variables[name]!!
                } else {
                    ctx.contextItem?.let {
                        steps.add(0, PathStep("child", StepTest("name", name)))
                        listOf(it)
                    } ?: emptyList()
                }
            }
            else -> emptyList()
        }

        return steps.fold(base) { current, step -> applyStep(current, step, ctx) }
    }

    private fun rootOf(item: Any?): XFormValue {
        var cur = item as? XmlNode ?: return emptyList()
        while (cur.parent != null) {
            cur = cur.parent!!
        }
        return listOf(cur)
    }

    private fun applyStep(items: XFormValue, step: PathStep, ctx: EvalContext): XFormValue {
        val out = mutableListOf<Any?>()
        for (item in items) {
            val node = item as? XmlNode ?: continue
            
            val candidates = when (step.axis) {
                "self" -> listOf(node)
                "parent" -> node.parent?.let { listOf(it) } ?: emptyList()
                "desc_or_self" -> listOf(node) + XmlModel.iterDescendants(node)
                "desc" -> XmlModel.iterDescendants(node)
                "attr" -> {
                    if (node.kind == "element") {
                        when (step.test.kind) {
                            "name" -> {
                                val name = step.test.name
                                if (name != null && name in node.attrs) {
                                    listOf(XmlNode("attribute", name = name, value = node.attrs[name]!!))
                                } else emptyList()
                            }
                            "wildcard" -> node.attrs.map { (k, v) ->
                                XmlNode("attribute", name = k, value = v)
                            }
                            else -> emptyList()
                        }
                    } else emptyList()
                }
                "child" -> node.children
                else -> emptyList()
            }

            val filtered = candidates.filter { matchesStepTest(step.test, it) }
            val afterPreds = applyPredicates(filtered, step.predicates, ctx)
            out.addAll(afterPreds)
        }
        return out
    }

    private fun applyPredicates(nodes: List<XmlNode>, preds: List<Expr>, ctx: EvalContext): List<XmlNode> {
        var result = nodes
        for (pred in preds) {
            result = result.filterIndexed { idx, node ->
                val predCtx = ctx.copy().apply {
                    contextItem = node
                    position = idx + 1
                    last = result.size
                }
                toBoolean(evalExpr(pred, predCtx))
            }
        }
        return result
    }

    private fun matchesStepTest(test: StepTest, node: XmlNode): Boolean {
        return when (test.kind) {
            "wildcard" -> node.kind == "element"
            "text" -> node.kind == "text"
            "node" -> true
            "comment" -> node.kind == "comment"
            "pi" -> node.kind == "pi"
            "name" -> node.name == test.name
            else -> false
        }
    }

    private fun evalConstructor(expr: Constructor, ctx: EvalContext): XmlNode {
        val node = XmlNode("element", name = expr.name)
        for (attr in expr.attrs) {
            val value = toString(evalExpr(attr.expr, ctx))
            node.attrs[attr.name] = value
            node.attrOrder.add(attr.name)
        }

        val children = mutableListOf<XmlNode>()
        for (content in expr.contents) {
            when (content) {
                is Text -> {
                    children.add(XmlNode("text", value = content.value))
                }
                else -> {
                    val seq = evalExpr(content, ctx)
                    for (item in seq) {
                        if (item is XmlNode) {
                            children.add(XmlModel.deepCopy(item, true))
                        } else {
                            children.add(XmlNode("text", value = toString(listOf(item))))
                        }
                    }
                }
            }
        }

        for (c in children) {
            c.parent = node
        }
        node.children.addAll(children)
        return node
    }

    fun matchPattern(pattern: Pattern, item: Any?): Pair<Boolean, MutableMap<String, XFormValue>> {
        val bindings = mutableMapOf<String, XFormValue>()
        return when (pattern) {
            is WildcardPattern -> true to bindings
            is AttributePattern -> {
                val node = item as? XmlNode
                (node?.kind == "attribute" && node.name == pattern.name) to bindings
            }
            is TypedPattern -> {
                val node = item as? XmlNode
                val ok = when (pattern.kind) {
                    "node" -> node != null
                    "text" -> node?.kind == "text"
                    "comment" -> node?.kind == "comment"
                    else -> false
                }
                ok to bindings
            }
            is ElementPattern -> {
                val node = item as? XmlNode
                if (node?.kind == "element" && node.name == pattern.name) {
                    if (pattern.variable != null) {
                        bindings[pattern.variable] = node.children.toList()
                    }
                    if (pattern.child != null) {
                        for (child in node.children) {
                            val (ok, childBindings) = matchPattern(pattern.child, child)
                            if (ok) {
                                bindings.putAll(childBindings)
                                return true to bindings
                            }
                        }
                        return false to bindings
                    }
                    true to bindings
                } else {
                    false to bindings
                }
            }
            else -> false to bindings
        }
    }

    fun toBoolean(seq: XFormValue): Boolean {
        if (seq.isEmpty()) return false
        for (item in seq) {
            if (item is XmlNode) return true
        }
        for (item in seq) {
            when (item) {
                is Boolean -> if (item) return true
                is Int -> if (item != 0) return true
                is Double -> if (item != 0.0) return true
                is String -> if (item.isNotEmpty()) return true
                null -> {}
                else -> return true
            }
        }
        return false
    }

    fun toString(seq: XFormValue): String {
        if (seq.isEmpty()) return ""
        return when (val item = seq[0]) {
            is XmlNode -> item.stringValue()
            null -> ""
            is Boolean -> item.toString()
            is Double -> {
                if (item == floor(item)) {
                    item.toLong().toString()
                } else {
                    item.toString()
                }
            }
            else -> item.toString()
        }
    }

    fun toNumber(seq: XFormValue): Double {
        if (seq.isEmpty()) return 0.0
        val item = seq[0]
        return when (item) {
            is XmlNode -> toNumber(listOf(item.stringValue()))
            is Boolean -> if (item) 1.0 else 0.0
            is Int -> item.toDouble()
            is Double -> item
            is String -> try {
                item.toDouble()
            } catch (_: NumberFormatException) {
                throw XFormException("XFDY0002: number conversion")
            }
            else -> throw XFormException("XFDY0002: number conversion")
        }
    }

    fun valueEqual(left: XFormValue, right: XFormValue): Boolean {
        return toString(left) == toString(right)
    }

    fun serializeItem(item: Any?): String {
        return if (item is XmlNode) {
            XmlModel.serialize(item)
        } else {
            toString(listOf(item))
        }
    }
}
