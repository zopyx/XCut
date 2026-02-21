package xform

import (
	"fmt"
	"strings"
	"unicode"
)

type TokenKind string

const (
	TokEOF    TokenKind = "EOF"
	TokKW     TokenKind = "KW"
	TokIdent  TokenKind = "IDENT"
	TokOp     TokenKind = "OP"
	TokPunct  TokenKind = "PUNCT"
	TokString TokenKind = "STRING"
	TokNumber TokenKind = "NUMBER"
	TokDot    TokenKind = "DOT"
	TokSlash  TokenKind = "SLASH"
	TokAt     TokenKind = "AT"
)

type Token struct {
	Kind TokenKind
	Val  string
	Pos  int
}

var keywords = map[string]bool{
	"xform":   true,
	"version": true,
	"import":  true,
	"as":      true,
	"ns":      true,
	"def":     true,
	"var":     true,
	"let":     true,
	"in":      true,
	"for":     true,
	"where":   true,
	"return":  true,
	"if":      true,
	"then":    true,
	"else":    true,
	"match":   true,
	"case":    true,
	"default": true,
	"and":     true,
	"or":      true,
	"not":     true,
	"div":     true,
	"mod":     true,
	"rule":    true,
}

type Lexer struct {
	Text   string
	Pos    int
	Buffer *Token
}

func NewLexer(text string) *Lexer {
	return &Lexer{Text: text, Pos: 0, Buffer: nil}
}

func (l *Lexer) Peek() Token {
	if l.Buffer == nil {
		tok := l.nextToken()
		l.Buffer = &tok
	}
	return *l.Buffer
}

func (l *Lexer) Next() Token {
	if l.Buffer != nil {
		tok := *l.Buffer
		l.Buffer = nil
		return tok
	}
	return l.nextToken()
}

func (l *Lexer) Expect(kind TokenKind, value string) Token {
	tok := l.Next()
	if tok.Kind != kind || (value != "" && tok.Val != value) {
		panic(fmt.Errorf("expected %s %s at %d", kind, value, tok.Pos))
	}
	return tok
}

func (l *Lexer) ClearBuffer() {
	l.Buffer = nil
}

func (l *Lexer) skipWsComments() {
	for l.Pos < len(l.Text) {
		ch := l.Text[l.Pos]
		if unicode.IsSpace(rune(ch)) {
			l.Pos++
			continue
		}
		if ch == '#' {
			for l.Pos < len(l.Text) && l.Text[l.Pos] != '\n' {
				l.Pos++
			}
			continue
		}
		break
	}
}

func (l *Lexer) nextToken() Token {
	l.skipWsComments()
	if l.Pos >= len(l.Text) {
		return Token{Kind: TokEOF, Val: "", Pos: l.Pos}
	}

	ch := l.Text[l.Pos]

	if ch == ':' && l.Pos+1 < len(l.Text) && l.Text[l.Pos+1] == '=' {
		start := l.Pos
		l.Pos += 2
		return Token{Kind: TokOp, Val: ":=", Pos: start}
	}

	if strings.Contains("(){}[],:;", string(ch)) {
		l.Pos++
		return Token{Kind: TokPunct, Val: string(ch), Pos: l.Pos - 1}
	}

	if ch == '.' {
		start := l.Pos
		if l.Pos+1 < len(l.Text) && l.Text[l.Pos:l.Pos+2] == ".." {
			l.Pos += 2
			return Token{Kind: TokDot, Val: "..", Pos: start}
		}
		if l.Pos+2 < len(l.Text) && l.Text[l.Pos:l.Pos+3] == ".//" {
			l.Pos += 3
			return Token{Kind: TokDot, Val: ".//", Pos: start}
		}
		l.Pos++
		return Token{Kind: TokDot, Val: ".", Pos: start}
	}

	if ch == '/' {
		start := l.Pos
		if l.Pos+1 < len(l.Text) && l.Text[l.Pos:l.Pos+2] == "//" {
			l.Pos += 2
			return Token{Kind: TokSlash, Val: "//", Pos: start}
		}
		l.Pos++
		return Token{Kind: TokSlash, Val: "/", Pos: start}
	}

	if strings.Contains("<>=!+-*", string(ch)) {
		start := l.Pos
		l.Pos++
		if l.Pos < len(l.Text) && l.Text[l.Pos] == '=' {
			l.Pos++
			return Token{Kind: TokOp, Val: l.Text[start:l.Pos], Pos: start}
		}
		return Token{Kind: TokOp, Val: string(ch), Pos: start}
	}

	if ch == '\'' || ch == '"' {
		quote := ch
		start := l.Pos
		l.Pos++
		out := make([]rune, 0)
		for l.Pos < len(l.Text) {
			c := l.Text[l.Pos]
			if c == '\\' {
				l.Pos++
				if l.Pos >= len(l.Text) {
					break
				}
				esc := l.Text[l.Pos]
				switch esc {
				case 'n':
					out = append(out, '\n')
				case 't':
					out = append(out, '\t')
				case 'r':
					out = append(out, '\r')
				case 'u':
					if l.Pos+4 < len(l.Text) {
						hex := l.Text[l.Pos+1 : l.Pos+5]
						out = append(out, rune(parseHex(hex)))
						l.Pos += 4
					}
				default:
					out = append(out, rune(esc))
				}
				l.Pos++
				continue
			}
			if c == quote {
				l.Pos++
				return Token{Kind: TokString, Val: string(out), Pos: start}
			}
			out = append(out, rune(c))
			l.Pos++
		}
		panic(fmt.Errorf("unterminated string at %d", start))
	}

	if unicode.IsDigit(rune(ch)) {
		start := l.Pos
		for l.Pos < len(l.Text) {
			c := l.Text[l.Pos]
			if !unicode.IsDigit(rune(c)) && c != '.' {
				break
			}
			l.Pos++
		}
		return Token{Kind: TokNumber, Val: l.Text[start:l.Pos], Pos: start}
	}

	if unicode.IsLetter(rune(ch)) || ch == '_' {
		start := l.Pos
		for l.Pos < len(l.Text) {
			c := l.Text[l.Pos]
			if c == ':' {
				if l.Pos+1 < len(l.Text) && (unicode.IsLetter(rune(l.Text[l.Pos+1])) || unicode.IsDigit(rune(l.Text[l.Pos+1])) || l.Text[l.Pos+1] == '_' || l.Text[l.Pos+1] == '-') {
					l.Pos++
					continue
				}
				break
			}
			if !(unicode.IsLetter(rune(c)) || unicode.IsDigit(rune(c)) || c == '_' || c == '-') {
				break
			}
			l.Pos++
		}
		val := l.Text[start:l.Pos]
		if keywords[val] {
			return Token{Kind: TokKW, Val: val, Pos: start}
		}
		return Token{Kind: TokIdent, Val: val, Pos: start}
	}

	if ch == '@' {
		l.Pos++
		return Token{Kind: TokAt, Val: "@", Pos: l.Pos - 1}
	}

	panic(fmt.Errorf("unexpected character %q at %d", ch, l.Pos))
}

func parseHex(s string) int {
	val := 0
	for i := 0; i < len(s); i++ {
		val <<= 4
		c := s[i]
		switch {
		case c >= '0' && c <= '9':
			val += int(c - '0')
		case c >= 'a' && c <= 'f':
			val += int(c-'a') + 10
		case c >= 'A' && c <= 'F':
			val += int(c-'A') + 10
		}
	}
	return val
}
