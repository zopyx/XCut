# XForm 2.1 vs XSLT — Close Comparison

This comparison is based on [xform-transformations-2.1.md](/Users/ajung/src/xform/xform-transformations-2.1.md) and contrasts XForm 2.1 with XSLT as commonly understood: template rules, XPath-based selection, recursive dispatch, and XML-oriented tooling.

## Executive Summary

XForm 2.1 is a compact, expression-centric XML transformation language that blends path expressions, functional expressions, explicit XML constructors, and a formally specified recursive rule-dispatch model. Compared to XSLT, XForm still aims for terser syntax and more direct output construction, but 2.1 closes several specification gaps that made the 2.0 comparison less stable: `apply()` is now formal, built-in rules exist, named arguments are part of the language, and XML data/namespace handling is more precise.

XSLT remains richer, more mature, and more optimized. XForm 2.1 is closer to a small, coherent core language than to a full XSLT replacement.

## Feature Mapping

| Area | XForm 2.1 | XSLT |
|---|---|---|
| Core paradigm | Expression-centric with rule dispatch | Template/rule-centric |
| Selection language | XPath-like path expressions | XPath |
| Rule dispatch | `rule` + built-in `apply(...)` | Template rules + `apply-templates` |
| Built-in default rules | Yes, specified in core spec | Yes |
| Pattern matching | `match expr: case pattern => expr` | Template match patterns |
| Construction | XML constructors plus text/comment/PI constructors | Literal result elements + instruction elements |
| Control flow | `if`, `for`, `let`, `match` | `xsl:if`, `xsl:choose`, `xsl:for-each`, variables |
| Named arguments | Yes | Parameters, tunnel params, named templates/functions |
| Maps | Yes, with minimal introspection | Yes in modern XSLT/XPath versions |
| Modules | `import "iri" as m;` + `ns` declarations | `xsl:import`, `xsl:include`, packages |
| Errors | Explicit static/dynamic error classes | Defined error model, processor-specific diagnostics |
| Streaming | Not defined in 2.1 core | Defined in XSLT 3.0 |

## Where 2.1 Improved the Comparison

Relative to 2.0, XForm 2.1 is easier to compare seriously with XSLT because the spec now does more of the work explicitly:

1. `apply(...)` is formalized instead of being half-library, half-prose.
2. Built-in default rules exist, reducing brittle "must match everything manually" behavior.
3. Pattern exactness and ruleset ordering are more explicit.
4. Namespace and expanded-name semantics are stronger.
5. Named arguments and default parameter behavior are defined.

That does not make XForm feature-equivalent to XSLT. It makes the *comparison* less fuzzy.

## Language Shape and Readability

XForm 2.1 still favors direct XML constructors with inline expression interpolation:

```xform
<entry id={string(@id)}>{ text(./title) }</entry>
```

This keeps the output shape visually central. XSLT is more verbose because the stylesheet structure, namespace declarations, and instruction elements are more explicit.

Tradeoff:

- XForm is terser for straightforward reshaping
- XSLT is clearer for large rule-driven systems with multiple modes and established conventions

## Rule Dispatch and Matching

XForm 2.1 now has a more stable recursive-dispatch story:

- `rule name match pattern := expr;`
- `apply(seq, ruleset?)`
- built-in low-priority fallback rules
- first-match dispatch over a defined ruleset order

This is conceptually similar to `apply-templates`, but still simpler than XSLT:

- no modes in the core spec
- no import precedence model as rich as XSLT's
- no built-in pattern priority system beyond order
- no large ecosystem of optimizer behavior

XSLT still wins on sophistication; XForm wins on surface simplicity.

## Construction and Serialization

XForm 2.1 supports:

- element constructors
- `text{...}`
- `comment{...}`
- `pi{target, value}`
- namespace fixup requirements
- duplicate-attribute errors

That puts construction semantics on a firmer XML footing than 2.0. XSLT still has broader construction facilities, especially for computed names and advanced namespace control.

## Data Model and Types

XForm 2.1 now defines a more XML-faithful data model than 2.0:

- expanded QNames
- parent relationships
- namespace bindings
- string value rules
- deep-copy requirements

Even so, XSLT/XPath still has the broader and more mature type ecosystem.

## Namespaces and Modules

XForm 2.1 separates:

- XML namespace bindings via `ns`
- module aliasing via `import ... as m`

That is clearer than before, but still less developed than XSLT package/import systems. XSLT has stronger long-term interoperability here.

## Errors and Conformance

XForm 2.1 is much sharper than 2.0 about:

- processor conformance
- required core features
- required error behavior
- observable edge cases

That is an important improvement because it makes the language more testable. XSLT still benefits from a far more mature conformance ecosystem and processor diversity.

## Pros and Cons

### Pros of XForm 2.1 relative to XSLT

1. More compact syntax for many XML reshaping tasks.
2. Output XML remains visually obvious in the transformation source.
3. Expression-level `match` is convenient for local branching.
4. Simpler core mental model than a full XSLT processor.
5. The 2.1 spec is much more coherent and implementation-ready than 2.0.

### Cons of XForm 2.1 relative to XSLT

1. Smaller feature set.
2. No core streaming profile.
3. No core mode system.
4. Less mature optimization and tooling ecosystem.
5. Less interoperability with existing enterprise XML pipelines.

### Pros of XSLT relative to XForm 2.1

1. Mature processors and production tooling.
2. Richer dispatch, packaging, and optimization model.
3. Deep integration with XPath/XDM.
4. More complete string, type, and transformation facilities.
5. Established best practices and deployment history.

### Cons of XSLT relative to XForm 2.1

1. More verbose surface syntax.
2. More ceremony for small transforms.
3. Template indirection can be harder to follow for newcomers.
4. Stylesheet structure can obscure simple output-focused reshaping.

## Key Semantic Differences

Important differences remain:

1. XForm boolean coercion is not XPath effective boolean value.
2. Unbound identifier path starts fall back to `./Identifier`.
3. Rule dispatch is explicitly order-based, not priority-based.
4. `apply(...)` is closer to a built-in special form than to general template machinery.
5. The XForm core library is intentionally smaller.

These are not bugs. They are part of XForm's language identity.

## When Each Is a Better Fit

XForm 2.1 is a better fit when:

- the goal is a compact XML reshaping language
- output structure should remain immediately visible
- the transformation problem fits a small, coherent core

XSLT is a better fit when:

- the transformation is large and rule-heavy
- mature tooling and deployment matter
- streaming, packaging, modes, and advanced optimizations matter
- compatibility with existing XML standards ecosystems is important

## Bottom Line

XForm 2.1 is now much easier to take seriously as a spec than 2.0. It is still not "small-syntax XSLT 3.0"; it is a narrower language with a clearer core. If the goal is a concise XML transformation language with explicit constructors and a disciplined rule model, XForm 2.1 is defensible. If the goal is the broadest XML transformation platform, XSLT still dominates.
