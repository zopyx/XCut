import Foundation

public final class Node {
    public let kind: String // document, element, attribute, text, comment, pi
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
        let node = Node(kind: "pi", value: data ?? "")
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
public struct FuncCall: Expr { public let name: String; public let args: [Expr] }
public struct UnaryOp: Expr { public let op: String; public let expr: Expr }
public struct BinaryOp: Expr { public let op: String; public let left: Expr; public let right: Expr }
public struct PathExpr: Expr { public let start: PathStart; public let steps: [PathStep] }
public struct Constructor: Expr { public let name: String; public let attrs: [(String, Expr)]; public let contents: [Expr] }
public struct TextConstructor: Expr { public let expr: Expr }
public struct Text: Expr { public let value: String }
public struct Interp: Expr { public let expr: Expr }

public struct PathStart { public let kind: String; public let name: String? }
public struct PathStep { public let axis: String; public let test: StepTest; public let predicates: [Expr] }
public struct StepTest { public let kind: String; public let name: String? }

public protocol Pattern {}
public struct WildcardPattern: Pattern {}
public struct ElementPattern: Pattern { public let name: String; public let varName: String?; public let child: Pattern? }
public struct TypedPattern: Pattern { public let kind: String }
public struct AttributePattern: Pattern { public let name: String }

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
    "if", "then", "else", "match", "case", "default", "and", "or", "not", "div", "mod", "rule"
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
            if version != "2.0" { fatalError("XFST0005: unsupported version") }
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
            alias = lexer.expect(.ident).value
        }
        _ = lexer.expect(.punct, ";")
        imports.append((iri, alias))
    }

    private func parseVar() -> (String, Expr) {
        _ = lexer.expect(.kw, "var")
        let name = lexer.expect(.ident).value
        _ = lexer.expect(.op, ":=")
        let value = parseExpr()
        _ = lexer.expect(.punct, ";")
        return (name, value)
    }

    private func parseDef(_ functions: inout [String: FunctionDef]) {
        _ = lexer.expect(.kw, "def")
        let name = parseQName()
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
        let name = lexer.expect(.ident).value
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
        if tok.kind == .ident && ["string", "number", "boolean", "null", "map"].contains(tok.value) {
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
        let name = lexer.expect(.ident).value
        _ = lexer.expect(.op, ":=")
        let value = parseExpr()
        _ = lexer.expect(.kw, "in")
        let body = parseExpr()
        return LetExpr(name: name, value: value, body: body)
    }

    private func parseFor() -> Expr {
        _ = lexer.expect(.kw, "for")
        let name = lexer.expect(.ident).value
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
        if tok.kind == .punct && tok.value == "(" {
            _ = lexer.next()
            let expr = parseExpr()
            _ = lexer.expect(.punct, ")")
            return expr
        }
        if tok.kind == .ident && tok.value == "text" {
            let savedPos = lexer.pos
            let savedBuf = lexer.snapshotBuffer()
            _ = lexer.next()
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                let expr = parseExpr()
                _ = lexer.expect(.punct, "}")
                return TextConstructor(expr: expr)
            }
            lexer.pos = savedPos
            lexer.restoreBuffer(savedBuf)
        }
        if tok.kind == .op && tok.value == "<" {
            return parseConstructor()
        }
        if tok.kind == .dot || tok.kind == .slash {
            return parsePath(start: nil)
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

    private func parseFuncCall(_ name: String) -> Expr {
        _ = lexer.expect(.punct, "(")
        var args: [Expr] = []
        if !(lexer.peek().kind == .punct && lexer.peek().value == ")") {
            args.append(parseExpr())
            while lexer.peek().kind == .punct && lexer.peek().value == "," {
                _ = lexer.next()
                args.append(parseExpr())
            }
        }
        _ = lexer.expect(.punct, ")")
        return FuncCall(name: name, args: args)
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
            } else if tok.kind == .ident {
                let test = parseStepTest()
                let preds = parsePredicates()
                steps.append(PathStep(axis: "child", test: test, predicates: preds))
            }
        }
        if ["desc", "desc_root"].contains(actualStart!.kind) {
            let tok = lexer.peek()
            if tok.kind == .ident || tok.kind == .op {
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
        if tok.kind == .ident {
            if ["text", "node", "comment", "pi"].contains(tok.value) {
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

    private func parseQName() -> String {
        return lexer.expect(.ident).value
    }

    private func parsePattern() -> Pattern {
        let tok = lexer.peek()
        if tok.kind == .at {
            _ = lexer.next()
            let name = parseQName()
            return AttributePattern(name: name)
        }
        if tok.kind == .ident && ["node", "text", "comment"].contains(tok.value) {
            _ = lexer.next()
            _ = lexer.expect(.punct, "(")
            _ = lexer.expect(.punct, ")")
            return TypedPattern(kind: tok.value)
        }
        if tok.kind == .ident && tok.value == "_" {
            _ = lexer.next()
            return WildcardPattern()
        }
        if tok.kind == .op && tok.value == "<" {
            _ = lexer.next()
            let name = parseQName()
            _ = lexer.expect(.op, ">")
            var varName: String? = nil
            var child: Pattern? = nil
            if lexer.peek().kind == .punct && lexer.peek().value == "{" {
                _ = lexer.next()
                varName = lexer.expect(.ident).value
                _ = lexer.expect(.punct, "}")
            } else if lexer.peek().kind == .op && lexer.peek().value == "<" {
                child = parsePattern()
            } else {
                fatalError("Invalid element pattern content")
            }
            _ = lexer.expect(.op, "<")
            _ = lexer.expect(.slash, "/")
            let end = parseQName()
            if end != name { fatalError("Mismatched pattern end tag") }
            _ = lexer.expect(.op, ">")
            return ElementPattern(name: name, varName: varName, child: child)
        }
        fatalError("Invalid pattern at \(tok.pos)")
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
}

public struct FunctionRef { public let name: String }

public func evalModule(_ module: Module, _ doc: Node) -> [Any] {
    var variables: [String: [Any]] = [:]
    let ctx = Context(contextItem: doc, variables: variables, functions: module.functions, rules: module.rules, position: nil, last: nil)
    for (name, expr) in module.vars {
        variables[name] = evalExpr(expr, ctx)
    }
    if module.expr == nil { return [] }
    return evalExpr(module.expr!, Context(contextItem: doc, variables: variables, functions: module.functions, rules: module.rules, position: nil, last: nil))
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
        return evalExpr(e.body, Context(contextItem: ctx.contextItem, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last))
    case let e as ForExpr:
        let seq = evalExpr(e.seq, ctx)
        var out: [Any] = []
        let total = seq.count
        for (idx, item) in seq.enumerated() {
            var newVars = ctx.variables
            newVars[e.name] = [item]
            let newCtx = Context(contextItem: item, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: idx + 1, last: total)
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
                    out.append(contentsOf: evalExpr(body, Context(contextItem: target, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last)))
                    break
                }
            }
            if !matchedAny {
                guard let def = e.defaultExpr else { fatalError("XFDY0001: no matching case") }
                out.append(contentsOf: evalExpr(def, Context(contextItem: target, variables: ctx.variables, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last)))
            }
        }
        return out
    case let e as FuncCall:
        let args = e.args.map { evalExpr($0, ctx) }
        return callFunction(e.name, args, ctx)
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
                let predCtx = Context(contextItem: child, variables: ctx.variables, functions: ctx.functions, rules: ctx.rules, position: i + 1, last: filtered.count)
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
    case "wildcard": return node.kind == "element"
    case "text": return node.kind == "text"
    case "node": return true
    case "comment": return node.kind == "comment"
    case "pi": return node.kind == "pi"
    case "name": return node.name == test.name
    default: return false
    }
}

public func evalConstructor(_ expr: Constructor, _ ctx: Context) -> Node {
    let node = Node(kind: "element", name: expr.name, attrs: [:], attrOrder: expr.attrs.map { $0.0 })
    for (name, aexpr) in expr.attrs {
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
                children.append(deepCopy(n, recurse: true))
            } else {
                children.append(Node(kind: "text", value: toString([item])))
            }
        }
    }
    for c in children { c.parent = node }
    node.children = children
    return node
}

public func callFunction(_ name: String, _ args: [[Any]], _ ctx: Context) -> [Any] {
    if let fn = ctx.functions[name] {
        return callUserFunction(fn, args, ctx)
    }
    guard let builtin = builtins[name] else { fatalError("XFST0003: unknown function \(name)") }
    return builtin(args, ctx)
}

private func callUserFunction(_ fn: FunctionDef, _ args: [[Any]], _ ctx: Context) -> [Any] {
    let params = fn.params
    if args.count > params.count { fatalError("XFDY0002: wrong arity") }
    var newVars = ctx.variables
    for (i, v) in args.enumerated() { newVars[params[i].name] = v }
    if args.count < params.count {
        for i in args.count..<params.count {
            let param = params[i]
            guard let def = param.defaultExpr else { fatalError("XFDY0002: wrong arity") }
            newVars[param.name] = evalExpr(def, ctx)
        }
    }
    let newCtx = Context(contextItem: ctx.contextItem, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last)
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
        else if item as AnyObject? != nil { return true }
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
    fatalError("XFDY0002: number conversion")
}

public func valueEqual(_ left: [Any], _ right: [Any]) -> Bool { toString(left) == toString(right) }

public func matchPattern(_ pattern: Pattern, _ item: Any) -> (Bool, [String: [Any]]) {
    switch pattern {
    case is WildcardPattern:
        return (true, [:])
    case let p as AttributePattern:
        if let node = item as? Node, node.kind == "attribute", node.name == p.name { return (true, [:]) }
        return (false, [:])
    case let p as TypedPattern:
        if item is NSNull { return (false, [:]) }
        if p.kind == "node" { return (item is Node, [:]) }
        if let node = item as? Node {
            if p.kind == "text" { return (node.kind == "text", [:]) }
            if p.kind == "comment" { return (node.kind == "comment", [:]) }
        }
        return (false, [:])
    case let p as ElementPattern:
        if let node = item as? Node, node.kind == "element", node.name == p.name {
            var bindings: [String: [Any]] = [:]
            if let v = p.varName {
                bindings[v] = node.children
                return (true, bindings)
            }
            if let childPattern = p.child {
                for child in node.children {
                    let (matched, childBindings) = matchPattern(childPattern, child)
                    if matched {
                        for (k, v) in childBindings { bindings[k] = v }
                        return (true, bindings)
                    }
                }
                return (false, [:])
            }
            return (true, [:])
        }
        return (false, [:])
    default:
        return (false, [:])
    }
}

// Builtins

private typealias BuiltinFn = (_ args: [[Any]], _ ctx: Context) -> [Any]

private func fnString(_ args: [[Any]], _ ctx: Context) -> [Any] { [toString(args.first ?? [])] }
private func fnNumber(_ args: [[Any]], _ ctx: Context) -> [Any] { [toNumber(args.first ?? [])] }
private func fnBoolean(_ args: [[Any]], _ ctx: Context) -> [Any] { [toBoolean(args.first ?? [])] }

private func fnTypeOf(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return ["null"] }
    let item = args[0][0]
    if item is Node { return ["node"] }
    if item is [String: [Any]] { return ["map"] }
    if item is Bool { return ["boolean"] }
    if item is Double || item is Int { return ["number"] }
    if item is NSNull { return ["null"] }
    return ["string"]
}

private func fnName(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    if let node = args[0][0] as? Node { return [node.name ?? ""] }
    return [""]
}

private func fnAttr(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    guard let node = args[0][0] as? Node, node.kind == "element" else { return [""] }
    if args.count < 2 { return [""] }
    let key = toString(args[1])
    return [node.attrs[key] ?? ""]
}

private func fnText(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [""] }
    let item = args[0][0]
    if let node = item as? Node {
        var deep = true
        if args.count > 1 { deep = toBoolean(args[1]) }
        if deep { return [node.stringValue()] }
        if node.kind == "element" || node.kind == "document" {
            let direct = node.children.filter { $0.kind == "text" }.map { $0.value ?? "" }.joined()
            return [direct]
        }
        return [node.stringValue()]
    }
    return [toString(args[0])]
}

private func fnChildren(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    if let node = args[0][0] as? Node { return node.children }
    return []
}

private func fnElements(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node, node.kind == "element" || node.kind == "document" else { return [] }
    let nameTest = args.count > 1 ? toString(args[1]) : ""
    let out = node.children.filter { $0.kind == "element" && (nameTest.isEmpty || $0.name == nameTest) }
    return out
}

private func fnCopy(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    guard let node = args[0][0] as? Node else { return [] }
    let recurse = args.count > 1 ? toBoolean(args[1]) : true
    return [deepCopy(node, recurse: recurse)]
}

private func fnCount(_ args: [[Any]], _ ctx: Context) -> [Any] {
    return [Double(args.first?.count ?? 0)]
}

private func fnEmpty(_ args: [[Any]], _ ctx: Context) -> [Any] {
    return [args.first?.isEmpty ?? true]
}

private func fnDistinct(_ args: [[Any]], _ ctx: Context) -> [Any] {
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

private func fnSort(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty { return [] }
    var seq = args[0]
    var keyFn: String? = nil
    if args.count > 1, let ref = args[1].first as? FunctionRef { keyFn = ref.name }
    seq.sort { a, b in
        if let k = keyFn, let fn = ctx.functions[k] {
            let ka = toString(callUserFunction(fn, [[a]], ctx))
            let kb = toString(callUserFunction(fn, [[b]], ctx))
            return ka < kb
        }
        return toString([a]) < toString([b])
    }
    return seq
}

private func fnConcat(_ args: [[Any]], _ ctx: Context) -> [Any] {
    var out: [Any] = []
    for seq in args { out.append(contentsOf: seq) }
    return out
}

private func fnHead(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    return [args[0][0]]
}

private func fnTail(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty { return [] }
    return Array(args[0].dropFirst())
}

private func fnLast(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty || args[0].isEmpty {
        if let last = ctx.last { return [Double(last)] }
        return []
    }
    return [args[0].last!]
}

private func fnIndex(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty { return [] }
    let seq = args[0]
    var keyFn: String? = nil
    if args.count > 1, let ref = args[1].first as? FunctionRef { keyFn = ref.name }
    var index: [String: [Any]] = [:]
    for item in seq {
        var key = toString([item])
        if let k = keyFn, let fn = ctx.functions[k] {
            key = toString(callUserFunction(fn, [[item]], ctx))
        }
        index[key, default: []].append(item)
    }
    return [index]
}

private func fnLookup(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.count < 2 { return [] }
    if args[0].isEmpty { return [] }
    guard let mapping = args[0][0] as? [String: [Any]] else { return [] }
    let key = toString(args[1])
    return mapping[key] ?? []
}

private func fnGroupBy(_ args: [[Any]], _ ctx: Context) -> [Any] {
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

private func fnSeq(_ args: [[Any]], _ ctx: Context) -> [Any] {
    var out: [Any] = []
    for seq in args { out.append(contentsOf: seq) }
    return out
}

private func fnPosition(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if let pos = ctx.position { return [Double(pos)] }
    return []
}

private func fnApply(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty { return [] }
    let seq = args[0]
    var ruleset = "main"
    if args.count > 1 && !args[1].isEmpty { ruleset = toString(args[1]) }
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
                let newCtx = Context(contextItem: item, variables: newVars, functions: ctx.functions, rules: ctx.rules, position: ctx.position, last: ctx.last)
                out.append(contentsOf: evalExpr(rule.body, newCtx))
                break
            }
        }
        if !matched { fatalError("XFDY0001: no matching rule") }
    }
    return out
}

private func fnSum(_ args: [[Any]], _ ctx: Context) -> [Any] {
    if args.isEmpty { return [0.0] }
    var total = 0.0
    for item in args[0] { total += toNumber([item]) }
    return [total]
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
    "apply": fnApply
]

public func serializeItem(_ item: Any) -> String {
    if let node = item as? Node { return serialize(node) }
    return toString([item])
}
