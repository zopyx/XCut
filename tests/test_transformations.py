from __future__ import annotations

import subprocess
import os
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures"
ENABLED_LANGS = {
    s.strip().lower()
    for s in os.getenv("XF_TEST_LANGS", "python,rust,ts,go,swift").split(",")
    if s.strip()
}


def _run_xslt(xslt: Path, xml: Path) -> str:
    if shutil.which("xsltproc") is None:
        pytest.skip("xsltproc not available")
    result = subprocess.run(
        ["xsltproc", str(xslt), str(xml)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _run_xform(xform: Path, xml: Path) -> str:
    result = subprocess.run(
        [sys.executable, "-m", "zopyx.xform.cli", str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


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


RUST_XFORM_BIN = ROOT / "xform-rs" / "target" / "release" / "xform"
TS_XFORM_BIN = ROOT / "xform-ts" / "dist" / "cli.js"
GO_XFORM_BIN = ROOT / "xform-go" / "bin" / "xform"
SWIFT_XFORM_BIN = ROOT / "xform-swift" / ".build" / "release" / "xform-swift"


def _run_rust_xform(xform: Path, xml: Path) -> str:
    if "rust" not in ENABLED_LANGS:
        pytest.skip("Rust tests disabled")
    if not RUST_XFORM_BIN.exists():
        pytest.skip("Rust xform binary not built")
    result = subprocess.run(
        [str(RUST_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _run_ts_xform(xform: Path, xml: Path) -> str:
    if "ts" not in ENABLED_LANGS:
        pytest.skip("TypeScript tests disabled")
    if not TS_XFORM_BIN.exists():
        pytest.skip("TypeScript xform binary not built")
    result = subprocess.run(
        ["node", str(TS_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _run_go_xform(xform: Path, xml: Path) -> str:
    if "go" not in ENABLED_LANGS:
        pytest.skip("Go tests disabled")
    if not GO_XFORM_BIN.exists():
        pytest.skip("Go xform binary not built")
    result = subprocess.run(
        [str(GO_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _run_swift_xform(xform: Path, xml: Path) -> str:
    if "swift" not in ENABLED_LANGS:
        pytest.skip("Swift tests disabled")
    if not SWIFT_XFORM_BIN.exists():
        pytest.skip("Swift xform binary not built")
    result = subprocess.run(
        [str(SWIFT_XFORM_BIN), str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _cases():
    return sorted(p for p in FIXTURES.iterdir() if p.is_dir())


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_xform_matches_xslt(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    xslt = case / "transform.xsl"
    expected = case / "expected.xml"

    xslt_out = _run_xslt(xslt, xml)
    xform_out = _run_xform(xform, xml)

    if expected.exists():
        expected_out = expected.read_text(encoding="utf-8").strip()
    else:
        expected_out = xslt_out

    assert _normalize_xml(xslt_out) == _normalize_xml(expected_out)
    assert _normalize_xml(xform_out) == _normalize_xml(xslt_out)


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_rust_xform_matches_xslt(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    xslt = case / "transform.xsl"

    xslt_out = _run_xslt(xslt, xml)
    rust_out = _run_rust_xform(xform, xml)

    assert _normalize_xml(rust_out) == _normalize_xml(xslt_out)


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_ts_xform_matches_xslt(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    xslt = case / "transform.xsl"

    xslt_out = _run_xslt(xslt, xml)
    ts_out = _run_ts_xform(xform, xml)

    assert _normalize_xml(ts_out) == _normalize_xml(xslt_out)


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_go_xform_matches_xslt(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    xslt = case / "transform.xsl"

    xslt_out = _run_xslt(xslt, xml)
    go_out = _run_go_xform(xform, xml)

    assert _normalize_xml(go_out) == _normalize_xml(xslt_out)


@pytest.mark.parametrize("case", _cases(), ids=lambda p: p.name)
def test_swift_xform_matches_xslt(case: Path) -> None:
    xml = case / "input.xml"
    xform = case / "transform.xform"
    xslt = case / "transform.xsl"

    xslt_out = _run_xslt(xslt, xml)
    swift_out = _run_swift_xform(xform, xml)

    assert _normalize_xml(swift_out) == _normalize_xml(xslt_out)
