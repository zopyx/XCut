export type TokenKind = "EOF" | "KW" | "IDENT" | "OP" | "PUNCT" | "STRING" | "NUMBER" | "DOT" | "SLASH" | "AT";

export interface Token {
  kind: TokenKind;
  value: string;
  pos: number;
}

const KEYWORDS = new Set([
  "xform",
  "version",
  "import",
  "as",
  "ns",
  "def",
  "var",
  "let",
  "in",
  "for",
  "where",
  "return",
  "if",
  "then",
  "else",
  "match",
  "case",
  "default",
  "and",
  "or",
  "not",
  "div",
  "mod",
  "rule",
]);

export class Lexer {
  private text: string;
  pos: number;
  private buffer: Token | null;

  constructor(text: string) {
    this.text = text;
    this.pos = 0;
    this.buffer = null;
  }

  peek(): Token {
    if (this.buffer === null) {
      this.buffer = this.nextToken();
    }
    return this.buffer;
  }

  next(): Token {
    if (this.buffer !== null) {
      const tok = this.buffer;
      this.buffer = null;
      return tok;
    }
    return this.nextToken();
  }

  expect(kind: TokenKind, value?: string): Token {
    const tok = this.next();
    if (tok.kind !== kind || (value !== undefined && tok.value !== value)) {
      throw new Error(`Expected ${kind} ${value ?? ""} at ${tok.pos}`);
    }
    return tok;
  }

  private skipWsComments(): void {
    while (this.pos < this.text.length) {
      const ch = this.text[this.pos];
      if (/\s/.test(ch)) {
        this.pos += 1;
        continue;
      }
      if (ch === "#") {
        while (this.pos < this.text.length && this.text[this.pos] !== "\n") {
          this.pos += 1;
        }
        continue;
      }
      break;
    }
  }

  private nextToken(): Token {
    this.skipWsComments();
    if (this.pos >= this.text.length) {
      return { kind: "EOF", value: "", pos: this.pos };
    }

    const ch = this.text[this.pos];

    if (ch === ":" && this.text[this.pos + 1] === "=") {
      const start = this.pos;
      this.pos += 2;
      return { kind: "OP", value: ":=", pos: start };
    }

    if ("(){}[],:;".includes(ch)) {
      this.pos += 1;
      return { kind: "PUNCT", value: ch, pos: this.pos - 1 };
    }

    if (ch === ".") {
      const start = this.pos;
      if (this.text.slice(this.pos, this.pos + 2) === "..") {
        this.pos += 2;
        return { kind: "DOT", value: "..", pos: start };
      }
      if (this.text.slice(this.pos, this.pos + 3) === ".//") {
        this.pos += 3;
        return { kind: "DOT", value: ".//", pos: start };
      }
      this.pos += 1;
      return { kind: "DOT", value: ".", pos: start };
    }

    if (ch === "/") {
      const start = this.pos;
      if (this.text.slice(this.pos, this.pos + 2) === "//") {
        this.pos += 2;
        return { kind: "SLASH", value: "//", pos: start };
      }
      this.pos += 1;
      return { kind: "SLASH", value: "/", pos: start };
    }

    if ("<>=!+-*".includes(ch)) {
      const start = this.pos;
      this.pos += 1;
      if (this.text[this.pos] === "=") {
        this.pos += 1;
        return { kind: "OP", value: this.text.slice(start, this.pos), pos: start };
      }
      return { kind: "OP", value: ch, pos: start };
    }

    if (ch === "'" || ch === "\"") {
      const quote = ch;
      const start = this.pos;
      this.pos += 1;
      const out: string[] = [];
      while (this.pos < this.text.length) {
        const c = this.text[this.pos];
        if (c === "\\") {
          this.pos += 1;
          if (this.pos >= this.text.length) break;
          const esc = this.text[this.pos];
          if (esc === "n") out.push("\n");
          else if (esc === "t") out.push("\t");
          else if (esc === "r") out.push("\r");
          else if (esc === "u") {
            const hex = this.text.slice(this.pos + 1, this.pos + 5);
            out.push(String.fromCharCode(parseInt(hex, 16)));
            this.pos += 4;
          } else out.push(esc);
          this.pos += 1;
          continue;
        }
        if (c === quote) {
          this.pos += 1;
          return { kind: "STRING", value: out.join(""), pos: start };
        }
        out.push(c);
        this.pos += 1;
      }
      throw new Error(`Unterminated string at ${start}`);
    }

    if (/\d/.test(ch)) {
      const start = this.pos;
      while (this.pos < this.text.length && /[\d.]/.test(this.text[this.pos])) {
        this.pos += 1;
      }
      return { kind: "NUMBER", value: this.text.slice(start, this.pos), pos: start };
    }

    if (/[A-Za-z_]/.test(ch)) {
      const start = this.pos;
      while (this.pos < this.text.length) {
        const c = this.text[this.pos];
        if (c === ":") {
          const next = this.text[this.pos + 1];
          if (next && /[A-Za-z0-9_-]/.test(next)) {
            this.pos += 1;
            continue;
          }
          break;
        }
        if (!/[A-Za-z0-9_-]/.test(c)) break;
        this.pos += 1;
      }
      const val = this.text.slice(start, this.pos);
      if (KEYWORDS.has(val)) {
        return { kind: "KW", value: val, pos: start };
      }
      return { kind: "IDENT", value: val, pos: start };
    }

    if (ch === "@") {
      this.pos += 1;
      return { kind: "AT", value: "@", pos: this.pos - 1 };
    }

    throw new Error(`Unexpected character ${JSON.stringify(ch)} at ${this.pos}`);
  }

  clearBuffer(): void {
    this.buffer = null;
  }
}
