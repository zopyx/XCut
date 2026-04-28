# XForm 2.1 Error Fixture Corpus

This directory contains language-independent negative fixtures for XForm 2.1.

Each case directory contains:

- `input.xml`
- `transform.xform`
- `expected_error.txt`
- `README.md`

The harness asserts:

1. the process exits non-zero
2. the process output contains the expected error code
