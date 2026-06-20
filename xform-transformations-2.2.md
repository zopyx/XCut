# XForm Transformations 2.2
**Editor's Draft (informal)** — Date: 2026-06-20

## 1. Purpose
XForm 2.2 is a conformance stabilization release. It does not redesign the language; it resolves the remaining 2.1 contradictions so independent processors can target one executable contract.

## 2. Version and Compatibility
Processors MUST accept the version string `"2.2"`. Processors SHOULD continue accepting `"2.0"` and `"2.1"` for compatibility. Unsupported version strings raise `XFST0005`.

## 3. Stabilized Semantics

### 3.1 Match Guards
`case Pattern where Expr => Body;` is core syntax.

Evaluation order:
1. Match `Pattern` against the current item.
2. If the pattern matches, bind pattern variables.
3. Evaluate `where Expr` in a dynamic context containing those bindings.
4. If the guard is false, continue to the next case.
5. If the guard is true or absent, evaluate `Body`.

If no case matches and no `default` exists, raise `XFDY0001`.

### 3.2 Attribute Patterns
Element attribute constraints use the implementation-facing form:

```xform
case <item @type="product">{v}</item> => ...
```

Standalone attribute patterns may match name and literal value:

```xform
case @type = "product" => ...
```

The unprefixed form `<item type="product">` is not part of the 2.2 core grammar.

### 3.3 Constructor Content
In XForm 2.2 modules, interpolating an AttributeNode into element content is a dynamic error `XFDY0005`.

Valid:

```xform
<out id={@id}/>
```

Invalid in 2.2:

```xform
<out>{ @id }</out>
```

Processors MAY keep legacy 2.1 behavior for modules declaring version `"2.1"`.

### 3.4 Ruleset Errors
An explicitly named missing ruleset raises `XFST0007`.

No matching case or rule for an existing ruleset raises `XFDY0001`.

### 3.5 Serialization Contract
Library serializers MAY omit the XML declaration. CLI processors MUST document whether they emit or suppress it. Namespace serialization MUST emit declarations for prefixes used in constructed output when namespace-aware construction is implemented.

### 3.6 Security Contract
Processors MUST reject or safely ignore DTD and external entity processing. If a platform XML parser cannot expose a reliable switch, the processor MUST document that limitation and include a regression test showing no external entity expansion occurs.

## 4. Conformance Tests
A 2.2 processor is considered conformant for this release when:

- All 2.0 and 2.1 fixture tests still pass.
- All `tests/fixtures_22` positive fixtures pass.
- All `tests/fixtures_22_errors` fixtures raise the expected error code.
- Guarded cases, `XFST0007`, and `XFDY0005` attribute-content behavior are tested in every runnable implementation.

