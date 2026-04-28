# XForm 2.1 Implementation Instructions

This document defines the requirements and working rules for all further XForm language implementations targeting the 2.1 specification.

It applies to every runtime in this repository, including but not limited to:

- Python
- Rust
- TypeScript
- JavaScript
- Go
- Swift
- Java
- Kotlin
- C++
- C

The goal is not "best effort 2.1-like behavior". The goal is **cross-language conformance** to the 2.1 draft.

## Source of Truth

Implementations MUST follow these documents in priority order:

1. [xform-transformations-2.1.md](/Users/ajung/src/xform/xform-transformations-2.1.md)
2. [FUNCTIONS.md](/Users/ajung/src/xform/FUNCTIONS.md)
3. [SUMMARY_21_CHANGES.md](/Users/ajung/src/xform/SUMMARY_21_CHANGES.md)
4. [EVAL.md](/Users/ajung/src/xform/EVAL.md)
5. [XSLT_COMPARISON.md](/Users/ajung/src/xform/XSLT_COMPARISON.md)

If a companion document conflicts with the 2.1 spec, the spec wins.

## Non-Negotiable Objective

All implementations must converge on:

- the same accepted 2.1 syntax
- the same observable evaluation behavior
- the same error classes for the same conditions
- the same output XML for the same valid fixture cases
- the same failure codes for the same invalid fixture cases

Differences in internal architecture are allowed. Differences in observable behavior are not.

## Implementation Order

Do not attempt a broad rewrite with no staging. Implement 2.1 in this order.

### Phase 1: Parser and AST

Every implementation must first support the 2.1 surface syntax:

- `xform version "2.1";`
- named arguments
- `apply(expr)` and `apply(expr, QName)`
- bare attribute path starts such as `@id`
- 2.1 pattern grammar
- comment constructor
- PI constructor
- reserved-word handling required by 2.1

Required parser outcomes:

- valid 2.1 source parses
- unsupported version raises `XFST0005`
- malformed syntax raises `XFST0001`
- reserved-word identifier misuse raises `XFST0006`

Do not proceed to runtime work while the parser still rejects valid 2.1 forms.

### Phase 2: Core Evaluation Semantics

Next implement the 2.1 evaluation contract:

- named argument binding
- required-parameter enforcement
- default parameter evaluation at call time
- later-parameter default references rejected
- ordered boolean coercion rules
- exact pattern matching semantics
- stable ruleset ordering
- built-in `apply()` fallback behavior
- `position()` / zero-arg `last()` iteration-context semantics

Required runtime outcomes:

- missing required parameter -> `XFDY0008`
- unknown ruleset -> `XFST0007`
- invalid iteration-context access -> `XFDY0006`
- no silent fallback to 2.0 behavior where 2.1 differs

### Phase 3: XML Data / Constructor Correctness

Then implement XML-facing behavior:

- attribute nodes invalid in element content
- duplicate constructed attributes invalid
- deep vs shallow `copy(...)`
- adjacent text merging
- comment/PI node support where required by 2.1
- correct text and attribute escaping
- namespace-aware construction and serialization where implemented

Required runtime outcomes:

- invalid attribute construction -> `XFDY0005`
- invalid constructor/serialization state -> `XFDY0004`

### Phase 4: Standard Library Completion

Then close the required 2.1 library gaps:

- `contains`
- `startsWith`
- `endsWith`
- `substring`
- `normalizeSpace`
- `replace`
- `attributes`
- `keys`
- `mapSize`
- `lookup` miss behavior

These must work consistently across runtimes.

### Phase 5: Cross-Language Fixture Parity

Only after the above phases should implementations be considered 2.1-capable.

The final bar is fixture parity, not local unit optimism.

## Required Test Targets

Every implementation is expected to eventually satisfy all of the following test layers.

### 1. Existing Baseline Tests

These preserve current stable behavior:

- [tests/test_parser.py](/Users/ajung/src/xform/tests/test_parser.py)
- [tests/test_eval.py](/Users/ajung/src/xform/tests/test_eval.py)
- [tests/test_transformations.py](/Users/ajung/src/xform/tests/test_transformations.py)

### 2. 2.1 Python-Side Spec Targets

These are forward-looking semantic targets:

- [tests/test_spec_21.py](/Users/ajung/src/xform/tests/test_spec_21.py)
- [tests/test_spec_21_extended.py](/Users/ajung/src/xform/tests/test_spec_21_extended.py)

These are implementation-facing tests, not cross-language conformance tests, but they are still valuable to clarify intended behavior.

### 3. 2.1 Positive Fixture Corpus

Language-independent success fixtures:

- [tests/fixtures_21](/Users/ajung/src/xform/tests/fixtures_21)
- [tests/test_transformations_21.py](/Users/ajung/src/xform/tests/test_transformations_21.py)

### 4. 2.1 Negative Fixture Corpus

Language-independent error fixtures:

- [tests/fixtures_21_errors](/Users/ajung/src/xform/tests/fixtures_21_errors)
- [tests/test_transformations_21_errors.py](/Users/ajung/src/xform/tests/test_transformations_21_errors.py)

## Error Code Discipline

Implementations MUST use the 2.1 error classes, not ad hoc text-only failures.

At minimum, the following must be observable:

- `XFST0001` syntax error
- `XFST0005` unsupported version string
- `XFST0006` reserved-word misuse
- `XFST0007` unknown ruleset
- `XFDY0004` invalid constructor/serialization state
- `XFDY0005` invalid attribute construction
- `XFDY0006` invalid iteration-context access
- `XFDY0008` invalid argument binding
- `XFDY0099` recursion/resource exhaustion where implemented

When the CLI fails, the error code must appear in process output. The language-independent error fixture runner depends on that.

## Behavioral Rules That Must Not Drift

The following 2.1 choices are intentional and must be preserved:

- boolean coercion is not XPath EBV
- unbound identifier path starts fall back to `./Identifier`
- `apply(...)` is a built-in dispatch form, not a normal function
- nested child pattern matching is exact, not prefix-based
- `lookup(map, missingKey)` returns empty sequence
- zero-arg `last()` is not the same as `last(seq)`
- named arguments require positional arguments first
- unprefixed attributes are in no namespace

Do not "improve" these away in one implementation only.

## Fixture Design Rules for Future 2.1 Cases

Any further 2.1 fixture added to this repo must follow these rules:

- one case per directory
- include `input.xml`
- include `transform.xform`
- include `expected.xml` for success fixtures
- include `expected_error.txt` for error fixtures
- include `README.md` with the purpose of the case
- keep the XML and transform minimal
- isolate one feature or one tight feature cluster per case

Do not add Python-only fixture semantics to the shared corpus.

## Cross-Language Development Rules

### Allowed

- different parser strategies
- different AST shapes internally
- different serialization internals
- different performance optimizations

### Not Allowed

- accepting different language subsets without marking the implementation incomplete
- using different error codes for the same condition
- returning different XML for the same fixture
- silently degrading 2.1 syntax to older semantics

## Runtime-Specific Guidance

### Python

- Python is currently the easiest place to lock down semantics first.
- Use it as the quickest path to clarify intended behavior, not as a license for Python-only semantics.

### Rust / Go / TypeScript / JavaScript

- Prioritize parser parity early because these runtimes are likely to surface grammar drift quickly.
- Keep error code strings stable so the shared error fixtures can validate them.

### Swift / Java / Kotlin / C++ / C

- It is acceptable to phase support in stages.
- It is not acceptable to claim 2.1 support until the 2.1 fixture corpora are passing for that runtime.

## Definition of "2.1 Support"

An implementation should only be described as supporting XForm 2.1 when all of the following are true:

1. it accepts the 2.1 version prolog
2. it parses the required 2.1 syntax additions
3. it implements the required 2.1 runtime semantics
4. it exposes the required 2.1 error codes
5. it passes the 2.1 positive fixture corpus for that runtime
6. it passes the 2.1 negative fixture corpus for that runtime

Anything short of that should be described more narrowly, for example:

- "partial 2.1 parser support"
- "2.1 grammar complete, evaluator incomplete"
- "2.1 fixtures pending"

## Recommended Work Pattern

For each runtime:

1. make parser accept 2.1 syntax
2. make unit/spec tests pass for the affected feature
3. make positive 2.1 fixture cases pass
4. make negative 2.1 fixture cases return correct error codes
5. only then move to the next feature cluster

Do not batch too many unrelated semantic changes into a single unverified jump.

## Immediate Priority List

If starting work on a runtime today, implement in this order:

1. 2.1 version support
2. named arguments
3. `apply(...)` formal behavior
4. bare `@id` path starts
5. pattern exactness and attribute-value patterns
6. iteration-context error behavior
7. duplicate attribute and attribute-in-content failures
8. string helpers
9. map helpers
10. fixture parity

## Final Rule

The repository now contains a 2.1 spec, 2.1 companion docs, 2.1 positive fixtures, and 2.1 error fixtures. All further implementation work must target those artifacts directly.

No implementation should invent its own local definition of "2.1 done".
