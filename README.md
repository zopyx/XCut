# XForm 2.0 — Declarative XML Transformation Language

XForm is a declarative XML transformation language designed as a modern, readable alternative to XSLT. It combines XPath-like path expressions with functional programming constructs — functions, pattern matching, `let` bindings, `for` iterations, and rule-based dispatch — to transform XML documents into new XML structures.

This repository contains:
- **The XForm 2.0 language specification** (`xform-transformations-2.0.md`)
- **A complete Python reference implementation** (`xform/`)
- **A Rust implementation** for performance-sensitive use cases (`xform-rs/`)
- **A TypeScript implementation** (`xform-ts/`)
- **A Go implementation** (`xform-go/`)
- **A Swift implementation** (`xform-swift/`)
- **15 test fixtures** covering real-world transformation patterns, validated against `xsltproc`

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Installation](#installation)
   - [Python](#python)
   - [Rust](#rust)
   - [TypeScript](#typescript)
   - [Go](#go)
   - [Swift](#swift)
3. [Usage](#usage)
4. [Language Reference](#language-reference)
   - [Module Structure](#module-structure)
   - [Path Expressions](#path-expressions)
   - [Constructors](#constructors)
   - [Control Flow](#control-flow)
   - [Pattern Matching & Rules](#pattern-matching--rules)
   - [Operators](#operators)
   - [Data Types](#data-types)
5. [Standard Library](#standard-library)
6. [Examples](#examples)
7. [Error Reference](#error-reference)
8. [Running Tests](#running-tests)
9. [Repository Layout](#repository-layout)
10. [Comparison to XSLT](#comparison-to-xslt)
11. [Language Design Notes](#language-design-notes)

---

## Quick Start

Given this XML input (`input.xml`):

```xml
<catalog>
  <item id="1"><name>Alpha</name><price>9.50</price></item>
  <item id="2"><name>Beta</name><price>12.00</price></item>
</catalog>
```

Write a transform (`transform.xform`):

```xform
xform version "2.0";

def itemToEntry(i) :=
  <entry id={string(i/@id)}>
    <title>{ i/name/text() }</title>
    <price currency="EUR">{ number(i/price/text()) }</price>
  </entry>;

<feed>{ for i in .//item return itemToEntry(i) }</feed>
```

Run it:

```bash
# Python
python -m zopyx.xform input.xml transform.xform

# Rust
xform input.xml transform.xform
```

Output:

```xml
<feed><entry id="1"><title>Alpha</title><price currency="EUR">9.5</price></entry><entry id="2"><title>Beta</title><price currency="EUR">12</price></entry></feed>
```

---

## Installation

### Python

**Requirements:** Python 3.10+

The Python implementation uses only the standard library. No external dependencies are required to run XForm transforms.

Clone the repository and run directly:

```bash
git clone https://github.com/your-org/xcut.git
cd xcut
python -m zopyx.xform input.xml transform.xform
```

To run the test suite you also need `pytest`, `lxml`, and `xsltproc`:

```bash
# Install test dependencies
pip install pytest lxml

# On Debian/Ubuntu
sudo apt-get install xsltproc

# On macOS
brew install libxslt
```

Optionally, set up an isolated environment with `uv`:

```bash
pip install uv
uv venv
source .venv/bin/activate
uv pip install pytest lxml
```

For development, set `PYTHONPATH` to the repository root so the `zopyx.xform` package resolves:

```bash
export PYTHONPATH=/path/to/xcut
python -m zopyx.xform input.xml transform.xform
```

### Rust

**Requirements:** Rust 1.70+ (stable toolchain)

Install Rust via [rustup](https://rustup.rs/):

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

Build the Rust binary:

```bash
cd xform-rs
cargo build --release
```

The compiled binary is placed at `xform-rs/target/release/xform`. You can copy it to any directory on your `$PATH`:

```bash
sudo cp xform-rs/target/release/xform /usr/local/bin/xform
```

Or run it in place:

```bash
./xform-rs/target/release/xform input.xml transform.xform
```

### TypeScript

**Requirements:** Node.js 20+ and npm

Build the TypeScript CLI:

```bash
cd xform-ts
npm install
npm run build
```

Run it:

```bash
node xform-ts/dist/cli.js input.xml transform.xform
```

### Go

**Requirements:** Go 1.21+

Build the Go CLI:

```bash
cd xform-go
mkdir -p bin
go build -o bin/xform ./cmd/xform
```

Run it:

```bash
xform-go/bin/xform input.xml transform.xform
```

### Swift

**Requirements:** Swift 5.7+ (macOS)

Build the Swift CLI:

```bash
cd xform-swift
swift build -c release -Xcc -fmodules-cache-path=/tmp/xform-swift-clang-cache
```

Run it:

```bash
xform-swift/.build/release/xform-swift input.xml transform.xform
```

---

## Usage

### Command-Line Interface

All implementations use the same interface:

```
xform <input.xml> <transform.xform>
```

| Argument | Description |
|---|---|
| `input.xml` | Path to the XML document to transform |
| `transform.xform` | Path to the XForm transformation file |

The result is written to standard output. Errors are written to standard error.

**Python:**

```bash
python -m zopyx.xform input.xml transform.xform
python -m zopyx.xform input.xml transform.xform > output.xml
```

**Rust:**

```bash
xform input.xml transform.xform
xform input.xml transform.xform > output.xml
```

**TypeScript:**

```bash
node xform-ts/dist/cli.js input.xml transform.xform
node xform-ts/dist/cli.js input.xml transform.xform > output.xml
```

**Go:**

```bash
xform-go/bin/xform input.xml transform.xform
xform-go/bin/xform input.xml transform.xform > output.xml
```

**Swift:**

```bash
xform-swift/.build/release/xform-swift input.xml transform.xform
xform-swift/.build/release/xform-swift input.xml transform.xform > output.xml
```

### Exit Codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | Error (parse error, evaluation error, or file not found) |

---

## Language Reference

### Module Structure

Every XForm file is a **module**. The optional prolog declares the version and any namespace bindings, imports, module-level variables, function definitions, and rules. The final expression is the transformation body.

```xform
xform version "2.0";                     # required version declaration

ns "dc" = "http://purl.org/dc/elements/1.1/";  # namespace binding
import "lib/utils.xform" as utils;              # module import

var threshold := 100;                    # module-level variable

def double(x) := x * 2;                 # function definition

rule main match <item>{c}</item> :=      # rule definition
  <entry>{ c }</entry>;

.//item                                  # transformation expression
```

All declarations are separated by semicolons. The final expression (the body of the transformation) does not end with a semicolon.

**Comments** use `#` and run to the end of the line:

```xform
# This is a comment
def add(a, b) := a + b;  # inline comment
```

### Path Expressions

Path expressions navigate the XML document tree. They mirror XPath syntax but integrate naturally with XForm expressions.

#### Path Starts

| Syntax | Description |
|---|---|
| `.` | Context item (the current node) |
| `/` | Document root |
| `.//` | Context node and all its descendants |
| `//` | Document root and all descendants |
| `name` | Variable reference or child element by name |

#### Path Steps

| Syntax | Description |
|---|---|
| `/child` | Child element named `child` |
| `/*` | All child elements (wildcard) |
| `//desc` | Descendant elements named `desc` |
| `/text()` | Child text nodes |
| `/node()` | All child nodes |
| `/comment()` | Child comment nodes |
| `.@attr` or `/@attr` | Attribute named `attr` |
| `/@*` | All attributes |
| `..` | Parent node |

#### Predicates

Predicates filter steps with `[expr]`:

```xform
.//item[number(price/text()) > 10]     # items with price > 10
.//item[@id = "42"]                    # items with id attribute "42"
```

#### Examples

```xform
.                          # the context node itself
./title/text()             # text of the <title> child
.//para                    # all <para> descendants
/@id                       # the id attribute of the context node
/catalog/item              # all <item> children of the root <catalog>
.//item[price/text() = "0"] # items with price zero
```

### Constructors

Constructors create new XML nodes. They use an XML-like syntax with expressions embedded using `{ }`.

#### Element Constructors

```xform
<tag>content</tag>
<tag attr="literal">content</tag>
<tag attr={expr}>content</tag>
<tag/>                              # self-closing (no children)
```

Attributes with static values use plain strings. Attributes with dynamic values use `{ expr }`. The expression in `{ }` is evaluated and coerced to a string.

Content can be:
- **Literal character data:** plain text between tags
- **Interpolated expressions:** `{ expr }` — evaluates to string or node sequence
- **Nested constructors:** `<inner>...</inner>`
- **Text constructors:** `text{ expr }` — forces content to be a text node

```xform
<article id={string(./@id)} class="highlight">
  <h1>{ ./title/text() }</h1>
  text{ concat("Author: ", ./author/text()) }
  { for p in ./para return <p>{ text(p) }</p> }
</article>
```

#### Text Constructors

`text{ expr }` creates a text node from an expression:

```xform
<p>text{ string(./price) }</p>
```

### Control Flow

#### If Expression

```xform
if condition then then-expr else else-expr
```

Only the selected branch is evaluated. Both branches must be present.

```xform
if number(./price/text()) > 100
  then <span class="expensive">{ ./name/text() }</span>
  else <span class="cheap">{ ./name/text() }</span>
```

#### Let Expression

```xform
let name := value-expr in body-expr
```

Binds a local variable for use in `body-expr`. The variable is in scope only within the `in` expression.

```xform
let price := number(./price/text()) in
let tax   := price * 0.19 in
<total>{ price + tax }</total>
```

#### For Expression

```xform
for var in seq-expr where filter-expr return body-expr
```

Iterates over the sequence, optionally filtering, and returns the concatenation of all `body-expr` results. The `where` clause is optional. Inside the loop, `position()` and `last()` give the 1-based position and total count.

```xform
for item in .//product
  where number(item/price/text()) > 0
  return
    <li>{ item/name/text() } — { item/price/text() }</li>
```

### Pattern Matching & Rules

#### Match Expression

```xform
match target-expr :
  case pattern => body;
  case pattern => body;
  default => default-body;
```

Evaluates `target-expr` to a sequence and matches each item against the cases in order, taking the first match. The `default` arm handles unmatched items; if absent and an item goes unmatched, a `XFDY0001` error is raised.

```xform
match ./* :
  case <section>{c}</section> => <div class="section">{ c }</div>;
  case <note>{c}</note>       => <aside>{ c }</aside>;
  default                     => copy(.);
```

#### Patterns

| Pattern | Matches |
|---|---|
| `_` | Any item (wildcard) |
| `<tag>{var}</tag>` | Element named `tag`; binds children to `var` |
| `<tag><child>{var}</child></tag>` | Element with specific child structure |
| `@attrName` | Attribute node named `attrName` |
| `node()` | Any node |
| `text()` | Any text node |
| `comment()` | Any comment node |

The variable bound in `{var}` is a sequence of child nodes, available in the `=>` body.

#### Rules

Rules are named pattern-dispatch tables, similar to XSLT templates:

```xform
rule main match <para>{c}</para>    := <p>{ c }</p>;
rule main match <emphasis>{c}</emphasis> := <em>{ c }</em>;
rule main match text()              := .;
rule main match _                   := apply(children(.), "main");
```

Apply a ruleset to a sequence with `apply()`:

```xform
apply(.//*, "main")
apply(children(.), "main")   # recursive descent
```

Rules are matched in source order. The ruleset name (e.g., `"main"`) groups related rules. `apply()` with no second argument defaults to `"main"`.

### Operators

#### Arithmetic

| Operator | Description |
|---|---|
| `+` | Addition |
| `-` | Subtraction (binary) or negation (unary) |
| `*` | Multiplication |
| `div` | Division (keyword, not `/`) |
| `mod` | Modulo (remainder) |

#### Comparison

| Operator | Description |
|---|---|
| `=` | Equal |
| `!=` | Not equal |
| `<` | Less than |
| `<=` | Less than or equal |
| `>` | Greater than |
| `>=` | Greater than or equal |

Comparisons convert both operands to strings (or numbers for `<`, `<=`, `>`, `>=`).

#### Logical

| Operator | Description |
|---|---|
| `and` | Short-circuit conjunction |
| `or` | Short-circuit disjunction |
| `not` | Unary negation |

`and` and `or` short-circuit: the right operand is not evaluated if the result is determined by the left.

#### Precedence (high to low)

```
unary: not, - (negation)
*, div, mod
+, -
<, <=, >, >=
=, !=
and
or
```

Parentheses override precedence: `(a + b) * c`.

### Data Types

| Type | Description | Boolean coercion |
|---|---|---|
| **string** | Unicode text | `false` if empty |
| **number** | IEEE 754 double | `false` if zero |
| **boolean** | `true` or `false` | direct |
| **null** | Absence of value | always `false` |
| **node** | XML node (element, text, attr…) | always `true` |
| **sequence** | Ordered collection of any items | `false` if empty |
| **map** | String-keyed collection of sequences | always `true` |

All expressions return **sequences**. A single value is a sequence of length one. The empty sequence `()` is falsy.

---

## Standard Library

### Type Conversion

| Function | Signature | Description |
|---|---|---|
| `string(x)` | `any → string` | Converts to string. For nodes, returns the string value (concatenated text content). |
| `number(x)` | `any → number` | Parses a string or converts a boolean/number to float. Errors on invalid input. |
| `boolean(x)` | `any → boolean` | Truthiness check. |
| `typeOf(x)` | `any → string` | Returns `"string"`, `"number"`, `"boolean"`, `"node"`, `"map"`, `"null"`, or `"function"`. |

### Node Navigation

| Function | Signature | Description |
|---|---|---|
| `name(node)` | `node → string` | Local name of an element or attribute. Returns `""` for non-elements. |
| `attr(node, name)` | `(node, string) → string` | Attribute value by name. Returns `""` if absent. |
| `text(node, deep?)` | `(node, bool?) → string` | String value of a node. `deep=true` (default) concatenates all descendant text; `deep=false` returns only direct text children. |
| `children(node)` | `node → seq` | All child nodes (elements, text, comments, PIs). |
| `elements(node, name?)` | `(node, string?) → seq` | Child elements, optionally filtered by name. |
| `copy(node)` | `node → node` | Deep copy of a node. |

### Sequence Operations

| Function | Signature | Description |
|---|---|---|
| `count(seq)` | `seq → number` | Number of items in the sequence. |
| `empty(seq)` | `seq → boolean` | `true` if the sequence is empty. |
| `head(seq)` | `seq → seq` | First item of the sequence, or empty if empty. |
| `tail(seq)` | `seq → seq` | All items except the first, or empty if empty. |
| `last(seq)` | `seq → item` | Last item of the sequence. With no argument inside a `for`, returns the total count. |
| `distinct(seq)` | `seq → seq` | Removes duplicates, preserving first occurrence order. Equality by string value. |
| `sort(seq, keyFn?)` | `(seq, fn?) → seq` | Sorts items by string value, or by applying a key function. |
| `concat(a, b)` | `(seq, seq) → seq` | Concatenates two sequences. |
| `seq(a, b, ...)` | `(any...) → seq` | Concatenates any number of arguments into a single sequence. |
| `sum(seq)` | `seq → number` | Sum of numeric items in the sequence. |
| `position()` | `→ number` | 1-based position of the current item in a `for` loop. |

### Map Operations

| Function | Signature | Description |
|---|---|---|
| `index(seq, keyFn)` | `(seq, fn) → map` | Groups items by key function into a map. Each key maps to a sequence of items with that key. |
| `lookup(map, key)` | `(map, string) → seq` | Looks up a key in a map, returning its sequence (or empty if absent). |
| `groupBy(seq, keyFn)` | `(seq, fn) → seq` | Groups items preserving insertion order. Returns a sequence of maps, each with `"key"` and `"items"` entries. |

### Dispatch

| Function | Signature | Description |
|---|---|---|
| `apply(seq, ruleset?)` | `(seq, string?) → seq` | Applies rules from the named ruleset (default `"main"`) to each item, first-match. Raises `XFDY0001` if an item matches no rule. |

---

## Examples

The `tests/fixtures/` directory contains 15 fully-worked examples. Each has an `input.xml`, `transform.xform`, and reference `expected.xml`.

### 1 — Basic Iteration and Function

**Input:**
```xml
<catalog>
  <item id="1"><name>Alpha</name><price>9.50</price></item>
  <item id="2"><name>Beta</name><price>12.00</price></item>
</catalog>
```

**Transform:**
```xform
xform version "2.0";

def itemToEntry(i) :=
  <entry id={string(i/@id)}>
    <title>{ i/name/text() }</title>
    <price currency="EUR">{ number(i/price/text()) }</price>
  </entry>;

<feed>{ for i in .//item return itemToEntry(i) }</feed>
```

**Output:**
```xml
<feed>
  <entry id="1"><title>Alpha</title><price currency="EUR">9.5</price></entry>
  <entry id="2"><title>Beta</title><price currency="EUR">12</price></entry>
</feed>
```

### 2 — Conditional Output

```xform
xform version "2.0";

<labels>{
  for p in .//product return
    <label id={string(p/@id)}>
      { if number(p/price/text()) > 20 then "expensive" else "cheap" }
    </label>
}</labels>
```

### 3 — Count and Boolean Attributes

```xform
xform version "2.0";

let u := .//user in
  <summary total={count(u)} empty={empty(u)} />
```

### 4 — Deep Text Extraction

```xform
xform version "2.0";

<allText>{ text(.) }</allText>
```

Collects all text content (including whitespace) from the entire document into a single element.

### 5 — Sort and Deduplication

```xform
xform version "2.0";

<tags>{
  for t in sort(distinct(.//tag/text())) return
    <tag>{ t }</tag>
}</tags>
```

### 6 — Element Name Introspection

```xform
xform version "2.0";

<names>{
  for e in elements(./*) return
    <n>{ name(e) }</n>
}</names>
```

### 7 — Identity Copy

```xform
xform version "2.0";

copy(/)
```

Copies the entire document without modification.

### 8 — Pattern Matching

```xform
xform version "2.0";

<out>{
  for n in ./*/* return
    match n :
      case <a>{x}</a> => <A>{ x }</A>;
      case <b>{x}</b> => <B>{ x }</B>;
      default         => <Other>{ string(n) }</Other>;
}</out>
```

### 9 — Filtered Iteration with Predicates

```xform
xform version "2.0";

<expensive>{
  for i in .//item[number(price/text()) > 10] return
    <name>{ i/name/text() }</name>
}</expensive>
```

### 10 — Boolean Short-Circuit

```xform
xform version "2.0";

<flags>{
  if (.//flag and .//missing)
    then "both"
    else "not-both"
}</flags>
```

### 11 — GroupBy with Nested Iteration

```xform
xform version "2.0";

def primaryKey(t)   := string(t/primary/text());
def secondaryKey(t) := string(t/secondary/text());
def groupKey(g)     := string(lookup(g, "key"));

<indexdoc>{
  for g in sort(groupBy(.//indexterm, primaryKey), groupKey) return
    for t in sort(lookup(g, "items"), secondaryKey) return
      seq(
        <primaryterm>{ t/primary/text() }</primaryterm>,
        <secondaryterm>{ t/secondary/text() }</secondaryterm>,
        <tertiaryterm>{ t/tertiary/text() }</tertiaryterm>
      )
}</indexdoc>
```

Groups index terms by primary key, sorts both groups and items, and outputs them in a flat structure. Uses function references (`primaryKey`, `secondaryKey`, `groupKey`) passed to `groupBy()` and `sort()`.

### 12 — Sequence Output with `seq()`

```xform
xform version "2.0";

for a in .//article return
  seq(
    <h3>{ text(a/title) }</h3>,
    for p in a/para return
      <p>{ text(p) }</p>
  )
```

`seq()` emits multiple items from a single `for` iteration without wrapping them in a container element.

### 13 — Attribute Access with Wildcards

```xform
xform version "2.0";

for s in .//skills/* return
  seq(string(s/@name), " ")
```

### 14 — Complex Table Generation

```xform
xform version "2.0";

<html>{
  <table border={1}>{
    for g in .//game return
      seq(
        <tr>{ seq(
          <td>Inning</td>,
          for i in g/innings/inning return <td>{ i/num/text() }</td>,
          <td>final</td>
        ) }</tr>,
        <tr>{ seq(
          <td><b>{ g/home/text() }</b></td>,
          for i in g/innings/inning return <td>{ i/home/runs/text() }</td>,
          <td>{ sum(g/innings/inning/home/runs/text()) }</td>
        ) }</tr>
      )
  }</table>
}</html>
```

---

## Error Reference

### Static Errors (raised at parse/compile time)

| Code | Meaning |
|---|---|
| `XFST0001` | Syntax error — unexpected token or malformed construct |
| `XFST0002` | Unbound prefix or unknown QName |
| `XFST0003` | Unknown function name |
| `XFST0004` | Import cycle detected |
| `XFST0005` | Unsupported XForm version (only `"2.0"` is supported) |

### Dynamic Errors (raised at evaluation time)

| Code | Meaning |
|---|---|
| `XFDY0001` | No matching case or rule for an item in `match` or `apply()` |
| `XFDY0002` | Type or conversion error (e.g., non-numeric string passed to `number()`) |
| `XFDY0003` | Node operation on an atomic value |
| `XFDY0004` | Invalid constructor — mismatched open and close tags |
| `XFDY0099` | Non-terminating recursion |

---

## Running Tests

The test suite validates the Python implementation and, optionally, each language implementation (Rust/TypeScript/Go/Swift) against `xsltproc`. All 15 fixture cases are tested for each enabled language.

### Prerequisites

```bash
# xsltproc (required for the reference XSLT comparison)
sudo apt-get install xsltproc          # Debian/Ubuntu
brew install libxslt                   # macOS

# Python test dependencies
pip install pytest lxml

# Build language binaries you want to test
cd xform-rs && cargo build --release
cd xform-ts && npm install && npm run build
cd xform-go && mkdir -p bin && go build -o bin/xform ./cmd/xform
cd xform-swift && swift build -c release -Xcc -fmodules-cache-path=/tmp/xform-swift-clang-cache
```

### Run Tests

```bash
# From the repository root
python -m pytest tests/ -v

# Limit transformation tests by language (comma-separated)
XF_TEST_LANGS=python,rust python -m pytest tests/ -v
XF_TEST_LANGS=python,ts python -m pytest tests/ -v
XF_TEST_LANGS=python,go python -m pytest tests/ -v
XF_TEST_LANGS=python,swift python -m pytest tests/ -v
```

Language tests are automatically skipped if the corresponding binary is not built.

### Makefile Targets

```bash
make build        # Build all language binaries (Rust/TS/Go/Swift)
make build-rust   # Build Rust binary
make build-ts     # Build TypeScript CLI
make build-go     # Build Go CLI
make build-swift  # Build Swift CLI
make test         # Run Python tests (builds required binaries first)
```

### Test Output

```
tests/test_transformations.py::test_xform_matches_xslt[case01] PASSED
tests/test_transformations.py::test_xform_matches_xslt[case02] PASSED
...
tests/test_transformations.py::test_rust_xform_matches_xslt[case01] PASSED
...
73 passed in 1.05s
```

### How Tests Work

Each fixture is compared using normalized XML:
1. The XML declaration (`<?xml ...?>`) is stripped
2. The output is wrapped in a synthetic root and parsed by ElementTree
3. Whitespace-only text nodes between elements are removed
4. The normalized form is compared between `xsltproc` output and XForm output

This ensures that insignificant formatting differences do not cause test failures.

---

## Repository Layout

```
xcut/
├── xform-transformations-2.0.md   Language specification
├── xform-transformations-1.0.md   Prior version specification (German)
├── EVAL.md                        Critical evaluation of both spec versions
├── README.md                      This file
│
├── xform/                         Python reference implementation
│   ├── __init__.py
│   ├── ast.py                     AST node dataclasses
│   ├── cli.py                     Command-line entry point
│   ├── eval.py                    Evaluator and built-in functions
│   ├── parser.py                  Lexer and recursive-descent parser
│   └── xmlmodel.py                XML parsing, serialization, tree model
│
├── xform-rs/                      Rust implementation
├── xform-ts/                      TypeScript implementation
├── xform-go/                      Go implementation
├── xform-swift/                   Swift implementation
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs                 Library crate root
│       ├── ast.rs                 AST enums and structs
│       ├── lexer.rs               Tokenizer
│       ├── parser.rs              Recursive-descent parser
│       ├── eval.rs                Evaluator and built-in functions
│       ├── xmlmodel.rs            XML tree model and serialization
│       └── bin/
│           └── xform.rs           CLI binary
│
├── tests/
│   ├── test_transformations.py    Main test suite (Python + language CLIs)
│   ├── test_parser.py             Parser unit tests
│   ├── test_xmlmodel.py           XML model unit tests
│   └── fixtures/
│       ├── case01/                Iteration and function definitions
│       │   ├── input.xml
│       │   ├── transform.xform
│       │   ├── transform.xsl      Equivalent XSLT (reference)
│       │   └── expected.xml
│       ├── case02/                Conditional output
│       ├── case03/                Let bindings and count/empty
│       ├── case04/                Deep text extraction
│       ├── case05/                Sort and distinct
│       ├── case06/                Element name introspection
│       ├── case07/                Identity copy of subtrees
│       ├── case08/                Pattern matching
│       ├── case09/                Path predicates
│       ├── case10/                Boolean short-circuit
│       ├── case11/                groupBy with nested iteration
│       ├── case12/                Full document copy
│       ├── case13/                seq() for multiple outputs
│       ├── case14/                Attribute access and wildcards
│       └── case15/                Complex table generation
│
└── .github/
    └── workflows/
        └── ci.yml                 GitHub Actions CI workflow
```

---

## Language Design Notes

### Why Not XSLT?

XSLT is powerful but verbose. Its template priority system is implicit, namespace handling is mandatory, and the XPath/XSLT split means transformation logic lives across two different languages.

XForm addresses these friction points:

| Concern | XSLT | XForm |
|---|---|---|
| Syntax | XML-based meta-language | First-class language with constructor syntax |
| Template dispatch | Priority rules + modes | Explicit `apply()` with named rulesets |
| Path expressions | Embedded XPath strings | Native path syntax |
| Variables | `<xsl:variable>` / `<xsl:param>` | `var`, `let`, function parameters |
| Iteration | `<xsl:for-each>` | `for x in seq return expr` |
| Conditions | `<xsl:choose>/<xsl:when>` | `if cond then a else b` |
| Functions | Separate XSLT function elements | `def fn(params) := expr;` |

### Sequence Semantics

Every XForm expression evaluates to a **sequence** — an ordered list of zero or more items. Path expressions naturally return sequences of nodes. Functions like `seq()` and `concat()` join sequences. Constructors flatten sequences of nodes into element content.

This uniform model means there is no distinction between "a value" and "a list of values" — an item is just a sequence of length one.

### Pattern Matching vs. Rules

XForm provides two dispatch mechanisms:

- **`match` expression** — inline, exhaustive dispatch over a known value. Best for local transformations where the cases are known at the point of use.

- **`rule`/`apply()`** — open-ended, ruleset-based dispatch. Rules are defined globally and composed by name. Best for document-wide recursive transformations (the XForm equivalent of XSLT's apply-templates).

### Functional Purity

XForm transformations are **side-effect-free and deterministic**. There is no mutation, no I/O within a transform, and no global state. The same input always produces the same output.

---

## Continuous Integration

The project uses GitHub Actions. On every push to `master` and on pull requests, the CI pipeline:

1. Installs `xsltproc`
2. Sets up Python 3.13
3. Installs `pytest` and `lxml`
4. Installs the stable Rust toolchain
5. Builds the Rust release binary (`cargo build --release`)
6. Runs the full test suite (`python -m pytest tests/ -v`)

The Rust binary build is cached via `Swatinem/rust-cache` to keep CI fast.

---

## Acknowledgements

Test fixtures 11–15 are adapted from the [GNOME libxslt test suite](https://gitlab.gnome.org/GNOME/libxslt), which provides a broad set of real-world XSLT transformation examples. See `tests/fixtures/SOURCES.md` for details.
