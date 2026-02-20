from __future__ import annotations

import argparse
from pathlib import Path

from .parser import Parser
from .eval import eval_module
from .xmlmodel import parse_xml, serialize


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="input XML file")
    ap.add_argument("xform", help="xform module")
    args = ap.parse_args()

    xml_text = Path(args.input).read_text(encoding="utf-8")
    xform_text = Path(args.xform).read_text(encoding="utf-8")

    doc = parse_xml(xml_text)
    module = Parser(xform_text).parse_module()
    result = eval_module(module, doc)
    output = "".join(serialize(item) if hasattr(item, "kind") else str(item) for item in result)
    print(output)


if __name__ == "__main__":
    main()
