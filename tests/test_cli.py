from __future__ import annotations

import sys
from pathlib import Path

from xform import cli


def test_cli_main_outputs_transformation(tmp_path: Path, capsys) -> None:
    xml_path = tmp_path / "input.xml"
    xform_path = tmp_path / "transform.xform"
    xml_path.write_text("<root/>", encoding="utf-8")
    xform_path.write_text("xform version '2.0'; <out>{'ok'}</out>", encoding="utf-8")

    argv = ["xform", str(xml_path), str(xform_path)]
    original_argv = sys.argv
    try:
        sys.argv = argv
        cli.main()
    finally:
        sys.argv = original_argv

    out = capsys.readouterr().out.strip()
    assert out == "<out>ok</out>"
