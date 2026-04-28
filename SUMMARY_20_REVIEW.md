# XForm 2.0 Specification Review

## Scope

This review covers the **specification only**. It intentionally does **not** consider any code or implementation.

Reviewed sources:

- `xform-transformations-2.0.md`
- `FUNCTIONS.md`
- `EVAL.md`
- `XSLT_COMPARISON.md`

Review method:

- One lead review plus **4 parallel expert reviews** focused on:
  - grammar and parsing
  - semantics and evaluation
  - XML data model, namespaces, constructors, serialization
  - conformance, error model, editorial and standards readiness

## Executive Summary

XForm 2.0 is a strong improvement over 1.0. The language direction is coherent: expression-centric XML transformation, compact constructors, explicit error codes, and a simpler alternative to template-heavy styles.

However, the draft is **not yet ready as a publication-grade normative specification**. The largest problems are not about language ideas; they are about **normative completeness and interoperability**. Several load-bearing features are still only partly specified, which means two independent implementors could produce materially different processors while both believing they conform.

Current readiness:

- **Good editor's draft for a core language**
- **Not yet a standards-ready normative spec**

## What Is Already Strong

- The document has a clear purpose and a sensible top-level structure.
- 2.0 fixes many real 1.0 issues: assignment syntax, variable path starts, `ns` declarations, recursion coverage, and a much better rule-dispatch story.
- The separation between static context, dynamic context, grammar, semantics, and errors is directionally correct.
- The minimum standard library is more coherent than in 1.0.
- The spec is now close enough to implementation reality that the remaining issues are concrete and fixable.

## Consolidated Findings

### Critical

1. **The normative grammar is still incomplete.**

The grammar uses nonterminals and token classes that are never defined normatively, including `Pattern`, `Literal`, `QName`, `Prefix`, and `StringLiteral` (`xform-transformations-2.0.md`, §7, lines 127-177). This is blocking because both `rule` dispatch and `match` depend on `Pattern`, yet pattern syntax exists only as prose in §9.1 (lines 233-241).

Why this matters:

- A conforming parser cannot be derived from the normative grammar.
- Pattern-heavy features are not testable at the syntax level.

Required fix:

- Make §7 self-contained enough to drive parser generation.
- Add full productions for `Pattern`, `Literal`, `QName`, `Prefix`, and tokenized literals.

2. **`apply()` is central to the language but is not fully integrated into the formal spec.**

`apply(seq, Name?)` is the load-bearing recursive dispatch primitive in §9.3 (lines 249-256), but it does not appear in the grammar, does not appear in the normative standard library list in §11 (lines 279-313), and is not reserved in Appendix A (lines 366-367).

Why this matters:

- It is unclear whether `apply` is syntax, a builtin function, or a shadowable user-defined name.
- The type of `Name` is unspecified.
- The central XSLT comparison claim depends on this feature.

Required fix:

- Define `apply` either as dedicated syntax in §7 or as a required builtin in §11.
- Specify signature, ruleset-name type, error behavior, and name-resolution/shadowing rules.

3. **Named arguments are used by the spec, but the grammar does not support them.**

The grammar defines `ArgList := Expr { "," Expr }` (§7, lines 166-167), but the normative library signatures and examples rely on named arguments such as `deep:=true`, `recurse:=true`, and `key:=exprOrFn` (§11.2-§11.4, lines 290, 293, 305; `FUNCTIONS.md` examples).

Why this matters:

- Many documented calls are grammatically invalid as written.
- Default-parameter semantics are incomplete without named-argument syntax.

Required fix:

- Extend the grammar with something like `Argument := [Identifier ":="] Expr`.
- Define positional/named argument mixing rules.
- Define when default expressions are evaluated and whether they may reference earlier parameters.

4. **The XML data model is too thin for interoperable XML semantics.**

The data model in §4 (lines 74-97) names node kinds, atomic types, and maps, but does not define enough XML/XDM-like structure: expanded names, parent relationships, in-scope namespaces, base URI, string-value, document invariants, or constructor normalization rules.

Why this matters:

- Path semantics, copying, equality/identity, and serialization remain underdefined.
- The spec claims XPath/XSLT adjacency, but the abstract model is too weak to support that claim reliably.

Required fix:

- Either normatively align with an XML/XPath-style data model or define equivalent semantics explicitly.
- At minimum, specify expanded QName handling, parent/child/document invariants, namespace bindings, base URI, and string-value behavior.

### High

5. **QName and namespace handling is underspecified.**

`QName` is defined only syntactically as `prefix:local` or `local` (§3, lines 63-70). The spec does not define expansion to `{namespace-uri, local-name}`, default namespace behavior for elements vs attributes, or whether the same QName resolution rules apply consistently to functions, rules, and type references. Serialization only says namespace declarations must be emitted for prefixed QNames (§10.3, lines 273-275), which is not enough.

Required fix:

- Define lexical QName vs expanded QName.
- State that default element namespace does not apply to attributes unless that is intentionally changed.
- Define namespace fixup and serialization rules, including default namespace handling.

6. **Constructor and serialization semantics are incomplete for XML fidelity.**

The constructor section says node items become children and atomic items become text (§10.1, lines 262-268), but it does not define behavior for:

- attribute nodes appearing in content
- duplicate attributes
- adjacent text node merging
- comment/PI constructors
- document node construction rules
- namespace fixup during copy and construction

Required fix:

- Add normalization and validity rules for constructed trees.
- Define constructor-time errors precisely, especially around attribute placement and duplicates.

7. **Pattern semantics are still too loose for independent interoperable implementations.**

§9.1 requires nested patterns such as `<a><b>{x}</b></a>` (line 239), but the matching model is not precise. It is unclear whether matching is exact, prefix-based, containment-based, or tolerant of unmatched siblings/whitespace. Attribute patterns are name-only (`@qname`, line 236), which is too weak for many real XML use cases.

Required fix:

- Define pattern matching formally over the data model.
- Specify exactness, ignored content, and binding scope.
- Add either guarded patterns or attribute/value constraints.

8. **Rule dispatch order is under-specified.**

The spec says the first matching rule wins (§9.3, lines 250-254), but it does not define stable ordering across module text, imports, and exported rules (§12.2-§12.3, lines 324-328).

Why this matters:

- "First matching rule" is only deterministic if rule ordering is defined globally.

Required fix:

- Define import linearization and rule ordering.
- If needed, add explicit priority semantics.

9. **Conformance is not yet testable as written.**

§2.2 says an `XForm Processor` must implement parsing, static checking, §8 semantics, §10 serialization, and §13 error reporting (lines 42-55), but other sections are also normative and not tied cleanly into processor conformance, especially §11 and §12. The `Streaming Profile` is still `TBD` while appearing inside normative-looking profile text (§2.3, lines 57-59).

Required fix:

- Rewrite §2 as a feature-based conformance section.
- Explicitly bind processor conformance to all required normative sections.
- Remove or defer any profile that is still undefined.

10. **The error model is too thin for a conformance test suite.**

The document defines a useful top-level error taxonomy in §13 (lines 332-350), but many observable edge cases remain unspecified:

- `head`, `tail`, `last(seq)` on empty sequences
- `position()` and iteration-form `last()` outside `for`
- precedence of boolean coercion rules for mixed sequences (§8.5, lines 220-224)
- default parameter evaluation context
- whether non-terminating recursion must raise `XFDY0099` or may terminate implementation-definedly (§8.6, lines 226-227)

Required fix:

- Specify edge-case behavior per function and map it to error codes.
- Prefer fewer implementation-defined branches.

### Medium

11. **The normative library remains underspecified in places and still depends on the practical reference for precision.**

Examples:

- `groupBy(seq, keyFn) -> Sequence(map{key, items})` is still pseudo-notation (§11.4, lines 304-307).
- The practical reference explains group-map shape and implies lookup behavior that the normative spec does not fully state (`FUNCTIONS.md`).
- Map introspection is missing entirely.

Required fix:

- Formalize group map shape in §11 itself.
- Define `lookup` miss behavior normatively.
- Add minimum map introspection such as `keys` and `mapSize`.

12. **XPath/XSLT similarity is real but currently overstated.**

The spec is clearly influenced by XPath/XSLT, and that is useful. But several semantics diverge materially:

- boolean coercion is not XPath EBV
- unbound `Identifier` at path start falls back to `./Identifier` (§8.4, lines 215-217)
- `apply()` is not yet equivalent in rigor to `apply-templates`
- default built-in rule behavior is absent

Required fix:

- Add a normative or semi-normative subsection documenting intentional divergences from XPath/XSLT.

13. **The language's identifier model is weak for an XML-facing spec.**

Identifiers are ASCII-only (§6.2, lines 116-117), while the language otherwise claims Unicode handling (§15, lines 360-362). This may be a valid simplification, but if it is deliberate, it should be explicit.

Required fix:

- Either expand identifier support toward XML-compatible Unicode names, or clearly state the intentional ASCII-only scope.

14. **Security and privacy coverage is too shallow for a normative spec.**

§14 (lines 354-356) is directionally right but minimal. It does not address import URI resolution policy, scheme restrictions, parser hardening, entity/DTD policy, recursion and resource exhaustion, or diagnostic leakage.

Required fix:

- Expand §14 with mandatory controls and implementation guidance around hostile inputs and resource limits.

15. **Editorial consistency still needs a standards-grade pass.**

Examples:

- The overview still uses `rule ... = ...; apply(...)` instead of `:=` (§5, line 105).
- Companion docs still contain stale names and syntax in a few places.
- Normative/informative boundaries are sometimes loose.

Required fix:

- Run a full editorial pass against the final grammar and library signatures.
- Treat all examples as conformance-sensitive.

## 360-Degree Assessment By Aspect

### Language Design

The core language direction is viable. The combination of path expressions, constructors, `let`/`for`/`if`, and rule dispatch is coherent and implementable for a compact XML transformation language.

### Syntax and Parsing

This remains the weakest part of the current draft because the grammar is still not fully normative. The biggest remaining syntactic gap is that the spec's most important advanced features are still specified partly in prose.

### Semantics and Evaluation

The semantic layer is much better than in 1.0, but still leaves too many observable edge cases open. Independent implementations would likely diverge around default parameters, dispatch order, pattern exactness, and some sequence behaviors.

### XML Correctness

This is the most important domain-specific gap. For an XML transformation language, the data model, namespace model, and constructor/serialization model need to be more precise than they are now.

### Standard Library

The library is plausible as a minimum core, but the normative definitions need tightening and a few essentials are still missing, especially around maps and strings.

### Conformance and Testability

The document is not yet strong enough to support an unambiguous conformance test suite. That is the clearest line between "good draft" and "ready specification."

### Security, I18N, and Editorial Quality

All three areas exist, which is good. None is complete enough yet for a mature standards document.

## Priority Fix Order

1. Complete the normative grammar, especially `Pattern`, `Literal`, QName-related syntax, and named arguments.
2. Formalize `apply()` completely.
3. Strengthen the XML data model and namespace semantics.
4. Define constructor normalization and serialization rules precisely.
5. Tighten pattern matching and dispatch order semantics.
6. Rewrite conformance so it is testable.
7. Expand the error model for observable edge cases.
8. Normalize the standard library definitions in the normative spec itself.
9. Expand security and internationalization language.
10. Run a full editorial consistency pass across all companion documents.

## Final Verdict

XForm 2.0 is a **credible core language draft** and a major step forward from 1.0. The design is good enough to justify continued refinement. The remaining issues are mostly specification-engineering problems, not fundamental language-design failures.

The right next step is **not** to redesign the language. It is to make the current design fully normative, XML-precise, and conformance-testable.
