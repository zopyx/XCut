# XForm Transformations 2.1
**Editor's Draft (informal)** — Date: 2026-06-19

> Note: The name "XForm" remains close to W3C XForms. A unique namespace is RECOMMENDED, e.g. `urn:xform-t:2.1`.

## Abstract
This document specifies **XForm 2.1**, a declarative transformation language for XML documents. XForm combines XPath-like path expressions, expression semantics, pattern matching, and XML constructors into a compact, readable language for restructuring and generating XML. Version 2.1 addresses all grammar, semantics, security, and internationalization gaps found in the 2.0 specification and is fully implementable for the core profile.

## Status of This Document
This is an Editor's Draft and has no official W3C status.

## Table of Contents
1. Introduction
2. Conformance
3. Terms and Notation
4. Data Model
5. Language Overview
6. Lexical Structure
7. Grammar (EBNF)
8. Semantics
9. Pattern Matching and Rules
10. XML Constructors and Serialization
11. Standard Library
12. Modules and Namespaces
13. Error Handling
14. Security and Privacy
15. Internationalization
A. Reserved Words
B. Diff vs 2.0
C. XSLT Comparison
D. References

---

## 1. Introduction (informative)
XForm is designed for readable, predictable, composable XML transformations. XForm is functional (side-effect-free), deterministic, and testable.

Version 2.1 is a consolidation release. It adds no new features relative to 2.0; it closes every spec gap, resolves every grammar ambiguity, and hardens the security and internationalization models so that independent conforming implementations can be built from the specification alone.

---

## 2. Conformance (normative)

### 2.1 Keywords
The keywords **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** are normative. They are interpreted per RFC 2119.

### 2.2 Conformance Classes
A product can conform as:

1. **XForm Processor**
   - MUST parse, statically check, and evaluate XForm modules.
   - MUST implement the semantics in §8, pattern matching and rules in §9, and serialization in §10.
   - MUST implement the complete standard library in §11.
   - MUST report errors per §13.
   - MUST implement the security mitigations in §14.
   - MUST handle Unicode per §15.

2. **XForm Host Environment** (optional)
   - MAY define embedding APIs.
   - MUST provide the dynamic context interface (§8.2).

3. **XForm Module**
   - Conforms if it satisfies the grammar (§7) and has no static errors (§13).

### 2.3 Profiles (optional)
- **Core Profile**: full language as specified in this document.
- **Streaming Profile**: streamable subset (TBD in a future version).

### 2.4 Version Negotiation
A processor MUST accept the version string `"2.1"`. A processor MAY accept other version strings. If a processor encounters an unsupported version string, it MUST raise `XFST0005`.

---

## 3. Terms and Notation (normative)
- **Node**: node in the data model (§4).
- **Item**: Node or atomic value.
- **Sequence**: ordered sequence of Items (may be empty).
- **Context Item**: current item during evaluation.
- **QName**: `prefix:local` or `local`.

`{ expr }` denotes expression interpolation in constructors.

EBNF notation in §7 uses `|` for alternation, `[ X ]` for optional, `[ X ]*` for zero or more, and `[ X ]+` for one or more. Literal braces in constructors are shown as `"{"` and `"}"`.

---

## 4. Data Model (normative)

### 4.1 Node Types
Processors MUST support:
- DocumentNode
- ElementNode (QName, Attributes, Children)
- AttributeNode (QName, StringValue)
- CommentNode (StringValue)
- ProcessingInstructionNode (Target, StringValue)
- TextNode (StringValue)

For ElementNode and AttributeNode, the QName consists of an optional namespace URI and a local name. If no namespace URI is present, the node is in the default (empty) namespace.

### 4.2 Atomic Types
Processors MUST support: `string`, `number` (IEEE-754 double), `boolean`, `null`.
Processors SHOULD support: `date`, `time`, `dateTime`, `duration`.

For `number`, the special values `NaN`, `+Infinity`, `-Infinity`, `+0`, and `-0` exist per IEEE 754. Their string serialization is defined in §10.3.

### 4.3 Map Type
Processors MUST support `map` as an opaque container type:
- A map is a key/value store where keys are string values.
- Values are Sequences.
- Maps are created by the standard library functions `index()` and `groupBy()`.
- Maps are inspected via `lookup(map, key)`, `keys(map)`, and `mapSize(map)`.
- There is no map literal syntax.

### 4.4 Identity and Order
- Node identity MUST be stable per input document.
- Constructed nodes (from constructors or `copy()`) receive new identities.
- Node sequences from path expressions MUST be in document order unless specified.
- For sequences containing both source and constructed nodes, document order is defined only for source nodes; constructed nodes appear in construction order relative to each other and at the position implied by their insertion.

---

## 5. Language Overview (informative)
- Path expressions: `.//item`, `./name/text()`, `./@id`, `i/@id`.
- Constructors: `<entry id={...}>{ ... }</entry>`, `<empty/>`.
- Control: `if/then/else`, `for`, `let`.
- Pattern matching: `match expr: case <b>{x}</b> => ...`.
- Rules: `rule main match <item>{x}</item> = ...; apply(.//item)`.

---

## 6. Lexical Structure (normative)

### 6.1 Whitespace and Comments
- Whitespace (space, tab, CR, LF) separates tokens except inside string literals.
- Line comments start with `#` and run to end of line.
- In constructor content and in attribute value expressions, `#` is treated as literal text, not a comment.

### 6.2 Identifiers
`[A-Za-z_][A-Za-z0-9_-]*` (prefixes follow the same rule).

### 6.3 String Literals
Strings are in single or double quotes. Escapes: `\'`, `\"`, `\\`, `\n`, `\t`, `\r`, `\uXXXX`.

### 6.4 Token-Level Ambiguity Resolution
The token `text` is ambiguous between `TextConstructor` (`text{ expr }`) and a function call (`text(node, ...)`). A parser MUST disambiguate by looking ahead one token: `{` indicates a text constructor; `(` indicates a function call.

The token `apply` is similarly ambiguous: `apply(expr)` is a dispatch expression; `apply` may also appear as a function name in a call. Since `apply` is a reserved word (Appendix A), it cannot be used as a user-defined function name.

An `Identifier` at the start of a `PathExpr` is distinguished from a `FuncCall` by one-token lookahead: if `(` follows, it is a `FuncCall`; otherwise it is a `PathExpr`.

---

## 7. Grammar (EBNF) (normative)

```ebnf
Module        := [ PrologDecl ] [ NsDecl ]* [ ImportDecl ]*
                 [ FuncDecl | RuleDecl | VarDecl ]* [ Expr ] ;
PrologDecl    := "xform" "version" StringLiteral ";" ;
NsDecl        := "ns" StringLiteral "=" StringLiteral ";" ;
ImportDecl    := "import" StringLiteral [ "as" Prefix ] ";" ;

FuncDecl      := "def" QName "(" [ ParamList ] ")" ":=" Expr ";" ;
RuleDecl      := "rule" QName "match" Pattern ":=" Expr ";" ;
VarDecl       := "var" Identifier ":=" Expr ";" ;

ParamList     := Param [ "," Param ]* ;
Param         := Identifier [ ":" TypeRef ] [ ":=" Expr ] ;
TypeRef       := "string" | "number" | "boolean" | "null" | "map" | QName ;

Expr          := IfExpr | LetExpr | ForExpr | MatchExpr | ApplyExpr | OrExpr ;
IfExpr        := "if" Expr "then" Expr "else" Expr ;
LetExpr       := "let" Identifier ":=" Expr "in" Expr ;
ForExpr       := "for" Identifier "in" Expr [ "where" Expr ] "return" Expr ;
MatchExpr     := "match" Expr ":" [ CaseClause ]* [ DefaultClause ] ;
CaseClause    := "case" Pattern [ "where" Expr ] "=>" Expr ";" ;
DefaultClause := "default" "=>" Expr ";" ;
ApplyExpr     := "apply" "(" Expr [ "," Expr ] ")" ;

OrExpr        := AndExpr [ "or" AndExpr ]* ;
AndExpr       := EqExpr  [ "and" EqExpr ]* ;
EqExpr        := RelExpr [ ("=" | "!=") RelExpr ]* ;
RelExpr       := AddExpr [ ("<" | "<=" | ">" | ">=") AddExpr ]* ;
AddExpr       := MulExpr [ ("+" | "-") MulExpr ]* ;
MulExpr       := UnaryExpr [ ("*" | "div" | "mod") UnaryExpr ]* ;
UnaryExpr     := [ "-" | "not" ] Primary ;

Primary       := Literal | PathExpr | FuncCall | Constructor | "(" Expr ")" ;

PathExpr      := PathStart [ PathStep ]* ;
PathStart     := "." | "/" | ".//" | "//" | Identifier ;
PathStep      := ( "/" | "//" ) StepTest [ PredicateList ]
               | "." | ".." | "/@" NameTest | ".@" NameTest ;
StepTest      := NameTest | "*" | "text()" | "node()" | "comment()" | "pi()"
               | "/@*" | ".@*" ;
NameTest      := QName ;
PredicateList := "[" Expr "]" [ "[" Expr "]" ]* ;

FuncCall      := QName "(" [ ArgList ] ")" ;
ArgList       := Expr [ "," Expr ]* ;

Pattern       := ElemPattern | AttrPattern | Wildcard | TypedPattern ;
ElemPattern   := "<" QName [ AttrPatternPattern ]* ">" [ Pattern | "{" Identifier "}" ]* "</" QName ">" ;
AttrPatternPattern := QName "=" ( StringLiteral | "{" Identifier "}" ) ;
AttrPattern   := "@" QName ;
Wildcard      := "_" ;
TypedPattern  := "node()" | "element()" | "text()" | "comment()" | "pi()" ;

Constructor   := ElemConstructor | TextConstructor ;
ElemConstructor := "<" QName [ AttrConstructor ]*
                   ( "/>" | ">" [ Content ]* "</" QName ">" ) ;
AttrConstructor := QName "=" ( StringLiteral | "{" Expr "}" ) ;
TextConstructor := "text" "{" Expr "}" ;
Content       := Constructor | "{" Expr "}" | CharData ;
CharData      := [ Char ]+ ;
Char          := any Unicode codepoint except '<' and '{' and '>';
```

Notes:
- `:=` is used for assignment to eliminate ambiguity with equality.
- `PathStart` allows bound variables directly (e.g., `i/@id`).
- Attribute access allows `/@name` and `.@name` forms, plus wildcard forms `/@*` and `.@*`.
- `ElemConstructor` supports both self-closing (`<tag/>`) and explicit close (`<tag></tag>`) forms. The QName in the close tag MUST match the QName in the open tag; a mismatch raises `XFDY0004`.
- `AttrConstructor` supports both literal (`attr="value"`) and dynamic (`attr={expr}`) forms. Mixing is allowed within a single element.
- `PredicateList` uses `[ Expr ]` notation; consecutive predicates `[E1][E2]` are evaluated left to right.
- Wildcard attribute access (`/@*`, `.@*`) returns all attribute nodes of the context element.
- `Char` excludes `>` only in the context of `CharData` that immediately precedes a closing tag `</`. All three characters (`<`, `{`, `>`) are excluded from CharData to permit unambiguous parsing. Literal `<` or `{` in output must be produced via `text{...}` or string expressions.
- `ApplyExpr` is the grammar production for the `apply()` dispatch primitive. Its first argument is the sequence to dispatch; the optional second argument is a string naming the ruleset (defaults to `"main"`).
- `Pattern` productions define the syntax for `match` case arms and `rule` match patterns. `ElemPattern` supports nested element patterns via recursive `Pattern` references within its content. `AttrPatternPattern` is used within `ElemPattern` for attribute value matching/binding; standalone `AttrPattern` (`@qname`) is used outside element context.
- `TypedPattern` includes `element()` distinct from `node()` — `element()` matches only ElementNodes, while `node()` matches any Node type.

---

## 8. Semantics (normative)

### 8.1 Static Context
A processor MUST build a static context when loading a module:
- Namespace bindings (prefix -> URI)
- Function signatures (QName -> parameter count and types)
- Rule signatures (QName -> list of pattern/body pairs)
- Type info (if provided)
- Imports

### 8.2 Dynamic Context
During evaluation, the following MUST be available:
- `contextItem` (Item or empty)
- `variables` (Identifier -> Sequence)
- `functions` (QName -> implementation)
- `rules` (ruleset name -> list of patterns + bodies)
- `baseURI` (optional)

The rules map is populated at module load time. The `"main"` ruleset is always present, even if empty (resulting in `XFDY0001` for bare `apply()` calls).

### 8.3 Evaluation Rules
- Every `Expr` returns a `Sequence`.
- `if` evaluates the condition to boolean; only the selected branch is evaluated (lazy). Both `then` and `else` branches MUST be present.
- `let x := E1 in E2`: evaluate `E1` once, bind `x` in a scope that encompasses `E2`, then evaluate `E2`.
- `for x in S [where W] return E`: evaluate `S` to a sequence; for each item, if `W` is present and evaluates to `false`, skip the item; otherwise, bind `x` to the item, set iteration context (`position()`, `last()`), evaluate `E`; concatenate all results in iteration order.
- `and` / `or` MUST be short-circuiting.
- `not` negates the boolean coercion of its operand.
- Function calls are referentially transparent (no side effects).
- `apply(seq)` dispatches each item in `seq` through the `"main"` ruleset; equivalent to `apply(seq, "main")`.
- `apply(seq, name)` dispatches each item in `seq` through the ruleset identified by the string `name`. If no ruleset with that name exists, `XFDY0001` is raised.

### 8.4 Path Expressions
- `.` refers to the current context item.
- `/` refers to the root node of the current document (or empty if no node context).
- `.//` and `//` perform descendant-or-self selection starting from `.` and `/` respectively.
- If `PathStart` is an `Identifier`:
  - If it is bound in `variables`, it is the base sequence.
  - Otherwise it is treated as a child step from `.` (equivalent to `./Identifier`).
- Each path step evaluates against the current sequence; result order is document order.
- `..` selects the parent of each node in the current sequence.
- `text()` selects text node children; `node()` selects all child nodes.
- `comment()` selects comment children; `pi()` selects processing instruction children.
- `*` selects all element children (wildcard).
- `/@name` selects the `name` attribute of each node in context. `.@name` is equivalent.
- `/@*` and `.@*` select all attributes of each node in context.
- A `PathExpr` starting with an Identifier that is NOT bound in variables and NOT a known function name is treated as `./Identifier`. If this produces no results at evaluation time, an empty sequence is returned (no error).

### 8.5 Boolean Coercion
A sequence converts to boolean using the following ordered rules:
1. If the sequence is empty -> `false`.
2. If the sequence contains at least one Node -> `true`.
3. Otherwise (all atomic values): `false` if every atomic value is falsy (`false`, `0`, `""`, `null`), else `true`.

### 8.6 Default Parameter Values
When a parameter declaration includes a default value (`Param := Identifier [":" TypeRef] [":=" Expr]`), the default expression is evaluated at the call site using the call site's dynamic context. A default expression MUST NOT reference parameters declared after it in the same parameter list.

### 8.7 Recursion
Recursion is allowed. Processors SHOULD support tail-call optimization. A processor MUST detect non-terminating recursion through one of:
- A configurable recursion depth limit (default 10,000).
- A configurable evaluation step limit.
- Other implementation-defined termination detection.

When a limit is exceeded, a dynamic error `XFDY0099` is raised.

---

## 9. Pattern Matching and Rules (normative)

### 9.1 Pattern Forms
Processors MUST support:
1. **Element Pattern**: `<qname>{var}</qname>` — matches an ElementNode with QName `qname`. The content `{var}` binds the full child sequence (elements, text, comments, PIs) to `var`.
   - **Attribute-constrained element pattern**: `<qname attr="value">{var}</qname>` matches only when the attribute `attr` has the exact string value `"value"`.
   - **Attribute-binding element pattern**: `<qname attr={v}>{var}</qname>` matches when attribute `attr` is present (any value) and binds its string value to `v`.
   - Multiple attribute patterns may be specified on the same element pattern.
2. **Attribute Pattern**: `@qname` — matches an AttributeNode with QName `qname`.
3. **Wildcard**: `_` — matches any Item.
4. **Typed Pattern**: `node()` — matches any Node; `element()` — matches any ElementNode; `text()` — matches any TextNode; `comment()` — matches any CommentNode; `pi()` — matches any ProcessingInstructionNode.
5. **Nested Element Pattern** (MUST): `<a><b>{x}</b></a>` — matches an ElementNode named `a` that contains a child ElementNode named `b`. The match is **exact**: only descendants explicitly described in the pattern are consumed into bindings; extra siblings or children not described by the pattern are ignored for matching purposes but are present in the node. A nested pattern matches if all elements named in the pattern exist as descendants at the positions implied by the pattern structure.
6. **Guarded Case** (MUST): `case Pattern where Expr => Body;` — the pattern matches only if the `where` condition evaluates to `true` (under the bindings introduced by the pattern).

### 9.2 Match Semantics
- `match Expr:` evaluates `Expr`.
- If `Expr` yields a single item, it is matched once.
- If `Expr` yields multiple items, each is matched in order; results are concatenated.
- Cases are tested in source order against each item. The first matching `case` is selected; subsequent cases for the same item are not evaluated.
- If no `case` matches an item, the `default` clause is used. If there is no `default` clause, `XFDY0001` is raised.
- Pattern variables (bound by `{var}`) are in scope within the corresponding `=>` body and any `where` guard.

### 9.3 Rule Dispatch
`rule Name match Pattern := Expr;` defines a rule in the named ruleset. Multiple rules may share a ruleset name; they form the dispatch table for that ruleset.

`apply(seq, Name?)` applies rules to each item in `seq`:
- If `Name` is omitted, ruleset `"main"` is used.
- If `Name` is a string, the ruleset with that name is used.
- For each item, the first rule whose pattern matches (in source order) is selected and its body is evaluated.
- If no rule matches, `XFDY0001` is raised.

### 9.4 Built-in Default Rules
New in 2.1: Every ruleset automatically includes the following built-in rules, which have the lowest priority (evaluated after all user-defined rules in the ruleset):

| Priority | Rule | Description |
|---|---|---|
| Lowest | `rule * match element() := apply(children(.), ruleset-name);` | Recursively processes child elements |
| Lowest | `rule * match text() := .;` | Copies text nodes through |
| Lowest | `rule * match comment() := ();` | Strips comments |
| Lowest | `rule * match pi() := ();` | Strips processing instructions |
| Lowest | `rule * match _ := ();` | Catch-all: empty sequence for anything else |

Where `ruleset-name` is the name of the ruleset currently being dispatched. These built-in rules are implicitly present in every ruleset and can be overridden by user-defined rules in the same ruleset.

Processors MUST implement these built-in rules. A processor MAY provide a mechanism to disable them (e.g., a prolog declaration `builtins off;`), but the default behavior is to include them.

---

## 10. XML Constructors and Serialization (normative)

### 10.1 Constructor Semantics
An `ElemConstructor` creates a new element node:
- QName MUST be bound (or in the default namespace).
- Literal attributes (`attr="value"`) produce string-valued attribute nodes.
- Dynamic attributes (`attr={expr}`) evaluate the expression and string-coerce the result.
- Self-closing form `<tag/>` is equivalent to `<tag></tag>`.
- `{ Expr }` content inserts node items as children and atomic items as text nodes (string-coerced).
- CharData between elements is preserved; leading/trailing whitespace-only CharData is preserved by default.

`text{Expr}` creates a text node from `Expr`.

The closing tag QName MUST match the opening tag QName. A mismatch raises `XFDY0004`.

The `xml:space`, `xml:lang`, and `xml:id` attributes, when used in constructors, are treated as ordinary attributes during construction. Processors SHOULD propagate `xml:lang` to descendant nodes in the constructed tree following the XML specification's inheritance rules.

### 10.2 Copy Model
Processors MUST provide `copy(node, recurse:=true)` producing a controlled copy:
- `recurse:=true` (default): deep copy of the node and all descendants.
- `recurse:=false`: shallow copy — copies the node itself (and its attributes, if an element) but omits child nodes.

Copied nodes receive new node identities.

### 10.3 Serialization
Processors MUST output well-formed XML.

**XML Declaration**: A processor MUST emit an XML declaration (`<?xml version="1.0" encoding="UTF-8"?>`) at the start of output unless the host environment suppresses it. The encoding declaration reflects the actual output encoding.

**Escaping**:
- In text content: `&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`.
- In attribute values: additionally `"` → `&quot;` (for double-quoted attributes) or `'` → `&apos;` (for single-quoted attributes).
- `]]>` in text nodes MUST be escaped (e.g., as `]]&gt;`) or split across text nodes.

**Namespace declarations**: The processor MUST emit namespace declarations (`xmlns:prefix="URI"`) for prefixed QNames used in the output. Declarations SHOULD be placed on the outermost element where they are needed, unless the prefix is also used on ancestor elements introduced by the same constructor. A processor MUST NOT emit duplicate namespace declarations for the same prefix in the same scope.

**Number serialization**: `NaN` serializes as `NaN`, `+Infinity` as `INF`, `-Infinity` as `-INF`, `-0` as `0`. Finite numbers use standard decimal representation without unnecessary trailing zeros (e.g., `9.5` not `9.500`).

**Default namespace**: The empty prefix corresponds to no namespace URI. To place constructed elements in a non-empty namespace, use a prefixed QName.

---

## 11. Standard Library (normative — minimum set)

### 11.1 Type & Conversion
- `string(x)` -> string. For nodes, returns the string value (concatenated descendant text). For NaN, returns `"NaN"`; for Infinities, returns `"INF"` or `"-INF"`.
- `number(x)` -> number. Parses a string; returns `NaN` on failure (not `XFDY0002`). Booleans convert to 1.0 or 0.0. Empty sequences convert to `NaN`.
- `boolean(x)` -> boolean per §8.5.
- `typeOf(x)` -> string. Returns `"string"`, `"number"`, `"boolean"`, `"node"`, `"map"`, `"null"`, or `"function"`.

### 11.2 Navigation & Selection
- `name(node)` -> string. Local name of element or attribute. Returns `""` for non-element nodes and for nodes in the default namespace.
- `attr(node, qnameOrString)` -> string. Attribute value by name. Returns `""` if absent. No error.
- `text(node, deep:=true)` -> string. `deep=true` concatenates all descendant text; `deep=false` returns only direct text child content.
- `children(node)` -> Sequence(Node). All child nodes (elements, text, comments, PIs).
- `elements(node, nameTest?)` -> Sequence(ElementNode). Child elements, optionally filtered by QName.
- `copy(node, recurse:=true)` -> Node. Deep copy by default; shallow with `recurse:=false`. Raises `XFDY0003` if `node` is not a Node.

### 11.3 Sequences
- `count(seq)` -> number. Item count.
- `empty(seq)` -> boolean. True if empty.
- `distinct(seq)` -> Sequence. Removes duplicate items by string-value equality, preserving first occurrence.
- `sort(seq, keyFn?)` -> Sequence. Sorts by string value of items (default) or by the string value of `keyFn(item)`. Where `keyFn` is an Identifier naming a function, the function is applied to each item. The collation order is Unicode codepoint by default; locale-sensitive collation is processor-defined (see §15).
- `concat(seq1, seq2)` -> Sequence. Concatenation.
- `seq(a, b, ...)` -> Sequence. Variadic concatenation; any number of arguments.
- `head(seq)` -> Item or empty. First item. Returns empty sequence if `seq` is empty. No error.
- `tail(seq)` -> Sequence. All items except the first. Returns empty sequence if `seq` is empty or has one item. No error.
- `last(seq)` -> Item or empty. Last item. Returns empty sequence if `seq` is empty. No error.
- `sum(seq)` -> number. Sum of numeric items in the sequence; non-numeric items contribute zero. Empty sequences return zero.

### 11.4 Indexing and Grouping
- `index(seq, key:=exprOrFn)` -> map. Groups items by key. The key can be a string expression or a function reference. Returns a map where each key maps to a Sequence of items sharing that key.
- `lookup(map, key)` -> Sequence. Looks up a string key in a map. Returns empty sequence if absent.
- `keys(map)` -> Sequence(string). Returns the set of string keys in the map. Order is implementation-defined.
- `mapSize(map)` -> number. Returns the number of key entries in the map.
- `groupBy(seq, keyFn)` -> Sequence(map). Groups items preserving insertion order. Each returned map contains exactly two keys: `"key"` (the group key value as a sequence) and `"items"` (the grouped items as a sequence). Where `keyFn` is an Identifier naming a function, the function is applied to each item.

### 11.5 Iteration Context
Inside a `for` expression's `return` clause, the following are available:
- `position()` -> number. 1-based index of the current item within the iteration sequence.
- `last()` -> number. Total count of items in the iteration sequence.

Calling `position()` or `last()` outside a `for` expression's `return` clause (or any nested expression called from it) raises `XFDY0003`.

### 11.6 Dispatch
- `apply(seq, ruleset?)` -> Sequence. Applies rules from the named ruleset (default `"main"`) to each item in `seq`, first-match. Raises `XFDY0001` if an item matches no rule (including built-in rules, see §9.4).

### 11.7 String Functions
New in 2.1:

- `contains(str, search)` -> boolean. True if `search` is a substring of `str`.
- `startsWith(str, prefix)` -> boolean. True if `str` starts with `prefix`.
- `endsWith(str, suffix)` -> boolean. True if `str` ends with `suffix`.
- `substring(str, start, length?)` -> string. 1-indexed. If `length` is omitted, returns from `start` to end. If `start` or `length` is out of range, the result is clamped.
- `stringLength(str)` -> number. Length in Unicode codepoints.
- `upperCase(str)` -> string. Unicode-aware uppercase mapping.
- `lowerCase(str)` -> string. Unicode-aware lowercase mapping.
- `normalizeSpace(str)` -> string. Strips leading/trailing whitespace and collapses internal whitespace sequences to single spaces.
- `replace(str, pattern, replacement)` -> string. Replaces each non-overlapping occurrence of `pattern` (a literal string, NOT a regex) with `replacement`.
- `matches(str, pattern)` -> boolean. True if the literal string `pattern` occurs in `str`.

### 11.8 Named Function References
When a function name (Identifier or QName) appears as an argument to `sort()`, `groupBy()`, `index()`, or any higher-order function, it refers to the function defined in the current module or imported scope. The function must accept a single argument. If the function is not found, `XFST0003` is raised at module load time.

---

## 12. Modules and Namespaces (normative)

### 12.1 Namespace Declaration
```xform
ns "p" = "urn:example:product";
```
Processors MUST add these bindings to the static context. Namespace declarations are scoped to the entire module.

### 12.2 Imports
`import "iri" as p;` loads another module. The IRI is resolved relative to the importing module's base URI. Cyclic imports are a static error (`XFST0004`).

### 12.3 Visibility
Functions and rules are exported by default. A processor MAY add `export` / `private` modifiers to restrict or require visibility. If such modifiers are supported, they MUST be respected.

---

## 13. Error Handling (normative)

### 13.1 Error Classes
**Static**
- `XFST0001` Syntax error
- `XFST0002` Unbound prefix/QName
- `XFST0003` Unknown function
- `XFST0004` Import error / cycle
- `XFST0005` Unsupported version string

**Dynamic**
- `XFDY0001` No matching case / rule (in `match` or `apply()`)
- `XFDY0002` Type/conversion error
- `XFDY0003` Node operation on atomic value — includes: calling `name()`, `attr()`, `text()`, `children()`, `elements()`, `copy()` on a non-Node; calling `position()` or `last()` outside `for`; applying a path step to an atomic value.
- `XFDY0004` Invalid constructor (e.g., mismatched open/close tags, attribute on non-element)
- `XFDY0099` Non-terminating recursion or resource exhaustion

### 13.2 Error Format
Processors SHOULD report errors with these fields:
- `code` (string, e.g., `"XFDY0001"`)
- `message` (string, human-readable)
- `module` (IRI string, optional)
- `line` (number, 1-indexed)
- `column` (number, 1-indexed)
- `trace` (array of {function, line, column}, optional)

---

## 14. Security and Privacy (normative)

### 14.1 Side-Effect Freedom
XForm transformations are side-effect-free. They cannot write files, make network requests (except `import`), access environment variables, or produce observable state changes outside the transformation result.

### 14.2 XML Parser Security
Processors MUST configure their XML parser with the following mitigations enabled by default:

1. **DTD processing**: MUST be disabled entirely. The processor MUST NOT load or process internal or external DTD subsets.
2. **External entities**: MUST NOT be resolved. This includes general entities, parameter entities, and any entity declared in an external DTD.
3. **Entity expansion**: If entity expansion is supported (e.g., for the predefined XML entities `&lt;`, `&gt;`, `&amp;`, `&quot;`, `&apos;`), the processor MUST impose an expansion limit. A cumulative entity expansion exceeding 10 million characters or 100,000 expansions MUST raise `XFDY0099`.
4. **XInclude**: MUST NOT be processed.
5. **XML Base**: `xml:base` attributes MUST NOT affect resolution of relative IRIs.
6. **Maximum input size**: Processors MAY impose a configurable maximum input document size. If a document exceeds the limit, a static or dynamic error MUST be raised.

### 14.3 Import Security
- Processors MUST NOT resolve `import` IRIs that use schemes other than `file`, `http`, or `https` unless explicitly configured by the host environment.
- A **Safe Mode** SHOULD be provided that disables all network `import`s, resolving only `file` IRIs within a configurable root directory.
- Recursive `import` depth SHOULD be limited to a configurable maximum (default 100).

### 14.4 Resource Limits
Processors MUST enforce:
- **Recursion depth**: configurable limit, default 10,000. Exceeded → `XFDY0099`.
- **Output size**: configurable maximum output size (in bytes or nodes). Exceeded → `XFDY0099`.
- **Evaluation steps**: configurable limit on total expression evaluations. Exceeded → `XFDY0099`.
- **Map size**: a map returned by `index()` or `groupBy()` MAY be limited in key count; if exceeded, `XFDY0099` is raised.

### 14.5 Privacy
- Processors MUST NOT log or retain the content of input XML documents beyond what is necessary for error reporting.
- Host environments MUST NOT expose input document content to third parties.
- Error messages SHOULD NOT embed full XML content; they SHOULD use abbreviated descriptions.

### 14.6 Input Validation
Processors MUST validate that the input is well-formed XML. Malformed input MUST produce a static or dynamic error (implementation-defined whether detected at parse time or transform-evaluation time).

---

## 15. Internationalization (normative)

### 15.1 Character Encoding
Processors MUST support:
- UTF-8 encoding for source files and XML input.
- UTF-8 encoding for XML output.

Processors SHOULD support UTF-16.

### 15.2 Unicode Processing
- All string functions MUST be Unicode-aware, operating on codepoint boundaries.
- Surrogate pairs MUST be handled correctly: a surrogate pair counts as one codepoint for `stringLength()`, `substring()`, etc.
- Supplementary characters (codepoints above U+FFFF) MUST be supported in string literals, XML content, and all string operations.
- Processors MUST accept and preserve Unicode characters in identifiers, QNames, and element/attribute names as permitted by XML 1.0 (5th edition or later) or XML 1.1.

### 15.3 Unicode Normalization
- Processors MUST NOT perform Unicode normalization on input or output by default.
- A processor MAY provide an optional normalization function.
- String comparisons (`=`, `!=`, `distinct()`, `sort()`) operate on the codepoint representation as present in the document; no normalization is applied.

### 15.4 Collation
- The default sort order for `sort()` is Unicode codepoint order (binary comparison).
- Processors SHOULD support a mechanism to specify locale-sensitive collation (e.g., `sort(seq, keyFn, collation:="de")`). The exact mechanism is implementation-defined in this version.
- Processors SHOULD support case-insensitive collation as an option.

### 15.5 Language
- `xml:lang` attributes in input XML are preserved through `copy()`.
- Constructors that set `xml:lang` attributes produce elements with the specified language tag.
- No automatic language-based processing (e.g., language-specific sorting) is mandated.

### 15.6 Number Formatting
- `number()` conversion from strings accepts `-?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?` with optional leading whitespace.
- `string()` conversion of numbers uses no thousands separator and `.` as the decimal separator.

### 15.7 Date and Time
When a processor supports the date/time types (§4.2):
- `date` MUST conform to ISO 8601 `YYYY-MM-DD`.
- `time` MUST conform to ISO 8601 `hh:mm:ss[.sss][+/-hh:mm]`.
- `dateTime` MUST conform to ISO 8601 `YYYY-MM-DDThh:mm:ss[.sss][+/-hh:mm]`.
- `duration` MUST conform to ISO 8601 `P[nY][nM][nD][T[nH][nM][nS]]`.

---

## Appendix A: Reserved Words (normative)

`xform, version, import, as, ns, def, rule, var, let, in, for, where, return, if, then, else, match, case, default, and, or, not, div, mod, apply, builtins, off`

The words `text`, `position`, `last`, `head`, `tail`, `sum`, `copy`, `string`, `number`, `boolean`, `typeOf`, `name`, `attr`, `children`, `elements`, `count`, `empty`, `distinct`, `sort`, `concat`, `seq`, `index`, `lookup`, `keys`, `mapSize`, `groupBy`, `contains`, `startsWith`, `endsWith`, `substring`, `stringLength`, `upperCase`, `lowerCase`, `normalizeSpace`, `replace`, `matches` are reserved as standard library function names and MUST NOT be used as user-defined function names.

---

## Appendix B: Diff vs 2.0 (informative)

### Grammar
- Added `ApplyExpr` production: `apply ( Expr [, Expr] )`.
- Added self-closing element form `/ >` to `ElemConstructor`.
- Added literal attribute syntax: `AttrConstructor := QName "=" ( StringLiteral | "{" Expr "}" )`.
- Added wildcard attribute access `/@*` and `.@*` to `StepTest`.
- Added `where` guard clause to `CaseClause`: `"case" Pattern [ "where" Expr ] "=>" Expr ";"`.
- Clarified EBNF notation: `[ X ]*` for zero-or-more to avoid confusion with constructor interpolation `{ X }`.
- Added `apply` to Appendix A reserved words.

### Semantics
- Added ordered-rules presentation for boolean coercion (§8.5).
- Defined default parameter evaluation context (§8.6).
- Specified `head()`/`tail()`/`last()` behavior on empty sequences (§11.3): return empty sequence, no error.
- Specified `position()`/`last()` outside `for` raises `XFDY0003` (§11.5).
- Expanded `XFDY0003` to enumerate all node operations (§13.1).

### Pattern Matching & Rules
- Defined nested pattern match semantics as allowing extra siblings (§9.1).
- Restored guarded patterns (`case P where E =>`) from 1.0 with normative MUST status (§9.1 item 6).
- Added built-in default rules for `apply()` dispatch: identity recursion for elements, text copy, comment/PI stripping, empty catch-all (§9.4).
- Added `builtins off;` as optional processor feature.

### Standard Library
- Added `sum(seq)` to §11.3 (was missing from 1.0 and 2.0 normative text but present in tests).
- Added map introspection: `keys(map)`, `mapSize(map)` (§11.4).
- Formalized `groupBy()` return type: each map has exactly the string keys `"key"` and `"items"` (§11.4).
- Added string functions: `contains`, `startsWith`, `endsWith`, `substring`, `stringLength`, `upperCase`, `lowerCase`, `normalizeSpace`, `replace`, `matches` (§11.7).
- Added §11.8 defining named function references as arguments.

### Constructors & Serialization
- Defined XML declaration emission (§10.3).
- Defined namespace declaration placement (§10.3).
- Defined number serialization (NaN, Infinity) (§10.3).
- Defined `xml:space`, `xml:lang`, `xml:id` constructor semantics (§10.1).
- Clarified `copy()` signature consistency between §10.2 and §11.2.

### Data Model
- Clarified QName structure for ElementNode/AttributeNode (§4.1).
- Defined identity model for constructed vs. source nodes (§4.4).
- Clarified ordering for mixed source/constructed sequences (§4.4).

### Security
- Expanded §14 from 2 lines to a full normative section covering:
  - DTD/entity expansion mitigations
  - XXE prevention
  - XInclude prohibition
  - Import security (scheme whitelist, Safe Mode)
  - Resource limits (recursion, output, steps, map size)
  - Privacy (logging, error messages)
  - Input validation requirements

### Internationalization
- Expanded §15 from 2 lines covering:
  - UTF-8/UTF-16 encoding requirements
  - Surrogate pair and supplementary character handling
  - Unicode normalization position
  - Collation defaults and locale extension point
  - `xml:lang` semantics
  - Number formatting rules
  - Date/time ISO 8601 conformance

### Editorial
- §5 overview: fixed `match node:` → `match expr:`.
- Added Appendices B, C, and D to Table of Contents.
- Added §6.4 documenting token-level disambiguation (`text`, `apply`, `Identifier`/`FuncCall`).
- Added §2.4 version negotiation.
- All EBNF now maintains a clear visual separation between language tokens and meta-notation.

---

## Appendix C: XSLT Comparison (informative)

| Feature | XSLT 3.0 | XForm 2.1 |
|---|---|---|
| Recursive dispatch | `apply-templates` (priority-based) | `rule` + `apply()` (first-match) |
| Built-in identity rule | Yes (element → recurse, text → copy) | Yes (element → recurse, text → copy, PI/comment → strip) |
| Modes | Yes | Implicit via named rulesets |
| Attribute patterns | Yes (match attribute by name + value) | Name-only + guarded `where` |
| Nested patterns | Yes | Yes (allows extra siblings) |
| Map type | Yes (full literal + introspection) | Yes (library-created, `keys()` + `mapSize()`) |
| Short-circuit eval | Yes | Required |
| Recursion | Yes (processors often TCO) | Permitted, limits mandated |
| String library | Extensive (regex, translate, format) | Basic 11 functions |
| External document loading | `document()`, `doc()`, `collection()` | Not in core; import only |
| Default parameters | Yes | Yes (call-site eval context) |
| Higher-order functions | Yes (XSLT 3.0) | Function references (§11.8) |
| Streaming | Yes (XSLT 3.0) | Stub (TBD) |
| Security hardening | Processor-dependent | Mandatory mitigations (§14) |
| XML declaration control | `xsl:output` | Always emitted (host-suppressible) |

---

## Appendix D: References (informative)

- [RFC 2119] Key words for use in RFCs to Indicate Requirement Levels
- [XML 1.0] Extensible Markup Language (XML) 1.0 (Fifth Edition), W3C Recommendation
- [XML Names] Namespaces in XML 1.0 (Third Edition), W3C Recommendation
- [XPath 3.1] XML Path Language (XPath) 3.1, W3C Recommendation
- [XSLT 3.0] XSL Transformations (XSLT) Version 3.0, W3C Recommendation
- [XQuery 3.1] XQuery 3.1: An XML Query Language, W3C Recommendation
- [IEEE 754] IEEE Standard for Floating-Point Arithmetic
- [ISO 8601] Date and time — Representations for information interchange
- [Unicode] The Unicode Standard, latest version

---

## Appendix E: Conformance Checklist (normative)

A conforming XForm 2.1 Processor:

- [ ] Parses all productions in §7, including `apply()`, self-closing tags, and literal attributes.
- [ ] Implements the boolean coercion rules in the order specified by §8.5.
- [ ] Implements short-circuit `and`/`or` and lazy `if` evaluation (§8.3).
- [ ] Implements the built-in default rules in every ruleset (§9.4).
- [ ] Implements exact nested pattern matching per §9.1.
- [ ] Implements guarded case clauses (§9.1.6).
- [ ] Implements all functions in §11, including string functions (§11.7) and map introspection (§11.4).
- [ ] Returns empty sequence for `head()`, `tail()`, `last()` on empty sequences (§11.3).
- [ ] Raises `XFDY0003` for `position()`/`last()` outside `for` (§11.5).
- [ ] Emits XML declaration in serialized output (§10.3).
- [ ] Emits namespace declarations on the outermost element (§10.3).
- [ ] Disables DTD processing and external entity resolution (§14.2).
- [ ] Enforces at least one of the recursion/step/output limits (§14.4).
- [ ] Supports UTF-8 encoding for source and XML (§15.1).
- [ ] Handles surrogate pairs and supplementary characters correctly (§15.2).
- [ ] Reports errors with the format specified in §13.2.
- [ ] Raises `XFST0005` for unsupported version strings (§2.4).
