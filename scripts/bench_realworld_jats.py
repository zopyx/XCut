#!/usr/bin/env python3
"""Benchmark XForm implementations on a real-world JATS XML document."""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
import re
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]
BENCH_DIR = ROOT / "benchmarks" / "realworld" / "jats"
VENDOR_DIR = BENCH_DIR / "vendor"
ZIP_URL = "https://github.com/ncbi/JATSPreviewStylesheets/archive/refs/heads/master.zip"
ZIP_PATH = VENDOR_DIR / "JATSPreviewStylesheets-master.zip"
EXTRACT_DIR = VENDOR_DIR / "JATSPreviewStylesheets-master"

DEFAULT_INPUT = os.getenv("XF_BENCH_JATS_INPUT", "userguide.xml")
DEFAULT_PMCID = os.getenv("XF_BENCH_JATS_PMCID", "PMC2231364")
DEFAULT_SOURCE = os.getenv("XF_BENCH_JATS_SOURCE", "pmc").strip().lower()
DEFAULT_LANGS = "python,rust,ts,go,swift"

LANGS = [
    s.strip().lower()
    for s in os.getenv("XF_BENCH_LANGS", DEFAULT_LANGS).split(",")
    if s.strip()
]

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


def _download_sources() -> None:
    VENDOR_DIR.mkdir(parents=True, exist_ok=True)
    if EXTRACT_DIR.exists():
        return
    if not ZIP_PATH.exists():
        print(f"Downloading {ZIP_URL}...")
        urllib.request.urlretrieve(ZIP_URL, ZIP_PATH)
    print("Extracting JATSPreviewStylesheets...")
    with zipfile.ZipFile(ZIP_PATH, "r") as zf:
        zf.extractall(VENDOR_DIR)


def _download_pmc_xml(pmcid: str, dest: Path) -> None:
    url = f"https://www.ebi.ac.uk/europepmc/webservices/rest/{pmcid}/fullTextXML"
    print(f"Downloading {pmcid} from Europe PMC...")
    with urllib.request.urlopen(url) as resp, dest.open("wb") as fh:
        fh.write(resp.read())
    _strip_doctype(dest)


def _strip_doctype(path: Path) -> None:
    text = path.read_text(encoding="utf-8", errors="ignore")
    if "<!DOCTYPE" not in text:
        return
    cleaned = re.sub(r"<!DOCTYPE[^>]*>", "", text, flags=re.DOTALL)
    path.write_text(cleaned, encoding="utf-8")


def _ensure_input() -> Path:
    dest = BENCH_DIR / "input.xml"
    if DEFAULT_SOURCE == "pmc":
        if not dest.exists() or dest.stat().st_size == 0:
            _download_pmc_xml(DEFAULT_PMCID, dest)
        return dest

    _download_sources()
    source_input = EXTRACT_DIR / DEFAULT_INPUT
    if not source_input.exists():
        raise FileNotFoundError(f"Input XML not found: {source_input}")
    if not dest.exists() or dest.stat().st_size != source_input.stat().st_size:
        shutil.copyfile(source_input, dest)
    return dest


def _run_cmd(cmd: list[str], capture: bool = True) -> tuple[float, str, int]:
    start = time.perf_counter()
    result = subprocess.run(
        cmd,
        capture_output=capture,
        text=True,
    )
    end = time.perf_counter()
    stdout = result.stdout if capture else ""
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"Command failed: {cmd}")
    return end - start, stdout, result.returncode


def _bench_xsltproc(xslt: Path, xml: Path) -> tuple[list[float], float]:
    if shutil.which("xsltproc") is None:
        print("xsltproc not available; skipping XSLT benchmarks")
        return [], 0.0
    times: list[float] = []
    for _ in range(WARMUP):
        _run_cmd(["xsltproc", str(xslt), str(xml)], capture=True)
    for _ in range(RUNS):
        elapsed, stdout, _ = _run_cmd(["xsltproc", str(xslt), str(xml)], capture=True)
        if "<summary" not in stdout:
            raise RuntimeError("Unexpected XSLT output (missing <summary>)")
        times.append(elapsed)
    return times, median(times) if times else 0.0


def _bench_jats_html(xslt: Path, xml: Path) -> tuple[list[float], float]:
    if shutil.which("xsltproc") is None:
        print("xsltproc not available; skipping JATS HTML benchmark")
        return [], 0.0
    times: list[float] = []
    with tempfile.TemporaryDirectory() as tmpdir:
        out = Path(tmpdir) / "jats.html"
        cmd = ["xsltproc", "-o", str(out), str(xslt), str(xml)]
        for _ in range(WARMUP):
            _run_cmd(cmd, capture=False)
        for _ in range(RUNS):
            elapsed, _, _ = _run_cmd(cmd, capture=False)
            times.append(elapsed)
    return times, median(times) if times else 0.0


def main() -> int:
    _download_sources()
    xml = _ensure_input()
    summary_xslt = BENCH_DIR / "transform.xsl"
    xform = BENCH_DIR / "transform.xform"
    jats_xslt = EXTRACT_DIR / "xslt" / "main" / "jats-html.xsl"

    if not summary_xslt.exists():
        raise FileNotFoundError(f"Missing summary XSLT: {summary_xslt}")
    if not xform.exists():
        raise FileNotFoundError(f"Missing XForm: {xform}")
    if not jats_xslt.exists():
        raise FileNotFoundError(f"Missing JATS XSLT: {jats_xslt}")

    size_mb = xml.stat().st_size / (1024 * 1024)
    print(f"JATS input: {xml} ({size_mb:.2f} MB)")
    print(f"Runs: {RUNS}, Warmup: {WARMUP}")
    print("")

    print("XForm summary benchmark:")
    results: list[tuple[str, float]] = []
    for lang in LANGS:
        cmd = _cmd_for(lang, xml, xform)
        if not cmd:
            print(f"{lang:7} SKIP (binary not found)")
            continue
        for _ in range(WARMUP):
            _run_cmd(cmd, capture=True)
        times: list[float] = []
        for _ in range(RUNS):
            elapsed, stdout, _ = _run_cmd(cmd, capture=True)
            if "<summary" not in stdout:
                raise RuntimeError("Unexpected XForm output (missing <summary>)")
            times.append(elapsed)
        med = median(times)
        results.append((lang, med))
        print(f"{lang:7} median {med:.4f}s (runs: {', '.join(f'{t:.4f}' for t in times)})")

    if results:
        fastest = min(results, key=lambda r: r[1])
        print("")
        print(f"Fastest XForm: {fastest[0]} ({fastest[1]:.4f}s)")

    print("")
    print("XSLT 1.0 summary benchmark (xsltproc):")
    xslt_times, xslt_med = _bench_xsltproc(summary_xslt, xml)
    if xslt_times:
        print(f"xsltproc median {xslt_med:.4f}s (runs: {', '.join(f'{t:.4f}' for t in xslt_times)})")

    print("")
    print("JATS HTML benchmark (xsltproc, full stylesheet):")
    jats_times, jats_med = _bench_jats_html(jats_xslt, xml)
    if jats_times:
        print(f"xsltproc median {jats_med:.4f}s (runs: {', '.join(f'{t:.4f}' for t in jats_times)})")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
