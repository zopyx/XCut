# XForm Transformations 2.1
**Editor's Draft (informal)** - Date: 2026-04-27

> Note: The name "XForm" remains close to W3C XForms. A unique namespace is RECOMMENDED, e.g. `urn:xform-t:2.1`.

## Abstract
This document specifies **XForm 2.1**, a declarative XML transformation language. XForm combines path expressions, functional expressions, pattern matching, recursive rule dispatch, and XML constructors into a compact language for restructuring and generating XML.

Version 2.1 tightens the 2.0 draft in the areas that most affect interoperability:

- complete normative grammar for load-bearing syntax
- formal `apply()` dispatch
- named arguments and default parameter semantics
- tighter XML data model and namespace rules
- clearer conformance requirements
- more explicit edge-case and error behavior

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

---

## 1. Introduction (informative)
XForm is designed for readable, predictable, composable XML transformations. XForm is functional (side-effect-free), deterministic except where explicitly implementation-defined, and testable.

XForm intentionally borrows ideas from XPath, XQuery, and XSLT, but it is not source-compatible with any of them. Where XForm differs materially, this specification defines XForm behavior explicitly.

---

## 2. Conformance (normative)

### 2.1 Keywords
The keywords **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, **MAY** are normative.

### 2.2 Conforming Processor
A conforming **XForm Processor** MUST:

1. parse XForm modules according to §6 and §7
2. enforce static constraints from §8, §9, §12, and §13
3. evaluate expressions according to §8 and §9
4. implement XML construction and serialization according to §10
5. implement the required standard library in §11
6. implement namespace and import handling in §12
7. report errors according to §13

### 2.3 Conforming Module
A conforming **XForm Module**:

1. satisfies the grammar in §7
2. violates no static rule defined by this specification
3. uses only required features unless it explicitly depends on implementation extensions

### 2.4 Host Environment
A conforming **XForm Host Environment** MAY define embedding APIs, but MUST expose the dynamic context defined in §8.2.

### 2.5 Profiles
This version defines one profile only:

- **Core Profile**: all normative features in this document

Future profiles, including streaming subsets, MUST be defined in separate specifications or later versions of this specification.

### 2.6 Extensions
Processors MAY provide extensions, but:

1. extensions MUST NOT change the behavior of conforming Core Profile programs
2. extension functions or syntax MUST be documented by the processor
3. processors SHOULD provide a mode in which unsupported extensions are reported as static errors

---

## 3. Terms and Notation (normative)

- **Item**: a Node or atomic value.
- **Sequence**: an ordered sequence of zero or more Items.
- **Node**: a node in the XForm data model.
- **Context Item**: the current item during evaluation.
- **Expanded QName**: a pair `(namespace-uri, local-name)` where `namespace-uri` MAY be empty.
- **Lexical QName**: source-form `prefix:local` or `local`.
- **Ruleset**: an ordered list of rules associated with a rule name.

`{ expr }` denotes expression interpolation in constructors.

Unless explicitly stated otherwise, all order-sensitive operations preserve input order.

---

## 4. Data Model (normative)

### 4.1 Overview
XForm operates on a tree-shaped XML-oriented data model plus atomic values and maps.

Processors MUST support:

- DocumentNode
- ElementNode
- AttributeNode
- TextNode
- CommentNode
- ProcessingInstructionNode
- atomic values
- map values

### 4.2 Node Properties

#### 4.2.1 DocumentNode
A DocumentNode has:

- ordered `children`
- optional `baseURI`
- stable `identity`

Children of a DocumentNode MAY include element, comment, and processing-instruction nodes.
If a DocumentNode is serialized, it MUST satisfy XML well-formedness constraints, including exactly one document element.

#### 4.2.2 ElementNode
An ElementNode has:

- `name` as an Expanded QName
- ordered `attributes`
- ordered `children`
- `namespaceBindings`
- optional `baseURI`
- `parent`
- stable `identity`

Child nodes MAY be elements, text, comments, or processing instructions.
Attribute nodes are not children.

#### 4.2.3 AttributeNode
An AttributeNode has:

- `name` as an Expanded QName
- `stringValue`
- `parent`
- stable `identity`

Attribute order is preserved by the data model but has no significance for equality or XML validity.

#### 4.2.4 TextNode
A TextNode has:

- `stringValue`
- `parent`
- stable `identity`

#### 4.2.5 CommentNode
A CommentNode has:

- `stringValue`
- `parent`
- stable `identity`

#### 4.2.6 ProcessingInstructionNode
A ProcessingInstructionNode has:

- `target`
- `stringValue`
- `parent`
- stable `identity`

### 4.3 Expanded Names and Namespaces
Element and attribute names are compared by Expanded QName, not by prefix spelling.

Default namespace rules:

1. the default namespace applies to element names
2. the default namespace does **not** apply to attribute names

### 4.4 Atomic Types
Processors MUST support:

- `string`
- `number` (IEEE-754 double)
- `boolean`
- `null`

Processors SHOULD support:

- `date`
- `time`
- `dateTime`
- `duration`

### 4.5 Map Type
Processors MUST support `map`:

- keys MUST be atomic values
- values MUST be Sequences
- lookup by a missing key MUST return the empty sequence

### 4.6 Identity, Order, and String Value

1. Node identity MUST be stable per input document and per constructed tree.
2. Node sequences produced by path expressions MUST be in document order unless otherwise specified.
3. The string value of:
   - a TextNode, CommentNode, or AttributeNode is its `stringValue`
   - an ElementNode is the concatenation of descendant text node string values in document order
   - a DocumentNode is the string value of its document element, if any; otherwise the concatenation of descendant text node string values

### 4.7 Deep Copy
`copy(node, recurse:=true)` MUST produce fresh node identities.
Expanded names, string values, and namespace bindings MUST be preserved.
If `recurse:=true`, all descendants and attributes MUST be copied.

---

## 5. Language Overview (informative)

- Path expressions: `.//item`, `./name/text()`, `./@id`, `i/@id`, `@id`
- Constructors: `<entry id={string(./@id)}>{ ... }</entry>`
- Control: `if/then/else`, `for`, `let`
- Pattern matching: `match .: case <b>{x}</b> => ...`
- Rules: `rule main match <item>{x}</item> := ...; apply(.//item)`

---

## 6. Lexical Structure (normative)

### 6.1 Character Model
Processors MUST accept Unicode source text.

### 6.2 Whitespace and Comments

1. Whitespace separates tokens except inside string literals and character data.
2. Line comments start with `#` and run to end of line.
3. Inside constructor character data, `#` is literal text and does not start a comment.

### 6.3 Names
`Identifier` and `Prefix` MUST follow the XML Namespaces `NCName` production.

`QName` is:

- `NCName`
- `NCName ":" NCName`

Reserved words listed in Appendix A MUST NOT be used where an `Identifier` is required.

### 6.4 String Literals
Strings MAY use single or double quotes.

Supported escapes:

- `\'`
- `\"`
- `\\`
- `\n`
- `\t`
- `\r`
- `\uXXXX`

### 6.5 Number Literals
Processors MUST support decimal numeric literals matching:

- integer form: `Digit+`
- decimal form: `Digit+ "." Digit+`

Support for exponent notation is optional.

### 6.6 Contextual Tokens
The token `text` is contextual:

- `text(` introduces the standard function `text(...)`
- `text{` introduces `TextConstructor`

An identifier followed by `(` is parsed as a function call unless it is the reserved word `apply`, which is parsed according to `ApplyExpr`.

---

## 7. Grammar (EBNF) (normative)

```ebnf
Module          := { PrologDecl | NsDecl | ImportDecl | FuncDecl | RuleDecl | VarDecl } [ Expr ] ;
PrologDecl      := "xform" "version" StringLiteral ";" ;
NsDecl          := "ns" StringLiteral "=" StringLiteral ";" ;
ImportDecl      := "import" StringLiteral [ "as" Prefix ] ";" ;

FuncDecl        := "def" QName "(" [ ParamList ] ")" ":=" Expr ";" ;
RuleDecl        := "rule" QName "match" Pattern ":=" Expr ";" ;
VarDecl         := "var" Identifier ":=" Expr ";" ;

ParamList       := Param { "," Param } ;
Param           := Identifier [ ":" TypeRef ] [ ":=" Expr ] ;
TypeRef         := "string" | "number" | "boolean" | "null" | "map" | QName ;

Expr            := IfExpr | LetExpr | ForExpr | MatchExpr | OrExpr ;
IfExpr          := "if" Expr "then" Expr "else" Expr ;
LetExpr         := "let" Identifier ":=" Expr "in" Expr ;
ForExpr         := "for" Identifier "in" Expr [ "where" Expr ] "return" Expr ;
MatchExpr       := "match" Expr ":" { CaseClause } [ DefaultClause ] ;
CaseClause      := "case" Pattern "=>" Expr ";" ;
DefaultClause   := "default" "=>" Expr ";" ;

OrExpr          := AndExpr { "or" AndExpr } ;
AndExpr         := EqExpr { "and" EqExpr } ;
EqExpr          := RelExpr { ("=" | "!=") RelExpr } ;
RelExpr         := AddExpr { ("<" | "<=" | ">" | ">=") AddExpr } ;
AddExpr         := MulExpr { ("+" | "-") MulExpr } ;
MulExpr         := UnaryExpr { ("*" | "div" | "mod") UnaryExpr } ;
UnaryExpr       := [ "-" | "not" ] Primary ;

Primary         := Literal | PathExpr | ApplyExpr | FuncCall | Constructor | "(" Expr ")" ;

PathExpr        := PathStart { PathStep } ;
PathStart       := "." | "/" | ".//" | "//" | Identifier | "@" NameTest ;
PathStep        := ( "/" | "//" ) StepTest [ PredicateList ]
                | "." | ".." | "/@" NameTest | ".@" NameTest ;
StepTest        := NameTest | "*" | "text()" | "node()" | "comment()" | "pi()" ;
NameTest        := QName ;
PredicateList   := { "[" Expr "]" } ;

ApplyExpr       := "apply" "(" Expr [ "," QName ] ")" ;

FuncCall        := QName "(" [ ArgList ] ")" ;
ArgList         := Argument { "," Argument } ;
Argument        := [ Identifier ":=" ] Expr ;

Constructor     := ElemConstructor | TextConstructor | CommentConstructor | PIConstructor ;
ElemConstructor := "<" QName { AttrConstructor } ">" { Content } "</" QName ">" ;
AttrConstructor := QName "=" "{" Expr "}" ;
TextConstructor := "text" "{" Expr "}" ;
CommentConstructor := "comment" "{" Expr "}" ;
PIConstructor   := "pi" "{" Expr "," Expr "}" ;
Content         := Constructor | "{" Expr "}" | CharData ;

Pattern         := WildcardPattern
                | TypedPattern
                | AttributePattern
                | ElementPattern ;
WildcardPattern := "_" ;
TypedPattern    := "node()" | "text()" | "comment()" | "pi()" | "document()" ;
AttributePattern := "@" QName [ "=" Literal ] ;
ElementPattern  := "<" QName { PatternAttr } ">" PatternBody "</" QName ">" ;
PatternAttr     := "@" QName | "@" QName "=" Literal ;
PatternBody     := "{" Identifier "}" | PatternChildren ;
PatternChildren := { ChildPattern } ;
ChildPattern    := ElementPattern | TypedPattern | StringLiteral ;

Literal         := StringLiteral | NumberLiteral | "true" | "false" | "null" ;
StringLiteral   := "'" { StringChar } "'" | "\"" { StringChar } "\"" ;
NumberLiteral   := Digit { Digit } [ "." Digit { Digit } ] ;

QName           := Identifier [ ":" Identifier ] ;
Prefix          := Identifier ;
Identifier      := NCName ;

CharData        := { Char } ;
Char            := any Unicode codepoint except '<' and '{' ;
StringChar      := any Unicode codepoint except the active quote and backslash, or an escape sequence ;
Digit           := "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
```

Grammar notes:

1. If `PathStart` is `@name`, it is shorthand for `./@name`.
2. In `ElementPattern`, the start-name and end-name MUST be lexically equal; otherwise `XFST0001`.
3. In `ElemConstructor`, the start-name and end-name MUST be lexically equal; otherwise `XFDY0004`.
4. `PatternChildren` matches the full child sequence, not a prefix.

---

## 8. Semantics (normative)

### 8.1 Static Context
The static context MUST include:

- namespace prefix bindings
- imported module aliases
- function signatures
- rule signatures
- variable declarations
- type annotations, if any

Unknown prefixes in QName-bearing positions are static errors (`XFST0002`).

### 8.2 Dynamic Context
The dynamic context MUST include:

- `contextItem` (Item or empty)
- `contextPosition` (optional iteration context)
- `contextSize` (optional iteration context)
- variable bindings
- function bindings
- rule bindings
- optional `baseURI`

### 8.3 General Evaluation Rules

1. Every `Expr` evaluates to a Sequence.
2. `if` evaluates only the selected branch.
3. `and` and `or` MUST short-circuit left-to-right.
4. `let x := E1 in E2` evaluates `E1` once, binds `x`, then evaluates `E2`.
5. `for x in S return E` evaluates `E` once per item of `S` in order and concatenates the results.

### 8.4 Function Calls and Arguments

#### 8.4.1 Argument Binding
Argument binding occurs as follows:

1. positional arguments bind from left to right
2. named arguments bind by parameter name
3. positional arguments MUST precede named arguments
4. a parameter MUST NOT receive more than one argument
5. a required parameter with no bound value is a dynamic error (`XFDY0008`)

#### 8.4.2 Default Parameter Evaluation
Default expressions are evaluated at call time in the callee's dynamic context after earlier parameters have been bound.

Therefore:

- a default expression MAY reference earlier parameters
- a default expression MUST NOT reference later parameters

Violations are static errors when detectable, otherwise dynamic errors (`XFDY0008`).

### 8.5 Path Expressions

1. `.` refers to `contextItem`.
2. `/` refers to the root node of the document containing `contextItem`; if `contextItem` is absent or not rooted in a document, the result is empty.
3. `.//` and `//` perform descendant-or-self traversal starting from `.` and `/` respectively.
4. If `PathStart` is an `Identifier`:
   - if the name is bound in variables, that variable's sequence is the base sequence
   - otherwise it is treated as `./Identifier`
5. If `PathStart` is `@name`, it is treated as `./@name`.
6. Each path step is evaluated against the current sequence.
7. Step results are flattened and returned in document order with duplicates removed by node identity.

Predicates:

- Each predicate is evaluated with the candidate item as `contextItem`.
- Predicate truth is determined by boolean coercion (§8.6).

### 8.6 Boolean Coercion
Boolean coercion is defined by the following ordered rules:

1. empty sequence -> `false`
2. sequence containing at least one Node -> `true`
3. otherwise, if all atomic values are falsy (`false`, `0`, `""`, `null`) -> `false`
4. otherwise -> `true`

This is intentionally not identical to XPath effective boolean value.

### 8.7 Recursion
Recursion is permitted.
Processors MAY optimize tail calls but are not required to.

If evaluation exceeds an implementation-defined recursion or resource limit, the processor MUST raise `XFDY0099`.

### 8.8 Rule Ordering Across Modules
For each ruleset name, effective rule order is:

1. rules declared in the current module, in textual order
2. then rules from imported modules, in import declaration order
3. each imported module contributes rules in its own effective order

This ordering is the basis for "first matching rule wins."

---

## 9. Pattern Matching and Rules (normative)

### 9.1 Pattern Semantics

#### 9.1.1 Wildcard Pattern
`_` matches any item.

#### 9.1.2 Typed Patterns

- `node()` matches any node
- `text()` matches any TextNode
- `comment()` matches any CommentNode
- `pi()` matches any ProcessingInstructionNode
- `document()` matches any DocumentNode

#### 9.1.3 Attribute Patterns
`@qname` matches an AttributeNode whose expanded name matches `qname`.

`@qname = Literal` additionally requires the attribute's string value to equal the literal string form.

#### 9.1.4 Element Patterns
An `ElementPattern` matches an ElementNode if:

1. the element expanded name matches the pattern name
2. every required `PatternAttr` is present and matches
3. the body matches according to §9.1.5 or §9.1.6

#### 9.1.5 Capture Body
`<a>{x}</a>` matches an element named `a` and binds `x` to the full child sequence, including text nodes, comments, and processing instructions.

#### 9.1.6 Nested Child Body
If the body is `PatternChildren`, matching is exact:

1. the number of child pattern items MUST equal the number of child nodes
2. each child pattern item MUST match the corresponding child node in order

String literal child patterns match a single TextNode with the same string value.

### 9.2 Match Expression Semantics

1. `match Expr:` evaluates `Expr`.
2. If the result contains one item, matching occurs once.
3. If the result contains multiple items, each item is matched in order and results are concatenated.
4. The first matching `case` is selected.
5. If no case matches, `default` MUST exist or `XFDY0001` is raised.

Bindings introduced by a matching pattern are visible only in the selected case expression.

### 9.3 Rule Dispatch
`rule Name match Pattern := Expr;` defines a rule in ruleset `Name`.

`apply(seq, Name?)` dispatches each item in `seq` as follows:

1. if `Name` is omitted, ruleset `main` is used
2. rules are considered in effective order (§8.8)
3. the first matching rule is selected
4. if no user-defined rule matches, built-in rules are considered

### 9.4 Built-in Rules
Built-in rules behave as if they were lowest-priority rules in every ruleset:

1. DocumentNode -> produce the concatenation of applying the same ruleset to its children in order
2. ElementNode -> produce a new element with the same expanded name and copied attributes, then apply the same ruleset to its children in order and use those results as the new children
3. AttributeNode -> `copy(.)`
4. TextNode -> `copy(.)`
5. CommentNode -> `copy(.)`
6. ProcessingInstructionNode -> `copy(.)`

### 9.5 Ruleset Errors

- unknown ruleset name -> `XFST0007`
- required ruleset argument not resolvable -> `XFST0007`

---

## 10. XML Constructors and Serialization (normative)

### 10.1 Constructor Semantics
An `ElemConstructor` creates a new ElementNode:

1. the constructor QName MUST be statically resolvable
2. attributes are evaluated left-to-right
3. attribute constructor results are string-coerced
4. content expressions are evaluated left-to-right

### 10.2 Content Normalization
When `{ Expr }` appears in element content:

1. node items of kind element, text, comment, and processing instruction are inserted as children
2. atomic items are converted to TextNodes
3. AttributeNodes in content are a dynamic error (`XFDY0005`)
4. adjacent text nodes created by construction MUST be merged

### 10.3 Attribute Rules

1. Attribute constructor names use element-namespace resolution rules only for explicit prefixes; unprefixed attributes are in no namespace.
2. Duplicate attributes on the same constructed element, as determined by Expanded QName, are a dynamic error (`XFDY0005`).

### 10.4 Text, Comment, and PI Constructors

- `text{Expr}` creates a TextNode from the string value of `Expr`
- `comment{Expr}` creates a CommentNode
- `pi{targetExpr, valueExpr}` creates a ProcessingInstructionNode

Invalid comment or PI content according to XML is a dynamic error (`XFDY0004`).

### 10.5 Copy Model
`copy(node, recurse:=true)` MUST follow §4.7.

### 10.6 Serialization
Processors MUST serialize output as well-formed XML.

Serialization MUST:

1. escape text and attribute values correctly
2. emit required namespace declarations
3. preserve expanded names, not prefix spellings
4. perform namespace fixup so that every prefixed element and attribute name has an in-scope declaration

If namespace fixup cannot produce a well-formed result, serialization MUST fail with `XFDY0004`.

---

## 11. Standard Library (normative)

### 11.1 Type and Conversion

- `string(x) -> string`
- `number(x) -> number`
- `boolean(x) -> boolean`
- `typeOf(x) -> string`

`number(x)` raises `XFDY0002` on invalid conversion.

### 11.2 Navigation and Inspection

- `name(node) -> string`
- `attr(node, qnameOrString) -> string`
- `text(node, deep:=true) -> string`
- `children(node) -> Sequence(Node)`
- `elements(node, nameTest?) -> Sequence(ElementNode)`
- `attributes(node) -> Sequence(AttributeNode)`
- `copy(node, recurse:=true) -> Node`

`attr(...)` returns the empty string if the attribute is absent.

### 11.3 Sequences

- `count(seq) -> number`
- `empty(seq) -> boolean`
- `distinct(seq) -> Sequence`
- `sort(seq, keyFn?) -> Sequence`
- `concat(seq1, seq2) -> Sequence`
- `seq(a, b, ...) -> Sequence`
- `head(seq) -> Item | empty`
- `tail(seq) -> Sequence`
- `last(seq) -> Item | empty`

Edge cases:

- `head(()) -> ()`
- `tail(()) -> ()`
- `last(()) -> ()`

### 11.4 Strings

- `contains(s, part) -> boolean`
- `startsWith(s, prefix) -> boolean`
- `endsWith(s, suffix) -> boolean`
- `substring(s, start, length?) -> string`
- `normalizeSpace(s) -> string`
- `replace(s, pattern, replacement) -> string`

String functions MUST be Unicode-aware.
Regex syntax for `replace` is implementation-defined unless a processor documents a specific profile.

### 11.5 Maps, Indexing, and Grouping

- `index(seq, key:=exprOrFn) -> map`
- `lookup(map, key) -> Sequence`
- `keys(map) -> Sequence`
- `mapSize(map) -> number`
- `groupBy(seq, keyFn) -> Sequence(map)`

For `groupBy`, each returned group map MUST contain exactly:

- key `"key"` -> the grouping key as a Sequence
- key `"items"` -> the grouped items as a Sequence

`lookup(map, missingKey)` MUST return the empty sequence.

### 11.6 Iteration Context

Inside `for`, the following are available:

- `position() -> number`
- `last() -> number`

Outside `for`, calling `position()` or zero-argument `last()` is a dynamic error (`XFDY0006`).

### 11.7 Built-in Dispatch
`apply(seq, Name?)` is a built-in dispatch form defined by §9.3 and §9.4, not a normal function, and it MUST NOT be shadowed.

---

## 12. Modules and Namespaces (normative)

### 12.1 Namespace Declarations
Namespace declarations use:

```xform
ns "p" = "urn:example:product";
```

Processors MUST add these bindings to the static XML namespace context.

### 12.2 Imports
`import "iri" as m;` loads another module.

Import rules:

1. cyclic imports are static errors (`XFST0004`)
2. module alias `m` occupies the module-alias namespace, not the XML namespace-prefix namespace
3. in function and ruleset references, a prefix MAY resolve as a module alias according to processor-defined import linkage

### 12.3 Visibility
Functions, variables, and rules are exported by default unless a processor-defined visibility system states otherwise.

### 12.4 QName Resolution

1. In element and attribute names, prefixes are resolved against the XML namespace context.
2. In function and ruleset references, prefixes are resolved first against imported module aliases, then against processor-defined builtin namespaces, if any.
3. Unprefixed function and ruleset names refer to the current module unless otherwise specified by the processor.

---

## 13. Error Handling (normative)

### 13.1 Static Errors

- `XFST0001` Syntax error
- `XFST0002` Unbound prefix or QName
- `XFST0003` Unknown function
- `XFST0004` Import error or import cycle
- `XFST0005` Unsupported version string
- `XFST0006` Reserved word used as identifier
- `XFST0007` Unknown ruleset

### 13.2 Dynamic Errors

- `XFDY0001` No matching `case`
- `XFDY0002` Type or conversion error
- `XFDY0003` Node operation on atomic value
- `XFDY0004` Invalid constructor or serialization state
- `XFDY0005` Invalid attribute construction
- `XFDY0006` Invalid iteration-context access
- `XFDY0008` Invalid argument binding
- `XFDY0099` Resource exhaustion or non-terminating recursion

### 13.3 Error Reporting
Processors SHOULD report:

- code
- message
- module IRI
- line and column
- optional stack trace

### 13.4 Error Determinism
If this specification defines an error for an observable condition, a conforming processor MUST raise that error class and MUST NOT silently continue with a different result.

---

## 14. Security and Privacy (normative/informative)

Processors MUST NOT load external resources except:

1. explicitly imported modules
2. host-provided documents or values made available through the embedding API

Processors SHOULD provide:

- a safe mode disabling network-based imports
- import scheme restrictions
- limits on recursion depth, memory growth, and total constructed nodes
- XML parser hardening against hostile inputs
- diagnostic redaction controls where source paths or imported URIs are sensitive

If a processor supports DTDs or external entities in host-provided XML inputs, that behavior MUST be documented.

---

## 15. Internationalization (normative/informative)

Processors MUST:

- handle Unicode source text
- handle Unicode XML content
- implement Unicode-aware string functions

QName, NCName, and namespace processing MUST follow XML namespace rules for Unicode characters.

---

## Appendix A: Reserved Words
`xform, version, import, as, ns, def, rule, var, let, in, for, where, return, if, then, else, match, case, default, and, or, not, div, mod, true, false, null, string, number, boolean, map, apply, text, comment, pi`

---

## Appendix B: Diff vs 2.0 (informative)

### Grammar and Parsing

- Added normative grammar for `Pattern`, `Literal`, `QName`, `Prefix`, and `StringLiteral`.
- Added named-argument grammar.
- Added top-level attribute path start `@name`.
- Reserved `apply` and defined it in grammar.
- Added comment and PI constructors.

### Semantics

- Defined argument binding and default parameter evaluation.
- Defined path result duplicate elimination by node identity.
- Defined stable rule ordering across imports.
- Recast boolean coercion as ordered rules.

### XML Data Model

- Added expanded-name semantics, namespace rules, parent/baseURI properties, string-value rules, and deep-copy requirements.

### Rule Dispatch

- Formalized `apply()` and added built-in identity-style rules.

### Standard Library

- Added `attributes`, `keys`, `mapSize`, and a minimum string library.
- Specified `head`, `tail`, `last`, `lookup`, `position`, and zero-argument `last` edge cases.

### Conformance and Errors

- Rewrote processor conformance requirements.
- Removed undefined streaming profile from normative scope.
- Expanded static and dynamic error taxonomy.
