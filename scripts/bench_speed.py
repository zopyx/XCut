#!/usr/bin/env python3
"""Benchmark XForm implementations on a larger synthetic dataset."""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]

DEFAULT_LANGS = "python,rust,ts,go,swift"

LANGS = [
    s.strip().lower()
    for s in os.getenv("XF_BENCH_LANGS", DEFAULT_LANGS).split(",")
    if s.strip()
]

ITEMS = int(os.getenv("XF_BENCH_ITEMS", "20000"))
GROUPS = int(os.getenv("XF_BENCH_GROUPS", "50"))
RUNS = int(os.getenv("XF_BENCH_RUNS", "3"))
WARMUP = int(os.getenv("XF_BENCH_WARMUP", "1"))

BIN_RUST = ROOT / "xform-rs" / "target" / "release" / "xform"
BIN_TS = ROOT / "xform-ts" / "dist" / "cli.js"
BIN_GO = ROOT / "xform-go" / "bin" / "xform"
BIN_SWIFT = ROOT / "xform-swift" / ".build" / "release" / "xform-swift"


def _cmd_for(lang: str, xml: Path, xform: Path) -> list[str] | None:
    if lang == "python":
        return [sys.executable, "-m", "zopyx.xform.cli", str(xml), str(xform)]
    if lang == "rust":
        if not BIN_RUST.exists():
            return None
        return [str(BIN_RUST), str(xml), str(xform)]
    if lang == "ts":
        if not BIN_TS.exists():
            return None
        return ["node", str(BIN_TS), str(xml), str(xform)]
    if lang == "go":
        if not BIN_GO.exists():
            return None
        return [str(BIN_GO), str(xml), str(xform)]
    if lang == "swift":
        if not BIN_SWIFT.exists():
            return None
        return [str(BIN_SWIFT), str(xml), str(xform)]
    return None


def _generate_xml(path: Path, items: int, groups: int) -> None:
    lines = ["<data>"]
    for i in range(items):
        g = i % groups
        lines.append(
            f"  <item id=\"{i}\"><category>g{g}</category><value>{(i * 7) % 1000}</value></item>"
        )
    lines.append("</data>")
    path.write_text("\n".join(lines), encoding="utf-8")


def _generate_xform(path: Path) -> None:
    path.write_text(
        """xform version "2.0";

# key functions for grouping

def catKey(i) := string(i/category/text());

def groupKey(g) := string(lookup(g, "key"));

def itemCount(g) := count(lookup(g, "items"));

def itemSum(g) :=
  let items := lookup(g, "items") in
    sum(items/value/text());

let items := .//item in
<report total={count(items)} sum={sum(items/value/text())} groups={count(distinct(items/category/text()))}>
  {
    for g in sort(groupBy(items, catKey), groupKey) return
      <group name={groupKey(g)} count={itemCount(g)} sum={itemSum(g)} />
  }
</report>
""",
        encoding="utf-8",
    )


def _run_cmd(cmd: list[str]) -> tuple[float, str]:
    start = time.perf_counter()
    result = subprocess.run(cmd, capture_output=True, text=True)
    end = time.perf_counter()
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"Command failed: {cmd}")
    if "<report" not in result.stdout:
        raise RuntimeError("Unexpected output (missing <report>)")
    return end - start, result.stderr.strip()


def main() -> int:
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        xml = tmp / "input.xml"
        xform = tmp / "transform.xform"
        _generate_xml(xml, ITEMS, GROUPS)
        _generate_xform(xform)

        print(f"Items: {ITEMS}, Groups: {GROUPS}, Runs: {RUNS}, Warmup: {WARMUP}")
        print("")

        results: list[tuple[str, float]] = []
        for lang in LANGS:
            cmd = _cmd_for(lang, xml, xform)
            if not cmd:
                print(f"{lang:7} SKIP (binary not found)")
                continue
            # warmup
            for _ in range(WARMUP):
                _, timing_line = _run_cmd(cmd)
                if timing_line:
                    print(f"{lang:7} {timing_line}")
            times = []
            for _ in range(RUNS):
                elapsed, timing_line = _run_cmd(cmd)
                if timing_line:
                    print(f"{lang:7} {timing_line}")
                times.append(elapsed)
            med = median(times)
            results.append((lang, med))
            print(f"{lang:7} median {med:.4f}s (runs: {', '.join(f'{t:.4f}' for t in times)})")

        if results:
            fastest = min(results, key=lambda r: r[1])
            print("")
            print(f"Fastest: {fastest[0]} ({fastest[1]:.4f}s)")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
