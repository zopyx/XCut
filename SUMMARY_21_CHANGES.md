# XForm 2.1 Change Summary

This document summarizes the specification-level changes introduced by XForm 2.1 relative to 2.0.

## Purpose

Version 2.1 does not attempt to redesign the language. It makes the 2.0 design more complete, more XML-precise, and more testable.

Primary goals:

- make the core grammar genuinely normative
- formalize rule dispatch
- support named arguments and default parameters correctly
- tighten XML data, namespace, and constructor semantics
- improve conformance and error determinism

## Headline Changes

### Grammar and Parsing

- Added normative grammar for `Pattern`.
- Added normative grammar for `Literal`, `StringLiteral`, `NumberLiteral`, `QName`, and `Prefix`.
- Added named arguments via `Argument := [Identifier ":="] Expr`.
- Added `ApplyExpr` to the grammar.
- Reserved `apply`.
- Added bare attribute path starts such as `@id`.
- Added comment and processing-instruction constructors.

### Rule Dispatch

- `apply(seq, ruleset?)` is now a formal built-in dispatch form.
- Ruleset ordering across imports is defined.
- Built-in low-priority fallback rules are defined for document, element, attribute, text, comment, and PI nodes.

### Patterns

- Pattern syntax is now part of the normative grammar.
- Attribute value patterns are supported in the core form `@qname = Literal`.
- Nested child matching is defined as exact child-sequence matching.
- Capture-body semantics remain available via `<a>{x}</a>`.

### Function Calls

- Named arguments are now part of the language.
- Positional arguments must precede named arguments.
- Default parameter expressions are evaluated at call time.
- Default parameter expressions may reference earlier parameters, not later ones.

### XML Data Model

- Expanded QName semantics are explicit.
- Parent relationships and `baseURI` are part of node properties.
- Namespace bindings are part of element state.
- String-value rules are defined.
- Deep-copy behavior is more explicit.

### Constructors and Serialization

- Content normalization is defined more precisely.
- Attribute nodes in element content are now explicitly invalid.
- Duplicate attributes are explicitly an error.
- Adjacent constructed text nodes must be merged.
- Namespace fixup is required during serialization.

### Standard Library

- Added `attributes(node)`.
- Added string functions: `contains`, `startsWith`, `endsWith`, `substring`, `normalizeSpace`, `replace`.
- Added map helpers: `keys(map)` and `mapSize(map)`.
- `lookup(map, missingKey)` is explicitly defined to return the empty sequence.
- `groupBy(...)` group map shape is now explicit.
- `head`, `tail`, and `last(seq)` empty-sequence behavior is defined.
- `position()` and zero-argument `last()` now have explicit error behavior outside `for`.

### Conformance and Errors

- Processor conformance now explicitly includes parsing, static checking, evaluation, serialization, the standard library, namespace/import handling, and error reporting.
- The undefined streaming profile has been removed from normative scope.
- Added or clarified error classes for:
  - reserved-word misuse
  - unknown rulesets
  - invalid attribute construction
  - invalid iteration-context access
  - invalid argument binding
  - resource exhaustion or non-terminating recursion

## Net Effect

The net effect of 2.1 is that XForm moves from a promising but still partially underspecified 2.0 draft to a much more coherent core specification draft. The remaining open questions are now mostly about scope and refinement rather than missing fundamentals.
