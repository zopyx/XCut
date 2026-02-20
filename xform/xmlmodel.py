from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Iterable
import xml.etree.ElementTree as ET


@dataclass
class Node:
    kind: str  # document, element, attribute, text, comment, pi
    name: Optional[str] = None
    value: Optional[str] = None
    children: List["Node"] = field(default_factory=list)
    attrs: Dict[str, str] = field(default_factory=dict)
    parent: Optional["Node"] = None

    def string_value(self) -> str:
        if self.kind == "text":
            return self.value or ""
        if self.kind == "attribute":
            return self.value or ""
        if self.kind in ("element", "document"):
            return "".join(child.string_value() for child in self.children)
        return ""


def parse_xml(text: str) -> Node:
    root_el = ET.fromstring(text)
    doc = Node(kind="document")
    root_node = _build_element(root_el)
    root_node.parent = doc
    doc.children = [root_node]
    return doc


def _build_element(el: ET.Element) -> Node:
    node = Node(kind="element", name=el.tag, attrs=dict(el.attrib))
    children: List[Node] = []

    if el.text:
        children.append(Node(kind="text", value=el.text, parent=node))

    for child in list(el):
        child_node = _build_element(child)
        child_node.parent = node
        children.append(child_node)
        if child.tail:
            children.append(Node(kind="text", value=child.tail, parent=node))

    node.children = children
    return node


def deep_copy(node: Node, recurse: bool = True) -> Node:
    copied = Node(kind=node.kind, name=node.name, value=node.value, attrs=dict(node.attrs))
    if recurse:
        copied.children = [deep_copy(c, recurse=recurse) for c in node.children]
        for c in copied.children:
            c.parent = copied
    return copied


def iter_descendants(node: Node) -> Iterable[Node]:
    for child in node.children:
        yield child
        yield from iter_descendants(child)


def serialize(item: Node) -> str:
    if item.kind == "document":
        return "".join(serialize(c) for c in item.children)
    if item.kind == "text":
        return _escape_text(item.value or "")
    if item.kind == "attribute":
        return _escape_attr(item.value or "")
    if item.kind == "element":
        attrs = "".join(
            f" {name}=\"{_escape_attr(value)}\"" for name, value in item.attrs.items()
        )
        if not item.children:
            return f"<{item.name}{attrs}/>"
        inner = "".join(serialize(c) for c in item.children)
        return f"<{item.name}{attrs}>{inner}</{item.name}>"
    return ""


def _escape_text(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def _escape_attr(text: str) -> str:
    return _escape_text(text).replace('"', "&quot;")
