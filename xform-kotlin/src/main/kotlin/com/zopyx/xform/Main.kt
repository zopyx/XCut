package com.zopyx.xform

import java.io.File

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: xform <input.xml> <transform.xform>")
        System.exit(1)
    }

    try {
        val xmlBytes = File(args[0]).readBytes()
        val xformText = File(args[1]).readText()

        val doc = XmlModel.parseXMLBytes(xmlBytes)
        val module = Parser(xformText).parseModule()
        val result = Evaluator.evalModule(module, doc)

        val out = result.joinToString("") { Evaluator.serializeItem(it) }
        println(out)
    } catch (e: Exception) {
        System.err.println(e.message ?: e.toString())
        System.exit(1)
    }
}
