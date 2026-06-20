from __future__ import annotations

import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures_22"
ENABLED_LANGS = {
    s.strip().lower()
    for s in os.getenv("XF_TEST_LANGS", "python,rust,ts,go,swift,js,cpp,java,kotlin,c").split(",")
    if s.strip()
}

RUST_XFORM_BIN = ROOT / "xform-rs" / "target" / "release" / "xform"
TS_XFORM_BIN = ROOT / "xform-ts" / "dist" / "cli.js"
GO_XFORM_BIN = ROOT / "xform-go" / "bin" / "xform"
SWIFT_XFORM_BIN = ROOT / "xform-swift" / ".build" / "release" / "xform-swift"
JS_XFORM_BIN = ROOT / "xform-js" / "src" / "cli.js"
JAVA_XFORM_BIN = ROOT / "xform-java" / "bin" / "xform"
KOTLIN_XFORM_JAR = ROOT / "xform-kotlin" / "build" / "libs" / "xform-kotlin-1.0.jar"
CPP_XFORM_BIN = ROOT / "xform-cpp" / "build" / "src" / "xform"
C_XFORM_BIN = ROOT / "xform-c" / "build" / "src" / "xform"


def _cases():
    return sorted(p for p in FIXTURES.iterdir() if p.is_dir())


def _normalize_xml(text: str) -> str:
    stripped = text.lstrip()
    if stripped.startswith("<?xml"):
        stripped = stripped.split("?>", 1)[1]
    wrapped = f"<_root>{stripped}</_root>"
    root = ET.fromstring(wrapped)
    _strip_ws(root)
    if root.text is not None:
        root.text = root.text.lstrip()
    return ET.tostring(root, encoding="unicode")


def _strip_ws(elem: ET.Element) -> None:
    if elem.text is not None and elem.text.strip() == "":
        elem.text = ""
    if elem.tail is not None and elem.tail.strip() == "":
        elem.tail = ""
    for child in list(elem):
        _strip_ws(child)


def _run(cmd: list[str]) -> str:
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()


def _cmd(lang: str, xform: Path, xml: Path) -> list[str]:
    if lang not in ENABLED_LANGS:
        pytest.skip(f"{lang} tests disabled")
    if lang == "python":
        return [sys.executable, "-m", "zopyx.xform.cli", str(xml), str(xform)]
    if lang == "rust":
        if not RUST_XFORM_BIN.exists():
            pytest.skip("Rust xform binary not built")
        return [str(RUST_XFORM_BIN), str(xml), str(xform)]
    if lang == "ts":
        if not TS_XFORM_BIN.exists():
            pytest.skip("TypeScript xform binary not built")
        return ["node", str(TS_XFORM_BIN), str(xml), str(xform)]
    if lang == "go":
        if not GO_XFORM_BIN.exists():
            pytest.skip("Go xform binary not built")
        return [str(GO_XFORM_BIN), str(xml), str(xform)]
    if lang == "swift":
        if not SWIFT_XFORM_BIN.exists():
            pytest.skip("Swift xform binary not built")
        return [str(SWIFT_XFORM_BIN), str(xml), str(xform)]
    if lang == "js":
        if not JS_XFORM_BIN.exists():
            pytest.skip("JavaScript xform CLI not built")
        return ["node", str(JS_XFORM_BIN), str(xml), str(xform)]
    if lang == "java":
        if not JAVA_XFORM_BIN.exists():
            pytest.skip("Java xform binary not built")
        return [str(JAVA_XFORM_BIN), str(xml), str(xform)]
    if lang == "kotlin":
        if not KOTLIN_XFORM_JAR.exists():
            pytest.skip("Kotlin xform binary not built")
        return ["java", "-jar", str(KOTLIN_XFORM_JAR), str(xml), str(xform)]
    if lang == "cpp":
        if not CPP_XFORM_BIN.exists():
            pytest.skip("C++ xform binary not built")
        return [str(CPP_XFORM_BIN), str(xml), str(xform)]
    if lang == "c":
        if not C_XFORM_BIN.exists():
            pytest.skip("C xform binary not built")
        return [str(C_XFORM_BIN), str(xml), str(xform)]
    raise AssertionError(lang)


@pytest.mark.parametrize("lang", ["python", "rust", "ts", "go", "swift", "js", "cpp", "java", "kotlin", "c"])
@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_xform_matches_expected_22(lang: str, case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run(_cmd(lang, xform, xml))
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))

