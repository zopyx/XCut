package xform

import (
	"fmt"
	"strconv"
)

type Parser struct {
	text  string
	lexer *Lexer
}

func NewParser(text string) *Parser {
	return &Parser{text: text, lexer: NewLexer(text)}
}

func (p *Parser) ParseModule() *Module {
	functions := map[string]FunctionDef{}
	rules := map[string][]RuleDef{}
	vars := map[string]Expr{}
	namespaces := map[string]string{}
	imports := [][2]*string{}

	tok := p.lexer.Peek()
	if tok.Kind == TokKW && tok.Val == "xform" {
		p.lexer.Next()
		p.lexer.Expect(TokKW, "version")
		version := p.lexer.Expect(TokString, "").Val
		if version != "2.0" {
			panic(fmt.Errorf("XFST0005: unsupported version"))
		}
		p.lexer.Expect(TokPunct, ";")
	}

	for {
		tok = p.lexer.Peek()
		if tok.Kind == TokKW && tok.Val == "ns" {
			p.parseNs(namespaces)
			continue
		}
		if tok.Kind == TokKW && tok.Val == "import" {
			p.parseImport(&imports)
			continue
		}
		if tok.Kind == TokKW && tok.Val == "var" {
			name, expr := p.parseVar()
			vars[name] = expr
			continue
		}
		if tok.Kind == TokKW && tok.Val == "def" {
			p.parseDef(functions)
			continue
		}
		if tok.Kind == TokKW && tok.Val == "rule" {
			p.parseRule(rules)
			continue
		}
		break
	}

	var expr Expr
	if p.lexer.Peek().Kind != TokEOF {
		expr = p.parseExpr()
		if p.lexer.Peek().Kind != TokEOF {
			panic(fmt.Errorf("unexpected token at %d", p.lexer.Peek().Pos))
		}
	}

	return &Module{
		Functions:  functions,
		Rules:      rules,
		Vars:       vars,
		Namespaces: namespaces,
		Imports:    imports,
		Expr:       expr,
	}
}

func (p *Parser) parseNs(namespaces map[string]string) {
	p.lexer.Expect(TokKW, "ns")
	prefix := p.lexer.Expect(TokString, "").Val
	p.lexer.Expect(TokOp, "=")
	uri := p.lexer.Expect(TokString, "").Val
	p.lexer.Expect(TokPunct, ";")
	namespaces[prefix] = uri
}

func (p *Parser) parseImport(imports *[][2]*string) {
	p.lexer.Expect(TokKW, "import")
	iri := p.lexer.Expect(TokString, "").Val
	var alias *string
	if p.lexer.Peek().Kind == TokKW && p.lexer.Peek().Val == "as" {
		p.lexer.Next()
		val := p.lexer.Expect(TokIdent, "").Val
		alias = &val
	}
	p.lexer.Expect(TokPunct, ";")
	iriCopy := iri
	*imports = append(*imports, [2]*string{&iriCopy, alias})
}

func (p *Parser) parseVar() (string, Expr) {
	p.lexer.Expect(TokKW, "var")
	name := p.lexer.Expect(TokIdent, "").Val
	p.lexer.Expect(TokOp, ":=")
	value := p.parseExpr()
	p.lexer.Expect(TokPunct, ";")
	return name, value
}

func (p *Parser) parseDef(functions map[string]FunctionDef) {
	p.lexer.Expect(TokKW, "def")
	name := p.parseQName()
	p.lexer.Expect(TokPunct, "(")
	params := []Param{}
	if !(p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == ")") {
		params = append(params, p.parseParam())
		for p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "," {
			p.lexer.Next()
			params = append(params, p.parseParam())
		}
	}
	p.lexer.Expect(TokPunct, ")")
	p.lexer.Expect(TokOp, ":=")
	body := p.parseExpr()
	p.lexer.Expect(TokPunct, ";")
	functions[name] = FunctionDef{Params: params, Body: body}
}

func (p *Parser) parseParam() Param {
	name := p.lexer.Expect(TokIdent, "").Val
	var typeRef *string
	var def Expr
	if p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == ":" {
		p.lexer.Next()
		tr := p.parseTypeRef()
		typeRef = &tr
	}
	if p.lexer.Peek().Kind == TokOp && p.lexer.Peek().Val == ":=" {
		p.lexer.Next()
		def = p.parseExpr()
	}
	return Param{Name: name, TypeRef: typeRef, Default: def}
}

func (p *Parser) parseTypeRef() string {
	tok := p.lexer.Peek()
	if tok.Kind == TokIdent {
		if tok.Val == "string" || tok.Val == "number" || tok.Val == "boolean" || tok.Val == "null" || tok.Val == "map" {
			return p.lexer.Next().Val
		}
	}
	return p.parseQName()
}

func (p *Parser) parseRule(rules map[string][]RuleDef) {
	p.lexer.Expect(TokKW, "rule")
	name := p.parseQName()
	p.lexer.Expect(TokKW, "match")
	pattern := p.parsePattern()
	p.lexer.Expect(TokOp, ":=")
	body := p.parseExpr()
	p.lexer.Expect(TokPunct, ";")
	rules[name] = append(rules[name], RuleDef{Pattern: pattern, Body: body})
}

func (p *Parser) parseExpr() Expr {
	tok := p.lexer.Peek()
	if tok.Kind == TokKW && tok.Val == "if" {
		return p.parseIf()
	}
	if tok.Kind == TokKW && tok.Val == "let" {
		return p.parseLet()
	}
	if tok.Kind == TokKW && tok.Val == "for" {
		return p.parseFor()
	}
	if tok.Kind == TokKW && tok.Val == "match" {
		return p.parseMatch()
	}
	return p.parseOr()
}

func (p *Parser) parseIf() Expr {
	p.lexer.Expect(TokKW, "if")
	cond := p.parseExpr()
	p.lexer.Expect(TokKW, "then")
	thenExpr := p.parseExpr()
	p.lexer.Expect(TokKW, "else")
	elseExpr := p.parseExpr()
	return IfExpr{Cond: cond, ThenExpr: thenExpr, ElseExpr: elseExpr}
}

func (p *Parser) parseLet() Expr {
	p.lexer.Expect(TokKW, "let")
	name := p.lexer.Expect(TokIdent, "").Val
	p.lexer.Expect(TokOp, ":=")
	value := p.parseExpr()
	p.lexer.Expect(TokKW, "in")
	body := p.parseExpr()
	return LetExpr{Name: name, Value: value, Body: body}
}

func (p *Parser) parseFor() Expr {
	p.lexer.Expect(TokKW, "for")
	name := p.lexer.Expect(TokIdent, "").Val
	p.lexer.Expect(TokKW, "in")
	seq := p.parseExpr()
	var where Expr
	if p.lexer.Peek().Kind == TokKW && p.lexer.Peek().Val == "where" {
		p.lexer.Next()
		where = p.parseExpr()
	}
	p.lexer.Expect(TokKW, "return")
	body := p.parseExpr()
	return ForExpr{Name: name, Seq: seq, Where: where, Body: body}
}

func (p *Parser) parseMatch() Expr {
	p.lexer.Expect(TokKW, "match")
	target := p.parseExpr()
	p.lexer.Expect(TokPunct, ":")
	cases := []MatchCase{}
	var def Expr
	for {
		tok := p.lexer.Peek()
		if tok.Kind == TokKW && tok.Val == "case" {
			p.lexer.Next()
			pattern := p.parsePattern()
			p.lexer.Expect(TokOp, "=")
			p.lexer.Expect(TokOp, ">")
			expr := p.parseExpr()
			p.lexer.Expect(TokPunct, ";")
			cases = append(cases, MatchCase{Pattern: pattern, Expr: expr})
			continue
		}
		if tok.Kind == TokKW && tok.Val == "default" {
			p.lexer.Next()
			p.lexer.Expect(TokOp, "=")
			p.lexer.Expect(TokOp, ">")
			def = p.parseExpr()
			p.lexer.Expect(TokPunct, ";")
			break
		}
		break
	}
	return MatchExpr{Target: target, Cases: cases, Default: def}
}

func (p *Parser) parseOr() Expr {
	expr := p.parseAnd()
	for p.lexer.Peek().Kind == TokKW && p.lexer.Peek().Val == "or" {
		p.lexer.Next()
		right := p.parseAnd()
		expr = BinaryOp{Op: "or", Left: expr, Right: right}
	}
	return expr
}

func (p *Parser) parseAnd() Expr {
	expr := p.parseEq()
	for p.lexer.Peek().Kind == TokKW && p.lexer.Peek().Val == "and" {
		p.lexer.Next()
		right := p.parseEq()
		expr = BinaryOp{Op: "and", Left: expr, Right: right}
	}
	return expr
}

func (p *Parser) parseEq() Expr {
	expr := p.parseRel()
	for p.lexer.Peek().Kind == TokOp && (p.lexer.Peek().Val == "=" || p.lexer.Peek().Val == "!=") {
		op := p.lexer.Next().Val
		right := p.parseRel()
		expr = BinaryOp{Op: op, Left: expr, Right: right}
	}
	return expr
}

func (p *Parser) parseRel() Expr {
	expr := p.parseAdd()
	for p.lexer.Peek().Kind == TokOp {
		op := p.lexer.Peek().Val
		if op != "<" && op != "<=" && op != ">" && op != ">=" {
			break
		}
		p.lexer.Next()
		right := p.parseAdd()
		expr = BinaryOp{Op: op, Left: expr, Right: right}
	}
	return expr
}

func (p *Parser) parseAdd() Expr {
	expr := p.parseMul()
	for p.lexer.Peek().Kind == TokOp && (p.lexer.Peek().Val == "+" || p.lexer.Peek().Val == "-") {
		op := p.lexer.Next().Val
		right := p.parseMul()
		expr = BinaryOp{Op: op, Left: expr, Right: right}
	}
	return expr
}

func (p *Parser) parseMul() Expr {
	expr := p.parseUnary()
	for {
		tok := p.lexer.Peek()
		if tok.Kind == TokOp && tok.Val == "*" {
			p.lexer.Next()
			right := p.parseUnary()
			expr = BinaryOp{Op: "*", Left: expr, Right: right}
			continue
		}
		if tok.Kind == TokKW && (tok.Val == "div" || tok.Val == "mod") {
			op := p.lexer.Next().Val
			right := p.parseUnary()
			expr = BinaryOp{Op: op, Left: expr, Right: right}
			continue
		}
		break
	}
	return expr
}

func (p *Parser) parseUnary() Expr {
	tok := p.lexer.Peek()
	if tok.Kind == TokOp && tok.Val == "-" {
		p.lexer.Next()
		return UnaryOp{Op: "-", Expr: p.parseUnary()}
	}
	if tok.Kind == TokKW && tok.Val == "not" {
		p.lexer.Next()
		return UnaryOp{Op: "not", Expr: p.parseUnary()}
	}
	return p.parsePrimary()
}

func (p *Parser) parsePrimary() Expr {
	tok := p.lexer.Peek()
	if tok.Kind == TokNumber {
		p.lexer.Next()
		return Literal{Value: mustParseFloat(tok.Val)}
	}
	if tok.Kind == TokString {
		p.lexer.Next()
		return Literal{Value: tok.Val}
	}
	if tok.Kind == TokPunct && tok.Val == "(" {
		p.lexer.Next()
		expr := p.parseExpr()
		p.lexer.Expect(TokPunct, ")")
		return expr
	}
	if tok.Kind == TokIdent && tok.Val == "text" {
		savedPos := p.lexer.Pos
		savedBuf := p.lexer.Buffer
		p.lexer.Next()
		if p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "{" {
			p.lexer.Next()
			expr := p.parseExpr()
			p.lexer.Expect(TokPunct, "}")
			return TextConstructor{Expr: expr}
		}
		p.lexer.Pos = savedPos
		p.lexer.Buffer = savedBuf
	}
	if tok.Kind == TokOp && tok.Val == "<" {
		return p.parseConstructor()
	}
	if tok.Kind == TokDot || tok.Kind == TokSlash {
		return p.parsePath(nil)
	}
	if tok.Kind == TokIdent {
		name := p.lexer.Next().Val
		if p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "(" {
			return p.parseFuncCall(name)
		}
		if p.pathContinues() {
			return p.parsePath(&PathStart{Kind: "var", Name: &name})
		}
		return VarRef{Name: name}
	}
	panic(fmt.Errorf("unexpected token at %d", tok.Pos))
}

func (p *Parser) parseFuncCall(name string) Expr {
	p.lexer.Expect(TokPunct, "(")
	args := []Expr{}
	if !(p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == ")") {
		args = append(args, p.parseExpr())
		for p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "," {
			p.lexer.Next()
			args = append(args, p.parseExpr())
		}
	}
	p.lexer.Expect(TokPunct, ")")
	return FuncCall{Name: name, Args: args}
}

func (p *Parser) pathContinues() bool {
	tok := p.lexer.Peek()
	return tok.Kind == TokSlash || tok.Kind == TokDot || tok.Kind == TokAt
}

func (p *Parser) parsePath(start *PathStart) Expr {
	actualStart := start
	if actualStart == nil {
		tok := p.lexer.Next()
		if tok.Kind == TokDot {
			if tok.Val == ".//" {
				actualStart = &PathStart{Kind: "desc"}
			} else {
				actualStart = &PathStart{Kind: "context"}
			}
		} else if tok.Kind == TokSlash {
			if tok.Val == "//" {
				actualStart = &PathStart{Kind: "desc_root"}
			} else {
				actualStart = &PathStart{Kind: "root"}
			}
		} else {
			panic(fmt.Errorf("invalid path start at %d", tok.Pos))
		}
	}

	steps := []PathStep{}
	if actualStart.Kind == "root" || actualStart.Kind == "context" || actualStart.Kind == "var" {
		tok := p.lexer.Peek()
		if tok.Kind == TokAt {
			p.lexer.Next()
			test := StepTest{Kind: "name", Name: strPtr(p.parseQName())}
			steps = append(steps, PathStep{Axis: "attr", Test: test, Predicates: []Expr{}})
		} else if tok.Kind == TokOp && tok.Val == "*" {
			test := p.parseStepTest()
			preds := p.parsePredicates()
			steps = append(steps, PathStep{Axis: "child", Test: test, Predicates: preds})
		} else if tok.Kind == TokIdent {
			test := p.parseStepTest()
			preds := p.parsePredicates()
			steps = append(steps, PathStep{Axis: "child", Test: test, Predicates: preds})
		}
	}
	if actualStart.Kind == "desc" || actualStart.Kind == "desc_root" {
		tok := p.lexer.Peek()
		if tok.Kind == TokIdent || tok.Kind == TokOp {
			test := p.parseStepTest()
			preds := p.parsePredicates()
			steps = append(steps, PathStep{Axis: "desc_or_self", Test: test, Predicates: preds})
		}
	}

	for {
		tok := p.lexer.Peek()
		if tok.Kind == TokSlash {
			axis := "child"
			if tok.Val == "//" {
				axis = "desc"
			}
			p.lexer.Next()
			var test StepTest
			preds := []Expr{}
			if p.lexer.Peek().Kind == TokAt {
				p.lexer.Next()
				test = StepTest{Kind: "name", Name: strPtr(p.parseQName())}
				axis = "attr"
			} else {
				test = p.parseStepTest()
				preds = p.parsePredicates()
			}
			steps = append(steps, PathStep{Axis: axis, Test: test, Predicates: preds})
			continue
		}
		if tok.Kind == TokDot {
			if tok.Val == "." {
				p.lexer.Next()
				if p.lexer.Peek().Kind == TokAt {
					p.lexer.Next()
					test := StepTest{Kind: "name", Name: strPtr(p.parseQName())}
					steps = append(steps, PathStep{Axis: "attr", Test: test, Predicates: []Expr{}})
				} else {
					steps = append(steps, PathStep{Axis: "self", Test: StepTest{Kind: "node"}, Predicates: []Expr{}})
				}
				continue
			}
			if tok.Val == ".." {
				p.lexer.Next()
				steps = append(steps, PathStep{Axis: "parent", Test: StepTest{Kind: "node"}, Predicates: []Expr{}})
				continue
			}
		}
		if tok.Kind == TokAt {
			p.lexer.Next()
			test := StepTest{Kind: "name", Name: strPtr(p.parseQName())}
			steps = append(steps, PathStep{Axis: "attr", Test: test, Predicates: []Expr{}})
			continue
		}
		break
	}

	return PathExpr{Start: *actualStart, Steps: steps}
}

func (p *Parser) parseStepTest() StepTest {
	tok := p.lexer.Peek()
	if tok.Kind == TokOp && tok.Val == "*" {
		p.lexer.Next()
		return StepTest{Kind: "wildcard"}
	}
	if tok.Kind == TokIdent {
		if tok.Val == "text" || tok.Val == "node" || tok.Val == "comment" || tok.Val == "pi" {
			p.lexer.Next()
			p.lexer.Expect(TokPunct, "(")
			p.lexer.Expect(TokPunct, ")")
			return StepTest{Kind: tok.Val}
		}
		name := p.parseQName()
		return StepTest{Kind: "name", Name: strPtr(name)}
	}
	panic(fmt.Errorf("invalid step test at %d", tok.Pos))
}

func (p *Parser) parsePredicates() []Expr {
	preds := []Expr{}
	for p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "[" {
		p.lexer.Next()
		preds = append(preds, p.parseExpr())
		p.lexer.Expect(TokPunct, "]")
	}
	return preds
}

func (p *Parser) parseQName() string {
	return p.lexer.Expect(TokIdent, "").Val
}

func (p *Parser) parsePattern() Pattern {
	tok := p.lexer.Peek()
	if tok.Kind == TokAt {
		p.lexer.Next()
		name := p.parseQName()
		return AttributePattern{Name: name}
	}
	if tok.Kind == TokIdent && (tok.Val == "node" || tok.Val == "text" || tok.Val == "comment") {
		p.lexer.Next()
		p.lexer.Expect(TokPunct, "(")
		p.lexer.Expect(TokPunct, ")")
		return TypedPattern{Kind: tok.Val}
	}
	if tok.Kind == TokIdent && tok.Val == "_" {
		p.lexer.Next()
		return WildcardPattern{}
	}
	if tok.Kind == TokOp && tok.Val == "<" {
		p.lexer.Next()
		name := p.parseQName()
		p.lexer.Expect(TokOp, ">")
		varName := (*string)(nil)
		var child Pattern
		if p.lexer.Peek().Kind == TokPunct && p.lexer.Peek().Val == "{" {
			p.lexer.Next()
			v := p.lexer.Expect(TokIdent, "").Val
			varName = &v
			p.lexer.Expect(TokPunct, "}")
		} else if p.lexer.Peek().Kind == TokOp && p.lexer.Peek().Val == "<" {
			child = p.parsePattern()
		} else {
			panic(fmt.Errorf("invalid element pattern content"))
		}
		p.lexer.Expect(TokOp, "<")
		p.lexer.Expect(TokSlash, "/")
		end := p.parseQName()
		if end != name {
			panic(fmt.Errorf("mismatched pattern end tag"))
		}
		p.lexer.Expect(TokOp, ">")
		return ElementPattern{Name: name, Var: varName, Child: child}
	}
	panic(fmt.Errorf("invalid pattern at %d", tok.Pos))
}

func (p *Parser) parseConstructor() Expr {
	p.lexer.Expect(TokOp, "<")
	name := p.parseQName()
	attrs := []AttrConstructor{}
	for {
		tok := p.lexer.Peek()
		if tok.Kind == TokOp && tok.Val == ">" {
			p.lexer.Next()
			break
		}
		if tok.Kind == TokSlash && tok.Val == "/" {
			p.lexer.Next()
			p.lexer.Expect(TokOp, ">")
			return Constructor{Name: name, Attrs: attrs, Contents: []Expr{}}
		}
		attrName := p.parseQName()
		p.lexer.Expect(TokOp, "=")
		p.lexer.Expect(TokPunct, "{")
		expr := p.parseExpr()
		p.lexer.Expect(TokPunct, "}")
		attrs = append(attrs, AttrConstructor{Name: attrName, Expr: expr})
	}

	contents := []Expr{}
	p.lexer.ClearBuffer()
	for {
		if p.lexer.Pos >= len(p.text) {
			panic(fmt.Errorf("unterminated constructor"))
		}
		if p.text[p.lexer.Pos:] != "" && len(p.text[p.lexer.Pos:]) >= 2 && p.text[p.lexer.Pos:p.lexer.Pos+2] == "</" {
			endName, newPos := p.readEndTag()
			if endName != name {
				panic(fmt.Errorf("mismatched end tag"))
			}
			p.lexer.Pos = newPos
			p.lexer.ClearBuffer()
			break
		}
		if len(p.text[p.lexer.Pos:]) >= 5 && p.text[p.lexer.Pos:p.lexer.Pos+5] == "text{" {
			p.lexer.Pos += 4
			p.lexer.ClearBuffer()
			p.lexer.Expect(TokPunct, "{")
			expr := p.parseExpr()
			p.lexer.Expect(TokPunct, "}")
			contents = append(contents, TextConstructor{Expr: expr})
			continue
		}
		ch := p.text[p.lexer.Pos]
		if ch == '<' {
			p.lexer.ClearBuffer()
			contents = append(contents, p.parseConstructor())
			continue
		}
		if ch == '{' {
			p.lexer.Pos++
			p.lexer.ClearBuffer()
			expr := p.parseExpr()
			p.lexer.Expect(TokPunct, "}")
			contents = append(contents, Interp{Expr: expr})
			continue
		}
		text := p.parseCharData()
		if len(text) > 0 {
			if len(stripSpace(text)) > 0 {
				contents = append(contents, Text{Value: text})
			}
		}
	}
	return Constructor{Name: name, Attrs: attrs, Contents: contents}
}

func (p *Parser) parseCharData() string {
	out := []byte{}
	for {
		if p.lexer.Pos >= len(p.text) {
			break
		}
		ch := p.text[p.lexer.Pos]
		if ch == '<' || ch == '{' {
			break
		}
		out = append(out, ch)
		p.lexer.Pos++
	}
	return string(out)
}

func (p *Parser) readEndTag() (string, int) {
	pos := p.lexer.Pos
	if pos+2 > len(p.text) || p.text[pos:pos+2] != "</" {
		panic(fmt.Errorf("expected end tag"))
	}
	pos += 2
	start := pos
	for pos < len(p.text) {
		c := p.text[pos]
		if !(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && c != '_' && c != ':' && c != '-' {
			break
		}
		pos++
	}
	name := p.text[start:pos]
	for pos < len(p.text) && (p.text[pos] == ' ' || p.text[pos] == '\n' || p.text[pos] == '\t' || p.text[pos] == '\r') {
		pos++
	}
	if pos >= len(p.text) || p.text[pos] != '>' {
		panic(fmt.Errorf("unterminated end tag"))
	}
	return name, pos + 1
}

func mustParseFloat(s string) float64 {
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		panic(err)
	}
	return f
}

func strPtr(s string) *string { return &s }

func stripSpace(s string) string {
	out := make([]rune, 0, len(s))
	for _, r := range s {
		if r != ' ' && r != '\t' && r != '\n' && r != '\r' {
			out = append(out, r)
		}
	}
	return string(out)
}
