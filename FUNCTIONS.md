# XForm 2.0 Function Reference (Practical)

This is a practical reference for the XForm 2.0 standard-library functions described in `xform-transformations-2.0.md` (§11). It focuses on behavior, return shapes (especially sequences/maps), and common pitfalls.

## How to Read This

- **Sequence** means zero or more items in order.
- **Item** means a node or atomic value.
- **Map** means key/value store where values are sequences.
- Many functions are easiest to use when you normalize cardinality explicitly with `head(...)`, `count(...)`, or `for`.

## General Semantics (Applies to Many Functions)

- Path expressions often return sequences, not single values.
- `lookup(...)` always returns a sequence (including empty).
- `groupBy(...)` returns a sequence of maps (group records).
- Constructors coerce atomic values to text nodes.
- Type/conversion mistakes typically raise `XFDY0002`.
- Node-only operations on atomic values typically raise `XFDY0003`.

## Alphabetical Reference

### `attr(node, qnameOrString) -> string`

Returns an attribute value as a string. If the attribute does not exist, returns an empty string.

Use it when:
- attribute name is dynamic
- you want a guaranteed string result

Examples:

```xform
attr(., "id")
attr(i, "class")
```

Notes:
- `attr()` returns a string, not an attribute node.
- `./@id` returns an attribute node and is better when you want node semantics.

### `boolean(x) -> boolean`

Converts using XForm 2.0 boolean coercion.

Spec behavior (high level):
- empty sequence -> `false`
- sequence containing at least one node -> `true`
- atomic-only sequence -> `false` only if all atomic values are falsy (`false`, `0`, `""`, `null`)

Examples:

```xform
boolean(.//item)
boolean("")
boolean(0)
```

Common use:

```xform
if .//error then <failed/> else <ok/>
```

### `children(node) -> Sequence(Node)`

Returns all child nodes of `node` (not just elements).

May include:
- element nodes
- text nodes
- comments
- processing instructions

Example:

```xform
for c in children(.) return <child kind={typeOf(c)}/>
```

Use `elements(...)` if you want only elements.

### `concat(seq1, seq2) -> Sequence`

Concatenates two sequences.

Example:

```xform
concat(.//a, .//b)
```

Tip:
- For more than two inputs, `seq(...)` is often clearer.

### `copy(node, recurse:=true) -> Node`

Copies a node.

Modes:
- `recurse:=true` (default): deep copy
- `recurse:=false`: shallow copy

Examples:

```xform
copy(/)
copy(.//section)
copy(., recurse:=false)
```

Errors:
- Passing an atomic value is a node-operation error (`XFDY0003`)

### `count(seq) -> number`

Returns the number of items in a sequence.

Examples:

```xform
count(.//item)
count(lookup(g, "items"))
```

### `distinct(seq) -> Sequence`

Removes duplicate items (commonly used with strings/numbers).

Example:

```xform
sort(distinct(.//tag/text()))
```

Notes:
- Most practical uses are on atomic values.
- Behavior on nodes depends on node identity semantics.

### `elements(node, nameTest?) -> Sequence(ElementNode)`

Returns child element nodes only.

Examples:

```xform
elements(.)
elements(., "item")
```

Use it to avoid text/comment/PI noise in mixed-content documents.

### `empty(seq) -> boolean`

Returns `true` if the sequence has no items.

Example:

```xform
if empty(.//warning) then <ok/> else <warn/>
```

### `groupBy(seq, keyFn) -> Sequence(Map)`

Groups items by a computed key and returns a sequence of group maps.

Each group map contains at least:
- `"key"` -> group key (as sequence)
- `"items"` -> grouped items (sequence)

Example:

```xform
for g in groupBy(.//indexterm, primaryKey) return
  <group k={string(lookup(g, "key"))} n={count(lookup(g, "items"))}/>
```

Typical pattern:

```xform
for g in sort(groupBy(.//indexterm, primaryKey), groupKey) return
  for t in sort(lookup(g, "items"), secondaryKey) return ...
```

Use `groupBy()` when you want to iterate groups as records.

### `head(seq) -> Item | empty`

Returns the first item of a sequence.

Example:

```xform
head(.//item)
```

Useful for cardinality normalization before `string(...)` or `number(...)`:

```xform
string(head(i/name/text()))
```

### `index(seq, key:=exprOrFn) -> Map`

Builds a map from key -> sequence of items matching that key.

Example:

```xform
let idx := index(.//item, key:=string(@id)) in
lookup(idx, "42")
```

Use `index()` when you need repeated direct lookups by key.

Difference vs `groupBy()`:
- `index()` gives a lookup map
- `groupBy()` gives a sequence of group records for iteration

### `last() -> number` (iteration-context form)

Inside a `for`, returns the size of the active iteration sequence.

Example:

```xform
for i in .//item return <row pos={position()} total={last()}/>
```

Important:
- `last()` (no args) is different from `last(seq)`.

### `last(seq) -> Item | empty` (sequence form)

Returns the last item of a sequence.

Example:

```xform
last(.//item)
```

### `lookup(map, key) -> Sequence`

Looks up a key in a map and returns the associated sequence.

Examples:

```xform
lookup(idx, "A123")
lookup(g, "key")
lookup(g, "items")
```

Notes:
- Missing keys usually return an empty sequence.
- `lookup()` returning a sequence is the most common source of cardinality confusion.

### `name(node) -> string`

Returns the name of an element/attribute node.

Example:

```xform
for n in ./* return <n>{ name(n) }</n>
```

Useful for generic transforms, inspection, and debugging.

### `number(x) -> number`

Converts to number.

Example:

```xform
number(i/price/text())
```

Errors:
- Invalid conversion raises `XFDY0002` (e.g. `number("abc")`)

Tip:
- Guard uncertain inputs with pattern checks or `if`.

### `position() -> number`

Inside a `for`, returns the current 1-based iteration index.

Example:

```xform
for i in .//item return <row pos={position()}>{ i/name/text() }</row>
```

### `seq(a, b, ...) -> Sequence`

Variadic sequence constructor.

Example:

```xform
seq(
  <primaryterm>{ t/primary/text() }</primaryterm>,
  <secondaryterm>{ t/secondary/text() }</secondaryterm>
)
```

Use `seq(...)` when a `return` expression should emit multiple siblings/items.

### `sort(seq, keyFn?) -> Sequence`

Returns a sorted sequence.

Forms:
- `sort(seq)` for direct values (strings/numbers)
- `sort(seq, keyFn)` for computed keys

Examples:

```xform
sort(distinct(.//tag/text()))
sort(groupBy(.//indexterm, primaryKey), groupKey)
```

Pitfalls:
- Mixed key types can lead to implementation-specific ordering.
- If key extraction returns sequences, normalize keys (`string(...)`, `head(...)`).

### `string(x) -> string`

Converts a node or atomic value to string.

Examples:

```xform
string(./@id)
string(.//title)
string(number("12.5"))
```

Notes:
- Common for constructor attributes and sort/group keys.
- If `x` is a multi-item sequence, behavior may be surprising; normalize first.

### `tail(seq) -> Sequence`

Returns all items except the first.

Example:

```xform
tail(.//item)
```

### `text(node, deep:=true) -> string`

Returns text content from a node.

Modes:
- `deep:=true` (default): descendant text concatenation
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

## Patterns and Recipes

### Safe String Extraction

Avoid cardinality surprises:

```xform
string(head(i/name/text()))
```

### Group + Sort + Flatten

```xform
for g in sort(groupBy(.//item, keyFn), groupKey) return
  for i in sort(lookup(g, "items"), itemKey) return ...
```

### Build and Reuse Index

```xform
let idx := index(.//item, key:=string(@id)) in
<out>{ count(lookup(idx, "A42")) }</out>
```

### Debug Map/Group Shapes

```xform
for g in groupBy(.//item, keyFn) return
  <g k={string(lookup(g, "key"))} n={count(lookup(g, "items"))}/>
```

## Common Mistakes (Quick Checklist)

- Treating `lookup(...)` as a scalar instead of a sequence
- Confusing `last()` and `last(seq)`
- Using `attr(...)` when an attribute node is needed
- Calling `copy(...)` on atomic values
- Sorting on non-normalized keys
- Calling `number(...)` on unvalidated text

## Related Docs

- Tutorial: `TUTORIAL.md`
- Language spec: `xform-transformations-2.0.md`
- Fixture examples: `tests/fixtures/case*/transform.xform`

