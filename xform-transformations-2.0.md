# XForm Transformations 2.0
**Editor’s Draft (informal)** – Date: 2026-02-20

> Note: The name “XForm” remains close to W3C XForms. A unique namespace is RECOMMENDED, e.g. `urn:xform-t:2.0`.

## Abstract
This document specifies **XForm 2.0**, a declarative transformation language for XML documents. XForm combines XPath-like path expressions, expression semantics, pattern matching, and XML constructors into a compact, readable language for restructuring and generating XML. Version 2.0 addresses the grammar and semantics gaps found in 1.0 and introduces a minimal **recursive rule dispatch** model comparable to XSLT’s `apply-templates`.

## Status of This Document
This is an Editor’s Draft and has no official W3C status.

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

---

## 1. Introduction (informative)
XForm is designed for readable, predictable, composable XML transformations. XForm is functional (side-effect-free), deterministic, and testable.

---

## 2. Conformance (normative)

### 2.1 Keywords
The keywords **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, **MAY** are normative.

### 2.2 Conformance Classes
A product can conform as:

1. **XForm Processor**
   - MUST parse, statically check, and evaluate XForm modules.
   - MUST implement the semantics in §8 and serialization in §10.
   - MUST report errors per §13.

2. **XForm Host Environment** (optional)
   - MAY define embedding APIs.
   - MUST provide the dynamic context interface (§8.2).

3. **XForm Module**
   - Conforms if it satisfies the grammar and has no static errors.

### 2.3 Profiles (optional)
- **Core Profile**: full language.
- **Streaming Profile**: streamable subset (TBD).

---

## 3. Terms and Notation (normative)
- **Node**: node in the data model (§4).
- **Item**: Node or atomic value.
- **Sequence**: ordered sequence of Items (may be empty).
- **Context Item**: current item during evaluation.
- **QName**: `prefix:local` or `local`.

`{ expr }` denotes expression interpolation in constructors.

---

## 4. Data Model (normative)

### 4.1 Node Types
Processors MUST support:
- DocumentNode
- ElementNode (Name, Attributes, Children)
- AttributeNode (Name, StringValue)
- TextNode (StringValue)
- CommentNode (StringValue)
- ProcessingInstructionNode (Target, StringValue)

### 4.2 Atomic Types
Processors MUST support: `string`, `number` (IEEE-754 double), `boolean`, `null`.
Processors SHOULD support: `date`, `time`, `dateTime`, `duration`.

### 4.3 Map Type
Processors MUST support `map` as an atomic container type:
- A map is a key/value store where keys are atomic values.
- Values are Sequences.

### 4.4 Identity and Order
- Node identity MUST be stable per input document.
- Node sequences from path expressions MUST be in document order unless specified.

---

## 5. Language Overview (informative)
- Path expressions: `.//item`, `./name/text()`, `./@id`, `i/@id`.
- Constructors: `<entry id={...}>{ ... }</entry>`.
- Control: `if/then/else`, `for`, `let`.
- Pattern matching: `match node: case <b>{x}</b> => ...`.
- Rules: `rule main match <item>{x}</item> = ...; apply(.//item)`.

---

## 6. Lexical Structure (normative)

### 6.1 Whitespace and Comments
- Whitespace separates tokens except inside strings.
- Line comments start with `#` and run to end of line.
- In constructor content, `#` is treated as literal text, not a comment.

### 6.2 Identifiers
`[A-Za-z_][A-Za-z0-9_-]*` (prefixes follow the same rule).

### 6.3 String Literals
Strings are in single or double quotes. Escapes: `\'`, `\"`, `\\`, `\n`, `\t`, `\r`, `\uXXXX`.

---

## 7. Grammar (EBNF) (normative)

```ebnf
Module        := { PrologDecl | NsDecl | ImportDecl | FuncDecl | RuleDecl | VarDecl } [ Expr ] ;
PrologDecl    := "xform" "version" StringLiteral ";" ;
NsDecl        := "ns" StringLiteral "=" StringLiteral ";" ;
ImportDecl    := "import" StringLiteral [ "as" Prefix ] ";" ;

FuncDecl      := "def" QName "(" [ ParamList ] ")" ":=" Expr ";" ;
RuleDecl      := "rule" QName "match" Pattern ":=" Expr ";" ;
VarDecl       := "var" Identifier ":=" Expr ";" ;

ParamList     := Param { "," Param } ;
Param         := Identifier [ ":" TypeRef ] [ ":=" Expr ] ;
TypeRef       := "string" | "number" | "boolean" | "null" | "map" | QName ;

Expr          := IfExpr | LetExpr | ForExpr | MatchExpr | OrExpr ;
IfExpr        := "if" Expr "then" Expr "else" Expr ;
LetExpr       := "let" Identifier ":=" Expr "in" Expr ;
ForExpr       := "for" Identifier "in" Expr [ "where" Expr ] "return" Expr ;
MatchExpr     := "match" Expr ":" { CaseClause } [ DefaultClause ] ;
CaseClause    := "case" Pattern "=>" Expr ";" ;
DefaultClause := "default" "=>" Expr ";" ;

OrExpr        := AndExpr { "or" AndExpr } ;
AndExpr       := EqExpr  { "and" EqExpr } ;
EqExpr        := RelExpr { ("=" | "!=") RelExpr } ;
RelExpr       := AddExpr { ("<" | "<=" | ">" | ">=") AddExpr } ;
AddExpr       := MulExpr { ("+" | "-") MulExpr } ;
MulExpr       := UnaryExpr { ("*" | "div" | "mod") UnaryExpr } ;
UnaryExpr     := [ "-" | "not" ] Primary ;

Primary       := Literal | PathExpr | FuncCall | Constructor | "(" Expr ")" ;

PathExpr      := PathStart { PathStep } ;
PathStart     := "." | "/" | ".//" | "//" | Identifier ;
PathStep      := ( "/" | "//" ) StepTest [ PredicateList ]
              | "." | ".." | "/@" NameTest | ".@" NameTest ;
StepTest      := NameTest | "*" | "text()" | "node()" | "comment()" | "pi()" ;
NameTest      := QName ;
PredicateList := { "[" Expr "]" } ;

FuncCall      := QName "(" [ ArgList ] ")" ;
ArgList       := Expr { "," Expr } ;

Constructor   := ElemConstructor | TextConstructor ;
ElemConstructor := "<" QName { AttrConstructor } ">" { Content } "</" QName ">" ;
AttrConstructor := QName "=" "{" Expr "}" ;
TextConstructor := "text" "{" Expr "}" ;
Content       := Constructor | "{" Expr "}" | CharData ;

CharData      := { Char } ;
Char          := any Unicode codepoint except '<' and '{' ;
```

Notes:
- `:=` is used for assignment to eliminate ambiguity with equality.
- `PathStart` allows bound variables directly (e.g., `i/@id`).
- Attribute access allows `/@name` and `.@name` forms.

---

## 8. Semantics (normative)

### 8.1 Static Context
A processor MUST build a static context when loading a module:
- Namespace bindings (prefix -> URI)
- Function signatures
- Rule signatures (name -> pattern list)
- Type info (if provided)
- Imports

### 8.2 Dynamic Context
During evaluation, the following MUST be available:
- `contextItem` (Item or empty)
- `variables` (Identifier -> Sequence)
- `functions` (QName -> implementation)
- `rules` (QName -> list of patterns + bodies)
- `baseURI` (optional)

### 8.3 Evaluation Rules
- Every `Expr` returns a `Sequence`.
- `if` evaluates the condition to boolean; only the selected branch is evaluated (lazy).
- `let x := E1 in E2`: evaluate `E1` once, bind `x`, then evaluate `E2`.
- `for x in S return E`: evaluate `E` for each item in `S` in order; concatenate results.
- `and` / `or` MUST be short-circuiting.

### 8.4 Path Expressions
- `.` refers to the current context item.
- `/` refers to the root node of the current document (or empty if no node context).
- `.//` and `//` perform descendant-or-self selection starting from `.` and `/` respectively.
- If `PathStart` is an `Identifier`:
  - If it is bound in `variables`, it is the base sequence.
  - Otherwise it is treated as a child step from `.` (equivalent to `./Identifier`).
- Each path step evaluates against the current sequence; result order is document order.

### 8.5 Boolean Coercion
A sequence converts to boolean as:
- empty -> `false`
- contains at least one Node -> `true`
- otherwise: `false` only if all atomic values are falsy (`false`, `0`, `""`, `null`), else `true`.

### 8.6 Recursion
Recursion is allowed. Processors MAY optimize tail calls but are not required to. Non-terminating recursion results in a dynamic error (`XFDY0099`) or implementation-defined termination.

---

## 9. Pattern Matching and Rules (normative)

### 9.1 Pattern Forms
Processors MUST support:
1. **Element Pattern**: `<qname>{var}</qname>`
2. **Attribute Pattern**: `@qname` (matches attribute nodes)
3. **Wildcard**: `_` matches any item
4. **Typed Pattern**: `node()`, `text()`, `comment()`
5. **Nested Pattern** (MUST): `<a><b>{x}</b></a>`

`{var}` binds the full child sequence of the matched element, including text nodes.

### 9.2 Match Semantics
- `match Expr:` evaluates `Expr`.
- If `Expr` yields a single item, it is matched once.
- If `Expr` yields multiple items, each is matched in order; results are concatenated.
- The first matching `case` is selected; otherwise `default` MUST exist or `XFDY0001` is raised.

### 9.3 Rule Dispatch
`rule Name match Pattern := Expr;` defines a rule in ruleset `Name`.
`apply(seq, Name?)` applies rules to each item in `seq`:
- If `Name` is omitted, ruleset `main` is used.
- For each item, the first rule whose pattern matches is selected.
- If no rule matches, `XFDY0001` is raised.

This provides recursive dispatch comparable to XSLT `apply-templates`.

---

## 10. XML Constructors and Serialization (normative)

### 10.1 Constructor Semantics
An `ElemConstructor` creates a new element node:
- QName MUST be bound (or default namespace).
- Attributes are evaluated in order; values are string-coerced.
- `{ Expr }` content inserts node items as children and atomic items as text nodes.

`text{Expr}` creates a text node from `Expr`.

### 10.2 Copy Model
Processors MUST provide `copy(node, recurse:=true)` producing a deep copy.

### 10.3 Serialization
Processors MUST output well-formed XML and escape text/attributes correctly.
Namespace declarations MUST be emitted for prefixed QNames.

---

## 11. Standard Library (normative – minimum set)

### 11.1 Type & Conversion
- `string(x)` -> string
- `number(x)` -> number (error on failure)
- `boolean(x)` -> boolean
- `typeOf(x)` -> string

### 11.2 Navigation & Selection
- `name(node)` -> string
- `attr(node, qnameOrString)` -> string (empty if absent)
- `text(node, deep:=true)` -> string (deep concatenation if `deep=true`, direct text children if `false`)
- `children(node)` -> Sequence(Node)
- `elements(node, nameTest?)` -> Sequence(ElementNode)
- `copy(node, recurse:=true)` -> Node

### 11.3 Sequences
- `count(seq)`
- `empty(seq)`
- `distinct(seq)`
- `sort(seq, keyFn?)`
- `concat(seq1, seq2)`
- `seq(a, b, ...)` (variadic concatenation)
- `head(seq)` / `tail(seq)` / `last(seq)`

### 11.4 Indexing and Grouping
- `index(seq, key:=exprOrFn)` -> map(keyValue -> Sequence(items))
- `lookup(map, key)` -> Sequence
- `groupBy(seq, keyFn)` -> Sequence(map{key, items})

### 11.5 Iteration Context
Inside `for`, the following are available:
- `position()` -> 1-based index
- `last()` -> last index

---

## 12. Modules and Namespaces (normative)

### 12.1 Namespace Declaration
```xform
ns "p" = "urn:example:product";
```
Processors MUST add these bindings to the static context.

### 12.2 Imports
`import "iri" as p;` loads another module. Cyclic imports are a static error (`XFST0004`).

### 12.3 Visibility
Functions and rules are exported by default. A processor MAY add `export` / `private` modifiers.

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
- `XFDY0001` No matching case / rule
- `XFDY0002` Type/conversion error
- `XFDY0003` Node operation on atomic value
- `XFDY0004` Invalid constructor (e.g., mismatched end tag)
- `XFDY0099` Non-terminating recursion

### 13.2 Error Format
Processors SHOULD report: code, message, module IRI, line/column, optional stack trace.

---

## 14. Security and Privacy (normative/informative)
- XForm is side-effect-free. Processors MUST not load external resources unless explicitly imported.
- A Safe Mode SHOULD disable network imports.

---

## 15. Internationalization (normative/informative)
- Processors MUST handle Unicode source and XML.
- String functions MUST be Unicode-aware.

---

## Appendix A: Reserved Words
`xform, version, import, as, ns, def, rule, var, let, in, for, where, return, if, then, else, match, case, default, and, or, not, div, mod`

---

## Appendix B: Diff vs 1.0 (informative)

### Grammar
- Added `NsDecl` to module grammar; `ns` is now a real declaration.
- Added `TypeRef` definition and default parameter syntax in `Param`.
- Added `CharData` and `TextConstructor` productions.
- Added `Identifier` as `PathStart` (fixes `i/@id`, `i/price` examples).
- Added `.@name` attribute shorthand in `PathStep`.
- Changed assignment from `=` to `:=` to remove ambiguity with equality.
- `AttrConstructor` now uses `QName` instead of `Identifier`.
- Introduced `var` for module-level variables to avoid `let` ambiguity.

### Semantics
- Explicit evaluation rules for path expressions, including variable path starts.
- Defined `.`/`/`/`.//`/`//` precisely.
- Clarified boolean coercion for mixed node/atomic sequences.
- Required short-circuit evaluation for `and`/`or` and laziness for `if` branches.
- Added recursion semantics and error for non-terminating recursion.

### Pattern Matching & Rules
- Added attribute patterns and nested element patterns.
- Defined binding semantics for `{var}` as full child sequence (including text).
- Defined `match` on sequences (item-wise matching).
- Introduced `rule` + `apply()` for recursive dispatch (XSLT-like behavior).

### Standard Library
- `attr()` now returns string consistently (no union type).
- `text(node, deep:=true)` separates deep text from direct text.
- Added `concat`, `seq`, `head`, `tail`, `last`, `position()` and `last()`.
- Defined `map` as a data model type (used by `index`/`lookup`).

### Namespaces and Imports
- Clarified `ns` declaration and how it feeds static context.
- `import ... as p` is for module aliasing; namespace binding is separate.

### Error Handling
- Added `XFST0005` for unsupported version.
- Added `XFDY0099` for non-terminating recursion.

### Misc
- Clarified that `#` comments are not recognized inside constructor content.
- Fixed examples to use `:=` and attribute constructors `attr={expr}`.

---

## Appendix C: Change Log (by Section) (informative)

### §2 Conformance
- Added `XFST0005` (unsupported version) and `XFDY0099` (non-terminating recursion) to error taxonomy in §13.

### §4 Data Model
- Added `map` as a first-class atomic container type to support `index()`/`lookup()`.

### §6 Lexical Structure
- Clarified that `#` comments are not recognized inside constructor content (treated as text).

### §7 Grammar
- Added `NsDecl` (`ns "p" = "uri";`).
- Added `TypeRef` and default parameter syntax in `Param`.
- Added `CharData` and `TextConstructor`.
- Added `Identifier` as `PathStart` (variable path starts).
- Added `.@name` attribute shorthand in `PathStep`.
- Changed assignment operator from `=` to `:=`.
- Added `var` for module-level bindings to remove `let` ambiguity.
- `AttrConstructor` now accepts `QName`.

### §8 Semantics
- Added explicit path evaluation rules (context item, root, descendant-or-self).
- Clarified boolean coercion for mixed sequences.
- Required short-circuiting for `and`/`or` and laziness for `if` branches.
- Added recursion semantics and error handling.

### §9 Pattern Matching & Rules
- Added attribute patterns and nested element patterns.
- Defined `{var}` binding as full child sequence (including text).
- Defined `match` over sequences (item-wise).
- Introduced `rule` + `apply()` for recursive dispatch.

### §10 Constructors & Serialization
- Added `text{Expr}` constructor.
- Clarified QName/namespace requirements for element and attribute constructors.

### §11 Standard Library
- Normalized `attr()` return type to `string` (empty if absent).
- Split `text()` into `text(node, deep:=true)`.
- Added `concat`, `seq`, `head`, `tail`, `last`, `position()` and `last()` in `for`.
- Defined `map`-based return types for `index()` and `groupBy()`.

### §12 Modules & Namespaces
- Clarified `ns` as namespace declaration and `import ... as p` as module aliasing.

### §13 Error Handling
- Added `XFST0005` and `XFDY0099`.
