const KEYWORDS: &[&str] = &[
    "xform", "version", "import", "as", "ns", "def", "var", "let", "in",
    "for", "where", "return", "if", "then", "else", "match", "case",
    "default", "and", "or", "not", "div", "mod", "rule",
];

#[derive(Debug, Clone, PartialEq)]
pub enum TK {
    Kw, Ident, Str, Num, Op, Punct, Dot, Slash, At, Eof,
}

#[derive(Debug, Clone)]
pub struct Token {
    pub kind: TK,
    pub value: String,
    pub pos: usize,
}

pub struct Lexer {
    pub chars: Vec<char>,
    pub pos: usize,
    pub buf: Option<Token>,
}

impl Lexer {
    pub fn new(text: &str) -> Self {
        Lexer { chars: text.chars().collect(), pos: 0, buf: None }
    }

    pub fn peek(&mut self) -> &Token {
        if self.buf.is_none() {
            self.buf = Some(self.next_token());
        }
        self.buf.as_ref().unwrap()
    }

    pub fn next(&mut self) -> Token {
        if let Some(tok) = self.buf.take() {
            return tok;
        }
        self.next_token()
    }

    pub fn expect(&mut self, kind: TK, value: Option<&str>) -> Result<Token, String> {
        let tok = self.next();
        if tok.kind != kind || value.map_or(false, |v| tok.value != v) {
            return Err(format!(
                "Expected {:?} {:?} at pos {}, got {:?} {:?}",
                kind, value, tok.pos, tok.kind, tok.value
            ));
        }
        Ok(tok)
    }

    fn skip_ws(&mut self) {
        while self.pos < self.chars.len() {
            let ch = self.chars[self.pos];
            if ch.is_whitespace() {
                self.pos += 1;
            } else if ch == '#' {
                while self.pos < self.chars.len() && self.chars[self.pos] != '\n' {
                    self.pos += 1;
                }
            } else {
                break;
            }
        }
    }

    fn next_token(&mut self) -> Token {
        self.skip_ws();
        if self.pos >= self.chars.len() {
            return Token { kind: TK::Eof, value: String::new(), pos: self.pos };
        }
        let start = self.pos;
        let ch = self.chars[self.pos];

        // :=
        if ch == ':' && self.pos + 1 < self.chars.len() && self.chars[self.pos + 1] == '=' {
            self.pos += 2;
            return Token { kind: TK::Op, value: ":=".into(), pos: start };
        }

        // Punctuation
        if "(){}[],:;".contains(ch) {
            self.pos += 1;
            return Token { kind: TK::Punct, value: ch.to_string(), pos: start };
        }

        // Dot variants
        if ch == '.' {
            if self.pos + 2 < self.chars.len()
                && self.chars[self.pos + 1] == '/'
                && self.chars[self.pos + 2] == '/'
            {
                self.pos += 3;
                return Token { kind: TK::Dot, value: ".//".into(), pos: start };
            }
            if self.pos + 1 < self.chars.len() && self.chars[self.pos + 1] == '.' {
                self.pos += 2;
                return Token { kind: TK::Dot, value: "..".into(), pos: start };
            }
            self.pos += 1;
            return Token { kind: TK::Dot, value: ".".into(), pos: start };
        }

        // Slash variants
        if ch == '/' {
            if self.pos + 1 < self.chars.len() && self.chars[self.pos + 1] == '/' {
                self.pos += 2;
                return Token { kind: TK::Slash, value: "//".into(), pos: start };
            }
            self.pos += 1;
            return Token { kind: TK::Slash, value: "/".into(), pos: start };
        }

        // Operators
        if "<>=!+-*".contains(ch) {
            self.pos += 1;
            if self.pos < self.chars.len() && self.chars[self.pos] == '=' {
                self.pos += 1;
                let s: String = self.chars[start..self.pos].iter().collect();
                return Token { kind: TK::Op, value: s, pos: start };
            }
            return Token { kind: TK::Op, value: ch.to_string(), pos: start };
        }

        // Strings
        if ch == '\'' || ch == '"' {
            let quote = ch;
            self.pos += 1;
            let mut out = String::new();
            while self.pos < self.chars.len() {
                let c = self.chars[self.pos];
                if c == '\\' {
                    self.pos += 1;
                    if self.pos < self.chars.len() {
                        let esc = self.chars[self.pos];
                        match esc {
                            'n' => out.push('\n'),
                            't' => out.push('\t'),
                            'r' => out.push('\r'),
                            'u' if self.pos + 4 < self.chars.len() => {
                                let hex: String =
                                    self.chars[self.pos + 1..self.pos + 5].iter().collect();
                                if let Ok(n) = u32::from_str_radix(&hex, 16) {
                                    if let Some(uc) = char::from_u32(n) {
                                        out.push(uc);
                                    }
                                }
                                self.pos += 4;
                            }
                            _ => out.push(esc),
                        }
                        self.pos += 1;
                    }
                    continue;
                }
                if c == quote {
                    self.pos += 1;
                    return Token { kind: TK::Str, value: out, pos: start };
                }
                out.push(c);
                self.pos += 1;
            }
            return Token { kind: TK::Str, value: out, pos: start };
        }

        // Numbers
        if ch.is_ascii_digit() {
            while self.pos < self.chars.len()
                && (self.chars[self.pos].is_ascii_digit() || self.chars[self.pos] == '.')
            {
                self.pos += 1;
            }
            let s: String = self.chars[start..self.pos].iter().collect();
            return Token { kind: TK::Num, value: s, pos: start };
        }

        // Identifiers / keywords
        if ch.is_alphabetic() || ch == '_' {
            while self.pos < self.chars.len() {
                let c = self.chars[self.pos];
                if c == ':' {
                    if self.pos + 1 < self.chars.len()
                        && (self.chars[self.pos + 1].is_alphanumeric()
                            || self.chars[self.pos + 1] == '_'
                            || self.chars[self.pos + 1] == '-')
                    {
                        self.pos += 1;
                        continue;
                    }
                    break;
                }
                if c.is_alphanumeric() || c == '_' || c == '-' {
                    self.pos += 1;
                } else {
                    break;
                }
            }
            let s: String = self.chars[start..self.pos].iter().collect();
            let kind = if KEYWORDS.contains(&s.as_str()) { TK::Kw } else { TK::Ident };
            return Token { kind, value: s, pos: start };
        }

        if ch == '@' {
            self.pos += 1;
            return Token { kind: TK::At, value: "@".into(), pos: start };
        }

        // Fallback
        self.pos += 1;
        Token { kind: TK::Ident, value: ch.to_string(), pos: start }
    }
}
