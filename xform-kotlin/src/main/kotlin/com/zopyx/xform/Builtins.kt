package com.zopyx.xform

typealias BuiltinFn = (List<XFormValue>, EvalContext) -> XFormValue

object Builtins {
    private val builtins = mutableMapOf<String, BuiltinFn>()

    init {
        // Type conversion
        builtins["string"] = ::fnString
        builtins["number"] = ::fnNumber
        builtins["boolean"] = ::fnBoolean
        builtins["typeOf"] = ::fnTypeOf

        // Node navigation
        builtins["name"] = ::fnName
        builtins["attr"] = ::fnAttr
        builtins["text"] = ::fnText
        builtins["children"] = ::fnChildren
        builtins["elements"] = ::fnElements
        builtins["copy"] = ::fnCopy

        // Sequence operations
        builtins["count"] = ::fnCount
        builtins["empty"] = ::fnEmpty
        builtins["distinct"] = ::fnDistinct
        builtins["sort"] = ::fnSort
        builtins["concat"] = ::fnConcat
        builtins["head"] = ::fnHead
        builtins["tail"] = ::fnTail
        builtins["last"] = ::fnLast
        builtins["seq"] = ::fnSeq
        builtins["position"] = ::fnPosition
        builtins["sum"] = ::fnSum

        // Map operations
        builtins["index"] = ::fnIndex
        builtins["lookup"] = ::fnLookup
        builtins["groupBy"] = ::fnGroupBy

        // Dispatch
        builtins["apply"] = ::fnApply
    }

    fun callFunction(name: String, args: List<XFormValue>, ctx: EvalContext): XFormValue {
        val userFn = ctx.functions[name]
        if (userFn != null) {
            return callUserFunction(userFn, args, ctx)
        }

        val builtin = builtins[name]
        if (builtin != null) {
            return builtin(args, ctx)
        }

        throw XFormException("XFST0003: unknown function $name")
    }

    private fun callUserFunction(fn: FunctionDef, args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.size > fn.params.size) {
            throw XFormException("XFDY0002: wrong arity")
        }
        val newVars = ctx.variables.toMutableMap()
        for ((i, arg) in args.withIndex()) {
            newVars[fn.params[i].name] = arg
        }
        for (i in args.size until fn.params.size) {
            val param = fn.params[i]
            if (param.default == null) {
                throw XFormException("XFDY0002: wrong arity")
            }
            newVars[param.name] = Evaluator.evalExpr(param.default, ctx)
        }
        val newCtx = ctx.copy().apply { variables = newVars }
        return Evaluator.evalExpr(fn.body, newCtx)
    }

    private fun firstOrEmpty(args: List<XFormValue>): XFormValue = args.firstOrNull() ?: emptyList()

    // Type conversion functions
    private fun fnString(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return listOf(Evaluator.toString(firstOrEmpty(args)))
    }

    private fun fnNumber(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return listOf(Evaluator.toNumber(firstOrEmpty(args)))
    }

    private fun fnBoolean(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return listOf(Evaluator.toBoolean(firstOrEmpty(args)))
    }

    private fun fnTypeOf(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("null")
        return listOf(when (val item = args[0][0]) {
            is XmlNode -> "node"
            is Map<*, *> -> "map"
            is Boolean -> "boolean"
            is Int, is Double -> "number"
            null -> "null"
            else -> "string"
        })
    }

    // Node navigation functions
    private fun fnName(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: return listOf("")
        return listOf(node.name)
    }

    private fun fnAttr(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: return listOf("")
        if (node.kind != "element" || args.size < 2) return listOf("")
        val key = Evaluator.toString(args[1])
        return listOf(node.attrs[key] ?: "")
    }

    private fun fnText(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: return listOf(Evaluator.toString(args[0]))
        val deep = if (args.size > 1) Evaluator.toBoolean(args[1]) else true
        return if (deep) {
            listOf(node.stringValue())
        } else {
            if (node.kind == "element" || node.kind == "document") {
                val direct = node.children.filter { it.kind == "text" }.joinToString("") { it.value }
                listOf(direct)
            } else {
                listOf(node.stringValue())
            }
        }
    }

    private fun fnChildren(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: return emptyList()
        return node.children.toList()
    }

    private fun fnElements(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: return emptyList()
        if (node.kind != "element" && node.kind != "document") return emptyList()
        val nameTest = if (args.size > 1) Evaluator.toString(args[1]) else ""
        return node.children.filter { it.kind == "element" && (nameTest.isEmpty() || it.name == nameTest) }
    }

    private fun fnCopy(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: return emptyList()
        val recurse = if (args.size > 1) Evaluator.toBoolean(args[1]) else true
        return listOf(XmlModel.deepCopy(node, recurse))
    }

    // Sequence operations
    private fun fnCount(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return listOf((args.firstOrNull()?.size ?: 0).toDouble())
    }

    private fun fnEmpty(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return listOf(args.isEmpty() || args[0].isEmpty())
    }

    private fun fnDistinct(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return args[0].filter { item ->
            val key = Evaluator.toString(listOf(item))
            seen.add(key)
        }
    }

    private fun fnSort(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seq = args[0].toMutableList()
        val keyFn = if (args.size > 1 && args[1].isNotEmpty() && args[1][0] is FunctionRef) {
            (args[1][0] as FunctionRef).name
        } else ""

        seq.sortWith { a, b ->
            if (keyFn.isNotEmpty()) {
                val fn = ctx.functions[keyFn]!!
                val ka = Evaluator.toString(callUserFunction(fn, listOf(listOf(a)), ctx))
                val kb = Evaluator.toString(callUserFunction(fn, listOf(listOf(b)), ctx))
                ka.compareTo(kb)
            } else {
                Evaluator.toString(listOf(a)).compareTo(Evaluator.toString(listOf(b)))
            }
        }
        return seq
    }

    private fun fnConcat(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return args.flatten()
    }

    private fun fnHead(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        return listOf(args[0][0])
    }

    private fun fnTail(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        return args[0].drop(1)
    }

    private fun fnLast(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) {
            return ctx.last?.let { listOf(it.toDouble()) } ?: emptyList()
        }
        val seq = args[0]
        return if (seq.isEmpty()) emptyList() else listOf(seq.last())
    }

    private fun fnSeq(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return args.flatten()
    }

    private fun fnPosition(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        return ctx.position?.let { listOf(it.toDouble()) } ?: emptyList()
    }

    private fun fnSum(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty()) return listOf(0.0)
        return listOf(args[0].sumOf { Evaluator.toNumber(listOf(it)) })
    }

    // Map operations
    private fun fnIndex(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seq = args[0]
        val keyFn = if (args.size > 1 && args[1].isNotEmpty() && args[1][0] is FunctionRef) {
            (args[1][0] as FunctionRef).name
        } else ""

        val index = mutableMapOf<String, MutableList<Any?>>()
        for (item in seq) {
            var key = Evaluator.toString(listOf(item))
            if (keyFn.isNotEmpty()) {
                val fn = ctx.functions[keyFn]!!
                key = Evaluator.toString(callUserFunction(fn, listOf(listOf(item)), ctx))
            }
            index.getOrPut(key) { mutableListOf() }.add(item)
        }
        return listOf(index)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fnLookup(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.size < 2 || args[0].isEmpty()) return emptyList()
        val mapping = args[0][0] as? Map<String, List<Any?>> ?: return emptyList()
        val key = Evaluator.toString(args[1])
        return mapping[key] ?: emptyList()
    }

    private fun fnGroupBy(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.size < 2) return emptyList()
        val seq = args[0]
        val keyFn = if (args[1].isNotEmpty() && args[1][0] is FunctionRef) {
            (args[1][0] as FunctionRef).name
        } else ""

        val groups = mutableMapOf<String, MutableList<Any?>>()
        for (item in seq) {
            var key = Evaluator.toString(listOf(item))
            if (keyFn.isNotEmpty()) {
                val fn = ctx.functions[keyFn]!!
                key = Evaluator.toString(callUserFunction(fn, listOf(listOf(item)), ctx))
            }
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }

        return groups.map { (k, v) ->
            mutableMapOf<String, List<Any?>>("key" to listOf(k), "items" to v)
        }
    }

    // Apply rules
    private fun fnApply(args: List<XFormValue>, ctx: EvalContext): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seq = args[0]
        val ruleset = if (args.size > 1 && args[1].isNotEmpty()) {
            Evaluator.toString(args[1])
        } else "main"

        val rules = ctx.rules[ruleset] ?: return emptyList()
        val out = mutableListOf<Any?>()

        for (item in seq) {
            var matched = false
            for (rule in rules) {
                val (ok, bindings) = Evaluator.matchPattern(rule.pattern, item)
                if (ok) {
                    matched = true
                    val newCtx = ctx.copy().apply {
                        contextItem = item
                        variables.putAll(bindings)
                    }
                    out.addAll(Evaluator.evalExpr(rule.body, newCtx))
                    break
                }
            }
            if (!matched) {
                throw XFormException("XFDY0001: no matching rule")
            }
        }
        return out
    }
}
