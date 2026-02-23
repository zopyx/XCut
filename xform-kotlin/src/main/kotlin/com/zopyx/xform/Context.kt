package com.zopyx.xform

typealias XFormValue = List<Any?>

data class EvalContext(
    var contextItem: Any? = null,
    var variables: MutableMap<String, XFormValue> = mutableMapOf(),
    var functions: Map<String, FunctionDef> = emptyMap(),
    var rules: Map<String, List<RuleDef>> = emptyMap(),
    var position: Int? = null,
    var last: Int? = null
) {
    fun copy(): EvalContext = EvalContext(
        contextItem = contextItem,
        variables = variables.toMutableMap(),
        functions = functions,
        rules = rules,
        position = position,
        last = last
    )
}
