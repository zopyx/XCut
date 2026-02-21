# XForm 2.0 vs XSLT — Close Comparison

This comparison is based on `xform-transformations-2.0.md` (Editor’s Draft, 2026-02-20) and contrasts XForm 2.0 with XSLT as commonly understood (templates, modes, XPath-based selection, and processor/tooling ecosystem). It focuses on language shape and practical tradeoffs.

## Executive Summary
XForm 2.0 is a compact, expression‑centric XML transformation language that blends XPath-like paths, functional expressions, and XML constructors with a minimal rule dispatch model similar to XSLT’s `apply-templates`. Compared to XSLT, XForm aims for terser syntax and direct XML construction, at the cost of ecosystem maturity, advanced optimization facilities, and the broad feature set available across XSLT versions.

## Feature Mapping

| Area | XForm 2.0 | XSLT |
|---|---|---|
| Core paradigm | Expression-centric, functional, constructor-heavy | Template/rule-centric with declarative matching |
| Selection language | XPath-like path expressions | XPath (with version-dependent features) |
| Rule dispatch | `rule` + `apply(seq, Name?)` | Template rules + `apply-templates` |
| Pattern matching | `match expr: case pattern => expr` | Template match patterns |
| Construction | XML constructors `<a>{...}</a>` | Literal result elements + `xsl:element`/`xsl:attribute` |
| Control flow | `if/then/else`, `for`, `let`, `match` | `xsl:if`, `xsl:choose`, `xsl:for-each`, variables |
| Modules | `import "iri" as p;` + `ns` declaration | `xsl:import` / `xsl:include`, namespaces |
| Errors | Defined static/dynamic error codes | Defined error model (processor-dependent details) |
| Profiles | Core + Streaming (TBD) | Streaming defined in XSLT 3.0 |

## Language Shape and Readability
XForm 2.0 favors direct XML constructors with inline expression interpolation and a minimal number of keywords. In practice, this reduces boilerplate for straight‑through reshaping and makes the output structure highly visible in the transformation. XSLT’s template/stylesheet wrapping is more verbose but makes the overall transformation model explicit and scales well for complex, rule‑driven transformations.

## Rule Dispatch and Matching
XForm introduces `rule` plus `apply()` as a minimal recursive dispatch model comparable to XSLT’s `apply-templates`. Unlike classic XSLT, XForm’s `match` is also an expression construct that can dispatch over sequences, which makes it easy to do inline pattern matching within a larger expression pipeline. XSLT’s template matching model is more established and supports modes, built‑in template rules, and a rich set of optimization behaviors in mature processors.

## Construction and Serialization
XForm’s constructor syntax is concise and consistent: `<elem attr={expr}>{expr}</elem>` and `text{expr}`. This makes expression output intent explicit and reduces the need for instruction elements. XSLT uses literal result elements, which can be equally readable, but adds verbosity when attributes or computed names are needed (`xsl:attribute`, `xsl:element`).

## Data Model and Types
XForm 2.0 defines a simple item model: nodes plus basic atomic types and `map`, with a clear static/dynamic context split. XSLT operates on the XPath data model and supports a broader, version‑dependent type system (particularly in XSLT 2.0/3.0).

## Namespaces and Modules
XForm separates module imports from namespace bindings (`import ... as p` vs `ns` declaration). XSLT relies on XML namespaces and explicit import/include relationships. XSLT has a long‑standing, interoperable module system with predictable precedence rules and wide tooling support.

## Errors and Diagnostics
XForm defines specific static and dynamic error codes (e.g., syntax, import cycles, missing rules). XSLT has a defined error model but processors differ in diagnostic quality and granularity. XForm’s explicit codes can make testing and conformance clearer if consistently implemented.

## Pros and Cons

### Pros of XForm 2.0 (relative to XSLT)
1. Concise syntax that closely mirrors output XML.
2. Expression‑centric flow reduces ceremony for simple transforms.
3. Inline pattern matching (`match`) makes local decisions easy.
4. Clear static/dynamic contexts and explicit error taxonomy.
5. Minimal rule dispatch model that is easy to explain and implement.

### Cons of XForm 2.0 (relative to XSLT)
1. Immature ecosystem: fewer processors, libraries, editors, and tooling.
2. Limited standard library and features compared to modern XSLT versions.
3. Fewer established optimization and streaming capabilities (Streaming profile is TBD).
4. Smaller body of best practices and interoperability guidance.
5. Less compatibility with existing XSLT assets and enterprise pipelines.

### Pros of XSLT (relative to XForm 2.0)
1. Mature, widely deployed processors and tooling across platforms.
2. Rich features in XSLT 2.0/3.0 (functions, packages, streaming).
3. Strong integration with XPath and XML tooling ecosystems.
4. Well‑understood template matching and mode systems.
5. Extensive community knowledge, examples, and production usage.

### Cons of XSLT (relative to XForm 2.0)
1. More verbose syntax for common transformations.
2. Boilerplate around stylesheet structure and namespaces.
3. Indirection via templates can be harder to follow in small transforms.
4. Mixed instruction/element syntax can reduce readability for newcomers.

## When Each Is a Better Fit
XForm 2.0 is likely a better fit for compact, readable XML reshaping where the transformation is most easily expressed as constructors plus simple path expressions. XSLT is likely a better fit for large, rule‑driven transformations, complex matching logic with modes, and environments that depend on mature tooling, standards compliance, and high‑performance processors.

## Notes on Alignment
The XForm 2.0 draft explicitly positions `rule` + `apply()` as an analogue to XSLT `apply-templates`, which is the closest conceptual anchor between the two languages. Beyond that, XForm prioritizes terseness and a unified expression model, while XSLT prioritizes a robust, well‑specified template processing system with decades of tooling and implementation experience.
