from __future__ import annotations

from xform.xmlmodel import (
    Node,
    deep_copy,
    iter_descendants,
    parse_xml,
    serialize,
)


def test_parse_xml_and_string_value() -> None:
    doc = parse_xml("<root a='1'>hi<child/>tail</root>")
    root = doc.children[0]
    assert root.attrs["a"] == "1"
    assert root.string_value() == "hitail"
    assert doc.string_value() == "hitail"


def test_deep_copy_recurse_false() -> None:
    root = Node(kind="element", name="root")
    child = Node(kind="element", name="child", parent=root)
    root.children = [child]
    copied = deep_copy(root, recurse=False)
    assert copied.children == []


def test_iter_descendants_order() -> None:
    root = Node(kind="element", name="root")
    a = Node(kind="element", name="a", parent=root)
    b = Node(kind="element", name="b", parent=root)
    c = Node(kind="element", name="c", parent=b)
    root.children = [a, b]
    b.children = [c]
    names = [n.name for n in iter_descendants(root)]
    assert names == ["a", "b", "c"]


def test_serialize_text_and_attrs() -> None:
    root = Node(kind="element", name="root", attrs={"q": 'a"b'})
    root.children = [Node(kind="text", value="a&b<c>")]
    out = serialize(root)
    assert out == '<root q="a&quot;b">a&amp;b&lt;c&gt;</root>'


def test_serialize_document_and_empty_element() -> None:
    empty = Node(kind="element", name="empty")
    doc = Node(kind="document", children=[empty])
    assert serialize(doc) == "<empty/>"
