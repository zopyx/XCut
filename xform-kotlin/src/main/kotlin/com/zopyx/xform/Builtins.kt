package com.zopyx.xform

typealias BuiltinFn = (List<XFormValue>, EvalContext, Map<String, XFormValue>, Map<String, Expr>) -> XFormValue

object Builtins {
    private const val MAX_RECURSION_DEPTH = 10000
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
        builtins["attributes"] = ::fnAttributes

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

        // String operations
        builtins["contains"] = ::fnContains
        builtins["startsWith"] = ::fnStartsWith
        builtins["endsWith"] = ::fnEndsWith
        builtins["substring"] = ::fnSubstring
        builtins["stringLength"] = ::fnStringLength
        builtins["upperCase"] = ::fnUpperCase
        builtins["lowerCase"] = ::fnLowerCase
        builtins["normalizeSpace"] = ::fnNormalizeSpace
        builtins["replace"] = ::fnReplace
        builtins["matches"] = ::fnMatches

        // Map operations
        builtins["index"] = ::fnIndex
        builtins["lookup"] = ::fnLookup
        builtins["groupBy"] = ::fnGroupBy
        builtins["keys"] = ::fnKeys
        builtins["mapSize"] = ::fnMapSize

        // Dispatch
        builtins["apply"] = ::fnApply
    }

    fun callFunction(
        name: String,
        args: List<XFormValue>,
        ctx: EvalContext,
        namedArgs: Map<String, XFormValue> = emptyMap(),
        namedRaw: Map<String, Expr> = emptyMap()
    ): XFormValue {
        val userFn = ctx.functions[name]
        if (userFn != null) {
            return callUserFunction(userFn, args, ctx, namedArgs, namedRaw)
        }

        val builtin = builtins[name]
        if (builtin != null) {
            return builtin(args, ctx, namedArgs, namedRaw)
        }

        throw XFormException("XFST0003: unknown function $name")
    }

    private fun callUserFunction(
        fn: FunctionDef,
        args: List<XFormValue>,
        ctx: EvalContext,
        namedArgs: Map<String, XFormValue> = emptyMap(),
        namedRaw: Map<String, Expr> = emptyMap()
    ): XFormValue {
        if (ctx.recursionDepth >= MAX_RECURSION_DEPTH) {
            throw XFormException("XFDY0099")
        }
        if (args.size > fn.params.size) {
            throw XFormException("XFDY0002: wrong arity")
        }
        val newVars = ctx.variables.toMutableMap()
        val bound = mutableSetOf<String>()

        // Positional args
        for ((i, arg) in args.withIndex()) {
            if (fn.params[i].name in namedArgs) {
                throw XFormException("XFDY0008: duplicate argument")
            }
            newVars[fn.params[i].name] = arg
            bound.add(fn.params[i].name)
        }

        // Named args
        for ((name, value) in namedArgs) {
            if (name in bound) {
                throw XFormException("XFDY0008: duplicate argument")
            }
            val paramNames = fn.params.map { it.name }
            if (name !in paramNames) {
                throw XFormException("XFDY0008: unknown parameter '$name'")
            }
            newVars[name] = value
            bound.add(name)
        }

        // Defaults for missing params
        for (param in fn.params) {
            if (param.name !in bound) {
                if (param.default == null) {
                    throw XFormException("XFDY0008: missing required parameter")
                }
                newVars[param.name] = Evaluator.evalExpr(param.default, ctx)
                bound.add(param.name)
            }
        }

        val newCtx = ctx.copy().apply {
            variables = newVars
            recursionDepth = ctx.recursionDepth + 1
        }
        return Evaluator.evalExpr(fn.body, newCtx)
    }

    private fun firstOrEmpty(args: List<XFormValue>): XFormValue = args.firstOrNull() ?: emptyList()

    // Type conversion functions
    private fun fnString(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return listOf(Evaluator.toString(firstOrEmpty(args)))
    }

    private fun fnNumber(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return listOf(Evaluator.toNumberOrNaN(firstOrEmpty(args)))
    }

    private fun fnBoolean(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return listOf(Evaluator.toBoolean(firstOrEmpty(args)))
    }

    private fun fnTypeOf(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("null")
        return listOf(when (val item = args[0][0]) {
            is XmlNode -> "node"
            is Map<*, *> -> "map"
            is FunctionRef -> "function"
            is Boolean -> "boolean"
            is Int, is Double -> "number"
            null -> "null"
            else -> "string"
        })
    }

    // Node navigation functions
    private fun fnName(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        return listOf(node.name)
    }

    private fun fnAttr(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        if (node.kind != "element" || args.size < 2) return listOf("")
        val key = Evaluator.toString(args[1])
        return listOf(node.attrs[key] ?: "")
    }

    private fun fnText(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf("")
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        var deep = true
        if (args.size > 1) {
            deep = Evaluator.toBoolean(args[1])
        } else if (named.containsKey("deep")) {
            deep = Evaluator.toBoolean(named["deep"]!!)
        }
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

    private fun fnChildren(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        return node.children.toList()
    }

    private fun fnElements(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        if (node.kind != "element" && node.kind != "document") return emptyList()
        val nameTest = if (args.size > 1) Evaluator.toString(args[1]) else ""
        return node.children.filter { it.kind == "element" && (nameTest.isEmpty() || it.name == nameTest) }
    }

    private fun fnCopy(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        var recurse = true
        if (args.size > 1) {
            recurse = Evaluator.toBoolean(args[1])
        } else if (named.containsKey("recurse")) {
            recurse = Evaluator.toBoolean(named["recurse"]!!)
        }
        return listOf(XmlModel.deepCopy(node, recurse))
    }

    private fun fnAttributes(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val node = args[0][0] as? XmlNode ?: throw XFormException("XFDY0003")
        if (node.kind != "element") return emptyList()
        return node.attrs.map { (k, v) -> XmlNode("attribute", name = k, value = v) }
    }

    // Sequence operations
    private fun fnCount(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return listOf((args.firstOrNull()?.size ?: 0).toDouble())
    }

    private fun fnEmpty(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return listOf(args.isEmpty() || args[0].isEmpty())
    }

    private fun fnDistinct(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return args[0].filter { item ->
            val key = Evaluator.toString(listOf(item))
            seen.add(key)
        }
    }

    private fun fnSort(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
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

    private fun fnConcat(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return args.flatten()
    }

    private fun fnHead(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        return listOf(args[0][0])
    }

    private fun fnTail(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        return args[0].drop(1)
    }

    private fun fnLast(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) {
            if (ctx.last == null) {
                throw XFormException("XFDY0003")
            }
            return listOf(ctx.last!!.toDouble())
        }
        val seq = args[0]
        return if (seq.isEmpty()) emptyList() else listOf(seq.last())
    }

    private fun fnSeq(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        return args.flatten()
    }

    private fun fnPosition(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (ctx.position == null) {
            throw XFormException("XFDY0003")
        }
        return listOf(ctx.position!!.toDouble())
    }

    private fun fnSum(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty()) return listOf(0.0)
        return listOf(args[0].sumOf { Evaluator.toNumber(listOf(it)) })
    }

    // String operations
    private fun fnContains(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 2) return listOf(false)
        val s = Evaluator.toString(args[0])
        val substr = Evaluator.toString(args[1])
        return listOf(s.contains(substr))
    }

    private fun fnStartsWith(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 2) return listOf(false)
        val s = Evaluator.toString(args[0])
        val substr = Evaluator.toString(args[1])
        return listOf(s.startsWith(substr))
    }

    private fun fnEndsWith(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 2) return listOf(false)
        val s = Evaluator.toString(args[0])
        val substr = Evaluator.toString(args[1])
        return listOf(s.endsWith(substr))
    }

    private fun fnSubstring(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty()) return listOf("")
        val s = Evaluator.toString(args[0])
        val start = if (args.size > 1) Evaluator.toNumber(args[1]).toInt() else 1
        val length = if (args.size > 2) Evaluator.toNumber(args[2]).toInt() else s.length
        // 1-based indexing
        val from = (start - 1).coerceIn(0, s.length)
        val to = (from + length).coerceIn(0, s.length)
        return listOf(s.substring(from, to))
    }

    private fun fnNormalizeSpace(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty()) return listOf("")
        val s = Evaluator.toString(args[0])
        return listOf(s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" "))
    }

    private fun fnReplace(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 3) return listOf("")
        val s = Evaluator.toString(args[0])
        val pattern = Evaluator.toString(args[1])
        val replacement = Evaluator.toString(args[2])
        return listOf(s.replace(Regex(pattern), replacement))
    }

    private fun fnStringLength(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        val s = Evaluator.toString(firstOrEmpty(args))
        return listOf(s.length.toDouble())
    }

    private fun fnUpperCase(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        val s = Evaluator.toString(firstOrEmpty(args))
        return listOf(s.uppercase())
    }

    private fun fnLowerCase(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        val s = Evaluator.toString(firstOrEmpty(args))
        return listOf(s.lowercase())
    }

    private fun fnMatches(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 2) return listOf(false)
        val s = Evaluator.toString(args[0])
        val pattern = Evaluator.toString(args[1])
        return listOf(s.contains(pattern))
    }

    // Map operations
    private fun fnIndex(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty()) return emptyList()
        val seq = args[0]

        var keyFn = ""
        var keyExpr: Expr? = null
        if (args.size > 1 && args[1].isNotEmpty() && args[1][0] is FunctionRef) {
            keyFn = (args[1][0] as FunctionRef).name
        }
        if (namedRaw.containsKey("key")) {
            keyExpr = namedRaw["key"]
        }

        val index = mutableMapOf<String, MutableList<Any?>>()
        for (item in seq) {
            var key = Evaluator.toString(listOf(item))
            if (keyFn.isNotEmpty()) {
                val fn = ctx.functions[keyFn]!!
                key = Evaluator.toString(callUserFunction(fn, listOf(listOf(item)), ctx))
            } else if (keyExpr != null) {
                val itemCtx = ctx.copy().apply {
                    contextItem = item
                    variables = ctx.variables.toMutableMap()
                }
                key = Evaluator.toString(Evaluator.evalExpr(keyExpr, itemCtx))
            }
            index.getOrPut(key) { mutableListOf() }.add(item)
        }
        return listOf(index)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fnLookup(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.size < 2 || args[0].isEmpty()) return emptyList()
        val mapping = args[0][0] as? Map<String, List<Any?>> ?: return emptyList()
        val key = Evaluator.toString(args[1])
        return mapping[key] ?: emptyList()
    }

    private fun fnGroupBy(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
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

    @Suppress("UNCHECKED_CAST")
    private fun fnKeys(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return emptyList()
        val map = args[0][0] as? Map<String, List<Any?>> ?: return emptyList()
        return map.keys.sorted().toList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fnMapSize(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (args.isEmpty() || args[0].isEmpty()) return listOf(0.0)
        val map = args[0][0] as? Map<String, List<Any?>> ?: return listOf(0.0)
        return listOf(map.size.toDouble())
    }

    // Apply rules
    private fun fnApply(args: List<XFormValue>, ctx: EvalContext, named: Map<String, XFormValue>, namedRaw: Map<String, Expr>): XFormValue {
        if (ctx.recursionDepth >= MAX_RECURSION_DEPTH) {
            throw XFormException("XFDY0099")
        }
        if (args.isEmpty()) return emptyList()
        val seq = args[0]
        val ruleset = if (args.size > 1 && args[1].isNotEmpty()) {
            Evaluator.toString(args[1])
        } else "main"

        val rules = ctx.rules[ruleset]
        if (rules == null) {
            throw XFormException("XFST0007: unknown ruleset '$ruleset'")
        }
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
                        recursionDepth = ctx.recursionDepth + 1
                    }
                    out.addAll(Evaluator.evalExpr(rule.body, newCtx))
                    break
                }
            }
            if (!matched) {
                out.addAll(applyBuiltinIdentity(item))
            }
        }
        return out
    }

    private fun applyBuiltinIdentity(item: Any?): XFormValue {
        if (item is XmlNode) {
            return when (item.kind) {
                "document", "element", "attribute", "text", "comment", "pi" ->
                    listOf(XmlModel.deepCopy(item, true))
                else -> emptyList()
            }
        }
        return listOf(item)
    }
}
