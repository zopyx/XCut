# XForm Transformations 1.0  
**W3C Editor’s Draft (informell)** – Stand: 2026-02-20  

> Hinweis: Der Name „XForm“ ist nah an „XForms“ (W3C). Für eine echte W3C-Einreichung wäre ein eindeutigerer Name/Namespace sinnvoll, z. B. `urn:xform-t:1.0`.

## Abstract
Dieses Dokument spezifiziert **XForm**, eine deklarative Transformationssprache für XML-Dokumente. XForm kombiniert **Pfadausdrücke** (XPath-ähnlich), **Ausdruckssemantik**, **Pattern Matching** und **XML-Konstruktoren** zu einer kompakten, leicht lesbaren Sprache für Umstrukturierung, Extraktion, Normalisierung und Generierung von XML. XForm ist so entworfen, dass typische Transformationsaufgaben ohne die Komplexität template-basierter Sprachen gelöst werden können.

## Status of This Document
Dieses Dokument ist ein **Editor’s Draft** und hat keinen offiziellen W3C-Status. Es ist als Spezifikation im Stil eines W3C-Dokuments formuliert (Normativität, Konformitätsklassen, Verarbeitungmodell). Implementationshinweise sind als *informativ* gekennzeichnet.

## Inhaltsverzeichnis
1. Einleitung  
2. Konformität  
3. Begriffe und Notation  
4. Datenmodell  
5. Sprachüberblick  
6. Lexikalische Struktur  
7. Grammatik (EBNF)  
8. Semantik  
9. Pattern Matching  
10. XML-Konstruktoren und Serialisierung  
11. Standardbibliothek  
12. Module und Namespaces  
13. Fehlerbehandlung  
14. Sicherheits- und Datenschutzaspekte  
15. Internationalisierung  
A. Vollständige Grammatik (EBNF)  
B. Reservierte Wörter  

---

## 1. Einleitung (informativ)
XForm adressiert drei praktische Probleme klassischer XML-Transformationen:

- **Lesbarkeit:** Transformationen sollen wie „Daten umformen“ wirken, nicht wie ein Meta-Programm in XML-Syntax.  
- **Vorhersagbarkeit:** Keine impliziten Prioritäten/Modi; Auswertung ist klar definiert.  
- **Komponierbarkeit:** Funktionen, Module, Pattern Matching und Indexe sind integraler Bestandteil.

XForm ist **funktional** (side-effect-frei), deterministisch und gut testbar.

---

## 2. Konformität (normativ)

### 2.1 Schlüsselwörter
Die Schlüsselwörter **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, **MAY** sind als normative Anforderungen zu verstehen.

### 2.2 Konformitätsklassen
Ein Produkt kann zu XForm konform sein als:

1. **XForm Processor**  
   Ein Prozessor MUST:
   - XForm-Module parsen, statisch prüfen und auswerten,
   - das in Abschnitt 8 definierte Semantikmodell einhalten,
   - XML-Serialisierung gemäß Abschnitt 10 bereitstellen,
   - Fehler gemäß Abschnitt 13 berichten.

2. **XForm Host Environment** (optional)  
   Ein Host MAY Einbettungs-APIs definieren. Ein Host MUST die in Abschnitt 8.2 definierte „Dynamic Context“-Schnittstelle bereitstellen.

3. **XForm Module**  
   Ein Modul ist konform, wenn es die Grammatik einhält und keine statischen Fehler enthält.

### 2.3 Profile (optional)
Ein Prozessor MAY zusätzlich Profile unterstützen:
- **Core Profile** (vollständige Sprache)  
- **Streaming Profile** (einschränkendes Profil für streambare Auswertung)

---

## 3. Begriffe und Notation (normativ)
- **Node**: Ein Knoten im Datenmodell (Abschnitt 4).  
- **Item**: Entweder Node oder Atomwert.  
- **Sequence**: Geordnete Folge von Items (kann leer sein).  
- **Context Item**: Aktueller Item-Kontext während der Auswertung.  
- **QName**: Qualifizierter Name `prefix:local` oder `local` im Default-Namespace.

Notation:
- Code wird in Monospace dargestellt.  
- `{ expr }` bezeichnet *Expression Interpolation* in Konstruktoren.

---

## 4. Datenmodell (normativ)

### 4.1 Knotentypen
Ein XForm Processor MUST mindestens folgende Knotentypen unterstützen:

- DocumentNode  
- ElementNode (Name, Attribute, Children)  
- AttributeNode (Name, StringValue)  
- TextNode (StringValue)  
- CommentNode (StringValue)  
- ProcessingInstructionNode (Target, StringValue)

Namespaces werden als Eigenschaften von Element/Attribut-Namen modelliert (kein eigener Namespace-Knoten erforderlich).

### 4.2 Atomare Typen
Ein Prozessor MUST folgende atomare Typen unterstützen:
- `string`, `number` (IEEE-754 double), `boolean`, `null`

Ein Prozessor SHOULD zusätzlich unterstützen:
- `date`, `time`, `dateTime`, `duration`

### 4.3 Identität und Ordnung
- Knotenidentität MUST pro Input-Dokument stabil sein.  
- Node-Sequenzen, die durch Pfadausdrücke entstehen, MUST in Dokumentordnung sein, sofern nicht anders spezifiziert.  
- Atomare Werte haben keine Dokumentordnung.

---

## 5. Sprachüberblick (informativ)
XForm ist eine Ausdruckssprache mit:
- Pfadausdrücken: `.//item`, `./name/text()`, `.@id`  
- Konstruktoren: `<entry id="{...}">{ ... }</entry>`  
- Kontrollstrukturen: `if/then/else`, `for`, `let`  
- Pattern Matching: `match node: case <b>{x}</b> => ...`  
- Bibliothek: `copy()`, `index()`, `groupBy()`, `string()`, `number()`

---

## 6. Lexikalische Struktur (normativ)

### 6.1 Whitespace und Kommentare
- Whitespace trennt Tokens, außer innerhalb von Stringliteralen.  
- Kommentare beginnen mit `#` bis Zeilenende und MAY überall auftreten, wo Whitespace zulässig ist.

### 6.2 Bezeichner
- Identifier: `[A-Za-z_][A-Za-z0-9_-]*`  
- Prefixe folgen denselben Regeln.

### 6.3 Stringliterale
Strings MUST in einfachen oder doppelten Anführungszeichen stehen: `'...'` oder `"..."`. Escape-Sequenzen:
- `\'`, `\"`, `\\`, `\n`, `\t`, `\r`, `\uXXXX`

---

## 7. Grammatik (EBNF) (normativ – Auszug)
```ebnf
Module        := { PrologDecl } { ImportDecl } { FuncDecl } { VarDecl } { RuleDecl } [ Expr ] ;
PrologDecl    := "xform" "version" StringLiteral ";" ;
ImportDecl    := "import" StringLiteral [ "as" Prefix ] ";" ;

FuncDecl      := "def" QName "(" [ ParamList ] ")" "=" Expr ";" ;
ParamList     := Param { "," Param } ;
Param         := Identifier [ ":" TypeRef ] ;

VarDecl       := "let" Identifier "=" Expr ";" ;

RuleDecl      := "rule" Identifier "(" [ ParamList ] ")" "=" Expr ";" ;

Expr          := IfExpr | LetExpr | ForExpr | MatchExpr | OrExpr ;

IfExpr        := "if" Expr "then" Expr "else" Expr ;
LetExpr       := "let" Identifier "=" Expr "in" Expr ;
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
PathStart     := "." | "/" | ".//" | "//" ;
PathStep      := ( "/" | "//" ) StepTest [ PredicateList ]
              | "." | ".." | "/@" NameTest ;

StepTest      := NameTest | "*" | "text()" | "node()" | "comment()" | "pi()" ;
NameTest      := QName ;
PredicateList := { "[" Expr "]" } ;

FuncCall      := QName "(" [ ArgList ] ")" ;
ArgList       := Expr { "," Expr } ;

Constructor   := ElemConstructor | TextConstructor ;
ElemConstructor := "<" QName { AttrConstructor } ">" { Content } "</" QName ">" ;
AttrConstructor := Identifier "=" "{" Expr "}" ;
Content       := Constructor | "{" Expr "}" | CharData ;
```
*Anmerkung:* Diese EBNF ist repräsentativ; eine vollständige Grammatik stünde in Anhang A.

---

## 8. Semantik (normativ)

### 8.1 Statischer Kontext
Ein Prozessor MUST beim Laden eines Moduls einen statischen Kontext bilden:
- Namespace-Bindings (Prefix → URI)  
- Funktionssignaturen  
- Typinformationen (optional)  
- Modulimporte

Statische Fehler (Abschnitt 13) MUST vor Ausführung erkannt werden, soweit möglich.

### 8.2 Dynamischer Kontext
Während der Auswertung MUST verfügbar sein:
- `contextItem` (Item oder leer)  
- `variables` (Map Identifier → Sequence)  
- `functions` (QName → Implementierung)  
- `baseURI` (optional)

### 8.3 Auswertungsregeln (Kern)
- Jede `Expr` liefert eine `Sequence`.  
- `if` wertet Bedingung als boolean (mit boolean-Coercion, siehe 8.4).  
- `let x = E1 in E2`: `E1` einmal auswerten, an `x` binden, dann `E2`.  
- `for x in S return E`: `E` für jedes Item in `S` auswerten, Ergebnisse konkatenieren.  
- Funktionsaufrufe sind referentiell transparent (keine Side Effects).

### 8.4 Typkonversionen (boolean-coercion)
Eine Sequence wird zu boolean wie folgt:
- leer → `false`  
- enthält mindestens ein Node → `true`  
- enthält atomare Werte → `false` nur wenn alle Werte „falsy“ sind (`false`, `0`, `""`, `null`), sonst `true`

---

## 9. Pattern Matching (normativ)

### 9.1 Pattern-Kategorien
XForm MUST folgende Pattern unterstützen:

1. **Element Pattern**: `<qname>{var}</qname>`  
   Matcht ElementNode mit Name `qname`. Der Inhalt `{var}` bindet die Kindsequenz an `var`.

2. **Wildcard**: `_` matcht jedes Item.

3. **Typed Pattern** (optional): `node()`, `text()`, `comment()`

4. **Guarded Pattern** (optional): `case P where E => ...`

### 9.2 Matching-Reihenfolge
- Cases werden in Quelltextreihenfolge geprüft.  
- Das erste passende `case` MUST gewählt werden.  
- Wenn kein `case` passt, MUST `default` existieren, sonst dynamischer Fehler.

---

## 10. XML-Konstruktoren und Serialisierung (normativ)

### 10.1 Konstruktor-Semantik
Ein `ElemConstructor` erzeugt einen neuen ElementNode:
- Name MUST ein QName sein, dessen Prefix gebunden ist (oder Default-Namespace).  
- Attribute werden in Evaluationsreihenfolge ausgewertet; Werte werden zu `string` konvertiert.  
- `{ Expr }` in Content wird ausgewertet:  
  - Node-Items werden als Children eingefügt  
  - Atomare Items werden als TextNodes eingefügt (string-coercion)

### 10.2 Kopiermodell
XForm definiert zwei Modi (Processor muss mindestens einen anbieten, SHOULD beide anbieten):
- **Deep Copy Mode**: Eingabe-Nodes werden beim Einfügen kopiert (neue Identität).  
- **Reference Mode**: Nodes können als Referenzen übernommen werden, solange Serialisierung konsistent ist.

Die Standardfunktion `copy(node, recurse=true)` MUST bereitgestellt werden und Deep Copy erzeugen.

### 10.3 Serialisierung
Ein Processor MUST wohlgeformte XML-Ausgabe erzeugen.
- Text MUST korrekt escaped werden (`&`, `<`, `>`, `"` in Attributen).  
- Namespace-Deklarationen MUST erzeugt werden, wenn QNames Prefixe verwenden.

---

## 11. Standardbibliothek (normativ – Mindestumfang)

### 11.1 Typ & Konversion
- `string(x)` → string  
- `number(x)` → number (oder Fehler bei Nicht-Konvertierbarkeit)  
- `boolean(x)` → boolean  
- `typeOf(x)` → string  

### 11.2 Navigation & Selektion
- `name(node)` → string  
- `attr(node, qnameOrString)` → Sequence(AttributeNode|string)  
- `text(node)` → string (Konkatenation aller Textdescendants)

### 11.3 Struktur
- `children(node)` → Sequence(Node)  
- `elements(node, nameTest?)` → Sequence(ElementNode)  
- `copy(node, recurse := true)` → Node  

### 11.4 Sequenzen
- `count(seq)`  
- `empty(seq)`  
- `distinct(seq)` (nach value-equality)  
- `sort(seq, keyFn?)`

### 11.5 Indexe / Keys
- `index(seq, key := exprOrFn)` → map(keyValue → Sequence(items))  
- `lookup(map, key)` → Sequence

### 11.6 Gruppierung (SHOULD)
- `groupBy(seq, keyFn)` → Sequence(map{key, items})

---

## 12. Module und Namespaces (normativ)

### 12.1 Namespace-Deklaration
Ein Modul MAY Namespace-Bindings deklarieren:
```xform
ns "p" = "urn:example:product";
```
Ein Processor MUST diese Bindings im statischen Kontext berücksichtigen.

### 12.2 Imports
`import "iri" as p;` lädt ein weiteres Modul. Ein Processor MUST zyklische Imports erkennen (statischer Fehler).

### 12.3 Sichtbarkeit
- Funktionen sind standardmäßig exportiert.  
- Ein Processor MAY `export`/`private` ergänzen; falls vorhanden, MUST es respektiert werden.

---

## 13. Fehlerbehandlung (normativ)

### 13.1 Fehlerklassen
Ein Processor MUST mindestens folgende Fehlerklassen berichten:

**Statische Fehler**
- `XFST0001` Syntaxfehler  
- `XFST0002` Ungebundener Prefix/QName  
- `XFST0003` Unbekannte Funktion  
- `XFST0004` Importfehler / Zyklus  

**Dynamische Fehler**
- `XFDY0001` Kein `default` im `match` und kein Case passt  
- `XFDY0002` Typ-/Konversionsfehler (z. B. `number("abc")`)  
- `XFDY0003` Knotenoperation auf Atomwert  
- `XFDY0004` Ungültige Konstruktion (z. B. mismatched Endtag)

### 13.2 Fehlerformat
Ein Processor SHOULD Fehler mit folgenden Feldern liefern:
- Code, Message, Modul-IRI, Zeile/Spalte, optional Stack (Funktionskette)

---

## 14. Sicherheits- und Datenschutzaspekte (normativ/informativ)
- XForm ist side-effect-frei. Ein Processor MUST standardmäßig keine externen Ressourcen laden, außer explizit durch `import`.  
- Ein Processor SHOULD „Safe Mode“ anbieten, der `import` aus dem Netzwerk verbietet.  
- Host Environments MUST beachten, dass Transformationen sensible Daten aus XML extrahieren können; Logging SHOULD minimiert und konfigurierbar sein.

---

## 15. Internationalisierung (normativ/informativ)
- Ein Processor MUST Unicode für Quelltext und XML verarbeiten.  
- Stringfunktionen MUST Unicode-aware sein (mindestens Codepoint-basiert).  
- Sortierung SHOULD locale-sensitiv konfigurierbar sein.

---

## Anhang A: Vollständige Grammatik (EBNF) (informativ)
Für eine echte Einreichung würde hier eine vollständige, konfliktfreie Grammatik inkl. Tokenization der XML-Konstruktoren stehen, insbesondere zur Abgrenzung von `CharData` vs. `{Expr}`.

## Anhang B: Reservierte Wörter (normativ)
`xform, version, import, as, ns, def, let, in, for, where, return, if, then, else, match, case, default, and, or, not, div, mod, rule`

---

## Kurzes „Hello Transform“ (informativ)
```xform
xform version "1.0";

def itemToEntry(i) =
  <entry id="{string(i.@id)}">
    <title>{ i./name/text() }</title>
    <price currency="EUR">{ number(i./price/text()) }</price>
  </entry>;

<feed>{
  for i in .//item return itemToEntry(i)
}</feed>
```
