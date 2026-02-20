from __future__ import annotations

import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures"
PYTHON = ROOT / ".venv" / "bin" / "python"


def run_xslt(xslt: Path, xml: Path) -> str:
    result = subprocess.run(
        ["xsltproc", str(xslt), str(xml)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def run_xform(xform: Path, xml: Path) -> str:
    result = subprocess.run(
        [str(PYTHON), "-m", "xform.cli", str(xml), str(xform)],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def normalize_xml(text: str) -> str:
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


def main() -> int:
    cases = sorted(p for p in FIXTURES.iterdir() if p.is_dir())
    ok = True
    for case in cases:
        xml = case / "input.xml"
        xform = case / "transform.xform"
        xslt = case / "transform.xsl"
        expected = case / "expected.xml"

        xslt_out = run_xslt(xslt, xml)
        xform_out = run_xform(xform, xml)

        if expected.exists():
            expected_out = expected.read_text(encoding="utf-8").strip()
        else:
            expected_out = xslt_out

        if normalize_xml(xslt_out) != normalize_xml(expected_out):
            print(f"{case.name}: XSLT output differs from expected")
            ok = False
        if normalize_xml(xform_out) != normalize_xml(xslt_out):
            print(f"{case.name}: XForm output differs from XSLT")
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
