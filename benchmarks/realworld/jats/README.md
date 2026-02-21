# JATS Real-World Benchmark

This benchmark uses a real JATS XML document from Europe PMC and the NCBI JATS Preview Stylesheets, plus a small summary transformation written in both XSLT 1.0 and XForm 2.0.

## What Gets Downloaded

The benchmark script downloads `ncbi/JATSPreviewStylesheets` and uses:

- `userguide.xml` (input XML)
- `xslt/main/jats-html.xsl` (full JATS HTML stylesheet for the XSLT baseline)

By default, the input XML is fetched from Europe PMC (`PMC2231364`) via the `fullTextXML` endpoint and saved as `input.xml`. You can switch back to the vendor sample via `XF_BENCH_JATS_SOURCE=vendor`.

## Files In This Folder

- `transform.xsl` - summary transformation in XSLT 1.0
- `transform.xform` - equivalent summary transformation in XForm 2.0
- `.gitignore` - ignores downloaded sources and `input.xml`

## How To Run

From the repo root:

```bash
uv run scripts/bench_realworld_jats.py
```

Use env vars to tune runs:

```bash
XF_BENCH_LANGS=python,rust,ts,go,swift XF_BENCH_RUNS=3 XF_BENCH_WARMUP=1 \
  uv run scripts/bench_realworld_jats.py
```

Select a different input source:

```bash
XF_BENCH_JATS_SOURCE=pmc XF_BENCH_JATS_PMCID=PMC2231364 \
  uv run scripts/bench_realworld_jats.py

XF_BENCH_JATS_SOURCE=vendor XF_BENCH_JATS_INPUT=userguide.xml \
  uv run scripts/bench_realworld_jats.py
```
