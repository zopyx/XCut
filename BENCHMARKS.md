# Benchmarks

## Environment

- Date: 2026-02-21
- OS: Darwin 25.3.0 (arm64)
- Host: Andreass-MacBook-Pro-2.local
- CPU: Apple M3 Pro
- Memory: 36 GB

## Benchmark Description

Synthetic XML with `XF_BENCH_ITEMS=20000` and `XF_BENCH_GROUPS=50`, grouped and aggregated via XForm (`groupBy`, `lookup`, `sum`, `count`) and rendered to XML.

Command:

```bash
uv run scripts/bench_speed.py
```

## Results

```
python  median 0.3040s (runs: 0.3028, 0.3040, 0.3106)
rust    median 0.2734s (runs: 0.2815, 0.2721, 0.2734)
ts      median 0.1974s (runs: 0.1983, 0.1960, 0.1974)
go      median 0.0638s (runs: 0.0638, 0.0638, 0.0644)
swift   median 0.1831s (runs: 0.1811, 0.1831, 0.1867)
```

Notes:
- Results are not cross‑machine comparable; use them for relative comparisons on the same host.
- If you change inputs or run on another machine, record a new section below.

## Real-World JATS Benchmark

Input:
- Default source: Europe PMC `fullTextXML` for `PMC2231364` (via `https://www.ebi.ac.uk/europepmc/webservices/rest/{PMCID}/fullTextXML`)
- Alternative source: `ncbi/JATSPreviewStylesheets` sample (`userguide.xml`)
- Stylesheet: NCBI JATS Preview Stylesheets (`xslt/main/jats-html.xsl`)
- Input size (PMC2231364): 0.15 MB (DOCTYPE stripped to avoid external DTD resolution)

Command:

```bash
uv run scripts/bench_realworld_jats.py
```

Tuning:

```bash
XF_BENCH_LANGS=python,rust,ts,go,swift
XF_BENCH_RUNS=3
XF_BENCH_WARMUP=1
XF_BENCH_JATS_SOURCE=pmc        # pmc (default) or vendor
XF_BENCH_JATS_PMCID=PMC2231364   # used when source=pmc
XF_BENCH_JATS_INPUT=userguide.xml # used when source=vendor
```

Results (XForm summary transformation):

```
python  median 0.0588s (runs: 0.0622, 0.0586, 0.0588)
rust    median 0.0090s (runs: 0.0094, 0.0090, 0.0089)
ts      median 0.0923s (runs: 0.0920, 0.0923, 0.0928)
go      median 0.0121s (runs: 0.0120, 0.0121, 0.0125)
swift   median 0.0352s (runs: 0.0352, 0.0352, 0.0346)
```

Results (XSLT 1.0 summary transformation, `xsltproc`):

```
xsltproc median 0.0059s (runs: 0.0060, 0.0059, 0.0058)
```

Results (JATS HTML, full stylesheet, `xsltproc`):

```
xsltproc median 0.0177s (runs: 0.0182, 0.0176, 0.0177)
```
