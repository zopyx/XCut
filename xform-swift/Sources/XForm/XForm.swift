import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

public final class Node {
    public let kind: String
    public var name: String?
    public var value: String?
    public var children: [Node]
    public var attrs: [String: String]
    public var attrOrder: [String]
    public weak var parent: Node?

    public init(kind: String, name: String? = nil, value: String? = nil, children: [Node] = [], attrs: [String: String] = [:], attrOrder: [String] = [], parent: Node? = nil) {
        self.kind = kind
        self.name = name
        self.value = value
        self.children = children
        self.attrs = attrs
        self.attrOrder = attrOrder
        self.parent = parent
    }

    public func stringValue() -> String {
        switch kind {
        case "text", "attribute":
            return value ?? ""
        case "element", "document":
            return children.map { $0.stringValue() }.joined()
        default:
            return ""
        }
    }
}

public func parseXML(_ text: String) throws -> Node {
    let normalized = replaceNamedEntities(text)
    let builder = XMLBuilder()
    let parser = XMLParser(data: Data(normalized.utf8))
    parser.delegate = builder
    if !parser.parse() {
        throw parser.parserError ?? NSError(domain: "xform", code: 1)
    }
    return builder.doc
}

final class XMLBuilder: NSObject, XMLParserDelegate {
    let doc = Node(kind: "document")
    private var stack: [Node] = []

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        let order = attributeDict.keys.sorted()
        let node = Node(kind: "element", name: elementName, attrs: attributeDict, attrOrder: order)
        if let parent = stack.last {
            node.parent = parent
            parent.children.append(node)
        } else {
            node.parent = doc
            doc.children.append(node)
        }
        stack.append(node)
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if !stack.isEmpty { _ = stack.removeLast() }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard let parent = stack.last else { return }
        let node = Node(kind: "text", value: string)
        node.parent = parent
        parent.children.append(node)
    }

    func parser(_ parser: XMLParser, foundComment comment: String) {
        guard let parent = stack.last else { return }
        let node = Node(kind: "comment", value: comment)
        node.parent = parent
        parent.children.append(node)
    }

    func parser(_ parser: XMLParser, foundProcessingInstructionWithTarget target: String, data: String?) {
        guard let parent = stack.last else { return }
        let node = Node(kind: "pi", name: target, value: data ?? "")
        node.parent = parent
        parent.children.append(node)
    }
}

public func deepCopy(_ node: Node, recurse: Bool = true) -> Node {
    let copied = Node(kind: node.kind, name: node.name, value: node.value, attrs: node.attrs, attrOrder: node.attrOrder)
    if recurse {
        copied.children = node.children.map { child in
            let c = deepCopy(child, recurse: true)
            c.parent = copied
            return c
        }
    }
    return copied
}

public func iterDescendants(_ node: Node) -> [Node] {
    var out: [Node] = []
    for child in node.children {
        out.append(child)
        out.append(contentsOf: iterDescendants(child))
    }
    return out
}

public func serialize(_ item: Node) -> String {
    switch item.kind {
    case "document":
        return item.children.map { serialize($0) }.joined()
    case "text":
        return escapeText(item.value ?? "")
    case "attribute":
        return escapeAttr(item.value ?? "")
    case "element":
        let keys = item.attrOrder.isEmpty ? item.attrs.keys.sorted() : item.attrOrder
        let attrs = keys.map { key in
            " \(key)=\"\(escapeAttr(item.attrs[key] ?? ""))\""
        }.joined()
        if item.children.isEmpty {
            return "<\(item.name ?? "")\(attrs)/>"
        }
        let inner = item.children.map { serialize($0) }.joined()
        return "<\(item.name ?? "")\(attrs)>\(inner)</\(item.name ?? "")>"
    default:
        return ""
    }
}

private func escapeText(_ text: String) -> String {
    return text.replacingOccurrences(of: "&", with: "&amp;")
        .replacingOccurrences(of: "<", with: "&lt;")
        .replacingOccurrences(of: ">", with: "&gt;")
}

private func escapeAttr(_ text: String) -> String {
    return escapeText(text).replacingOccurrences(of: "\"", with: "&quot;")
}

private func replaceNamedEntities(_ text: String) -> String {
    return text
        .replacingOccurrences(of: "&mdash;", with: "—")
        .replacingOccurrences(of: "&hellip;", with: "…")
        .replacingOccurrences(of: "&nbsp;", with: "\u{00A0}")
}

// AST

public final class Module {
    public let functions: [String: FunctionDef]
    public let rules: [String: [RuleDef]]
    public let vars: [String: Expr]
    public let namespaces: [String: String]
    public let imports: [(String, String?)]
    public let expr: Expr?

    public init(functions: [String: FunctionDef], rules: [String: [RuleDef]], vars: [String: Expr], namespaces: [String: String], imports: [(String, String?)], expr: Expr?) {
        self.functions = functions
        self.rules = rules
        self.vars = vars
        self.namespaces = namespaces
        self.imports = imports
        self.expr = expr
    }
}

public protocol Expr {}

public struct Literal: Expr { public let value: Any }
public struct VarRef: Expr { public let name: String }
public struct IfExpr: Expr { public let cond: Expr; public let thenExpr: Expr; public let elseExpr: Expr }
public struct LetExpr: Expr { public let name: String; public let value: Expr; public let body: Expr }
public struct ForExpr: Expr { public let name: String; public let seq: Expr; public let whereExpr: Expr?; public let body: Expr }
public struct MatchExpr: Expr { public let target: Expr; public let cases: [(Pattern, Expr)]; public let defaultExpr: Expr? }
public struct FuncCall: Expr { public let name: String; public let args: [Expr]; public let namedArgs: [(String, Expr)] }
public struct ApplyExpr: Expr { public let expr: Expr; public let ruleset: String? }
public struct UnaryOp: Expr { public let op: String; public let expr: Expr }
public struct BinaryOp: Expr { public let op: String; public let left: Expr; public let right: Expr }
public struct PathExpr: Expr { public let start: PathStart; public let steps: [PathStep] }
public struct Constructor: Expr { public let name: String; public let attrs: [(String, Expr)]; public let contents: [Expr] }
public struct TextConstructor: Expr { public let expr: Expr }
public struct CommentConstructor: Expr { public let expr: Expr }
public struct PIConstructor: Expr { public let target: Expr; public let value: Expr }
public struct Text: Expr { public let value: String }
public struct Interp: Expr { public let expr: Expr }

public struct PathStart { public let kind: String; public let name: String? }
public struct PathStep { public let axis: String; public let test: StepTest; public let predicates: [Expr] }
public struct StepTest { public let kind: String; public let name: String? }

public protocol Pattern {}
public struct WildcardPattern: Pattern {}
public struct ElementPattern: Pattern { public let name: String; public let varName: String?; public let attrs: [(String, Any?)]; public let children: [Pattern] }
public struct TypedPattern: Pattern { public let kind: String }
public struct AttributePattern: Pattern { public let name: String; public let value: Any? }
public struct LiteralPattern: Pattern { public let value: String }

public struct Param { public let name: String; public let typeRef: String?; public let defaultExpr: Expr? }
public struct FunctionDef { public let params: [Param]; public let body: Expr }
public struct RuleDef { public let pattern: Pattern; public let body: Expr }

// Lexer

enum TokenKind { case eof, kw, ident, op, punct, string, number, dot, slash, at }
struct Token { let kind: TokenKind; let value: String; let pos: Int }

final class Lexer {
    private let text: [Character]
    var pos: Int
    private var buffer: Token?

    init(_ text: String) {
        self.text = Array(text)
        self.pos = 0
        self.buffer = nil
    }

    func peek() -> Token {
        if buffer == nil { buffer = nextToken() }
        return buffer!
    }

    func next() -> Token {
        if let tok = buffer { buffer = nil; return tok }
        return nextToken()
    }

    func expect(_ kind: TokenKind, _ value: String? = nil) -> Token {
        let tok = next()
        if tok.kind != kind || (value != nil && tok.value != value!) {
            fatalError("Expected \(kind) \(value ?? "") at \(tok.pos)")
        }
        return tok
    }

    func clearBuffer() { buffer = nil }
    func snapshotBuffer() -> Token? { return buffer }
    func restoreBuffer(_ tok: Token?) { buffer = tok }

    private func skipWsComments() {
        while pos < text.count {
            let ch = text[pos]
            if ch.isWhitespace { pos += 1; continue }
            if ch == "#" {
                while pos < text.count && text[pos] != "\n" { pos += 1 }
                continue
            }
            break
        }
    }

    private func nextToken() -> Token {
        skipWsComments()
        if pos >= text.count { return Token(kind: .eof, value: "", pos: pos) }
        let ch = text[pos]

        if ch == ":" && pos + 1 < text.count && text[pos + 1] == "=" {
            let start = pos; pos += 2
            return Token(kind: .op, value: ":=", pos: start)
        }
        if "(){}[],:;".contains(ch) {
            pos += 1
            return Token(kind: .punct, value: String(ch), pos: pos - 1)
        }
        if ch == "." {
            let start = pos
            if pos + 1 < text.count && text[pos] == "." && text[pos + 1] == "." {
                pos += 2
                return Token(kind: .dot, value: "..", pos: start)
            }
            if pos + 2 < text.count && text[pos] == "." && text[pos + 1] == "/" && text[pos + 2] == "/" {
                pos += 3
                return Token(kind: .dot, value: ".//", pos: start)
            }
            pos += 1
            return Token(kind: .dot, value: ".", pos: start)
        }
        if ch == "/" {
            let start = pos
            if pos + 1 < text.count && text[pos + 1] == "/" {
                pos += 2
                return Token(kind: .slash, value: "//", pos: start)
            }
            pos += 1
            return Token(kind: .slash, value: "/", pos: start)
        }
        if "<>=!+-*".contains(ch) {
            let start = pos
            pos += 1
            if pos < text.count && text[pos] == "=" {
                pos += 1
                return Token(kind: .op, value: String(text[start..<pos]), pos: start)
            }
            return Token(kind: .op, value: String(ch), pos: start)
        }
        if ch == "'" || ch == "\"" {
            let quote = ch
            let start = pos
            pos += 1
            var out: [Character] = []
            while pos < text.count {
                let c = text[pos]
                if c == "\\" {
                    pos += 1
                    if pos >= text.count { break }
                    let esc = text[pos]
                    switch esc {
                    case "n": out.append("\n")
                    case "t": out.append("\t")
                    case "r": out.append("\r")
                    case "u":
                        if pos + 4 < text.count {
                            let hex = String(text[(pos + 1)...(pos + 4)])
                            if let v = UInt32(hex, radix: 16), let scalar = UnicodeScalar(v) {
                                out.append(Character(scalar))
                            }
                            pos += 4
                        }
                    default:
                        out.append(esc)
                    }
                    pos += 1
                    continue
                }
                if c == quote {
                    pos += 1
                    return Token(kind: .string, value: String(out), pos: start)
                }
                out.append(c)
                pos += 1
            }
            fatalError("Unterminated string at \(start)")
        }
        if ch.isNumber {
            let start = pos
            while pos < text.count && (text[pos].isNumber || text[pos] == ".") { pos += 1 }
            return Token(kind: .number, value: String(text[start..<pos]), pos: start)
        }
        if ch.isLetter || ch == "_" {
            let start = pos
            while pos < text.count {
                let c = text[pos]
                if c == ":" {
                    if pos + 1 < text.count {
                        let n = text[pos + 1]
                        if n.isLetter || n.isNumber || n == "_" || n == "-" {
                            pos += 1
                            continue
                        }
                    }
                    break
                }
                if !(c.isLetter || c.isNumber || c == "_" || c == "-") { break }
                pos += 1
            }
            let val = String(text[start..<pos])
            if keywords.contains(val) {
                return Token(kind: .kw, value: val, pos: start)
            }
            return Token(kind: .ident, value: val, pos: start)
        }
        if ch == "@" {
            pos += 1
            return Token(kind: .at, value: "@", pos: pos - 1)
        }
        fatalError("Unexpected character \(ch) at \(pos)")
    }
}

private let keywords: Set<String> = [
    "xform", "version", "import", "as", "ns", "def", "var", "let", "in", "for", "where", "return",
    "if", "then", "else", "match", "case", "default", "and", "or", "not", "div", "mod", "rule",
    "true", "false", "null", "string", "number", "boolean", "map",
    "apply", "text", "comment", "pi"
]

private let reservedFunctionNames: Set<String> = [
    "string", "number", "boolean", "typeOf", "name", "attr", "text", "children",
    "elements", "attributes", "copy", "count", "empty", "distinct", "sort",
    "concat", "seq", "head", "tail", "last", "index", "lookup", "groupBy",
    "sum", "position", "apply", "contains", "startsWith", "endsWith",
    "substring", "stringLength", "upperCase", "lowerCase", "normalizeSpace",
    "replace", "matches", "keys", "mapSize"
]

// Parser

public final class Parser {
    private let text: String
    private let lexer: Lexer

    public init(_ text: String) {
        self.text = text
        self.lexer = Lexer(text)
    }

    public func parseModule() -> Module {
        var functions: [String: FunctionDef] = [:]
        var rules: [String: [RuleDef]] = [:]
        var vars: [String: Expr] = [:]
        var namespaces: [String: String] = [:]
        var imports: [(String, String?)] = []

        var tok = lexer.peek()
        if tok.kind == .kw && tok.value == "xform" {
            _ = lexer.next()
            _ = lexer.expect(.kw, "version")
            let version = lexer.expect(.string).value
            if version != "2.0" && version != "2.1" { fatalError("XFST0005: unsupported version") }
            _ = lexer.expect(.punct, ";")
        }

        while true {
            tok = lexer.peek()
            if tok.kind == .kw && tok.value == "ns" {
                parseNs(&namespaces)
                continue
            }
            if tok.kind == .kw && tok.value == "import" {
                parseImport(&imports)
                continue
            }
            if tok.kind == .kw && tok.value == "var" {
                let (name, expr) = parseVar()
                vars[name] = expr
                continue
            }
            if tok.kind == .kw && tok.value == "def" {
                parseDef(&functions)
                continue
            }
            if tok.kind == .kw && tok.value == "rule" {
                parseRule(&rules)
                continue
            }
            break
        }

        var expr: Expr? = nil
        if lexer.peek().kind != .eof {
            expr = parseExpr()
            if lexer.peek().kind != .eof { fatalError("Unexpected token at \(lexer.peek().pos)") }
        }

        return Module(functions: functions, rules: rules, vars: vars, namespaces: namespaces, imports: imports, expr: expr)
    }

    private func parseNs(_ namespaces: inout [String: String]) {
        _ = lexer.expect(.kw, "ns")
        let prefix = lexer.expect(.string).value
        _ = lexer.expect(.op, "=")
        let uri = lexer.expect(.string).value
        _ = lexer.expect(.punct, ";")
        namespaces[prefix] = uri
    }

    private func parseImport(_ imports: inout [(String, String?)]) {
        _ = lexer.expect(.kw, "import")
        let iri = lexer.expect(.string).value
        var alias: String? = nil
        if lexer.peek().kind == .kw && lexer.peek().value == "as" {
            _ = lexer.next()
            alias = expectIdentifier()
        }
        _ = lexer.expect(.punct, ";")
        imports.append((iri, alias))
    }

    private func parseVar() -> (String, Expr) {
        _ = lexer.expect(.kw, "var")
        let name = expectIdentifier()
        _ = lexer.expect(.op, ":=")
        let value = parseExpr()
        _ = lexer.expect(.punct, ";")
        return (name, value)
    }

    private func parseDef(_ functions: inout [String: FunctionDef]) {
        _ = lexer.expect(.kw, "def")
        let name = parseQName()
        if reservedFunctionNames.contains(name) { fatalError("XFST0006: reserved function name '\(name)'") }
        _ = lexer.expect(.punct, "(")
        var params: [Param] = []
        if !(lexer.peek().kind == .punct && lexer.peek().value == ")") {
            params.append(parseParam())
            while lexer.peek().kind == .punct && lexer.peek().value == "," {
                _ = lexer.next()
                params.append(parseParam())
            }
        }
        _ = lexer.expect(.punct, ")")
        _ = lexer.expect(.op, ":=")
        let body = parseExpr()
        _ = lexer.expect(.punct, ";")
        functions[name] = FunctionDef(params: params, body: body)
    }

    private func parseParam() -> Param {
        let name = expectIdentifier()
        var typeRef: String? = nil
        var def: Expr? = nil
        if lexer.peek().kind == .punct && lexer.peek().value == ":" {
            _ = lexer.next()
            typeRef = parseTypeRef()
        }
        if lexer.peek().kind == .op && lexer.peek().value == ":=" {
            _ = lexer.next()
            def = parseExpr()
        }
        return Param(name: name, typeRef: typeRef, defaultExpr: def)
    }

    private func parseTypeRef() -> String {
        let tok = lexer.peek()
        if (tok.kind == .ident || tok.kind == .kw) && ["string", "number", "boolean", "null", "map"].contains(tok.value) {
            return lexer.next().value
        }
        return parseQName()
    }

    private func parseRule(_ rules: inout [String: [RuleDef]]) {
        _ = lexer.expect(.kw, "rule")
        let name = parseQName()
        _ = lexer.expect(.kw, "match")
        let pattern = parsePattern()
        _ = lexer.expect(.op, ":=")
        let body = parseExpr()
        _ = lexer.expect(.punct, ";")
        rules[name, default: []].append(RuleDef(pattern: pattern, body: body))
    }

    private func parseExpr() -> Expr {
        let tok = lexer.peek()
        if tok.kind == .kw && tok.value == "if" { return parseIf() }
        if tok.kind == .kw && tok.value == "let" { return parseLet() }
        if tok.kind == .kw && tok.value == "for" { return parseFor() }
        if tok.kind == .kw && tok.value == "match" { return parseMatch() }
        return parseOr()
    }

    private func parseIf() -> Expr {
        _ = lexer.expect(.kw, "if")
        let cond = parseExpr()
        _ = lexer.expect(.kw, "then")
        let thenExpr = parseExpr()
        _ = lexer.expect(.kw, "else")
        let elseExpr = parseExpr()
        return IfExpr(cond: cond, thenExpr: thenExpr, elseExpr: elseExpr)
    }

    private func parseLet() -> Expr {
        _ = lexer.expect(.kw, "let")
        let name = expectIdentifier()
        _ = lexer.expect(.op, ":=")
        let value = parseExpr()
        _ = lexer.expect(.kw, "in")
        let body = parseExpr()
        return LetExpr(name: name, value: value, body: body)
    }

    private func parseFor() -> Expr {
        _ = lexer.expect(.kw, "for")
        let name = expectIdentifier()
        _ = lexer.expect(.kw, "in")
        let seq = parseExpr()
        var whereExpr: Expr? = nil
        if lexer.peek().kind == .kw && lexer.peek().value == "where" {
            _ = lexer.next()
            whereExpr = parseExpr()
        }
        _ = lexer.expect(.kw, "return")
        let body = parseExpr()
        return ForExpr(name: name, seq: seq, whereExpr: whereExpr, body: body)
    }

    private func parseMatch() -> Expr {
        _ = lexer.expect(.kw, "match")
        let target = parseExpr()
        _ = lexer.expect(.punct, ":")
        var cases: [(Pattern, Expr)] = []
        var def: Expr? = nil
        while true {
            let tok = lexer.peek()
            if tok.kind == .kw && tok.value == "case" {
                _ = lexer.next()
                let pattern = parsePattern()
                _ = lexer.expect(.op, "=")
                _ = lexer.expect(.op, ">")
                let expr = parseExpr()
                _ = lexer.expect(.punct, ";")
                cases.append((pattern, expr))
                continue
            }
            if tok.kind == .kw && tok.value == "default" {
                _ = lexer.next()
                _ = lexer.expect(.op, "=")
                _ = lexer.expect(.op, ">")
                def = parseExpr()
                _ = lexer.expect(.punct, ";")
                break
            }
            break
        }
        return MatchExpr(target: target, cases: cases, defaultExpr: def)
    }

    private func parseOr() -> Expr {
        var expr = parseAnd()
        while lexer.peek().kind == .kw && lexer.peek().value == "or" {
            _ = lexer.next()
            let right = parseAnd()
            expr = BinaryOp(op: "or", left: expr, right: right)
        }
        return expr
    }

    private func parseAnd() -> Expr {
        var expr = parseEq()
        while lexer.peek().kind == .kw && lexer.peek().value == "and" {
            _ = lexer.next()
            let right = parseEq()
            expr = BinaryOp(op: "and", left: expr, right: right)
        }
        return expr
    }

    private func parseEq() -> Expr {
        var expr = parseRel()
        while lexer.peek().kind == .op && ["=", "!="].contains(lexer.peek().value) {
            let op = lexer.next().value
            let right = parseRel()
            expr = BinaryOp(op: op, left: expr, right: right)
        }
        return expr
    }

    private func parseRel() -> Expr {
        var expr = parseAdd()
        while lexer.peek().kind == .op && ["<", "<=", ">", ">="].contains(lexer.peek().value) {
            let op = lexer.next().value
            let right = parseAdd()
            expr = BinaryOp(op: op, left: expr, right: right)
        }
        return expr
    }

    private func parseAdd() -> Expr {
        var expr = parseMul()
        while lexer.peek().kind == .op && ["+", "-"].contains(lexer.peek().value) {
            let op = lexer.next().value
            let right = parseMul()
            expr = BinaryOp(op: op, left: expr, right: right)
        }
        return expr
    }

    private func parseMul() -> Expr {
        var expr = parseUnary()
        while true {
            let tok = lexer.peek()
            if tok.kind == .op && tok.value == "*" {
                _ = lexer.next()
                let right = parseUnary()
                expr = BinaryOp(op: "*", left: expr, right: right)
                continue
            }
            if tok.kind == .kw && ["div", "mod"].contains(tok.value) {
                let op = lexer.next().value
                let right = parseUnary()
                expr = BinaryOp(op: op, left: expr, right: right)
                continue
            }
            break
        }
        return expr
    }

    private func parseUnary() -> Expr {
        let tok = lexer.peek()
        if tok.kind == .op && tok.value == "-" {
            _ = lexer.next()
            return UnaryOp(op: "-", expr: parseUnary())
        }
        if tok.kind == .kw && tok.value == "not" {
            _ = lexer.next()
            return UnaryOp(op: "not", expr: parseUnary())
        }
        return parsePrimary()
    }

    private func parsePrimary() -> Expr {
        let tok = lexer.peek()

        if tok.kind == .number {
            _ = lexer.next()
            return Literal(value: Double(tok.value) ?? 0.0)
        }
        if tok.kind == .string {
            _ = lexer.next()
            return Literal(value: tok.value)
        }
        if tok.kind == .kw && ["true", "false", "null"].contains(tok.value) {
            _ = lexer.next()
            switch tok.value {
            case "true": return Literal(value: true)
            case "false": return Literal(value: false)
            case "null": return Literal(value: NSNull())
            default: fatalError("Unexpected")
            }
        }
        if tok.kind == .punct && tok.value == "(" {
            _ = lexer.next()
            let expr = parseExpr()
            _ = lexer.expect(.punct, ")")
            return expr
        }
        if tok.kind == .kw && tok.value == "apply" {
            return parseApply()
        }
        if tok.kind == .kw && tok.value == "text" {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            _ = lexer.next()
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                return TextConstructor(expr: expr)
            }
            if lexer.peek().kind == .punct && lexer.peek().value == "(" {
                return parseFuncCall("text")
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        if tok.kind == .kw && tok.value == "comment" {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            _ = lexer.next()
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                return CommentConstructor(expr: expr)
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        if tok.kind == .kw && tok.value == "pi" {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            _ = lexer.next()
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                let target = parseExpr()
                _ = lexer.expect(.punct, ",")
                let value = parseExpr()
                _ = lexer.expect(.punct, "}")
                return PIConstructor(target: target, value: value)
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        if tok.kind == .op && tok.value == "<" {
            return parseConstructor()
        }
        if tok.kind == .dot || tok.kind == .slash || tok.kind == .at {
            return parsePath(start: nil)
        }
        if tok.kind == .kw && ["string", "number", "boolean", "map"].contains(tok.value) {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            _ = lexer.next()
            if lexer.peek().kind == .punct && lexer.peek().value == "(" {
                return parseFuncCall(tok.value)
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        if tok.kind == .ident {
            let name = lexer.next().value
            if lexer.peek().kind == .punct && lexer.peek().value == "(" {
                return parseFuncCall(name)
            }
            if pathContinues() {
                return parsePath(start: PathStart(kind: "var", name: name))
            }
            return VarRef(name: name)
        }
        fatalError("Unexpected token at \(tok.pos)")
    }

    private func parseApply() -> Expr {
        _ = lexer.expect(.kw, "apply")
        _ = lexer.expect(.punct, "(")
        let expr = parseExpr()
        var ruleset: String? = nil
        if lexer.peek().kind == .punct && lexer.peek().value == "," {
            _ = lexer.next()
            ruleset = parseQName()
        }
        _ = lexer.expect(.punct, ")")
        return ApplyExpr(expr: expr, ruleset: ruleset)
    }

    private func parseFuncCall(_ name: String) -> Expr {
        _ = lexer.expect(.punct, "(")
        var args: [Expr] = []
        var namedArgs: [(String, Expr)] = []
        if !(lexer.peek().kind == .punct && lexer.peek().value == ")") {
            let (argName, argExpr) = parseArgument()
            if let n = argName {
                namedArgs.append((n, argExpr))
            } else {
                args.append(argExpr)
            }
            while lexer.peek().kind == .punct && lexer.peek().value == "," {
                _ = lexer.next()
                let (argName2, argExpr2) = parseArgument()
                if let n = argName2 {
                    namedArgs.append((n, argExpr2))
                } else {
                    if !namedArgs.isEmpty { fatalError("XFST0001: positional argument after named argument") }
                    args.append(argExpr2)
                }
            }
        }
        _ = lexer.expect(.punct, ")")
        return FuncCall(name: name, args: args, namedArgs: namedArgs)
    }

    private func parseArgument() -> (String?, Expr) {
        if lexer.peek().kind == .ident {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            let name = lexer.next().value
            if lexer.peek().kind == .op && lexer.peek().value == ":=" {
                _ = lexer.next()
                let expr = parseExpr()
                return (name, expr)
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        return (nil, parseExpr())
    }

    private func pathContinues() -> Bool {
        let tok = lexer.peek()
        return tok.kind == .slash || tok.kind == .dot || tok.kind == .at
    }

    private func parsePath(start: PathStart?) -> Expr {
        var actualStart = start
        if actualStart == nil {
            let tok = lexer.next()
            if tok.kind == .dot {
                actualStart = tok.value == ".//" ? PathStart(kind: "desc", name: nil) : PathStart(kind: "context", name: nil)
            } else if tok.kind == .slash {
                actualStart = tok.value == "//" ? PathStart(kind: "desc_root", name: nil) : PathStart(kind: "root", name: nil)
            } else if tok.kind == .at {
                let name = parseQName()
                let steps = [PathStep(axis: "attr", test: StepTest(kind: "name", name: name), predicates: [])]
                return PathExpr(start: PathStart(kind: "context", name: nil), steps: steps)
            } else {
                fatalError("Invalid path start at \(tok.pos)")
            }
        }
        var steps: [PathStep] = []
        if ["root", "context", "var"].contains(actualStart!.kind) {
            let tok = lexer.peek()
            if tok.kind == .at {
                _ = lexer.next()
                let test = StepTest(kind: "name", name: parseQName())
                steps.append(PathStep(axis: "attr", test: test, predicates: []))
            } else if tok.kind == .op && tok.value == "*" {
                let test = parseStepTest()
                let preds = parsePredicates()
                steps.append(PathStep(axis: "child", test: test, predicates: preds))
            } else if tok.kind == .ident || tok.kind == .kw {
                let test = parseStepTest()
                let preds = parsePredicates()
                steps.append(PathStep(axis: "child", test: test, predicates: preds))
            }
        }
        if ["desc", "desc_root"].contains(actualStart!.kind) {
            let tok = lexer.peek()
            if tok.kind == .ident || tok.kind == .kw || tok.kind == .op {
                let test = parseStepTest()
                let preds = parsePredicates()
                steps.append(PathStep(axis: "desc_or_self", test: test, predicates: preds))
            }
        }

        while true {
            let tok = lexer.peek()
            if tok.kind == .slash {
                var axis = tok.value == "/" ? "child" : "desc"
                _ = lexer.next()
                var test: StepTest
                var preds: [Expr] = []
                if lexer.peek().kind == .at {
                    _ = lexer.next()
                    test = StepTest(kind: "name", name: parseQName())
                    axis = "attr"
                } else {
                    test = parseStepTest()
                    preds = parsePredicates()
                }
                steps.append(PathStep(axis: axis, test: test, predicates: preds))
                continue
            }
            if tok.kind == .dot {
                if tok.value == "." {
                    _ = lexer.next()
                    if lexer.peek().kind == .at {
                        _ = lexer.next()
                        let test = StepTest(kind: "name", name: parseQName())
                        steps.append(PathStep(axis: "attr", test: test, predicates: []))
                    } else {
                        steps.append(PathStep(axis: "self", test: StepTest(kind: "node", name: nil), predicates: []))
                    }
                    continue
                }
                if tok.value == ".." {
                    _ = lexer.next()
                    steps.append(PathStep(axis: "parent", test: StepTest(kind: "node", name: nil), predicates: []))
                    continue
                }
            }
            if tok.kind == .at {
                _ = lexer.next()
                let test = StepTest(kind: "name", name: parseQName())
                steps.append(PathStep(axis: "attr", test: test, predicates: []))
                continue
            }
            break
        }
        return PathExpr(start: actualStart!, steps: steps)
    }

    private func parseStepTest() -> StepTest {
        let tok = lexer.peek()
        if tok.kind == .op && tok.value == "*" {
            _ = lexer.next()
            return StepTest(kind: "wildcard", name: nil)
        }
        if tok.kind == .ident || tok.kind == .kw {
            if ["text", "node", "element", "comment", "pi", "document"].contains(tok.value) {
                _ = lexer.next()
                _ = lexer.expect(.punct, "(")
                _ = lexer.expect(.punct, ")")
                return StepTest(kind: tok.value, name: nil)
            }
            let name = parseQName()
            return StepTest(kind: "name", name: name)
        }
        fatalError("Invalid step test at \(tok.pos)")
    }

    private func parsePredicates() -> [Expr] {
        var preds: [Expr] = []
        while lexer.peek().kind == .punct && lexer.peek().value == "[" {
            _ = lexer.next()
            preds.append(parseExpr())
            _ = lexer.expect(.punct, "]")
        }
        return preds
    }

    private func expectIdentifier() -> String {
        let tok = lexer.next()
        if tok.kind == .ident { return tok.value }
        if tok.kind == .kw { fatalError("XFST0006: reserved word '\(tok.value)' used as identifier") }
        fatalError("Expected identifier at \(tok.pos)")
    }

    private func parseQName() -> String {
        return expectIdentifier()
    }

    private func parsePattern() -> Pattern {
        let tok = lexer.peek()
        if tok.kind == .at {
            _ = lexer.next()
            let name = parseQName()
            var value: Any? = nil
            if lexer.peek().kind == .op && lexer.peek().value == "=" {
                let savedPos = lexer.pos
                let savedBuf = lexer.snapshotBuffer()
                _ = lexer.next()
                if lexer.peek().kind == .op && lexer.peek().value == ">" {
                    lexer.pos = savedPos
                    lexer.restoreBuffer(savedBuf)
                } else {
                    value = parsePatternLiteral()
                }
            }
            return AttributePattern(name: name, value: value)
        }
        if tok.kind == .ident || tok.kind == .kw {
            if ["node", "element", "text", "comment", "pi", "document"].contains(tok.value) {
                _ = lexer.next()
                _ = lexer.expect(.punct, "(")
                _ = lexer.expect(.punct, ")")
                return TypedPattern(kind: tok.value)
            }
            if tok.value == "_" {
                _ = lexer.next()
                return WildcardPattern()
            }
        }
        if tok.kind == .string {
            _ = lexer.next()
            return LiteralPattern(value: tok.value)
        }
        if tok.kind == .op && tok.value == "<" {
            _ = lexer.next()
            let name = parseQName()
            var attrs: [(String, Any?)] = []
            while lexer.peek().kind == .at {
                _ = lexer.next()
                let attrName = parseQName()
                var attrValue: Any? = nil
                if lexer.peek().kind == .op && lexer.peek().value == "=" {
                    _ = lexer.next()
                    attrValue = parsePatternLiteral()
                }
                attrs.append((attrName, attrValue))
            }
            _ = lexer.expect(.op, ">")
            var varName: String? = nil
            var children: [Pattern] = []
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                varName = expectIdentifier()
                _ = lexer.expect(.punct, "}")
            } else if lexer.peek().kind == .op && lexer.peek().value == "<" {
                while !(lexer.peek().kind == .op && lexer.peek().value == "<" && textAt(lexer.pos, prefix: "/")) {
                    children.append(parsePattern())
                }
            }
            _ = lexer.expect(.op, "<")
            _ = lexer.expect(.slash, "/")
            let end = parseQName()
            if end != name { fatalError("Mismatched pattern end tag") }
            _ = lexer.expect(.op, ">")
            return ElementPattern(name: name, varName: varName, attrs: attrs, children: children)
        }
        fatalError("Invalid pattern at \(tok.pos)")
    }

    private func parsePatternLiteral() -> Any {
        let tok = lexer.peek()
        if tok.kind == .string {
            _ = lexer.next()
            return tok.value
        }
        if tok.kind == .number {
            _ = lexer.next()
            return Double(tok.value) ?? 0.0
        }
        if tok.kind == .kw && ["true", "false", "null"].contains(tok.value) {
            _ = lexer.next()
            switch tok.value {
            case "true": return true
            case "false": return false
            case "null": return NSNull()
            default: fatalError("Invalid literal in pattern")
            }
        }
        fatalError("Invalid literal in pattern")
    }

    private func parseConstructor() -> Expr {
        _ = lexer.expect(.op, "<")
        let name = parseQName()
        var attrs: [(String, Expr)] = []
        while true {
            let tok = lexer.peek()
            if tok.kind == .op && tok.value == ">" {
                _ = lexer.next()
                break
            }
            if tok.kind == .slash && tok.value == "/" {
                _ = lexer.next()
                _ = lexer.expect(.op, ">")
                return Constructor(name: name, attrs: attrs, contents: [])
            }
            let attrName = parseQName()
            _ = lexer.expect(.op, "=")
            _ = lexer.expect(.punct, "{")
            let expr = parseExpr()
            _ = lexer.expect(.punct, "}")
            attrs.append((attrName, expr))
        }

        var contents: [Expr] = []
        lexer.clearBuffer()
        while true {
            if lexer.pos >= text.count {
                fatalError("Unterminated constructor")
            }
            if textAt(lexer.pos, prefix: "</") {
                let (endName, newPos) = readEndTag()
                if endName != name { fatalError("Mismatched end tag") }
                lexer.pos = newPos
                lexer.clearBuffer()
                break
            }
            if textAt(lexer.pos, prefix: "text{") {
                lexer.pos += 4
                lexer.clearBuffer()
                _ = lexer.expect(.punct, "{")
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                contents.append(TextConstructor(expr: expr))
                continue
            }
            if textAt(lexer.pos, prefix: "comment{") {
                lexer.pos += 7
                lexer.clearBuffer()
                _ = lexer.expect(.punct, "{")
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                contents.append(CommentConstructor(expr: expr))
                continue
            }
            if textAt(lexer.pos, prefix: "pi{") {
                lexer.pos += 2
                lexer.clearBuffer()
                _ = lexer.expect(.punct, "{")
                let target = parseExpr()
                _ = lexer.expect(.punct, ",")
                let value = parseExpr()
                _ = lexer.expect(.punct, "}")
                contents.append(PIConstructor(target: target, value: value))
                continue
            }
            let ch = charAt(lexer.pos)
            if ch == "<" {
                lexer.clearBuffer()
                contents.append(parseConstructor())
                continue
            }
            if ch == "{" {
                lexer.pos += 1
                lexer.clearBuffer()
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                contents.append(Interp(expr: expr))
                continue
            }
            let text = parseCharData()
            if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                contents.append(Text(value: text))
            }
        }
        return Constructor(name: name, attrs: attrs, contents: contents)
    }

    private func parseCharData() -> String {
        var out: [Character] = []
        while lexer.pos < text.count {
            let ch = charAt(lexer.pos)
            if ch == "<" || ch == "{" { break }
            out.append(ch)
            lexer.pos += 1
        }
        return String(out)
    }

    private func readEndTag() -> (String, Int) {
        var pos = lexer.pos
        if !textAt(pos, prefix: "</") { fatalError("Expected end tag") }
        pos += 2
        let start = pos
        while pos < text.count {
            let c = charAt(pos)
            if !(c.isLetter || c.isNumber || c == "_" || c == ":" || c == "-") { break }
            pos += 1
        }
        let name = substring(start, pos)
        while pos < text.count && charAt(pos).isWhitespace { pos += 1 }
        if pos >= text.count || charAt(pos) != ">" { fatalError("Unterminated end tag") }
        return (name, pos + 1)
    }

    private func charAt(_ pos: Int) -> Character { Array(text)[pos] }
    private func substring(_ start: Int, _ end: Int) -> String {
        let arr = Array(text)
        return String(arr[start..<end])
    }
    private func textAt(_ pos: Int, prefix: String) -> Bool {
        let arr = Array(text)
        let p = Array(prefix)
        if pos + p.count > arr.count { return false }
        return Array(arr[pos..<(pos + p.count)]) == p
    }
}

// Eval

public struct Context {
    public let contextItem: Any?
    public let variables: [String: [Any]]
    public let functions: [String: FunctionDef]
    public let rules: [String: [RuleDef]]
    public let position: Int?
    public let last: Int?
    public let recursionDepth: Int
}

public let MAX_RECURSION_DEPTH = 10000

public struct FunctionRef { public let name: String }

public func evalModule(_ module: Module, _ doc: Node) -> [Any] {
    var variables: [String: [Any]] = [:]
    let ctx = Context(contextItem: doc, variables: variables, functions: module.functions, rules: module.rules, position: nil, last: nil, recursionDepth: 0)
    for (name, expr) in module.vars {
        variables[name] = evalExpr(expr, ctx)
    }
    if module.expr == nil { return [] }
    return evalExpr(module.expr!, Context(contextItem: doc, variables: variables, functions: module.functions, rules: module.rules, position: nil, last: nil, recursionDepth: 0))
}

public func evalExpr(_ expr: Expr, _ ctx: Context) -> [Any] {
    switch expr {
    case let e as Literal:
        return [e.value]
    case let e as VarRef:
        if let v = ctx.variables[e.name] { return v }
        if ctx.functions[e.name] != nil { return [FunctionRef(name: e.name)] }
        if let node = ctx.contextItem as? Node {
            return node.children.filter { $0.kind == "element" && $0.name == e.name }
        }
        return []
    case let e as IfExpr:
        let cond = toBoolean(evalExpr(e.cond, ctx))
        return cond ? evalExpr(e.thenExpr, ctx) : evalExpr(e.elseExpr, ctx)
    case let e as LetExpr:
        var newVars = ctx.variables
        newVars[e.name] = evalExpr(e.value, ctx)
        return evalExpr(e.body, Context(contextItem: ctx.contextItem, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth))
    case let e as ForExpr:
        let seq = evalExpr(e.seq, ctx)
        var out: [Any] = []
        let total = seq.count
        for (idx, item) in seq.enumerated() {
            var newVars = ctx.variables
            newVars[e.name] = [item]
            let newCtx = Context(contextItem: item, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: idx + 1, last: total, recursionDepth: ctx.recursionDepth)
            if let w = e.whereExpr {
                if !toBoolean(evalExpr(w, newCtx)) { continue }
            }
            out.append(contentsOf: evalExpr(e.body, newCtx))
        }
        return out
    case let e as MatchExpr:
        let targetSeq = evalExpr(e.target, ctx)
        var out: [Any] = []
        for target in targetSeq {
            var matchedAny = false
            for (pattern, body) in e.cases {
                let (matched, bindings) = matchPattern(pattern, target)
                if matched {
                    matchedAny = true
                    var newVars = ctx.variables
                    for (k, v) in bindings { newVars[k] = v }
                    out.append(contentsOf: evalExpr(body, Context(contextItem: target, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth)))
                    break
                }
            }
            if !matchedAny {
                guard let def = e.defaultExpr else { fatalError("XFDY0001: no matching case") }
                out.append(contentsOf: evalExpr(def, Context(contextItem: target, variables: ctx.variables, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth)))
            }
        }
        return out
    case let e as FuncCall:
        let args = e.args.map { evalExpr($0, ctx) }
        var named: [String: [Any]] = [:]
        for (n, ex) in e.namedArgs {
            named[n] = evalExpr(ex, ctx)
        }
        var namedRaw: [String: Expr] = [:]
        for (n, ex) in e.namedArgs {
            namedRaw[n] = ex
        }
        return callFunction(e.name, args, ctx, named, namedRaw)
    case let e as ApplyExpr:
        let seq = evalExpr(e.expr, ctx)
        let ruleset = e.ruleset ?? "main"
        return doApply(seq, ruleset, ctx)
    case let e as UnaryOp:
        let val = evalExpr(e.expr, ctx)
        if e.op == "-" { return [-toNumber(val)] }
        if e.op == "not" { return [!toBoolean(val)] }
        return []
    case let e as BinaryOp:
        if e.op == "and" {
            let left = evalExpr(e.left, ctx)
            if !toBoolean(left) { return [false] }
            let right = evalExpr(e.right, ctx)
            return [toBoolean(right)]
        }
        if e.op == "or" {
            let left = evalExpr(e.left, ctx)
            if toBoolean(left) { return [true] }
            let right = evalExpr(e.right, ctx)
            return [toBoolean(right)]
        }
        let left = evalExpr(e.left, ctx)
        let right = evalExpr(e.right, ctx)
        return [evalBinary(e.op, left, right)]
    case let e as PathExpr:
        return evalPath(e, ctx)
    case let e as Constructor:
        return [evalConstructor(e, ctx)]
    case let e as TextConstructor:
        return [Node(kind: "text", value: toString(evalExpr(e.expr, ctx)))]
    case let e as CommentConstructor:
        return [Node(kind: "comment", value: toString(evalExpr(e.expr, ctx)))]
    case let e as PIConstructor:
        let target = toString(evalExpr(e.target, ctx))
        let value = toString(evalExpr(e.value, ctx))
        return [Node(kind: "pi", name: target, value: value)]
    case let e as Text:
        return [e.value]
    case let e as Interp:
        return evalExpr(e.expr, ctx)
    default:
        fatalError("Unknown expr")
    }
}

public func evalBinary(_ op: String, _ left: [Any], _ right: [Any]) -> Any {
    if op == "and" { return toBoolean(left) && toBoolean(right) }
    if op == "or" { return toBoolean(left) || toBoolean(right) }
    if op == "=" { return valueEqual(left, right) }
    if op == "!=" { return !valueEqual(left, right) }
    let lnum = toNumber(left)
    let rnum = toNumber(right)
    switch op {
    case "+": return lnum + rnum
    case "-": return lnum - rnum
    case "*": return lnum * rnum
    case "div": return lnum / rnum
    case "mod": return lnum.truncatingRemainder(dividingBy: rnum)
    case "<": return lnum < rnum
    case "<=": return lnum <= rnum
    case ">": return lnum > rnum
    case ">=": return lnum >= rnum
    default: fatalError("Unknown operator \(op)")
    }
}

public func evalPath(_ expr: PathExpr, _ ctx: Context) -> [Any] {
    var steps = expr.steps
    var base: [Any] = []
    switch expr.start.kind {
    case "context":
        if let c = ctx.contextItem { base = [c] }
    case "root":
        base = rootOf(ctx.contextItem)
    case "desc":
        if let c = ctx.contextItem { base = [c] }
    case "desc_root":
        base = rootOf(ctx.contextItem)
    case "var":
        if let name = expr.start.name {
            if let v = ctx.variables[name] {
                base = v
            } else if let c = ctx.contextItem {
                base = [c]
                steps = [PathStep(axis: "child", test: StepTest(kind: "name", name: name), predicates: [])] + steps
            }
        }
    default:
        break
    }
    var current = base
    for step in steps {
        current = applyStep(current, step, ctx)
    }
    return current
}

private func rootOf(_ item: Any?) -> [Any] {
    if let node = item as? Node {
        var cur = node
        while let p = cur.parent { cur = p }
        return [cur]
    }
    return []
}

public func applyStep(_ items: [Any], _ step: PathStep, _ ctx: Context) -> [Any] {
    var out: [Any] = []
    for item in items {
        guard let node = item as? Node else { continue }
        var candidates: [Node] = []
        switch step.axis {
        case "self": candidates = [node]
        case "parent": if let p = node.parent { candidates = [p] }
        case "desc_or_self": candidates = [node] + iterDescendants(node)
        case "desc": candidates = iterDescendants(node)
        case "attr":
            if node.kind == "element" {
                if step.test.kind == "name", let n = step.test.name {
                    if let v = node.attrs[n] { candidates = [Node(kind: "attribute", name: n, value: v)] }
                } else if step.test.kind == "wildcard" {
                    candidates = node.attrs.map { Node(kind: "attribute", name: $0.key, value: $0.value) }
                }
            }
        case "child": candidates = node.children
        default: break
        }
        var filtered = candidates.filter { matchesStepTest(step.test, $0) }
        for pred in step.predicates {
            var predOut: [Node] = []
            for (i, child) in filtered.enumerated() {
                let predCtx = Context(contextItem: child, variables: ctx.variables, functions: ctx.functions, rules: ctx.rules, position: i + 1, last: filtered.count, recursionDepth: ctx.recursionDepth)
                if toBoolean(evalExpr(pred, predCtx)) { predOut.append(child) }
            }
            filtered = predOut
        }
        out.append(contentsOf: filtered)
    }
    return out
}

private func matchesStepTest(_ test: StepTest, _ node: Node) -> Bool {
    switch test.kind {
    case "wildcard": return node.kind == "element" || node.kind == "attribute"
    case "text": return node.kind == "text"
    case "node": return true
    case "element": return node.kind == "element"
    case "comment": return node.kind == "comment"
    case "pi": return node.kind == "pi"
    case "document": return node.kind == "document"
    case "name": return node.name == test.name
    default: return false
    }
}

public func evalConstructor(_ expr: Constructor, _ ctx: Context) -> Node {
    let node = Node(kind: "element", name: expr.name, attrs: [:], attrOrder: expr.attrs.map { $0.0 })
    var seenAttrs = Set<String>()
    for (name, aexpr) in expr.attrs {
        if !seenAttrs.insert(name).inserted { fatalError("XFDY0005") }
        let val = evalExpr(aexpr, ctx)
        node.attrs[name] = toString(val)
    }
    var children: [Node] = []
    for content in expr.contents {
        if let text = content as? Text {
            children.append(Node(kind: "text", value: text.value))
            continue
        }
        let seq = evalExpr(content, ctx)
        for item in seq {
            if let n = item as? Node {
                if n.kind == "attribute" {
                    children.append(Node(kind: "text", value: n.value ?? ""))
                    continue
                }
                children.append(deepCopy(n, recurse: true))
            } else {
                children.append(Node(kind: "text", value: toString([item])))
            }
        }
    }
    // Merge adjacent text nodes
    var merged: [Node] = []
    for child in children {
        if child.kind == "text" {
            if let last = merged.last, last.kind == "text" {
                last.value = (last.value ?? "") + (child.value ?? "")
                continue
            }
        }
        merged.append(child)
    }
    for c in merged { c.parent = node }
    node.children = merged
    return node
}

public func callFunction(_ name: String, _ args: [[Any]], _ ctx: Context, _ named: [String: [Any]] = [:], _ namedRaw: [String: Expr] = [:]) -> [Any] {
    if let fn = ctx.functions[name] {
        return callUserFunction(fn, args, ctx, named)
    }
    guard let builtin = builtins[name] else { fatalError("XFST0003: unknown function \(name)") }
    return builtin(args, ctx, named, namedRaw)
}

private func callUserFunction(_ fn: FunctionDef, _ args: [[Any]], _ ctx: Context, _ named: [String: [Any]] = [:]) -> [Any] {
    if ctx.recursionDepth >= MAX_RECURSION_DEPTH { fatalError("XFDY0099") }
    let params = fn.params
    if args.count > params.count { fatalError("XFDY0008: too many arguments") }
    var newVars = ctx.variables
    var bound = Set<String>()
    for (i, v) in args.enumerated() {
        newVars[params[i].name] = v
        bound.insert(params[i].name)
    }
    for (paramName, value) in named {
        if bound.contains(paramName) { fatalError("XFDY0008: duplicate argument") }
        guard params.contains(where: { $0.name == paramName }) else { fatalError("XFDY0008: unknown parameter") }
        newVars[paramName] = value
        bound.insert(paramName)
    }
    let newCtx = Context(contextItem: ctx.contextItem, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth + 1)
    for param in params {
        if !bound.contains(param.name) {
            if let def = param.defaultExpr {
                newVars[param.name] = evalExpr(def, newCtx)
                bound.insert(param.name)
            } else {
                fatalError("XFDY0008: missing required parameter")
            }
        }
    }
    return evalExpr(fn.body, newCtx)
}

public func toBoolean(_ seq: [Any]) -> Bool {
    if seq.isEmpty { return false }
    if seq.contains(where: { $0 is Node }) { return true }
    for item in seq {
        if let b = item as? Bool { if b { return true } }
        else if let n = item as? Double { if n != 0 { return true } }
        else if let n = item as? Int { if n != 0 { return true } }
        else if let s = item as? String { if !s.isEmpty { return true } }
        else if let _ = item as? [String: [Any]] { return true }
        else if item is FunctionRef { return true }
    }
    return false
}

public func toString(_ seq: [Any]) -> String {
    if seq.isEmpty { return "" }
    let item = seq[0]
    if let node = item as? Node { return node.stringValue() }
    if item is NSNull { return "" }
    if let b = item as? Bool { return b ? "true" : "false" }
    if let n = item as? Double { return n == floor(n) ? String(Int(n)) : String(n) }
    if let n = item as? Int { return String(n) }
    if let _ = item as? [String: [Any]] { return "[map]" }
    if let ref = item as? FunctionRef { return ref.name }
    return String(describing: item)
}

public func toNumber(_ seq: [Any]) -> Double {
    if seq.isEmpty { return 0.0 }
    var item: Any = seq[0]
    if let node = item as? Node { item = node.stringValue() }
    if let b = item as? Bool { return b ? 1.0 : 0.0 }
    if let n = item as? Int { return Double(n) }
    if let n = item as? Double { return n }
    if let s = item as? String, let v = Double(s) { return v }
    return Double.nan
}

public func valueEqual(_ left: [Any], _ right: [Any]) -> Bool { toString(left) == toString(right) }

public func matchPattern(_ pattern: Pattern, _ item: Any) -> (Bool, [String: [Any]]) {
    switch pattern {
    case is WildcardPattern:
        return (true, [:])
    case let p as LiteralPattern:
        if let node = item as? Node, node.kind == "text", node.value == p.value { return (true, [:]) }
        return (false, [:])
    case let p as AttributePattern:
        if let node = item as? Node, node.kind == "attribute", node.name == p.name {
            if let val = p.value {
                if node.value == String(describing: val) { return (true, [:]) }
                return (false, [:])
            }
            return (true, [:])
        }
        return (false, [:])
    case let p as TypedPattern:
        if item is NSNull { return (false, [:]) }
        if p.kind == "node" { return (item is Node, [:]) }
        if let node = item as? Node {
            if p.kind == "text" { return (node.kind == "text", [:]) }
            if p.kind == "element" { return (node.kind == "element", [:]) }
            if p.kind == "comment" { return (node.kind == "comment", [:]) }
            if p.kind == "pi" { return (node.kind == "pi", [:]) }
            if p.kind == "document" { return (node.kind == "document", [:]) }
        }
        return (false, [:])
    case let p as ElementPattern:
        if let node = item as? Node, node.kind == "element", node.name == p.name {
            // Check attribute constraints
            for (attrName, attrValue) in p.attrs {
                guard let foundValue = node.attrs[attrName] else { return (false, [:]) }
                if let expected = attrValue {
                    if foundValue != String(describing: expected) { return (false, [:]) }
                }
            }
            var bindings: [String: [Any]] = [:]
            if let v = p.varName {
                bindings[v] = node.children
                return (true, bindings)
            }
            if !p.children.isEmpty {
                if node.children.count != p.children.count { return (false, [:]) }
                for (childPat, childNode) in zip(p.children, node.children) {
                    let (matched, childBindings) = matchPattern(childPat, childNode)
                    if !matched { return (false, [:]) }
                    for (k, v) in childBindings { bindings[k] = v }
                }
                return (true, bindings)
            }
            return (true, [:])
        }
        return (false, [:])
    default:
        return (false, [:])
    }
}

private func doApply(_ seq: [Any], _ ruleset: String, _ ctx: Context) -> [Any] {
    if ctx.recursionDepth >= MAX_RECURSION_DEPTH { fatalError("XFDY0099") }
    if ruleset != "main" && !ctx.rules.keys.contains(ruleset) { fatalError("XFST0007") }
    let rules = ctx.rules[ruleset] ?? []
    var out: [Any] = []
    for item in seq {
        var matched = false
        for rule in rules {
            let (ok, bindings) = matchPattern(rule.pattern, item)
            if ok {
                matched = true
                var newVars = ctx.variables
                for (k, v) in bindings { newVars[k] = v }
                let newCtx = Context(contextItem: item, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth + 1)
                out.append(contentsOf: evalExpr(rule.body, newCtx))
                break
            }
        }
        if !matched {
            out.append(contentsOf: applyBuiltin(item, ruleset, ctx))
        }
    }
    return out
}

private func applyBuiltin(_ item: Any, _ ruleset: String, _ ctx: Context) -> [Any] {
    guard let node = item as? Node else { return [] }
    switch node.kind {
    case "document":
        return doApply(node.children, ruleset, ctx)
    case "element":
        let applied = doApply(node.children, ruleset, ctx)
        let newChildren = applied.compactMap { $0 as? Node }
        let newEl = Node(kind: "element", name: node.name, attrs: node.attrs, attrOrder: node.attrOrder)
        for c in newChildren { c.parent = newEl }
        newEl.children = newChildren
        return [newEl]
    case "attribute", "text", "comment", "pi":
        return [deepCopy(node, recurse: true)]
    default:
        return []
    }
}

// Builtins

private typealias BuiltinFn = (_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any]

private func fnString(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] { [toString(args.first ?? [])] }
private func fnNumber(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] { [toNumber(args.first ?? [])] }
private func fnBoolean(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] { [toBoolean(args.first ?? [])] }

private func fnTypeOf(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return ["null"] }
    let item = args[0][0]
    if item is Node { return ["node"] }
    if item is [String: [Any]] { return ["map"] }
    if item is FunctionRef { return ["function"] }
    if item is Bool { return ["boolean"] }
    if item is Double || item is Int { return ["number"] }
    if item is NSNull { return ["null"] }
    return ["string"]
}

private func fnName(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    return [node.name ?? ""]
}

private func fnAttr(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    if node.kind != "element" { return [""] }
    if args.count < 2 { return [""] }
    let key = toString(args[1])
    return [node.attrs[key] ?? ""]
}

private func fnText(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    var deep = true
    if args.count > 1 {
        deep = toBoolean(args[1])
    } else if let d = named["deep"] {
        deep = toBoolean(d)
    }
    if deep { return [node.stringValue()] }
    if node.kind == "element" || node.kind == "document" {
        let direct = node.children.filter { $0.kind == "text" }.map { $0.value ?? "" }.joined()
        return [direct]
    }
    return [node.stringValue()]
}

private func fnChildren(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    return node.children
}

private func fnElements(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    if node.kind != "element" && node.kind != "document" { return [] }
    let nameTest = args.count > 1 ? toString(args[1]) : ""
    return node.children.filter { $0.kind == "element" && (nameTest.isEmpty || $0.name == nameTest) }
}

private func fnAttributes(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    if node.kind != "element" { return [] }
    return node.attrs.map { Node(kind: "attribute", name: $0.key, value: $0.value) }
}

private func fnCopy(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node else { fatalError("XFDY0003") }
    var recurse = true
    if args.count > 1 {
        recurse = toBoolean(args[1])
    } else if let r = named["recurse"] {
        recurse = toBoolean(r)
    }
    return [deepCopy(node, recurse: recurse)]
}

private func fnCount(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    return [Double(args.first?.count ?? 0)]
}

private func fnEmpty(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    return [args.first?.isEmpty ?? true]
}

private func fnDistinct(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty { return [] }
    var seen: Set<String> = []
    var out: [Any] = []
    for item in args[0] {
        let key = toString([item])
        if seen.contains(key) { continue }
        seen.insert(key)
        out.append(item)
    }
    return out
}

private func fnSort(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty { return [] }
    var seq = args[0]
    var keyFn: String? = nil
    if args.count > 1, let ref = args[1].first as? FunctionRef { keyFn = ref.name }
    seq.sort {
        if let k = keyFn, let fn = ctx.functions[k] {
            let ka = toString(callUserFunction(fn, [[$0]], ctx))
            let kb = toString(callUserFunction(fn, [[$1]], ctx))
            return ka < kb
        }
        return toString([$0]) < toString([$1])
    }
    return seq
}

private func fnConcat(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    var out: [Any] = []
    for seq in args { out.append(contentsOf: seq) }
    return out
}

private func fnHead(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    return [args[0][0]]
}

private func fnTail(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    return Array(args[0].dropFirst())
}

private func fnLast(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty {
        if let last = ctx.last { return [Double(last)] }
        fatalError("XFDY0003")
    }
    return [args[0].last!]
}

private func fnPosition(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if let pos = ctx.position { return [Double(pos)] }
    fatalError("XFDY0003")
}

private func fnIndex(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty { return [] }
    let seq = args[0]
    var keyFn: String? = nil
    if args.count > 1, let ref = args[1].first as? FunctionRef { keyFn = ref.name }
    var keyExpr: Expr? = nil
    if let ke = namedRaw["key"] { keyExpr = ke }
    var index: [String: [Any]] = [:]
    for item in seq {
        var key = toString([item])
        if let k = keyFn, let fn = ctx.functions[k] {
            key = toString(callUserFunction(fn, [[item]], ctx))
        } else if let ke = keyExpr {
            let itemCtx = Context(contextItem: item, variables: ctx.variables, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last, recursionDepth: ctx.recursionDepth)
            key = toString(evalExpr(ke, itemCtx))
        }
        index[key, default: []].append(item)
    }
    return [index]
}

private func fnLookup(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.count < 2 { return [] }
    if args[0].isEmpty { return [] }
    guard let mapping = args[0][0] as? [String: [Any]] else { return [] }
    let key = toString(args[1])
    return mapping[key] ?? []
}

private func fnGroupBy(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.count < 2 { return [] }
    let seq = args[0]
    var keyFn: String? = nil
    if let ref = args[1].first as? FunctionRef { keyFn = ref.name }
    var groups: [String: [Any]] = [:]
    for item in seq {
        var key = toString([item])
        if let k = keyFn, let fn = ctx.functions[k] {
            key = toString(callUserFunction(fn, [[item]], ctx))
        }
        groups[key, default: []].append(item)
    }
    return groups.map { ["key": [$0.key], "items": $0.value] as [String: [Any]] }
}

private func fnSeq(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    var out: [Any] = []
    for seq in args { out.append(contentsOf: seq) }
    return out
}

private func fnSum(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty { return [0.0] }
    var total = 0.0
    for item in args[0] { total += toNumber([item]) }
    return [total]
}

private func fnApply(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty { return [] }
    let seq = args[0]
    var ruleset = "main"
    if args.count > 1 && !args[1].isEmpty { ruleset = toString(args[1]) }
    return doApply(seq, ruleset, ctx)
}

private func fnContains(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let a = toString(args.get(0, otherwise: []))
    let b = toString(args.get(1, otherwise: []))
    return [a.contains(b)]
}

private func fnStartsWith(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let a = toString(args.get(0, otherwise: []))
    let b = toString(args.get(1, otherwise: []))
    return [a.hasPrefix(b)]
}

private func fnEndsWith(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let a = toString(args.get(0, otherwise: []))
    let b = toString(args.get(1, otherwise: []))
    return [a.hasSuffix(b)]
}

private func fnSubstring(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let s = toString(args.get(0, otherwise: []))
    if args.count < 2 { return [""] }
    let start = Int(toNumber(args[1]))
    if args.count > 2 {
        let length = Int(toNumber(args[2]))
        let startIndex = s.index(s.startIndex, offsetBy: max(0, start - 1), limitedBy: s.endIndex) ?? s.endIndex
        let endIndex = s.index(startIndex, offsetBy: length, limitedBy: s.endIndex) ?? s.endIndex
        return [String(s[startIndex..<endIndex])]
    }
    let startIndex = s.index(s.startIndex, offsetBy: max(0, start - 1), limitedBy: s.endIndex) ?? s.endIndex
    return [String(s[startIndex...])]
}

private func fnNormalizeSpace(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let s = toString(args.get(0, otherwise: []))
    let components = s.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    return [components.joined(separator: " ")]
}

private func fnReplace(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.count < 3 { return [""] }
    let s = toString(args[0])
    let pattern = toString(args[1])
    let replacement = toString(args[2])
    return [s.replacingOccurrences(of: pattern, with: replacement)]
}

private func fnKeys(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let mapping = args[0][0] as? [String: [Any]] else { return [] }
    return mapping.keys.sorted()
}

private func fnMapSize(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [0.0] }
    guard let mapping = args[0][0] as? [String: [Any]] else { return [0.0] }
    return [Double(mapping.count)]
}

private func fnStringLength(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let s = toString(args.get(0, otherwise: []))
    return [Double(s.count)]
}

private func fnUpperCase(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let s = toString(args.get(0, otherwise: []))
    return [s.uppercased()]
}

private func fnLowerCase(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    let s = toString(args.get(0, otherwise: []))
    return [s.lowercased()]
}

private func fnMatches(_ args: [[Any]], _ ctx: Context, _ named: [String: [Any]], _ namedRaw: [String: Expr]) -> [Any] {
    if args.count < 2 { return [false] }
    let s = toString(args[0])
    let pattern = toString(args[1])
    return [s.contains(pattern)]
}

private extension Array {
    func get(_ index: Int, otherwise: Element) -> Element {
        return indices.contains(index) ? self[index] : otherwise
    }
}

private let builtins: [String: BuiltinFn] = [
    "string": fnString,
    "number": fnNumber,
    "boolean": fnBoolean,
    "typeOf": fnTypeOf,
    "name": fnName,
    "attr": fnAttr,
    "text": fnText,
    "children": fnChildren,
    "elements": fnElements,
    "attributes": fnAttributes,
    "copy": fnCopy,
    "count": fnCount,
    "empty": fnEmpty,
    "distinct": fnDistinct,
    "sort": fnSort,
    "concat": fnConcat,
    "index": fnIndex,
    "lookup": fnLookup,
    "groupBy": fnGroupBy,
    "seq": fnSeq,
    "sum": fnSum,
    "head": fnHead,
    "tail": fnTail,
    "last": fnLast,
    "position": fnPosition,
    "apply": fnApply,
    "contains": fnContains,
    "startsWith": fnStartsWith,
    "endsWith": fnEndsWith,
    "substring": fnSubstring,
    "normalizeSpace": fnNormalizeSpace,
    "replace": fnReplace,
    "keys": fnKeys,
    "mapSize": fnMapSize,
    "stringLength": fnStringLength,
    "upperCase": fnUpperCase,
    "lowerCase": fnLowerCase,
    "matches": fnMatches
]

public func serializeItem(_ item: Any) -> String {
    if let node = item as? Node { return serialize(node) }
    return toString([item])
}
