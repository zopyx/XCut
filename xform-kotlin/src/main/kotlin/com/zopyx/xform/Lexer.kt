package com.zopyx.xform

enum class TokenKind {
    EOF, KW, IDENT, OP, PUNCT, STRING, NUMBER, DOT, SLASH, AT
}

data class Token(val kind: TokenKind, val value: String, val pos: Int)

class XFormException(message: String) : RuntimeException(message)

class Lexer(private val text: String) {
    internal var pos = 0
    internal var buffer: Token? = null

    private val keywords = setOf(
        "xform", "version", "import", "as", "ns", "def", "var", "let", "in",
        "for", "where", "return", "if", "then", "else", "match", "case",
        "default", "and", "or", "not", "div", "mod", "rule"
    )

    fun peek(): Token {
        if (buffer == null) {
            buffer = nextToken()
        }
        return buffer!!
    }

    fun next(): Token {
        if (buffer != null) {
            val tok = buffer!!
            buffer = null
            return tok
        }
        return nextToken()
    }

    fun expect(kind: TokenKind, value: String? = null): Token {
        val tok = next()
        if (tok.kind != kind || (value != null && tok.value != value)) {
            throw XFormException("expected $kind ${value ?: ""} at ${tok.pos}")
        }
        return tok
    }

    fun clearBuffer() {
        buffer = null
    }

    private fun skipWsComments() {
        while (pos < text.length) {
            val ch = text[pos]
            if (ch.isWhitespace()) {
                pos++
                continue
            }
            if (ch == '#') {
                while (pos < text.length && text[pos] != '\n') {
                    pos++
                }
                continue
            }
            break
        }
    }

    private fun nextToken(): Token {
        skipWsComments()
        if (pos >= text.length) {
            return Token(TokenKind.EOF, "", pos)
        }

        val ch = text[pos]

        // Assignment operator :=
        if (ch == ':' && pos + 1 < text.length && text[pos + 1] == '=') {
            val start = pos
            pos += 2
            return Token(TokenKind.OP, ":=", start)
        }

        // Punctuation
        if (ch in "(){}[],:;") {
            pos++
            return Token(TokenKind.PUNCT, ch.toString(), pos - 1)
        }

        // Dot operators (., .., .//)
        if (ch == '.') {
            val start = pos
            if (pos + 2 < text.length && text.substring(pos, pos + 3) == ".//") {
                pos += 3
                return Token(TokenKind.DOT, ".//", start)
            }
            if (pos + 1 < text.length && text[pos + 1] == '.') {
                pos += 2
                return Token(TokenKind.DOT, "..", start)
            }
            pos++
            return Token(TokenKind.DOT, ".", start)
        }

        // Slash operators (/ or //)
        if (ch == '/') {
            val start = pos
            if (pos + 1 < text.length && text[pos + 1] == '/') {
                pos += 2
                return Token(TokenKind.SLASH, "//", start)
            }
            pos++
            return Token(TokenKind.SLASH, "/", start)
        }

        // Comparison and arithmetic operators
        if (ch in "<>=!+-*") {
            val start = pos
            pos++
            if (pos < text.length && text[pos] == '=') {
                pos++
                return Token(TokenKind.OP, text.substring(start, pos), start)
            }
            return Token(TokenKind.OP, ch.toString(), start)
        }

        // String literals
        if (ch == '\'' || ch == '"') {
            return readString(ch)
        }

        // Numbers
        if (ch.isDigit()) {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) {
                pos++
            }
            return Token(TokenKind.NUMBER, text.substring(start, pos), start)
        }

        // Identifiers and keywords
        if (ch.isLetter() || ch == '_') {
            val start = pos
            while (pos < text.length) {
                val c = text[pos]
                if (c == ':') {
                    if (pos + 1 < text.length) {
                        val next = text[pos + 1]
                        if (next.isLetterOrDigit() || next == '_' || next == '-') {
                            pos++
                            continue
                        }
                    }
                    break
                }
                if (!(c.isLetterOrDigit() || c == '_' || c == '-')) {
                    break
                }
                pos++
            }
            val value = text.substring(start, pos)
            val kind = if (value in keywords) TokenKind.KW else TokenKind.IDENT
            return Token(kind, value, start)
        }

        // @ for attributes
        if (ch == '@') {
            pos++
            return Token(TokenKind.AT, "@", pos - 1)
        }

        throw XFormException("unexpected character '$ch' at $pos")
    }

    private fun readString(quote: Char): Token {
        val start = pos
        pos++
        val out = StringBuilder()
        while (pos < text.length) {
            val c = text[pos]
            if (c == '\\') {
                pos++
                if (pos >= text.length) break
                when (val esc = text[pos]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'u' -> {
                        if (pos + 4 < text.length) {
                            val hex = text.substring(pos + 1, pos + 5)
                            out.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                    }
                    else -> out.append(esc)
                }
                pos++
                continue
            }
            if (c == quote) {
                pos++
                return Token(TokenKind.STRING, out.toString(), start)
            }
            out.append(c)
            pos++
        }
        throw XFormException("unterminated string at $start")
    }
}
