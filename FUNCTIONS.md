# XForm 2.1 Function Reference (Practical)

This is a practical reference for the XForm 2.1 standard library described in [xform-transformations-2.1.md](/Users/ajung/src/xform/xform-transformations-2.1.md). It focuses on behavior, return shapes, named arguments, and common pitfalls.

## How to Read This

- **Sequence** means zero or more items in order.
- **Item** means a node or atomic value.
- **Map** means key/value store where values are sequences.
- Many functions are easiest to use when you normalize cardinality explicitly with `head(...)`, `count(...)`, `string(...)`, or `for`.

## General 2.1 Semantics

- Path expressions usually return sequences, not scalars.
- `lookup(...)` always returns a sequence, including empty.
- `groupBy(...)` returns a sequence of maps.
- Named arguments are part of the language in 2.1: `copy(., recurse:=false)`.
- `apply(...)` is a built-in dispatch form, not a normal function, and cannot be shadowed.
- Constructors coerce atomic values to text nodes in element content.
- Attribute nodes are not valid element-content items; inserting one in content raises `XFDY0005`.

## Named Arguments and Defaults

2.1 formally supports named arguments.

Rules:

- positional arguments come first
- named arguments bind by parameter name
- a parameter cannot be given twice
- defaults are evaluated at call time
- a default expression may refer to earlier parameters, not later ones

Example:

```xform
copy(., recurse:=false)
text(./p, deep:=false)
index(.//item, key:=string(./@id))
```

## Alphabetical Reference

### `apply(seq, rulesetName?) -> Sequence`

Dispatches each item in `seq` against the named ruleset, or `main` if omitted.

Examples:

```xform
apply(.//item)
apply(children(.), detail)
```

Notes:

- `apply(...)` is not a normal library function.
- Built-in low-priority rules exist in 2.1, so unmatched nodes do not necessarily fail.

### `attr(node, qnameOrString) -> string`

Returns an attribute value as a string. If the attribute does not exist, returns an empty string.

Examples:

```xform
attr(., "id")
attr(i, "class")
```

Notes:

- `attr()` returns a string, not an attribute node.
- `./@id` or `@id` returns an attribute node.

### `attributes(node) -> Sequence(AttributeNode)`

Returns the attribute nodes of `node`.

Example:

```xform
for a in attributes(.) return <a n={name(a)}>{ string(a) }</a>
```

Use it when you need node semantics rather than string-valued attribute access.

### `boolean(x) -> boolean`

Converts using XForm 2.1 boolean coercion.

Ordered behavior:

- empty sequence -> `false`
- any sequence containing at least one node -> `true`
- otherwise -> `false` only if all atomic values are falsy

Examples:

```xform
boolean(.//item)
boolean("")
boolean(0)
```

Note:

- This is intentionally not identical to XPath effective boolean value.

### `children(node) -> Sequence(Node)`

Returns all child nodes of `node`.

May include:

- element nodes
- text nodes
- comments
- processing instructions

Example:

```xform
for c in children(.) return <child kind={typeOf(c)}/>
```

### `concat(seq1, seq2) -> Sequence`

Concatenates two sequences.

Example:

```xform
concat(.//a, .//b)
```

### `contains(s, part) -> boolean`

Returns `true` if `part` occurs in `s`.

Example:

```xform
contains(normalizeSpace(text(.)), "warning")
```

### `copy(node, recurse:=true) -> Node`

Copies a node.

Modes:

- `recurse:=true`: deep copy
- `recurse:=false`: shallow copy

Examples:

```xform
copy(/)
copy(.//section)
copy(., recurse:=false)
```

Errors:

- passing an atomic value is a node-operation error (`XFDY0003`)

### `count(seq) -> number`

Returns the number of items in `seq`.

Examples:

```xform
count(.//item)
count(lookup(g, "items"))
```

### `distinct(seq) -> Sequence`

Removes duplicate items.

Example:

```xform
sort(distinct(.//tag/text()))
```

Notes:

- most practical uses are on atomic values
- node behavior depends on node identity

### `elements(node, nameTest?) -> Sequence(ElementNode)`

Returns child element nodes only.

Examples:

```xform
elements(.)
elements(., "item")
```

### `empty(seq) -> boolean`

Returns `true` if `seq` has no items.

Example:

```xform
if empty(.//warning) then <ok/> else <warn/>
```

### `endsWith(s, suffix) -> boolean`

Returns `true` if `s` ends with `suffix`.

### `groupBy(seq, keyFn) -> Sequence(Map)`

Groups items by a computed key and returns a sequence of group maps.

In 2.1, each group map contains exactly:

- `"key"` -> group key sequence
- `"items"` -> grouped item sequence

Example:

```xform
for g in groupBy(.//indexterm, primaryKey) return
  <group k={string(head(lookup(g, "key")))} n={count(lookup(g, "items"))}/>
```

Typical pattern:

```xform
for g in sort(groupBy(.//indexterm, primaryKey), groupKey) return
  for t in sort(lookup(g, "items"), secondaryKey) return ...
```

### `head(seq) -> Item | empty`

Returns the first item of a sequence.

Edge case:

- `head(()) -> ()`

Example:

```xform
string(head(i/name/text()))
```

### `index(seq, key:=exprOrFn) -> Map`

Builds a map from key to sequence of matching items.

Example:

```xform
let idx := index(.//item, key:=string(@id)) in
lookup(idx, "42")
```

Difference vs `groupBy()`:

- `index()` gives a lookup map
- `groupBy()` gives a sequence of group maps for iteration

### `keys(map) -> Sequence`

Returns the keys present in a map.

Example:

```xform
for k in keys(idx) return <k>{ string(k) }</k>
```

### `last() -> number`

Inside a `for`, returns the size of the active iteration sequence.

Example:

```xform
for i in .//item return <row pos={position()} total={last()}/>
```

Important:

- `last()` and `last(seq)` are different operations
- calling `last()` outside `for` raises `XFDY0006`

### `last(seq) -> Item | empty`

Returns the last item of a sequence.

Edge case:

- `last(()) -> ()`

Example:

```xform
last(.//item)
```

### `lookup(map, key) -> Sequence`

Looks up `key` in `map`.

Examples:

```xform
lookup(idx, "A123")
lookup(g, "key")
lookup(g, "items")
```

Notes:

- missing keys return the empty sequence in 2.1
- `lookup()` returning a sequence is a common source of cardinality confusion

### `mapSize(map) -> number`

Returns the number of keys in a map.

### `name(node) -> string`

Returns the lexical name representation of an element or attribute node.

Example:

```xform
for n in ./* return <n>{ name(n) }</n>
```

Note:

- name comparison in the spec is by expanded QName even if `name(...)` is shown lexically

### `normalizeSpace(s) -> string`

Collapses internal whitespace and trims leading/trailing whitespace.

### `number(x) -> number`

Converts to number.

Example:

```xform
number(i/price/text())
```

Errors:

- invalid conversion raises `XFDY0002`

### `position() -> number`

Inside a `for`, returns the current 1-based iteration index.

Example:

```xform
for i in .//item return <row pos={position()}>{ i/name/text() }</row>
```

Outside `for`, calling `position()` raises `XFDY0006`.

### `replace(s, pattern, replacement) -> string`

Returns a replaced string.

Note:

- regex flavor is processor-defined unless documented more precisely

### `seq(a, b, ...) -> Sequence`

Variadic sequence constructor.

Example:

```xform
seq(
  <primaryterm>{ t/primary/text() }</primaryterm>,
  <secondaryterm>{ t/secondary/text() }</secondaryterm>
)
```

### `sort(seq, keyFn?) -> Sequence`

Returns a sorted sequence.

Forms:

- `sort(seq)` for direct comparable values
- `sort(seq, keyFn)` for computed keys

Examples:

```xform
sort(distinct(.//tag/text()))
sort(groupBy(.//indexterm, primaryKey), groupKey)
```

Pitfalls:

- mixed key types can lead to implementation-defined ordering
- if key extraction returns sequences, normalize keys first

### `startsWith(s, prefix) -> boolean`

Returns `true` if `s` starts with `prefix`.

### `string(x) -> string`

Converts a node or atomic value to string.

Examples:

```xform
string(./@id)
string(.//title)
string(number("12.5"))
```

Tip:

- normalize multi-item sequences first when exact cardinality matters

### `substring(s, start, length?) -> string`

Returns a substring.

### `tail(seq) -> Sequence`

Returns all items except the first.

Edge case:

- `tail(()) -> ()`

### `text(node, deep:=true) -> string`

Returns text content from a node.

Modes:

- `deep:=true`: descendant text concatenation
- `deep:=false`: direct text children only

Examples:

```xform
text(./title)
text(./p, deep:=false)
```

Mixed-content example for `<p>Hello <b>world</b>!</p>`:

- `text(p)` -> `"Hello world!"`
- `text(p, deep:=false)` -> `"Hello !"`

### `typeOf(x) -> string`

Returns a type label for debugging and introspection.

Example:

```xform
<debug>{ typeOf(.) }</debug>
```

## Recipes

### Safe String Extraction

```xform
string(head(i/name/text()))
```

### Build and Reuse Index

```xform
let idx := index(.//item, key:=string(@id)) in
<out>{ count(lookup(idx, "A42")) }</out>
```

### Group, Sort, and Flatten

```xform
for g in sort(groupBy(.//item, keyFn), groupKey) return
  for i in sort(lookup(g, "items"), itemKey) return ...
```

### Attribute-Node Iteration

```xform
for a in attributes(.) return
  <attr name={name(a)}>{ string(a) }</attr>
```

## Common Mistakes

- Treating `lookup(...)` as a scalar instead of a sequence
- Confusing `last()` with `last(seq)`
- Using `attr(...)` when an attribute node is needed
- Calling `copy(...)` on atomic values
- Sorting on non-normalized keys
- Forgetting that named arguments must follow positional ones
- Assuming XPath effective boolean value rules apply unchanged

## Related Docs

- Spec: [xform-transformations-2.1.md](/Users/ajung/src/xform/xform-transformations-2.1.md)
- Evaluation history: [EVAL.md](/Users/ajung/src/xform/EVAL.md)
- Comparison: [XSLT_COMPARISON.md](/Users/ajung/src/xform/XSLT_COMPARISON.md)
