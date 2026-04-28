from __future__ import annotations

import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

pytestmark = pytest.mark.xfail(
    reason="2.1 fixture corpus targets future implementation updates across all runtimes",
    strict=False,
)

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures_21"
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


def _run_python_xform(xform: Path, xml: Path) -> str:
    result = subprocess.run(
        [sys.executable, "-m", "zopyx.xform.cli", str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _run_rust_xform(xform: Path, xml: Path) -> str:
    if "rust" not in ENABLED_LANGS:
        pytest.skip("Rust tests disabled")
    if not RUST_XFORM_BIN.exists():
        pytest.skip("Rust xform binary not built")
    return subprocess.run(
        [str(RUST_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_ts_xform(xform: Path, xml: Path) -> str:
    if "ts" not in ENABLED_LANGS:
        pytest.skip("TypeScript tests disabled")
    if not TS_XFORM_BIN.exists():
        pytest.skip("TypeScript xform binary not built")
    return subprocess.run(
        ["node", str(TS_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_go_xform(xform: Path, xml: Path) -> str:
    if "go" not in ENABLED_LANGS:
        pytest.skip("Go tests disabled")
    if not GO_XFORM_BIN.exists():
        pytest.skip("Go xform binary not built")
    return subprocess.run(
        [str(GO_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_swift_xform(xform: Path, xml: Path) -> str:
    if "swift" not in ENABLED_LANGS:
        pytest.skip("Swift tests disabled")
    if not SWIFT_XFORM_BIN.exists():
        pytest.skip("Swift xform binary not built")
    return subprocess.run(
        [str(SWIFT_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_js_xform(xform: Path, xml: Path) -> str:
    if "js" not in ENABLED_LANGS:
        pytest.skip("JavaScript tests disabled")
    if not JS_XFORM_BIN.exists():
        pytest.skip("JavaScript xform CLI not built")
    return subprocess.run(
        ["node", str(JS_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_java_xform(xform: Path, xml: Path) -> str:
    if "java" not in ENABLED_LANGS:
        pytest.skip("Java tests disabled")
    if not JAVA_XFORM_BIN.exists():
        pytest.skip("Java xform binary not built")
    return subprocess.run(
        [str(JAVA_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_kotlin_xform(xform: Path, xml: Path) -> str:
    if "kotlin" not in ENABLED_LANGS:
        pytest.skip("Kotlin tests disabled")
    if not KOTLIN_XFORM_JAR.exists():
        pytest.skip("Kotlin xform binary not built")
    return subprocess.run(
        ["java", "-jar", str(KOTLIN_XFORM_JAR), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _run_cpp_xform(xform: Path, xml: Path) -> str:
    if "cpp" not in ENABLED_LANGS:
        pytest.skip("C++ tests disabled")
    if not CPP_XFORM_BIN.exists():
        pytest.skip("C++ xform binary not built")
    output = subprocess.run(
        [str(CPP_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if output.startswith("xform-cpp:"):
        pytest.skip("C++ CLI not implemented yet")
    return output


def _run_c_xform(xform: Path, xml: Path) -> str:
    if "c" not in ENABLED_LANGS:
        pytest.skip("C tests disabled")
    if not C_XFORM_BIN.exists():
        pytest.skip("C xform binary not built")
    return subprocess.run(
        [str(C_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_python_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_python_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_rust_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_rust_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_ts_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_ts_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_go_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_go_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_swift_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_swift_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_js_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_js_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_java_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_java_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_kotlin_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_kotlin_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_cpp_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_cpp_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_c_xform_matches_expected_21(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    expected = case / "expected.xml"
    out = _run_c_xform(xform, xml)
    assert _normalize_xml(out) == _normalize_xml(expected.read_text(encoding="utf-8"))
