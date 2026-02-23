# XForm 2.0 Tutorial (One Page)

XForm 2.0 is a declarative XML transformation language: read XML, select nodes with path expressions, build new XML with constructors, and combine results with `for`, `let`, and functions.

This tutorial is based on `xform-transformations-2.0.md` and uses syntax/examples compatible with the reference implementations in this repo.
It focuses on the most useful 2.0 features, and also includes short spec-level examples for namespaces/modules and rule dispatch.

## 1. Quick Start

Input (`input.xml`):

```xml
<catalog>
  <item id="1"><name>Alpha</name><price>9.50</price></item>
  <item id="2"><name>Beta</name><price>12.00</price></item>
</catalog>
```

Transform (`transform.xform`):

```xform
xform version "2.0";

def itemToEntry(i) :=
  <entry id={string(i/@id)}>
    <title>{ i/name/text() }</title>
    <price currency={"EUR"}>{ number(i/price/text()) }</price>
  </entry>;

<feed>{ for i in .//item return itemToEntry(i) }</feed>
```

Run (pick any implementation):

```bash
python -m zopyx.xform input.xml transform.xform
xform-rs/target/release/xform input.xml transform.xform
xform-c/build/src/xform input.xml transform.xform
xform-cpp/build/src/xform input.xml transform.xform
xform-java/bin/xform input.xml transform.xform
```

Output:

```xml
<feed><entry id="1"><title>Alpha</title><price currency="EUR">9.5</price></entry><entry id="2"><title>Beta</title><price currency="EUR">12</price></entry></feed>
```

## 2. Core Ideas (2.0)

- Every expression returns a **sequence** (possibly empty).
- Items are either **nodes** (element/text/comment/PI/attribute/document) or **atomic values**.
- `.` is the current context item; `/` is the root of the current document.
- Path expressions (`.//item`, `./@id`, `i/name`) return nodes in document order.
- Use `:=` for bindings/function bodies (2.0 changed this from `=` to avoid ambiguity).

## 3. XForm Skeleton

```xform
xform version "2.0";

def helper(x) := ...;

<result>{ ... }</result>
```

Notes:

- Line comments start with `#`.
- In XML constructor content, `#` is text, not a comment.

## 4. Path Expressions and Constructors

Select nodes:

```xform
.//item           # all descendant <item> elements
./name/text()     # direct <name> child text nodes
./@id             # attribute node
i/@id             # path starting from variable i (2.0 path-start rule)
```

Construct XML:

```xform
<entry id={string(i/@id)}>
  <title>{ i/name/text() }</title>
</entry>
```

Rules:

- Attribute values are string-coerced.
- `{ expr }` inserts nodes directly; atomic values become text nodes.

## 5. Control Flow (`for`, `let`, `if`)

`for` maps over a sequence and concatenates results:

```xform
<items>{
  for i in .//item return <name>{ i/name/text() }</name>
}</items>
```

`let` binds once and reuses:

```xform
let p := number(.//price/text()) in
if p > 10 then <expensive>{ p }</expensive> else <cheap>{ p }</cheap>
```

## 6. Pattern Matching

Match on element shapes (from 2.0 pattern matching semantics):

```xform
xform version "2.0";

<out>{
  for n in ./*/* return
    match n:
      case <a>{x}</a> => <A>{ x }</A>;
      case <b>{x}</b> => <B>{ x }</B>;
      default => <Other>{ string(n) }</Other>;
}</out>
```

`match` evaluates the expression and processes sequence items in order; the first matching `case` wins.

## 7. Useful Standard Library Functions

Common 2.0 functions (minimum set in the spec):

- `string(x)`, `number(x)`, `boolean(x)`, `typeOf(x)`
- `text(node, deep:=true)`, `children(node)`, `elements(node, nameTest?)`
- `copy(node, recurse:=true)`
- `count(seq)`, `empty(seq)`, `distinct(seq)`, `sort(seq, keyFn?)`
- `seq(a, b, ...)`, `concat(a, b)`, `head(seq)`, `tail(seq)`, `last(seq)`
- `groupBy(seq, keyFn)`, `lookup(map, key)`

Two more 2.0 helpers worth knowing:

- `index(seq, key:=exprOrFn)` builds a map from key to matching items
- `position()` / `last()` work inside `for` iterations

## 8. Example: Unique Sorted Tags

Input:

```xml
<doc><tag>b</tag><tag>a</tag><tag>b</tag></doc>
```

Transform:

```xform
xform version "2.0";

<tags>{
  for t in sort(distinct(.//tag/text())) return <tag>{ t }</tag>
}</tags>
```

Output:

```xml
<tags><tag>a</tag><tag>b</tag></tags>
```

## 9. Example: Grouping (`groupBy` + `lookup`)

Transform pattern (based on fixture usage):

```xform
xform version "2.0";

def primaryKey(t) := string(t/primary/text());
def secondaryKey(t) := string(t/secondary/text());
def groupKey(g) := string(lookup(g, "key"));

<indexdoc>{
  for g in sort(groupBy(.//indexterm, primaryKey), groupKey) return
    for t in sort(lookup(g, "items"), secondaryKey) return
      seq(
        <primaryterm>{ t/primary/text() }</primaryterm>,
        <secondaryterm>{ t/secondary/text() }</secondaryterm>
      )
}</indexdoc>
```

`groupBy()` returns a sequence of maps (group objects). `lookup(g, "key")` gets the group key, and `lookup(g, "items")` gets the grouped items.

## 10. Example: Copying Existing XML

Copy the whole document:

```xform
xform version "2.0";
copy(/)
```

Copy selected subtrees:

```xform
xform version "2.0";
<root>{ copy(.//section) }</root>
```

## 11. Example: `index()` and Positional Iteration

```xform
xform version "2.0";

let idx := index(.//item, key:=string(@id)) in
<report>{
  for i in .//item return
    <row pos={position()} total={last()}>
      <id>{ string(i/@id) }</id>
      <same>{ count(lookup(idx, string(i/@id))) }</same>
    </row>
}</report>
```

Notes:

- `position()` / `last()` are defined for the current `for` loop iteration.
- `index()` returns a map; `lookup()` returns a sequence.

## 12. Rule-Based Dispatch (`rule` + `apply`) (2.0)

This is the XSLT-like recursive style introduced/clarified in 2.0:

```xform
xform version "2.0";

rule main match <catalog>{xs}</catalog> =
  <feed>{ apply(xs) }</feed>;

rule main match <item>{xs}</item> =
  <entry>{ apply(xs) }</entry>;

rule main match <name>{xs}</name> =
  <title>{ xs }</title>;

rule main match <price>{xs}</price> =
  <price>{ xs }</price>;

rule main match <_>{xs}</_> =
  apply(xs);

apply(.)
```

Mental model:

- `rule ... match PATTERN = EXPR;` registers pattern/action pairs.
- `apply(seq)` dispatches each item to the first matching rule (default ruleset: `main`).
- If no rule matches an item, `XFDY0001` is raised.

## 13. Namespaces and Modules (2.0)

Namespace declaration (feeds the static context):

```xform
xform version "2.0";
ns "h" = "http://www.w3.org/1999/xhtml";

<h:div class={"note"}>{ "Hello" }</h:div>
```

Imports are separate from namespace bindings:

```xform
xform version "2.0";
import "lib/format.xform" as fmt;

<out>{ fmt:render(.) }</out>
```

Module-level binding (2.0 uses `var` to avoid ambiguity with `let`):

```xform
xform version "2.0";
var currency := "EUR";
<price currency={currency}>{ .//price/text() }</price>
```

## 14. 2.0 Constructors and Patterns: Small but Important

Text constructor (2.0):

```xform
xform version "2.0";
<p>{ text{ "A < B & C" } }</p>
```

Pattern examples beyond simple element names:

```xform
match .//item:
  case <item id={id}>{x}</item> => <hit>{ id }</hit>;
  case <item><name>{n}</name>{rest}</item> => <named>{ n }</named>;
  default => <miss/>;
```

## 15. Errors You Will Actually See

Common 2.0 error classes from the spec:

- `XFST0001` syntax error (parse failure)
- `XFST0002` unbound prefix / QName
- `XFST0003` unknown function
- `XFST0005` unsupported version string
- `XFDY0001` no matching `case` / `rule`
- `XFDY0002` type/conversion error (for example `number("abc")`)
- `XFDY0003` node operation on atomic value
- `XFDY0004` invalid constructor
- `XFDY0099` non-terminating recursion

Tip: start debugging by isolating one expression and wrapping it in a tiny constructor such as `<debug>{ ... }</debug>`.

## 16. 2.0 Gotchas (Worth Remembering)

- Use `:=` (not `=`) for `def` and `let`.
- `i/@id` is valid in 2.0 because identifiers can start a path.
- `if` is lazy (only selected branch is evaluated).
- `and` / `or` are short-circuiting.
- `#` starts a comment in code, but inside constructor content it is literal text.
- `match` on a sequence is item-wise; results are concatenated in order.
- Boolean coercion of sequences is not just XPath-like strings: any sequence containing a node is truthy.
- If a `match` has no matching case and no `default`, it raises `XFDY0001`.

## 17. Where Next

- Full spec: `xform-transformations-2.0.md`
- More examples: `tests/fixtures/case*/transform.xform`
- Run cross-implementation fixture tests: `make test-python`
