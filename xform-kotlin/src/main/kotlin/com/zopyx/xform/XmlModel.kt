package com.zopyx.xform

import org.w3c.dom.Document
import org.w3c.dom.Node as DomNode
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class XmlNode(
    val kind: String,
    var name: String = "",
    var value: String = "",
    val children: MutableList<XmlNode> = mutableListOf(),
    val attrs: MutableMap<String, String> = mutableMapOf(),
    var attrOrder: MutableList<String> = mutableListOf(),
    var parent: XmlNode? = null
) {
    fun stringValue(): String {
        return when (kind) {
            "text", "attribute" -> value
            "element", "document" -> children.joinToString("") { it.stringValue() }
            else -> ""
        }
    }
}

object XmlModel {
    fun parseXMLBytes(data: ByteArray): XmlNode {
        val text = normalizeXMLBytes(data)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val doc = builder.parse(InputSource(StringReader(text)))
        val root = XmlNode("document")
        convertChildren(doc, root)
        return root
    }

    private fun convertChildren(domParent: DomNode, outParent: XmlNode) {
        val list = domParent.childNodes
        for (i in 0 until list.length) {
            val c = list.item(i)
            when (c.nodeType) {
                DomNode.ELEMENT_NODE -> {
                    val n = XmlNode("element", name = c.nodeName)
                    val attrs = c.attributes
                    for (j in 0 until attrs.length) {
                        val a = attrs.item(j) as org.w3c.dom.Attr
                        n.attrs[a.name] = a.value
                        n.attrOrder.add(a.name)
                    }
                    n.parent = outParent
                    outParent.children.add(n)
                    convertChildren(c, n)
                }
                DomNode.TEXT_NODE, DomNode.CDATA_SECTION_NODE -> {
                    val n = XmlNode("text", value = c.nodeValue)
                    n.parent = outParent
                    outParent.children.add(n)
                }
                DomNode.COMMENT_NODE -> {
                    val n = XmlNode("comment", value = c.nodeValue)
                    n.parent = outParent
                    outParent.children.add(n)
                }
                DomNode.PROCESSING_INSTRUCTION_NODE -> {
                    val n = XmlNode("pi", value = (c as org.w3c.dom.ProcessingInstruction).data)
                    n.parent = outParent
                    outParent.children.add(n)
                }
            }
        }
    }

    fun deepCopy(node: XmlNode, recurse: Boolean = true): XmlNode {
        val copied = XmlNode(
            kind = node.kind,
            name = node.name,
            value = node.value,
            attrs = node.attrs.toMutableMap(),
            attrOrder = node.attrOrder.toMutableList()
        )
        if (recurse) {
            for (c in node.children) {
                val child = deepCopy(c, true)
                child.parent = copied
                copied.children.add(child)
            }
        }
        return copied
    }

    fun iterDescendants(node: XmlNode): List<XmlNode> {
        val out = mutableListOf<XmlNode>()
        for (child in node.children) {
            out.add(child)
            out.addAll(iterDescendants(child))
        }
        return out
    }

    fun serialize(node: XmlNode): String {
        return when (node.kind) {
            "document" -> node.children.joinToString("") { serialize(it) }
            "text" -> escapeText(node.value)
            "attribute" -> escapeAttr(node.value)
            "element" -> {
                val keys = if (node.attrOrder.isEmpty()) {
                    node.attrs.keys.sorted()
                } else {
                    node.attrOrder
                }
                val attrs = keys.joinToString("") { " $it=\"${escapeAttr(node.attrs[it] ?: "")}\"" }
                if (node.children.isEmpty()) {
                    "<${node.name}$attrs/>"
                } else {
                    val inner = node.children.joinToString("") { serialize(it) }
                    "<${node.name}$attrs>$inner</${node.name}>"
                }
            }
            else -> ""
        }
    }

    private fun escapeText(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeAttr(text: String): String {
        return escapeText(text).replace("\"", "&quot;")
    }

    private fun normalizeXMLBytes(data: ByteArray): String {
        var text = data.toString(Charsets.UTF_8)
        val lower = text.lowercase()
        if ("encoding=\"iso-8859-1\"" in lower || "encoding='iso-8859-1'" in lower) {
            text = data.toString(Charsets.ISO_8859_1)
                .replace("encoding=\"ISO-8859-1\"", "encoding=\"UTF-8\"")
                .replace("encoding='ISO-8859-1'", "encoding=\"UTF-8\"")
        }
        return replaceNamedEntities(text)
    }

    private fun replaceNamedEntities(text: String): String {
        return text.replace("&mdash;", "—")
            .replace("&hellip;", "…")
            .replace("&nbsp;", "\u00A0")
    }
}
