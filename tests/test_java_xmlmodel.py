from __future__ import annotations

import os
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA_CLASSES = ROOT / "xform-java" / "build" / "classes"
_JAVA_BIN = "/Library/Java/JavaVirtualMachines/graalvm-ce-java17-22.1.0/Contents/Home/bin/java"
if not Path(_JAVA_BIN).exists():
    _JAVA_BIN = "java"
ENABLED_LANGS = {
    s.strip().lower()
    for s in os.getenv("XF_TEST_LANGS", "python,rust,ts,go,swift,js,cpp,java").split(",")
    if s.strip()
}


def _run_java_xmlmodel(cmd: str, xml: str) -> str:
    if "java" not in ENABLED_LANGS:
        pytest.skip("Java tests disabled")
    if not JAVA_CLASSES.exists():
        pytest.skip("Java classes not built")
    result = subprocess.run(
        [_JAVA_BIN, "-cp", str(JAVA_CLASSES), "zopyx.xform.XmlModelCli", cmd, xml],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def test_parse_xml_and_string_value_java() -> None:
    out = _run_java_xmlmodel("summary", "<root a='1'>hi<child/>tail</root>")
    assert out == "root|1|hitail|hitail"


def test_deep_copy_recurse_false_java() -> None:
    out = _run_java_xmlmodel("copy-shallow-child-count", "<root><child/></root>")
    assert out == "0"


def test_iter_descendants_order_java() -> None:
    out = _run_java_xmlmodel("iter-desc", "<root><a/><b><c/></b></root>")
    # Includes text/comments if present; this input has only elements.
    assert out == "a,b,c"


def test_serialize_text_and_attrs_java() -> None:
    xml = "<root q='a\"b'>a&amp;b&lt;c&gt;</root>"
    out = _run_java_xmlmodel("serialize", xml)
    assert out == '<root q="a&quot;b">a&amp;b&lt;c&gt;</root>'


def test_serialize_document_and_empty_element_java() -> None:
    out = _run_java_xmlmodel("serialize-doc", "<empty/>")
    assert out == "<empty/>"
