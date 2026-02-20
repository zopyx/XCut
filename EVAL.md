# Evaluation: XForm Transformations 1.0

## Overall Impression

The spec reads well as an initial Editor's Draft. The document structure, normative language (MUST/SHOULD/MAY), conformance classes, and error codes are all solid W3C-style scaffolding. The core design goals (readability, predictability, composability) are well-motivated. However, there are significant gaps in grammar consistency, semantics completeness, and expressive power compared to XSLT/XQuery that would block a real implementation.

---

## 1. Grammar Issues (Section 7)

### 1.1 Variable as PathStart is undefined

The "Hello Transform" example uses:
```xform
i.@id
i./name/text()
i./price/text()
```

But the EBNF defines:
```ebnf
PathStart := "." | "/" | ".//" | "//" ;
```

A bound variable (`i`) is not a valid `PathStart`. There is no rule for `VarExpr "/" ...` or `VarExpr ".@" NameTest`. This makes the only example in the document **grammatically invalid** according to the spec's own grammar. Either `PathStart` needs to include `Identifier`, or a separate `VarPath` rule is needed (e.g., `Identifier { PathStep }`).

### 1.2 `.@id` in the overview vs. `/@` NameTest in the grammar

Section 5 uses `.@id` as shorthand for attribute access. The grammar's `PathStep` only lists `"/@" NameTest` as the attribute step form. `".@"` is never defined. Pick one syntax and define it consistently.

### 1.3 `AttrConstructor` uses `Identifier`, not `QName`

```ebnf
AttrConstructor := Identifier "=" "{" Expr "}" ;
```

This forbids namespace-prefixed attributes like `xsi:type="{...}"`. Should be `QName`.

### 1.4 `TextConstructor` is declared but never defined

Line 185: `Constructor := ElemConstructor | TextConstructor ;`
`TextConstructor` has no production rule anywhere in the spec.

### 1.5 `CharData` is undefined

`Content := Constructor | "{" Expr "}" | CharData ;`
`CharData` appears with no lexical definition. The note in Appendix A acknowledges this but leaves it unresolved, which makes the grammar incomplete as normative text.

### 1.6 `TypeRef` is undefined

`Param := Identifier [ ":" TypeRef ] ;`
`TypeRef` is used in `ParamList` but never defined. With no type system spec, type annotations are purely decorative, which undermines static checking.

### 1.7 `rule` vs `def` distinction missing

Both `FuncDecl` and `RuleDecl` have nearly identical structure. The semantics section (§8) never mentions `rule`. The reserved words list includes it, but there is no explanation of what distinguishes a `rule` from a `def`. Is it a template rule (like XSLT)? A rewrite rule? This is a significant omission.

### 1.8 `VarDecl` vs `LetExpr` ambiguity

Module-level `VarDecl` uses `let`:
```ebnf
VarDecl := "let" Identifier "=" Expr ";" ;
```
Expression-level `LetExpr` uses:
```ebnf
LetExpr := "let" Identifier "=" Expr "in" Expr ;
```

A parser encountering `let x = ...` must look ahead past the full `Expr` to determine if `in` follows. For complex expressions, this may be arbitrarily deep lookahead. The ambiguity between a module-level declaration and the start of a `LetExpr` is a real parsing problem.

### 1.9 `EqExpr` uses `"="` for equality

Using `=` for both assignment (`VarDecl`, `FuncDecl`) and comparison (`EqExpr`) creates context-sensitivity in parsing. The grammar doesn't describe how a parser distinguishes them. XPath uses `=` for comparison and XQuery uses `:=`; this spec picks the ambiguous option without resolving it.

---

## 2. Semantics Gaps (Section 8)

### 2.1 No evaluation rule for `PathExpr` with variables

Section 8.3 describes `let`, `for`, `if`, and function calls, but says nothing about how path expressions are evaluated. Specifically:
- What is the context item when evaluating `.//item`?
- How does `for i in .//item` establish `i` as the context item for `itemToEntry(i)`?
- What does `/` as a `PathStart` mean (root of current document? which document?)?

These are foundational questions for a path-based language.

### 2.2 boolean-coercion for mixed sequences is ambiguous

Section 8.4:
> enthält atomare Werte → `false` nur wenn alle Werte „falsy" sind

What happens when the sequence contains *both* nodes and atomic values? The three cases (empty, nodes, atomic) are not exhaustive and not exclusive. A sequence like `(42, <foo/>)` is unaddressed.

### 2.3 No lazy evaluation or short-circuit semantics defined

For `if A then B else C`, does `A` always evaluate fully? Are `B` and `C` lazy? What about `and`/`or` — is short-circuit evaluation guaranteed? This matters for correctness in the presence of `XFDY0002` errors.

### 2.4 No recursion semantics

The spec claims XForm is for recursive document processing but never describes recursion. Can `def f(x) = f(x)` terminate? Is tail-call optimization required? This is critical for processing deeply nested XML.

---

## 3. Pattern Matching (Section 9)

### 3.1 No attribute patterns

The only structural pattern is `<qname>{var}</qname>`, which matches an element by name and binds its children. There is no way to match on attributes, e.g. `<item type="product">{children}</item>`. This severely limits usefulness for real XML processing.

### 3.2 `{var}` binding semantics are underspecified

Does `{var}` in `<foo>{var}</foo>` bind:
- only element children?
- all child nodes (including text nodes)?
- the full content sequence?

The spec says "bindet die Kindsequenz" (binds the child sequence) but doesn't define whether text nodes and comments are included.

### 3.3 No nested element patterns

You can't write `case <order><item>{x}</item></order> => ...` — there is no production for nested patterns. Without this, pattern matching on structured XML is very weak.

### 3.4 `match` operates on single item vs. sequences — unspecified

The spec describes `match Expr :` without specifying whether `Expr` must produce a single item or a sequence. Matching over sequences (with flat dispatch) is a common transformation pattern, and the relationship to XSLT's template dispatch is never addressed.

---

## 4. Standard Library Issues (Section 11)

### 4.1 `attr()` return type is inconsistent

`attr(node, qnameOrString)` returns `Sequence(AttributeNode|string)` — the union return type (either an `AttributeNode` or a `string`) is poorly motivated. Callers must type-check the result. Better to either always return an `AttributeNode` or always return the string value.

### 4.2 `text(node)` returns all descendant text

This conflates two common operations: direct text children vs. full text content. XSLT and XPath separate these (`.//text()` vs. `string()`). Having `text()` deep-concatenate makes it impossible to access only direct text children via the library.

### 4.3 `index()` map type is not formally defined

`index(seq, key)` returns `map(keyValue → Sequence(items))`. A `map` type is never defined in the data model (§4), the type system, or the grammar. The `lookup()` function takes a `map` argument, but maps cannot be constructed or inspected beyond these two functions.

### 4.4 `groupBy()` return type is informal

`Sequence(map{key, items})` is notation borrowed from prose, not from any formal type definition.

### 4.5 Missing common operations

- No `concat(seq1, seq2)` or sequence union
- No `head(seq)` / `tail(seq)` / `last(seq)` — essential for recursion
- No `position()` / `last()` in `for` context (XPath analogs)
- No string functions: `substring`, `contains`, `starts-with`, `replace` (regex)
- No `document()` / `doc()` to load external XML

---

## 5. Comparison to XSLT

| Feature | XSLT 3.0 | XForm 1.0 |
|---|---|---|
| Recursive dispatch | `apply-templates` (priority-based) | `match/case` (sequential, no dispatch) |
| Built-in rules | Yes (identity, text copy) | Not defined |
| Modes | Yes | Not defined |
| Streaming | Yes (XSLT 3.0 `streamable`) | Mentioned, not specified |
| Type system | XDM + schema types | 4 primitives + optional dates |
| Higher-order functions | Yes (XSLT 3.0) | Not defined |
| Map/array | Yes (XSLT 3.0) | Partially (map only via `index`) |
| Tunnel parameters | Yes | No equivalent |

The key gap vs. XSLT is the lack of a **recursive dispatch mechanism**. XSLT's `apply-templates` allows a transformation to process an arbitrary tree without the programmer enumerating every node type. In XForm, you must write explicit `match/case` expressions with explicit recursion, but recursion is never specified. Processing a heterogeneous XML document (like DocBook or DITA) with XForm would require manually threading recursion through every case arm.

---

## 6. Namespace Handling

### 6.1 `ns` declaration syntax conflicts with grammar

Section 12.1 shows:
```xform
ns "p" = "urn:example:product";
```
But `ns` does not appear in the `Module` grammar (§7), which only lists `PrologDecl`, `ImportDecl`, `FuncDecl`, `VarDecl`, `RuleDecl`. There is no `NsDecl` production.

### 6.2 Namespace syntax is unusual

Standard practice (XQuery, XSLT) uses `declare namespace p = "..."`. The `import "iri" as p` conflates module import with namespace binding — it is unclear whether the `as p` prefix creates a namespace binding or only a module alias.

---

## 7. Minor Issues

- **Name collision with W3C XForms**: acknowledged in the document but not resolved
- **`#` comments inside XML constructors**: comments starting with `#` are defined as "where whitespace is allowed," but `CharData` in element content is not whitespace — the interaction is unspecified
- **No version negotiation**: what happens if a processor encounters an unknown version string?
- **Appendix A is a placeholder**: the normative grammar in §7 is explicitly incomplete ("repräsentativ"), so the only complete grammar is deferred to an appendix that doesn't exist
- **`copy(node, recurse=true)` uses `=` for default param**: the grammar has no syntax for default parameter values

---

## Summary

| Area | Status |
|---|---|
| Document structure & conformance | Good |
| Motivation & goals | Good |
| Data model | Adequate |
| Grammar (EBNF) | Incomplete — 5+ undefined terminals, example violates grammar |
| Evaluation semantics | Thin — path eval, recursion, mixed-sequence coercion missing |
| Pattern matching | Weak — no attribute patterns, no nesting, no dispatch |
| Standard library | Partial — map type informal, common string/sequence ops missing |
| Namespace handling | Inconsistent — `ns` not in grammar |
| Comparison to XSLT | Regression on recursive dispatch |
| Implementability | Not yet — grammar holes block a conforming parser |

The spec has a coherent identity and the language design is well-motivated, but it is not yet implementable as written. The highest-priority fixes are:

1. Define `PathStart` to include `Identifier` (variables as path roots)
2. Define `CharData`, `TextConstructor`, and `TypeRef` production rules
3. Add attribute patterns to §9
4. Define `rule` semantics and distinguish from `def`
5. Specify how recursive tree traversal works without an `apply-templates` equivalent
6. Add `NsDecl` to the `Module` grammar
7. Formally define the `map` type in the data model

---

---

# Evaluation: XForm Transformations 2.0 (vs. 1.0)

## Overall Impression

2.0 is a substantial and accurate response to the 1.0 evaluation. Every critical grammar hole has been patched, the semantics section has been materially expanded, and the `rule`/`apply()` dispatch mechanism closes the most serious gap vs. XSLT. The built-in diff (Appendix B/C) is a useful addition for reviewers. The spec is now plausibly implementable for a core subset. Several issues of medium and low severity remain, and one new structural ambiguity is introduced.

---

## What 1.0 Issues Are Fixed

| 1.0 Issue | Status in 2.0 |
|---|---|
| Variable as `PathStart` | Fixed — `PathStart` now includes `Identifier` |
| `.@id` vs `/@` inconsistency | Fixed — both forms in `PathStep` |
| `AttrConstructor` uses `Identifier` | Fixed — uses `QName` |
| `TextConstructor` undefined | Fixed — `text { Expr }` production added |
| `CharData` undefined | Fixed — `{ Char }` with exclusions defined |
| `TypeRef` undefined | Fixed — closed enum + `QName` fallback |
| `rule` vs `def` distinction | Fixed — `rule` is now a dispatch pattern, `def` a function |
| `VarDecl` / `LetExpr` `let` ambiguity | Fixed — `var` keyword for module-level bindings |
| `=` used for both assign and equality | Fixed — `:=` for assignment, `=` for equality |
| No path evaluation semantics | Fixed — §8.4 defines context item, root, descendant-or-self |
| Boolean coercion for mixed sequences | Substantially fixed (see note below) |
| No lazy / short-circuit semantics | Fixed — §8.3 requires lazy `if`, short-circuit `and`/`or` |
| No recursion semantics | Fixed — §8.6 permits recursion, defines `XFDY0099` |
| No attribute patterns | Fixed — `@qname` pattern added |
| `{var}` binding underspecified | Fixed — binds full child sequence including text nodes |
| No nested element patterns | Fixed — `<a><b>{x}</b></a>` is MUST |
| `match` on sequences unspecified | Fixed — §9.2 defines item-wise matching with concatenation |
| No recursive dispatch | Fixed — `rule` + `apply()` (§9.3) |
| `attr()` union return type | Fixed — always returns `string`, empty if absent |
| `text()` conflates deep/direct | Fixed — `text(node, deep:=true)` parameter |
| `map` type informal | Fixed — §4.3 defines `map` in the data model |
| Missing `concat`, `head`, `tail`, `last` | Fixed — all added in §11.3 |
| No `position()`/`last()` in `for` | Fixed — §11.5 |
| `ns` not in grammar | Fixed — `NsDecl` added to `Module` production |
| `#` in constructor content | Fixed — §6.1 states `#` is literal text in constructors |
| No version error code | Fixed — `XFST0005` added |
| `groupBy()` type informal | Partially fixed — `map` is now formal, but return shape still informal |
| No default parameter syntax | Fixed — `Param := Identifier [ ":" TypeRef ] [ ":=" Expr ]` |

---

## Remaining Issues in 2.0

### Grammar

**`apply()` is not in the grammar or standard library**

§9.3 defines `apply(seq, Name?)` as a central dispatch primitive, but:
- It does not appear as a production in §7.
- It does not appear in §11 (standard library).
- Its argument type for `Name` is undefined — is it a string literal, a `QName`, an `Identifier`?
- `apply` is not listed in Appendix A reserved words, so a user can define `def apply(...)` and shadow the dispatch built-in.

This is the most significant new gap introduced in 2.0. `apply()` is load-bearing for the XSLT-parity claim and must be formally specified.

**`text { Expr }` vs `text(node, deep:=true)` lexical collision**

`TextConstructor := "text" "{" Expr "}" ;` and the library function `text(node, deep:=true)` both start with the token `text`. A parser seeing `text` must peek one token ahead (`{` vs `(`) to resolve the ambiguity. This is manageable but should be stated explicitly in §6.

**`Identifier` as `PathStart` is ambiguous with `FuncCall`**

`PathExpr` starts with `Identifier`, and `FuncCall := QName "(" ... ")"` also starts with `QName` (which includes bare identifiers). A parser must look ahead one token (`(` vs anything else) to distinguish them. This one-token lookahead is sufficient but is not stated, leaving a gap in the parsing model.

**`EqExpr` still uses `=` for equality while `NsDecl` also uses `=`**

`NsDecl := "ns" StringLiteral "=" StringLiteral ";"` and `EqExpr` use `=`. These are in distinct syntactic positions so there is no real ambiguity, but the two different uses of `=` (namespace binding vs. value comparison) alongside `:=` (assignment) are worth documenting to avoid confusion for implementors.

### Semantics

**Nested pattern match semantics incomplete**

`<a><b>{x}</b></a>` is defined as a MUST-support pattern, but the spec does not say whether it is an **exact** or **prefix** match. Does `<a><b>hello</b><c/></a>` match `<a><b>{x}</b></a>`? The `<c/>` is unaccounted for in the pattern. Without this, two conforming processors could disagree on whether a document matches.

**`apply()` has no built-in default rule**

XSLT's `apply-templates` has built-in template rules: elements recurse, text nodes copy. XForm's `apply()` raises `XFDY0001` when no rule matches. This means every ruleset must include a catch-all `rule main match _ := ...`, or any document with unexpected node types will fail at runtime. The spec should either define built-in default rules (identity rule for elements, pass-through for text) or mandate that rulesets be exhaustive.

**Boolean coercion for mixed sequences still implicit**

§8.5 lists three cases: empty → `false`; any Node → `true`; otherwise atomic coercion. For a mixed sequence `(42, <foo/>)` the "any Node → true" rule takes priority, but this priority is not stated. The three cases should be presented as ordered rules (if-else chain) rather than a flat list.

**Default parameter evaluation context undefined**

`Param := Identifier [ ":" TypeRef ] [ ":=" Expr ]` — when does the default expression evaluate? At call site (with the caller's dynamic context)? At parse time? Can a default expression reference other parameters of the same function? This is unspecified.

**`head()` / `tail()` behavior on empty sequence unspecified**

§11.3 adds `head(seq)` and `tail(seq)`. Neither specifies what happens when `seq` is empty — error (`XFDY0003`)? Empty sequence? These are common sources of bugs and should be stated.

**`position()` and `last()` outside `for` context**

§11.5 says these are "available inside `for`". What happens if they are called outside a `for` expression? A static error would be cleanest; a dynamic error (`XFDY0003`) is the minimum. Neither is specified.

### Pattern Matching

**Attribute pattern has no value matching**

`@qname` matches an attribute by name only. There is no way to write a pattern like `@type="product"` (attribute with specific value) or combine an element pattern with an attribute constraint. Guarded patterns (`case P where E`) from 1.0 are gone from §9.1 without explanation — they could have filled this role.

**Only one `{var}` binding per element pattern**

`<qname>{var}</qname>` binds the entire child sequence to `var`. There is no way to write a pattern that destructures specific children, e.g., `<order><id>{id}</id><amount>{amt}</amount></order>`. Nested patterns (§9.1 item 5) partially help, but there is still no way to bind siblings independently.

### Standard Library

**`groupBy()` return structure still informal**

`groupBy(seq, keyFn)` returns `Sequence(map{key, items})`. Now that `map` is formally a type (§4.3), the spec should state the exact keys of each returned map: a `string`-keyed map with entries `"key"` and `"items"`, or something else. `map{key, items}` remains pseudo-notation.

**No map introspection functions**

Maps can be created via `index()` and queried via `lookup()`, but there is no `keys(map)`, `entries(map)`, or `mapSize(map)`. A map returned by `groupBy()` or `index()` cannot be iterated without additional primitives.

**No map literal syntax**

Maps can only be produced by library functions. There is no map literal like `{"a": 1, "b": 2}`. Compared to XQuery 3.1 or XSLT 3.0 maps this is a notable gap for in-transformation data structures.

**String library still absent**

2.0 carries over the same gap: no `substring`, `contains`, `starts-with`, `ends-with`, `matches` (regex), `replace`, `normalize-space`. These are needed for almost any real XML transformation.

**No `document()` / `doc()` for loading external XML**

The only way to bring in external XML is via `import`, which is for modules (XForm code), not data. Loading a secondary XML document during transformation (common in XSLT with `document()`) is impossible and not addressed.

---

## Comparison Table (Updated)

| Feature | XSLT 3.0 | XForm 1.0 | XForm 2.0 |
|---|---|---|---|
| Recursive dispatch | `apply-templates` (priority) | None | `rule` + `apply()` (first-match) |
| Built-in identity rule | Yes | No | No |
| Modes | Yes | No | No |
| Attribute patterns | Yes | No | Name-only (`@qname`) |
| Nested patterns | Yes | No | Yes (exact-match TBD) |
| Map type | Yes | Informal | Formal (no literal, no introspection) |
| Short-circuit eval | Yes | Unspecified | Required |
| Recursion | Yes | Unspecified | Permitted, no TCO requirement |
| String library | Extensive | None | None |
| External document loading | `document()` | No | No |
| Default parameters | Yes | No | Yes (eval context unspecified) |
| Higher-order functions | Yes (XSLT 3.0) | No | No |
| Streaming | Yes | Stub | Stub |

---

## Summary

| Area | 1.0 | 2.0 |
|---|---|---|
| Document structure | Good | Good |
| Grammar completeness | Incomplete | Complete for core |
| Evaluation semantics | Thin | Adequate |
| Path expressions | Broken example | Well-defined |
| Pattern matching | Weak | Substantially improved |
| Rule dispatch | Missing | Present but under-specified |
| Standard library | Partial | Improved, still missing strings/map ops |
| Namespace handling | Inconsistent | Consistent |
| Implementability | Blocked | Feasible for core subset |

2.0 is a **major step forward** and is now implementable for a core subset. The highest-priority remaining work is:

1. Formally specify `apply()` in the grammar and standard library, and reserve `apply` as a keyword
2. Define nested pattern matching semantics (exact vs. prefix child match)
3. Add built-in default rules to `apply()` dispatch (identity for elements, text pass-through)
4. Specify `head()`/`tail()` and `position()`/`last()` edge-case behavior
5. Restore or replace guarded patterns to enable attribute-value matching
6. Add map introspection (`keys`, `entries`) and a minimum string library
