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
