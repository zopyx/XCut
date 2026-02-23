package zopyx.xform;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class XFormEngine {
    private XFormEngine() {}

    static final class XFormException extends RuntimeException {
        XFormException(String msg) { super(msg); }
        XFormException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static final class Node {
        public String kind; // document, element, attribute, text, comment, pi
        public String name = "";
        public String value = "";
        public List<Node> children = new ArrayList<>();
        public Map<String, String> attrs = new LinkedHashMap<>();
        public List<String> attrOrder = new ArrayList<>();
        public Node parent;

        Node(String kind) { this.kind = kind; }

        String stringValue() {
            switch (kind) {
                case "text":
                case "attribute":
                    return value == null ? "" : value;
                case "element":
                case "document": {
                    StringBuilder out = new StringBuilder();
                    for (Node c : children) out.append(c.stringValue());
                    return out.toString();
                }
                default:
                    return "";
            }
        }
    }

    public static Node parseXMLBytes(byte[] data) {
        String text = normalizeXMLBytes(data);
        text = stripDoctype(text);
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(true);
            f.setIgnoringComments(false);
            try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            try { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) {}
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document doc = b.parse(new InputSource(new StringReader(text)));
            Node out = new Node("document");
            convertChildren(doc, out);
            return out;
        } catch (Exception e) {
            throw new XFormException("XML parse error", e);
        }
    }

    private static void convertChildren(org.w3c.dom.Node domParent, Node outParent) {
        NodeList list = domParent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            org.w3c.dom.Node c = list.item(i);
            short t = c.getNodeType();
            if (t == org.w3c.dom.Node.ELEMENT_NODE) {
                Node n = new Node("element");
                n.name = c.getNodeName();
                NamedNodeMap attrs = c.getAttributes();
                if (attrs != null) {
                    for (int j = 0; j < attrs.getLength(); j++) {
                        Attr a = (Attr) attrs.item(j);
                        n.attrs.put(a.getName(), a.getValue());
                        n.attrOrder.add(a.getName());
                    }
                }
                n.parent = outParent;
                outParent.children.add(n);
                convertChildren(c, n);
            } else if (t == org.w3c.dom.Node.TEXT_NODE || t == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                Node n = new Node("text");
                n.value = c.getNodeValue();
                n.parent = outParent;
                outParent.children.add(n);
            } else if (t == org.w3c.dom.Node.COMMENT_NODE) {
                Node n = new Node("comment");
                n.value = ((Comment) c).getData();
                n.parent = outParent;
                outParent.children.add(n);
            } else if (t == org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE) {
                Node n = new Node("pi");
                n.value = ((ProcessingInstruction) c).getData();
                n.parent = outParent;
                outParent.children.add(n);
            }
        }
    }

    public static Node deepCopy(Node node, boolean recurse) {
        Node copied = new Node(node.kind);
        copied.name = node.name;
        copied.value = node.value;
        copied.attrs.putAll(node.attrs);
        copied.attrOrder = new ArrayList<>(node.attrOrder);
        if (recurse) {
            for (Node c : node.children) {
                Node child = deepCopy(c, true);
                child.parent = copied;
                copied.children.add(child);
            }
        }
        return copied;
    }

    public static List<Node> iterDescendants(Node node) {
        List<Node> out = new ArrayList<>();
        for (Node child : node.children) {
            out.add(child);
            out.addAll(iterDescendants(child));
        }
        return out;
    }

    public static String serialize(Node item) {
        switch (item.kind) {
            case "document": {
                StringBuilder out = new StringBuilder();
                for (Node c : item.children) out.append(serialize(c));
                return out.toString();
            }
            case "text":
                return escapeText(item.value == null ? "" : item.value);
            case "attribute":
                return escapeAttr(item.value == null ? "" : item.value);
            case "element": {
                List<String> keys = item.attrOrder.isEmpty() ? new ArrayList<>(item.attrs.keySet()) : item.attrOrder;
                if (item.attrOrder.isEmpty()) Collections.sort(keys);
                StringBuilder attrs = new StringBuilder();
                for (String k : keys) attrs.append(" ").append(k).append("=\"").append(escapeAttr(item.attrs.getOrDefault(k, ""))).append("\"");
                if (item.children.isEmpty()) return "<" + item.name + attrs + "/>";
                StringBuilder inner = new StringBuilder();
                for (Node c : item.children) inner.append(serialize(c));
                return "<" + item.name + attrs + ">" + inner + "</" + item.name + ">";
            }
            default:
                return "";
        }
    }

    private static String escapeText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String text) {
        return escapeText(text).replace("\"", "&quot;");
    }

    private static String normalizeXMLBytes(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("encoding=\"iso-8859-1\"") || lower.contains("encoding='iso-8859-1'")) {
            text = new String(data, StandardCharsets.ISO_8859_1)
                    .replace("encoding=\"ISO-8859-1\"", "encoding=\"UTF-8\"")
                    .replace("encoding='ISO-8859-1'", "encoding=\"UTF-8\"");
        }
        return replaceNamedEntities(text);
    }

    private static String replaceNamedEntities(String text) {
        return text.replace("&mdash;", "—").replace("&hellip;", "…").replace("&nbsp;", "\u00A0");
    }

    private static String stripDoctype(String text) {
        int idx = indexOfIgnoreCase(text, "<!DOCTYPE");
        while (idx >= 0) {
            int end = findDoctypeEnd(text, idx);
            if (end < 0) break;
            text = text.substring(0, idx) + text.substring(end + 1);
            idx = indexOfIgnoreCase(text, "<!DOCTYPE");
        }
        return text;
    }

    private static int indexOfIgnoreCase(String s, String needle) {
        return s.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static int findDoctypeEnd(String s, int start) {
        int depth = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (c == quote) inQuote = false;
                continue;
            }
            if (c == '\'' || c == '"') {
                inQuote = true;
                quote = c;
                continue;
            }
            if (c == '[') depth++;
            else if (c == ']') depth = Math.max(0, depth - 1);
            else if (c == '>' && depth == 0) return i;
        }
        return -1;
    }

    // AST
    public static final class Module {
        public Map<String, FunctionDef> functions = new HashMap<>();
        public Map<String, List<RuleDef>> rules = new HashMap<>();
        public Map<String, Expr> vars = new HashMap<>();
        public Map<String, String> namespaces = new HashMap<>();
        public List<ImportDecl> imports = new ArrayList<>();
        public Expr expr;
    }

    public static final class ImportDecl { public String iri; public String alias; }
    public interface Expr {}
    public interface Pattern {}

    public static final class Literal implements Expr { public Object value; Literal(Object v){value=v;} }
    public static final class VarRef implements Expr { public String name; VarRef(String n){name=n;} }
    public static final class IfExpr implements Expr { public Expr cond, thenExpr, elseExpr; }
    public static final class LetExpr implements Expr { public String name; public Expr value, body; }
    public static final class ForExpr implements Expr { public String name; public Expr seq, where, body; }
    public static final class MatchExpr implements Expr { public Expr target; public List<MatchCase> cases = new ArrayList<>(); public Expr defaultExpr; }
    public static final class MatchCase { public Pattern pattern; public Expr expr; }
    public static final class FuncCall implements Expr { public String name; public List<Expr> args = new ArrayList<>(); }
    public static final class UnaryOp implements Expr { public String op; public Expr expr; }
    public static final class BinaryOp implements Expr { public String op; public Expr left, right; }
    public static final class PathExpr implements Expr { public PathStart start; public List<PathStep> steps = new ArrayList<>(); }
    public static final class Constructor implements Expr { public String name; public List<AttrConstructor> attrs = new ArrayList<>(); public List<Expr> contents = new ArrayList<>(); }
    public static final class AttrConstructor { public String name; public Expr expr; }
    public static final class TextConstructor implements Expr { public Expr expr; }
    public static final class Text implements Expr { public String value; Text(String v){value=v;} }
    public static final class Interp implements Expr { public Expr expr; }
    public static final class PathStart { public String kind; public String name; }
    public static final class PathStep { public String axis; public StepTest test; public List<Expr> predicates = new ArrayList<>(); }
    public static final class StepTest { public String kind; public String name; }

    public static final class WildcardPattern implements Pattern {}
    public static final class ElementPattern implements Pattern { public String name; public String var; public Pattern child; }
    public static final class TypedPattern implements Pattern { public String kind; }
    public static final class AttributePattern implements Pattern { public String name; }

    public static final class Param { public String name; public String typeRef; public Expr defaultExpr; }
    public static final class FunctionDef { public List<Param> params = new ArrayList<>(); public Expr body; }
    public static final class RuleDef { public Pattern pattern; public Expr body; }

    // Lexer / Parser
    enum TokenKind { EOF, KW, IDENT, OP, PUNCT, STRING, NUMBER, DOT, SLASH, AT }

    static final class Token {
        final TokenKind kind;
        final String val;
        final int pos;
        Token(TokenKind kind, String val, int pos) { this.kind = kind; this.val = val; this.pos = pos; }
    }

    static final Set<String> KEYWORDS = Set.of("xform","version","import","as","ns","def","var","let","in","for","where","return","if","then","else","match","case","default","and","or","not","div","mod","rule");

    static final class Lexer {
        final String text;
        int pos;
        Token buffer;
        Lexer(String text) { this.text = text; }
        Token peek() { if (buffer == null) buffer = nextToken(); return buffer; }
        Token next() { if (buffer != null) { Token t = buffer; buffer = null; return t; } return nextToken(); }
        Token expect(TokenKind kind, String value) { Token t = next(); if (t.kind != kind || (value != null && !value.isEmpty() && !Objects.equals(t.val, value))) throw new XFormException("expected " + kind + " " + (value == null ? "" : value) + " at " + t.pos); return t; }
        void clearBuffer() { buffer = null; }

        private void skipWsComments() {
            while (pos < text.length()) {
                char ch = text.charAt(pos);
                if (Character.isWhitespace(ch)) { pos++; continue; }
                if (ch == '#') { while (pos < text.length() && text.charAt(pos) != '\n') pos++; continue; }
                break;
            }
        }

        private Token nextToken() {
            skipWsComments();
            if (pos >= text.length()) return new Token(TokenKind.EOF, "", pos);
            char ch = text.charAt(pos);
            if (ch == ':' && pos + 1 < text.length() && text.charAt(pos + 1) == '=') { int start = pos; pos += 2; return new Token(TokenKind.OP, ":=", start); }
            if ("(){}[],:;".indexOf(ch) >= 0) { pos++; return new Token(TokenKind.PUNCT, String.valueOf(ch), pos - 1); }
            if (ch == '.') {
                int start = pos;
                if (pos + 1 < text.length() && text.substring(pos, pos + 2).equals("..")) { pos += 2; return new Token(TokenKind.DOT, "..", start); }
                if (pos + 2 < text.length() && text.substring(pos, pos + 3).equals(".//")) { pos += 3; return new Token(TokenKind.DOT, ".//", start); }
                pos++; return new Token(TokenKind.DOT, ".", start);
            }
            if (ch == '/') {
                int start = pos;
                if (pos + 1 < text.length() && text.substring(pos, pos + 2).equals("//")) { pos += 2; return new Token(TokenKind.SLASH, "//", start); }
                pos++; return new Token(TokenKind.SLASH, "/", start);
            }
            if ("<>=!+-*".indexOf(ch) >= 0) {
                int start = pos++; if (pos < text.length() && text.charAt(pos) == '=') { pos++; return new Token(TokenKind.OP, text.substring(start, pos), start); }
                return new Token(TokenKind.OP, String.valueOf(ch), start);
            }
            if (ch == '\'' || ch == '"') {
                char quote = ch; int start = pos++; StringBuilder out = new StringBuilder();
                while (pos < text.length()) {
                    char c = text.charAt(pos);
                    if (c == '\\') {
                        pos++; if (pos >= text.length()) break; char esc = text.charAt(pos);
                        switch (esc) {
                            case 'n': out.append('\n'); break;
                            case 't': out.append('\t'); break;
                            case 'r': out.append('\r'); break;
                            case 'u':
                                if (pos + 4 < text.length()) {
                                    String hex = text.substring(pos + 1, pos + 5);
                                    out.append((char) Integer.parseInt(hex, 16));
                                    pos += 4;
                                }
                                break;
                            default: out.append(esc);
                        }
                        pos++; continue;
                    }
                    if (c == quote) { pos++; return new Token(TokenKind.STRING, out.toString(), start); }
                    out.append(c); pos++;
                }
                throw new XFormException("unterminated string at " + start);
            }
            if (Character.isDigit(ch)) {
                int start = pos;
                while (pos < text.length()) {
                    char c = text.charAt(pos);
                    if (!Character.isDigit(c) && c != '.') break;
                    pos++;
                }
                return new Token(TokenKind.NUMBER, text.substring(start, pos), start);
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = pos;
                while (pos < text.length()) {
                    char c = text.charAt(pos);
                    if (c == ':') {
                        if (pos + 1 < text.length()) {
                            char n = text.charAt(pos + 1);
                            if (Character.isLetterOrDigit(n) || n == '_' || n == '-') { pos++; continue; }
                        }
                        break;
                    }
                    if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) break;
                    pos++;
                }
                String val = text.substring(start, pos);
                return new Token(KEYWORDS.contains(val) ? TokenKind.KW : TokenKind.IDENT, val, start);
            }
            if (ch == '@') { pos++; return new Token(TokenKind.AT, "@", pos - 1); }
            throw new XFormException("unexpected character '" + ch + "' at " + pos);
        }
    }

    public static final class Parser {
        final String text;
        final Lexer lexer;
        public Parser(String text) { this.text = text; this.lexer = new Lexer(text); }

        public Module parseModule() {
            Module m = new Module();
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.KW && tok.val.equals("xform")) {
                lexer.next(); lexer.expect(TokenKind.KW, "version");
                String version = lexer.expect(TokenKind.STRING, null).val;
                if (!"2.0".equals(version)) throw new XFormException("XFST0005: unsupported version");
                lexer.expect(TokenKind.PUNCT, ";");
            }
            while (true) {
                tok = lexer.peek();
                if (tok.kind == TokenKind.KW && tok.val.equals("ns")) { parseNs(m.namespaces); continue; }
                if (tok.kind == TokenKind.KW && tok.val.equals("import")) { parseImport(m.imports); continue; }
                if (tok.kind == TokenKind.KW && tok.val.equals("var")) { Object[] v = parseVar(); m.vars.put((String) v[0], (Expr) v[1]); continue; }
                if (tok.kind == TokenKind.KW && tok.val.equals("def")) { parseDef(m.functions); continue; }
                if (tok.kind == TokenKind.KW && tok.val.equals("rule")) { parseRule(m.rules); continue; }
                break;
            }
            if (lexer.peek().kind != TokenKind.EOF) {
                m.expr = parseExpr();
                if (lexer.peek().kind != TokenKind.EOF) throw new XFormException("unexpected token at " + lexer.peek().pos);
            }
            return m;
        }

        private void parseNs(Map<String, String> namespaces) {
            lexer.expect(TokenKind.KW, "ns");
            String prefix = lexer.expect(TokenKind.STRING, null).val;
            lexer.expect(TokenKind.OP, "=");
            String uri = lexer.expect(TokenKind.STRING, null).val;
            lexer.expect(TokenKind.PUNCT, ";");
            namespaces.put(prefix, uri);
        }

        private void parseImport(List<ImportDecl> imports) {
            lexer.expect(TokenKind.KW, "import");
            ImportDecl d = new ImportDecl();
            d.iri = lexer.expect(TokenKind.STRING, null).val;
            if (lexer.peek().kind == TokenKind.KW && lexer.peek().val.equals("as")) {
                lexer.next(); d.alias = lexer.expect(TokenKind.IDENT, null).val;
            }
            lexer.expect(TokenKind.PUNCT, ";");
            imports.add(d);
        }

        private Object[] parseVar() {
            lexer.expect(TokenKind.KW, "var");
            String name = lexer.expect(TokenKind.IDENT, null).val;
            lexer.expect(TokenKind.OP, ":=");
            Expr value = parseExpr();
            lexer.expect(TokenKind.PUNCT, ";");
            return new Object[]{name, value};
        }

        private void parseDef(Map<String, FunctionDef> functions) {
            lexer.expect(TokenKind.KW, "def");
            String name = parseQName();
            lexer.expect(TokenKind.PUNCT, "(");
            List<Param> params = new ArrayList<>();
            if (!(lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals(")"))) {
                params.add(parseParam());
                while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals(",")) { lexer.next(); params.add(parseParam()); }
            }
            lexer.expect(TokenKind.PUNCT, ")");
            lexer.expect(TokenKind.OP, ":=");
            Expr body = parseExpr();
            lexer.expect(TokenKind.PUNCT, ";");
            FunctionDef fd = new FunctionDef(); fd.params = params; fd.body = body; functions.put(name, fd);
        }

        private Param parseParam() {
            Param p = new Param();
            p.name = lexer.expect(TokenKind.IDENT, null).val;
            if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals(":")) { lexer.next(); p.typeRef = parseTypeRef(); }
            if (lexer.peek().kind == TokenKind.OP && lexer.peek().val.equals(":=")) { lexer.next(); p.defaultExpr = parseExpr(); }
            return p;
        }

        private String parseTypeRef() {
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.IDENT && Set.of("string", "number", "boolean", "null", "map").contains(tok.val)) return lexer.next().val;
            return parseQName();
        }

        private void parseRule(Map<String, List<RuleDef>> rules) {
            lexer.expect(TokenKind.KW, "rule");
            String name = parseQName();
            lexer.expect(TokenKind.KW, "match");
            Pattern pat = parsePattern();
            lexer.expect(TokenKind.OP, ":=");
            Expr body = parseExpr();
            lexer.expect(TokenKind.PUNCT, ";");
            RuleDef rd = new RuleDef(); rd.pattern = pat; rd.body = body;
            rules.computeIfAbsent(name, k -> new ArrayList<>()).add(rd);
        }

        Expr parseExpr() {
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.KW && tok.val.equals("if")) return parseIf();
            if (tok.kind == TokenKind.KW && tok.val.equals("let")) return parseLet();
            if (tok.kind == TokenKind.KW && tok.val.equals("for")) return parseFor();
            if (tok.kind == TokenKind.KW && tok.val.equals("match")) return parseMatch();
            return parseOr();
        }

        private Expr parseIf() { lexer.expect(TokenKind.KW, "if"); IfExpr e = new IfExpr(); e.cond = parseExpr(); lexer.expect(TokenKind.KW, "then"); e.thenExpr = parseExpr(); lexer.expect(TokenKind.KW, "else"); e.elseExpr = parseExpr(); return e; }
        private Expr parseLet() { lexer.expect(TokenKind.KW, "let"); LetExpr e = new LetExpr(); e.name = lexer.expect(TokenKind.IDENT, null).val; lexer.expect(TokenKind.OP, ":="); e.value = parseExpr(); lexer.expect(TokenKind.KW, "in"); e.body = parseExpr(); return e; }
        private Expr parseFor() {
            lexer.expect(TokenKind.KW, "for"); ForExpr e = new ForExpr(); e.name = lexer.expect(TokenKind.IDENT, null).val; lexer.expect(TokenKind.KW, "in"); e.seq = parseExpr();
            if (lexer.peek().kind == TokenKind.KW && lexer.peek().val.equals("where")) { lexer.next(); e.where = parseExpr(); }
            lexer.expect(TokenKind.KW, "return"); e.body = parseExpr(); return e;
        }
        private Expr parseMatch() {
            lexer.expect(TokenKind.KW, "match"); MatchExpr e = new MatchExpr(); e.target = parseExpr(); lexer.expect(TokenKind.PUNCT, ":");
            while (true) {
                Token tok = lexer.peek();
                if (tok.kind == TokenKind.KW && tok.val.equals("case")) {
                    lexer.next(); MatchCase c = new MatchCase(); c.pattern = parsePattern(); lexer.expect(TokenKind.OP, "="); lexer.expect(TokenKind.OP, ">"); c.expr = parseExpr(); lexer.expect(TokenKind.PUNCT, ";"); e.cases.add(c); continue;
                }
                if (tok.kind == TokenKind.KW && tok.val.equals("default")) {
                    lexer.next(); lexer.expect(TokenKind.OP, "="); lexer.expect(TokenKind.OP, ">"); e.defaultExpr = parseExpr(); lexer.expect(TokenKind.PUNCT, ";"); break;
                }
                break;
            }
            return e;
        }
        private Expr parseOr() { Expr e = parseAnd(); while (lexer.peek().kind == TokenKind.KW && lexer.peek().val.equals("or")) { lexer.next(); BinaryOp b = new BinaryOp(); b.op = "or"; b.left = e; b.right = parseAnd(); e = b; } return e; }
        private Expr parseAnd() { Expr e = parseEq(); while (lexer.peek().kind == TokenKind.KW && lexer.peek().val.equals("and")) { lexer.next(); BinaryOp b = new BinaryOp(); b.op = "and"; b.left = e; b.right = parseEq(); e = b; } return e; }
        private Expr parseEq() { Expr e = parseRel(); while (lexer.peek().kind == TokenKind.OP && (lexer.peek().val.equals("=") || lexer.peek().val.equals("!="))) { String op = lexer.next().val; BinaryOp b = new BinaryOp(); b.op = op; b.left = e; b.right = parseRel(); e = b; } return e; }
        private Expr parseRel() { Expr e = parseAdd(); while (lexer.peek().kind == TokenKind.OP && Set.of("<", "<=", ">", ">=").contains(lexer.peek().val)) { String op = lexer.next().val; BinaryOp b = new BinaryOp(); b.op = op; b.left = e; b.right = parseAdd(); e = b; } return e; }
        private Expr parseAdd() { Expr e = parseMul(); while (lexer.peek().kind == TokenKind.OP && (lexer.peek().val.equals("+") || lexer.peek().val.equals("-"))) { String op = lexer.next().val; BinaryOp b = new BinaryOp(); b.op = op; b.left = e; b.right = parseMul(); e = b; } return e; }
        private Expr parseMul() {
            Expr e = parseUnary();
            while (true) {
                Token t = lexer.peek();
                if (t.kind == TokenKind.OP && t.val.equals("*")) { lexer.next(); BinaryOp b = new BinaryOp(); b.op = "*"; b.left = e; b.right = parseUnary(); e = b; continue; }
                if (t.kind == TokenKind.KW && (t.val.equals("div") || t.val.equals("mod"))) { String op = lexer.next().val; BinaryOp b = new BinaryOp(); b.op = op; b.left = e; b.right = parseUnary(); e = b; continue; }
                break;
            }
            return e;
        }
        private Expr parseUnary() {
            Token t = lexer.peek();
            if (t.kind == TokenKind.OP && t.val.equals("-")) { lexer.next(); UnaryOp u = new UnaryOp(); u.op = "-"; u.expr = parseUnary(); return u; }
            if (t.kind == TokenKind.KW && t.val.equals("not")) { lexer.next(); UnaryOp u = new UnaryOp(); u.op = "not"; u.expr = parseUnary(); return u; }
            return parsePrimary();
        }
        private Expr parsePrimary() {
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.NUMBER) { lexer.next(); return new Literal(Double.parseDouble(tok.val)); }
            if (tok.kind == TokenKind.STRING) { lexer.next(); return new Literal(tok.val); }
            if (tok.kind == TokenKind.PUNCT && tok.val.equals("(")) { lexer.next(); Expr e = parseExpr(); lexer.expect(TokenKind.PUNCT, ")"); return e; }
            if (tok.kind == TokenKind.IDENT && tok.val.equals("text")) {
                int savedPos = lexer.pos; Token savedBuf = lexer.buffer;
                lexer.next();
                if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals("{")) {
                    lexer.next(); TextConstructor tc = new TextConstructor(); tc.expr = parseExpr(); lexer.expect(TokenKind.PUNCT, "}"); return tc;
                }
                lexer.pos = savedPos; lexer.buffer = savedBuf;
            }
            if (tok.kind == TokenKind.OP && tok.val.equals("<")) return parseConstructor();
            if (tok.kind == TokenKind.DOT || tok.kind == TokenKind.SLASH) return parsePath(null);
            if (tok.kind == TokenKind.IDENT) {
                String name = lexer.next().val;
                if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals("(")) return parseFuncCall(name);
                if (pathContinues()) {
                    PathStart s = new PathStart(); s.kind = "var"; s.name = name; return parsePath(s);
                }
                return new VarRef(name);
            }
            throw new XFormException("unexpected token at " + tok.pos);
        }
        private Expr parseFuncCall(String name) {
            lexer.expect(TokenKind.PUNCT, "("); FuncCall fc = new FuncCall(); fc.name = name;
            if (!(lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals(")"))) {
                fc.args.add(parseExpr()); while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals(",")) { lexer.next(); fc.args.add(parseExpr()); }
            }
            lexer.expect(TokenKind.PUNCT, ")"); return fc;
        }
        private boolean pathContinues() { Token t = lexer.peek(); return t.kind == TokenKind.SLASH || t.kind == TokenKind.DOT || t.kind == TokenKind.AT; }
        private Expr parsePath(PathStart start) {
            PathStart actual = start;
            if (actual == null) {
                Token tok = lexer.next(); actual = new PathStart();
                if (tok.kind == TokenKind.DOT) actual.kind = tok.val.equals(".//") ? "desc" : "context";
                else if (tok.kind == TokenKind.SLASH) actual.kind = tok.val.equals("//") ? "desc_root" : "root";
                else throw new XFormException("invalid path start at " + tok.pos);
            }
            List<PathStep> steps = new ArrayList<>();
            if (Set.of("root", "context", "var").contains(actual.kind)) {
                Token tok = lexer.peek();
                if (tok.kind == TokenKind.AT) {
                    lexer.next(); PathStep s = new PathStep(); s.axis = "attr"; s.test = stepName(parseQName()); steps.add(s);
                } else if ((tok.kind == TokenKind.OP && tok.val.equals("*")) || tok.kind == TokenKind.IDENT) {
                    PathStep s = new PathStep(); s.axis = "child"; s.test = parseStepTest(); s.predicates = parsePredicates(); steps.add(s);
                }
            }
            if (actual.kind.equals("desc") || actual.kind.equals("desc_root")) {
                Token tok = lexer.peek();
                if (tok.kind == TokenKind.IDENT || tok.kind == TokenKind.OP) {
                    PathStep s = new PathStep(); s.axis = "desc_or_self"; s.test = parseStepTest(); s.predicates = parsePredicates(); steps.add(s);
                }
            }
            while (true) {
                Token tok = lexer.peek();
                if (tok.kind == TokenKind.SLASH) {
                    String axis = tok.val.equals("//") ? "desc" : "child";
                    lexer.next(); StepTest test; List<Expr> preds = new ArrayList<>();
                    if (lexer.peek().kind == TokenKind.AT) { lexer.next(); test = stepName(parseQName()); axis = "attr"; }
                    else { test = parseStepTest(); preds = parsePredicates(); }
                    PathStep s = new PathStep(); s.axis = axis; s.test = test; s.predicates = preds; steps.add(s); continue;
                }
                if (tok.kind == TokenKind.DOT) {
                    if (tok.val.equals(".")) {
                        lexer.next();
                        if (lexer.peek().kind == TokenKind.AT) { lexer.next(); PathStep s = new PathStep(); s.axis = "attr"; s.test = stepName(parseQName()); steps.add(s); }
                        else { PathStep s = new PathStep(); s.axis = "self"; s.test = new StepTest(); s.test.kind = "node"; steps.add(s); }
                        continue;
                    }
                    if (tok.val.equals("..")) { lexer.next(); PathStep s = new PathStep(); s.axis = "parent"; s.test = new StepTest(); s.test.kind = "node"; steps.add(s); continue; }
                }
                if (tok.kind == TokenKind.AT) { lexer.next(); PathStep s = new PathStep(); s.axis = "attr"; s.test = stepName(parseQName()); steps.add(s); continue; }
                break;
            }
            PathExpr e = new PathExpr(); e.start = actual; e.steps = steps; return e;
        }
        private StepTest stepName(String name) { StepTest t = new StepTest(); t.kind = "name"; t.name = name; return t; }
        private StepTest parseStepTest() {
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.OP && tok.val.equals("*")) { lexer.next(); StepTest t = new StepTest(); t.kind = "wildcard"; return t; }
            if (tok.kind == TokenKind.IDENT) {
                if (Set.of("text", "node", "comment", "pi").contains(tok.val)) { lexer.next(); lexer.expect(TokenKind.PUNCT, "("); lexer.expect(TokenKind.PUNCT, ")"); StepTest t = new StepTest(); t.kind = tok.val; return t; }
                return stepName(parseQName());
            }
            throw new XFormException("invalid step test at " + tok.pos);
        }
        private List<Expr> parsePredicates() {
            List<Expr> preds = new ArrayList<>();
            while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals("[")) { lexer.next(); preds.add(parseExpr()); lexer.expect(TokenKind.PUNCT, "]"); }
            return preds;
        }
        private String parseQName() { return lexer.expect(TokenKind.IDENT, null).val; }
        private Pattern parsePattern() {
            Token tok = lexer.peek();
            if (tok.kind == TokenKind.AT) { lexer.next(); AttributePattern p = new AttributePattern(); p.name = parseQName(); return p; }
            if (tok.kind == TokenKind.IDENT && Set.of("node", "text", "comment").contains(tok.val)) { lexer.next(); lexer.expect(TokenKind.PUNCT, "("); lexer.expect(TokenKind.PUNCT, ")"); TypedPattern p = new TypedPattern(); p.kind = tok.val; return p; }
            if (tok.kind == TokenKind.IDENT && tok.val.equals("_")) { lexer.next(); return new WildcardPattern(); }
            if (tok.kind == TokenKind.OP && tok.val.equals("<")) {
                lexer.next(); ElementPattern p = new ElementPattern(); p.name = parseQName(); lexer.expect(TokenKind.OP, ">");
                if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().val.equals("{")) { lexer.next(); p.var = lexer.expect(TokenKind.IDENT, null).val; lexer.expect(TokenKind.PUNCT, "}"); }
                else if (lexer.peek().kind == TokenKind.OP && lexer.peek().val.equals("<")) { p.child = parsePattern(); }
                else throw new XFormException("invalid element pattern content");
                lexer.expect(TokenKind.OP, "<"); lexer.expect(TokenKind.SLASH, "/"); String end = parseQName(); if (!end.equals(p.name)) throw new XFormException("mismatched pattern end tag"); lexer.expect(TokenKind.OP, ">"); return p;
            }
            throw new XFormException("invalid pattern at " + tok.pos);
        }
        private Expr parseConstructor() {
            lexer.expect(TokenKind.OP, "<"); Constructor c = new Constructor(); c.name = parseQName();
            while (true) {
                Token tok = lexer.peek();
                if (tok.kind == TokenKind.OP && tok.val.equals(">")) { lexer.next(); break; }
                if (tok.kind == TokenKind.SLASH && tok.val.equals("/")) { lexer.next(); lexer.expect(TokenKind.OP, ">"); return c; }
                AttrConstructor a = new AttrConstructor(); a.name = parseQName(); lexer.expect(TokenKind.OP, "="); lexer.expect(TokenKind.PUNCT, "{"); a.expr = parseExpr(); lexer.expect(TokenKind.PUNCT, "}"); c.attrs.add(a);
            }
            lexer.clearBuffer();
            while (true) {
                if (lexer.pos >= text.length()) throw new XFormException("unterminated constructor");
                if (lexer.pos + 2 <= text.length() && text.substring(lexer.pos, lexer.pos + 2).equals("</")) {
                    EndTag end = readEndTag();
                    if (!end.name.equals(c.name)) throw new XFormException("mismatched end tag");
                    lexer.pos = end.newPos; lexer.clearBuffer(); break;
                }
                if (lexer.pos + 5 <= text.length() && text.substring(lexer.pos, lexer.pos + 5).equals("text{")) {
                    lexer.pos += 4; lexer.clearBuffer(); lexer.expect(TokenKind.PUNCT, "{"); TextConstructor tc = new TextConstructor(); tc.expr = parseExpr(); lexer.expect(TokenKind.PUNCT, "}"); c.contents.add(tc); continue;
                }
                char ch = text.charAt(lexer.pos);
                if (ch == '<') { lexer.clearBuffer(); c.contents.add(parseConstructor()); continue; }
                if (ch == '{') { lexer.pos++; lexer.clearBuffer(); Interp i = new Interp(); i.expr = parseExpr(); lexer.expect(TokenKind.PUNCT, "}"); c.contents.add(i); continue; }
                String raw = parseCharData();
                if (!raw.isEmpty() && !stripSpace(raw).isEmpty()) c.contents.add(new Text(raw));
            }
            return c;
        }
        private String parseCharData() { int start = lexer.pos; while (lexer.pos < text.length()) { char ch = text.charAt(lexer.pos); if (ch == '<' || ch == '{') break; lexer.pos++; } return text.substring(start, lexer.pos); }
        private EndTag readEndTag() {
            int pos = lexer.pos; if (pos + 2 > text.length() || !text.substring(pos, pos + 2).equals("</")) throw new XFormException("expected end tag"); pos += 2;
            int start = pos;
            while (pos < text.length()) { char c = text.charAt(pos); if (!(Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-')) break; pos++; }
            String name = text.substring(start, pos);
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
            if (pos >= text.length() || text.charAt(pos) != '>') throw new XFormException("unterminated end tag");
            return new EndTag(name, pos + 1);
        }
    }
    static final class EndTag { final String name; final int newPos; EndTag(String n, int p){name=n;newPos=p;} }
    private static String stripSpace(String s){ StringBuilder out = new StringBuilder(); for (int i=0;i<s.length();i++){ char r=s.charAt(i); if (r!=' '&&r!='\t'&&r!='\n'&&r!='\r') out.append(r);} return out.toString(); }

    // Evaluator
    static final class Context {
        Object contextItem;
        Map<String, List<Object>> variables;
        Map<String, FunctionDef> functions;
        Map<String, List<RuleDef>> rules;
        Integer position;
        Integer last;
    }

    public static List<Object> evalModule(Module module, Node doc) {
        Context ctx = new Context();
        ctx.contextItem = doc;
        ctx.variables = new HashMap<>();
        ctx.functions = new HashMap<>(module.functions);
        ctx.rules = new HashMap<>(module.rules);
        for (Map.Entry<String, Expr> e : module.vars.entrySet()) ctx.variables.put(e.getKey(), evalExpr(e.getValue(), ctx));
        if (module.expr == null) return new ArrayList<>();
        return evalExpr(module.expr, ctx);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> evalExpr(Expr expr, Context ctx) {
        if (expr instanceof Literal e) return seq(e.value);
        if (expr instanceof VarRef e) {
            if (ctx.variables.containsKey(e.name)) return ctx.variables.get(e.name);
            if (ctx.functions.containsKey(e.name)) return seq(new FunctionRef(e.name));
            if (ctx.contextItem instanceof Node node) {
                List<Object> out = new ArrayList<>();
                for (Node child : node.children) if ("element".equals(child.kind) && e.name.equals(child.name)) out.add(child);
                return out;
            }
            return new ArrayList<>();
        }
        if (expr instanceof IfExpr e) return toBoolean(evalExpr(e.cond, ctx)) ? evalExpr(e.thenExpr, ctx) : evalExpr(e.elseExpr, ctx);
        if (expr instanceof LetExpr e) {
            Context n = cloneCtx(ctx); n.variables = copyVars(ctx.variables); n.variables.put(e.name, evalExpr(e.value, ctx)); return evalExpr(e.body, n);
        }
        if (expr instanceof ForExpr e) {
            List<Object> source = evalExpr(e.seq, ctx); List<Object> out = new ArrayList<>(); int total = source.size();
            for (int i = 0; i < source.size(); i++) {
                Object item = source.get(i); Context n = cloneCtx(ctx); n.contextItem = item; n.variables = copyVars(ctx.variables); n.variables.put(e.name, seq(item)); n.position = i + 1; n.last = total;
                if (e.where != null && !toBoolean(evalExpr(e.where, n))) continue;
                out.addAll(evalExpr(e.body, n));
            }
            return out;
        }
        if (expr instanceof MatchExpr e) {
            List<Object> target = evalExpr(e.target, ctx); List<Object> out = new ArrayList<>();
            for (Object item : target) {
                boolean matchedAny = false;
                for (MatchCase c : e.cases) {
                    MatchResult mr = matchPattern(c.pattern, item);
                    if (mr.matched) {
                        matchedAny = true;
                        Context n = cloneCtx(ctx); n.contextItem = item; n.variables = copyVars(ctx.variables); n.variables.putAll(mr.bindings);
                        out.addAll(evalExpr(c.expr, n));
                        break;
                    }
                }
                if (!matchedAny) {
                    if (e.defaultExpr == null) throw new XFormException("XFDY0001: no matching case");
                    Context n = cloneCtx(ctx); n.contextItem = item; n.variables = copyVars(ctx.variables);
                    out.addAll(evalExpr(e.defaultExpr, n));
                }
            }
            return out;
        }
        if (expr instanceof FuncCall e) {
            List<List<Object>> args = new ArrayList<>();
            for (Expr a : e.args) args.add(evalExpr(a, ctx));
            return callFunction(e.name, args, ctx);
        }
        if (expr instanceof UnaryOp e) {
            List<Object> v = evalExpr(e.expr, ctx);
            if ("-".equals(e.op)) return seq(-toNumber(v));
            if ("not".equals(e.op)) return seq(!toBoolean(v));
        }
        if (expr instanceof BinaryOp e) {
            if ("and".equals(e.op)) { List<Object> l = evalExpr(e.left, ctx); if (!toBoolean(l)) return seq(false); return seq(toBoolean(evalExpr(e.right, ctx))); }
            if ("or".equals(e.op)) { List<Object> l = evalExpr(e.left, ctx); if (toBoolean(l)) return seq(true); return seq(toBoolean(evalExpr(e.right, ctx))); }
            return seq(evalBinary(e.op, evalExpr(e.left, ctx), evalExpr(e.right, ctx)));
        }
        if (expr instanceof PathExpr e) return evalPath(e, ctx);
        if (expr instanceof Constructor e) return seq(evalConstructor(e, ctx));
        if (expr instanceof TextConstructor e) { Node n = new Node("text"); n.value = toString(evalExpr(e.expr, ctx)); return seq(n); }
        if (expr instanceof Text e) return seq(e.value);
        if (expr instanceof Interp e) return evalExpr(e.expr, ctx);
        throw new XFormException("unknown expr");
    }

    private static Object evalBinary(String op, List<Object> left, List<Object> right) {
        if ("=".equals(op)) return valueEqual(left, right);
        if ("!=".equals(op)) return !valueEqual(left, right);
        double l = toNumber(left), r = toNumber(right);
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "div" -> l / r;
            case "mod" -> l % r;
            case "<" -> l < r;
            case "<=" -> l <= r;
            case ">" -> l > r;
            case ">=" -> l >= r;
            default -> throw new XFormException("unknown operator " + op);
        };
    }

    private static List<Object> evalPath(PathExpr expr, Context ctx) {
        List<PathStep> steps = new ArrayList<>(expr.steps);
        List<Object> base = new ArrayList<>();
        switch (expr.start.kind) {
            case "context" -> { if (ctx.contextItem != null) base.add(ctx.contextItem); }
            case "root" -> base = rootOf(ctx.contextItem);
            case "desc" -> { if (ctx.contextItem != null) base.add(ctx.contextItem); }
            case "desc_root" -> base = rootOf(ctx.contextItem);
            case "var" -> {
                if (expr.start.name != null) {
                    if (ctx.variables.containsKey(expr.start.name)) base = ctx.variables.get(expr.start.name);
                    else if (ctx.contextItem != null) {
                        base.add(ctx.contextItem);
                        PathStep s = new PathStep(); s.axis = "child"; s.test = new StepTest(); s.test.kind = "name"; s.test.name = expr.start.name;
                        steps.add(0, s);
                    }
                }
            }
        }
        List<Object> current = base;
        for (PathStep step : steps) current = applyStep(current, step, ctx);
        return current;
    }

    private static List<Object> rootOf(Object item) {
        if (!(item instanceof Node cur)) return new ArrayList<>();
        while (cur.parent != null) cur = cur.parent;
        return seq(cur);
    }

    private static List<Object> applyStep(List<Object> items, PathStep step, Context ctx) {
        List<Object> out = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Node node)) continue;
            List<Node> candidates = new ArrayList<>();
            switch (step.axis) {
                case "self" -> candidates.add(node);
                case "parent" -> { if (node.parent != null) candidates.add(node.parent); }
                case "desc_or_self" -> { candidates.add(node); candidates.addAll(iterDescendants(node)); }
                case "desc" -> candidates.addAll(iterDescendants(node));
                case "attr" -> {
                    if ("element".equals(node.kind)) {
                        if ("name".equals(step.test.kind) && step.test.name != null) {
                            if (node.attrs.containsKey(step.test.name)) {
                                Node a = new Node("attribute"); a.name = step.test.name; a.value = node.attrs.get(step.test.name); candidates.add(a);
                            }
                        } else if ("wildcard".equals(step.test.kind)) {
                            for (Map.Entry<String, String> e : node.attrs.entrySet()) { Node a = new Node("attribute"); a.name = e.getKey(); a.value = e.getValue(); candidates.add(a); }
                        }
                    }
                }
                case "child" -> candidates.addAll(node.children);
            }
            List<Node> filtered = new ArrayList<>();
            for (Node c : candidates) if (matchesStepTest(step.test, c)) filtered.add(c);
            for (Expr pred : step.predicates) {
                List<Node> predOut = new ArrayList<>();
                for (int i = 0; i < filtered.size(); i++) {
                    Node child = filtered.get(i); Context pctx = cloneCtx(ctx); pctx.contextItem = child; pctx.position = i + 1; pctx.last = filtered.size();
                    if (toBoolean(evalExpr(pred, pctx))) predOut.add(child);
                }
                filtered = predOut;
            }
            out.addAll(filtered);
        }
        return out;
    }

    private static boolean matchesStepTest(StepTest test, Node node) {
        return switch (test.kind) {
            case "wildcard" -> "element".equals(node.kind);
            case "text" -> "text".equals(node.kind);
            case "node" -> true;
            case "comment" -> "comment".equals(node.kind);
            case "pi" -> "pi".equals(node.kind);
            case "name" -> test.name != null && Objects.equals(node.name, test.name);
            default -> false;
        };
    }

    private static Node evalConstructor(Constructor expr, Context ctx) {
        Node node = new Node("element"); node.name = expr.name;
        for (AttrConstructor attr : expr.attrs) {
            node.attrs.put(attr.name, toString(evalExpr(attr.expr, ctx)));
            node.attrOrder.add(attr.name);
        }
        List<Node> children = new ArrayList<>();
        for (Expr content : expr.contents) {
            if (content instanceof Text t) {
                Node c = new Node("text"); c.value = t.value; children.add(c);
                continue;
            }
            List<Object> seq = evalExpr(content, ctx);
            for (Object item : seq) {
                if (item instanceof Node n) children.add(deepCopy(n, true));
                else { Node t = new Node("text"); t.value = toString(seq(item)); children.add(t); }
            }
        }
        for (Node c : children) c.parent = node;
        node.children = children;
        return node;
    }

    static final class FunctionRef { final String name; FunctionRef(String n){name=n;} }
    @FunctionalInterface interface BuiltinFn { List<Object> apply(List<List<Object>> args, Context ctx); }
    static final Map<String, BuiltinFn> BUILTINS = new HashMap<>();
    static {
        BUILTINS.put("string", (a,c)-> seq(toString(firstOrEmpty(a))));
        BUILTINS.put("number", (a,c)-> seq(toNumber(firstOrEmpty(a))));
        BUILTINS.put("boolean", (a,c)-> seq(toBoolean(firstOrEmpty(a))));
        BUILTINS.put("typeOf", XFormEngine::fnTypeOf);
        BUILTINS.put("name", XFormEngine::fnName);
        BUILTINS.put("attr", XFormEngine::fnAttr);
        BUILTINS.put("text", XFormEngine::fnText);
        BUILTINS.put("children", XFormEngine::fnChildren);
        BUILTINS.put("elements", XFormEngine::fnElements);
        BUILTINS.put("copy", XFormEngine::fnCopy);
        BUILTINS.put("count", XFormEngine::fnCount);
        BUILTINS.put("empty", XFormEngine::fnEmpty);
        BUILTINS.put("distinct", XFormEngine::fnDistinct);
        BUILTINS.put("sort", XFormEngine::fnSort);
        BUILTINS.put("concat", XFormEngine::fnConcat);
        BUILTINS.put("head", XFormEngine::fnHead);
        BUILTINS.put("tail", XFormEngine::fnTail);
        BUILTINS.put("last", XFormEngine::fnLast);
        BUILTINS.put("index", XFormEngine::fnIndex);
        BUILTINS.put("lookup", XFormEngine::fnLookup);
        BUILTINS.put("groupBy", XFormEngine::fnGroupBy);
        BUILTINS.put("seq", XFormEngine::fnSeq);
        BUILTINS.put("position", XFormEngine::fnPosition);
        BUILTINS.put("apply", XFormEngine::fnApply);
        BUILTINS.put("sum", XFormEngine::fnSum);
    }

    private static List<Object> callFunction(String name, List<List<Object>> args, Context ctx) {
        if (ctx.functions.containsKey(name)) return callUserFunction(ctx.functions.get(name), args, ctx);
        BuiltinFn b = BUILTINS.get(name);
        if (b == null) throw new XFormException("XFST0003: unknown function " + name);
        return b.apply(args, ctx);
    }

    private static List<Object> callUserFunction(FunctionDef fn, List<List<Object>> args, Context ctx) {
        if (args.size() > fn.params.size()) throw new XFormException("XFDY0002: wrong arity");
        Context n = cloneCtx(ctx); n.variables = copyVars(ctx.variables);
        for (int i = 0; i < args.size(); i++) n.variables.put(fn.params.get(i).name, args.get(i));
        for (int i = args.size(); i < fn.params.size(); i++) {
            Param p = fn.params.get(i);
            if (p.defaultExpr == null) throw new XFormException("XFDY0002: wrong arity");
            n.variables.put(p.name, evalExpr(p.defaultExpr, ctx));
        }
        return evalExpr(fn.body, n);
    }

    private static boolean toBoolean(List<Object> seq) {
        if (seq.isEmpty()) return false;
        for (Object item : seq) if (item instanceof Node) return true;
        for (Object item : seq) {
            if (item instanceof Boolean b && b) return true;
            if (item instanceof Integer i && i != 0) return true;
            if (item instanceof Double d && d != 0.0) return true;
            if (item instanceof String s && !s.isEmpty()) return true;
            if (item != null && !(item instanceof Boolean || item instanceof Integer || item instanceof Double || item instanceof String)) return true;
        }
        return false;
    }

    private static String toString(List<Object> seq) {
        if (seq.isEmpty()) return "";
        Object item = seq.get(0);
        if (item instanceof Node n) return n.stringValue();
        if (item == null) return "";
        if (item instanceof Boolean b) return b ? "true" : "false";
        if (item instanceof Double d) {
            if (d == Math.rint(d)) return String.valueOf((long) d.doubleValue());
            return String.valueOf(d);
        }
        return String.valueOf(item);
    }

    private static double toNumber(List<Object> seq) {
        if (seq.isEmpty()) return 0.0;
        Object item = seq.get(0);
        if (item instanceof Node n) item = n.stringValue();
        if (item instanceof Boolean b) return b ? 1.0 : 0.0;
        if (item instanceof Integer i) return i.doubleValue();
        if (item instanceof Double d) return d;
        if (item instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { throw new XFormException("XFDY0002: number conversion"); }
        }
        throw new XFormException("XFDY0002: number conversion");
    }

    private static boolean valueEqual(List<Object> left, List<Object> right) { return toString(left).equals(toString(right)); }

    static final class MatchResult { boolean matched; Map<String, List<Object>> bindings = new HashMap<>(); }
    @SuppressWarnings("unchecked")
    private static MatchResult matchPattern(Pattern pattern, Object item) {
        MatchResult r = new MatchResult();
        if (pattern instanceof WildcardPattern) { r.matched = true; return r; }
        if (pattern instanceof AttributePattern p) {
            r.matched = item instanceof Node n && "attribute".equals(n.kind) && p.name.equals(n.name); return r;
        }
        if (pattern instanceof TypedPattern p) {
            if (item == null) { r.matched = false; return r; }
            if ("node".equals(p.kind)) { r.matched = item instanceof Node; return r; }
            if (item instanceof Node n) {
                r.matched = p.kind.equals(n.kind);
                return r;
            }
            r.matched = false; return r;
        }
        if (pattern instanceof ElementPattern p) {
            if (item instanceof Node n && "element".equals(n.kind) && p.name.equals(n.name)) {
                r.matched = true;
                if (p.var != null) {
                    List<Object> children = new ArrayList<>(n.children);
                    r.bindings.put(p.var, children); return r;
                }
                if (p.child != null) {
                    for (Node child : n.children) {
                        MatchResult cr = matchPattern(p.child, child);
                        if (cr.matched) { r.bindings.putAll(cr.bindings); return r; }
                    }
                    r.matched = false;
                }
                return r;
            }
        }
        r.matched = false; return r;
    }

    private static List<Object> fnTypeOf(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty() || args.get(0).isEmpty()) return seq("null");
        Object item = args.get(0).get(0);
        if (item instanceof Node) return seq("node");
        if (item instanceof Map<?, ?>) return seq("map");
        if (item instanceof Boolean) return seq("boolean");
        if (item instanceof Integer || item instanceof Double) return seq("number");
        if (item == null) return seq("null");
        return seq("string");
    }
    private static List<Object> fnName(List<List<Object>> args, Context _ctx) { if (args.isEmpty()||args.get(0).isEmpty()) return seq(""); Object i=args.get(0).get(0); return (i instanceof Node n)?seq(n.name):seq(""); }
    private static List<Object> fnAttr(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty() || args.get(0).isEmpty()) return seq("");
        Object i = args.get(0).get(0);
        if (!(i instanceof Node n) || !"element".equals(n.kind) || args.size() < 2) return seq("");
        String key = toString(args.get(1));
        return seq(n.attrs.getOrDefault(key, ""));
    }
    private static List<Object> fnText(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty() || args.get(0).isEmpty()) return seq("");
        Object i = args.get(0).get(0);
        if (i instanceof Node n) {
            boolean deep = args.size() <= 1 || toBoolean(args.get(1));
            if (deep) return seq(n.stringValue());
            if ("element".equals(n.kind) || "document".equals(n.kind)) {
                StringBuilder s = new StringBuilder(); for (Node c : n.children) if ("text".equals(c.kind)) s.append(c.value); return seq(s.toString());
            }
            return seq(n.stringValue());
        }
        return seq(toString(args.get(0)));
    }
    private static List<Object> fnChildren(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty()||args.get(0).isEmpty()||!(args.get(0).get(0) instanceof Node n)) return new ArrayList<>(); return new ArrayList<>(n.children);
    }
    private static List<Object> fnElements(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty()||args.get(0).isEmpty()||!(args.get(0).get(0) instanceof Node n)) return new ArrayList<>();
        if (!("element".equals(n.kind) || "document".equals(n.kind))) return new ArrayList<>();
        String nameTest = args.size() > 1 ? toString(args.get(1)) : ""; List<Object> out = new ArrayList<>();
        for (Node c : n.children) if ("element".equals(c.kind) && (nameTest.isEmpty() || nameTest.equals(c.name))) out.add(c); return out;
    }
    private static List<Object> fnCopy(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty()||args.get(0).isEmpty()||!(args.get(0).get(0) instanceof Node n)) return new ArrayList<>(); boolean recurse = args.size() <= 1 || toBoolean(args.get(1)); return seq(deepCopy(n, recurse));
    }
    private static List<Object> fnCount(List<List<Object>> args, Context _ctx) { return seq((double)(args.isEmpty()?0:args.get(0).size())); }
    private static List<Object> fnEmpty(List<List<Object>> args, Context _ctx) { return seq(args.isEmpty() || args.get(0).isEmpty()); }
    private static List<Object> fnDistinct(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty()) return new ArrayList<>(); Set<String> seen = new HashSet<>(); List<Object> out = new ArrayList<>();
        for (Object item : args.get(0)) { String k = toString(seq(item)); if (seen.add(k)) out.add(item); }
        return out;
    }
    private static List<Object> fnSort(List<List<Object>> args, Context ctx) {
        if (args.isEmpty()) return new ArrayList<>(); List<Object> out = new ArrayList<>(args.get(0)); String keyFn = "";
        if (args.size() > 1 && !args.get(1).isEmpty() && args.get(1).get(0) instanceof FunctionRef fr) keyFn = fr.name;
        final String finalKeyFn = keyFn;
        out.sort((a,b) -> {
            if (!finalKeyFn.isEmpty()) {
                FunctionDef fn = ctx.functions.get(finalKeyFn);
                String ka = toString(callUserFunction(fn, List.of(seq(a)), ctx));
                String kb = toString(callUserFunction(fn, List.of(seq(b)), ctx));
                return ka.compareTo(kb);
            }
            return toString(seq(a)).compareTo(toString(seq(b)));
        });
        return out;
    }
    private static List<Object> fnConcat(List<List<Object>> args, Context _ctx) { List<Object> out = new ArrayList<>(); for (List<Object> s : args) out.addAll(s); return out; }
    private static List<Object> fnHead(List<List<Object>> args, Context _ctx) { if (args.isEmpty()||args.get(0).isEmpty()) return new ArrayList<>(); return seq(args.get(0).get(0)); }
    private static List<Object> fnTail(List<List<Object>> args, Context _ctx) { if (args.isEmpty()||args.get(0).isEmpty()) return new ArrayList<>(); return new ArrayList<>(args.get(0).subList(1, args.get(0).size())); }
    private static List<Object> fnLast(List<List<Object>> args, Context ctx) {
        if (args.isEmpty() || args.get(0).isEmpty()) { if (ctx.last == null) return new ArrayList<>(); return seq(ctx.last.doubleValue()); }
        List<Object> s = args.get(0); return seq(s.get(s.size()-1));
    }
    private static List<Object> fnIndex(List<List<Object>> args, Context ctx) {
        if (args.isEmpty()) return new ArrayList<>(); List<Object> seq = args.get(0); String keyFn = "";
        if (args.size() > 1 && !args.get(1).isEmpty() && args.get(1).get(0) instanceof FunctionRef fr) keyFn = fr.name;
        Map<String,List<Object>> index = new HashMap<>();
        for (Object item : seq) {
            String key = toString(seq(item));
            if (!keyFn.isEmpty()) key = toString(callUserFunction(ctx.functions.get(keyFn), List.of(seq(item)), ctx));
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        return seq(index);
    }
    @SuppressWarnings("unchecked")
    private static List<Object> fnLookup(List<List<Object>> args, Context _ctx) {
        if (args.size() < 2 || args.get(0).isEmpty()) return new ArrayList<>();
        Object m = args.get(0).get(0); if (!(m instanceof Map<?,?>)) return new ArrayList<>();
        Map<String,List<Object>> mapping = (Map<String, List<Object>>) m;
        return mapping.getOrDefault(toString(args.get(1)), new ArrayList<>());
    }
    private static List<Object> fnGroupBy(List<List<Object>> args, Context ctx) {
        if (args.size() < 2) return new ArrayList<>(); List<Object> seq = args.get(0); String keyFn = "";
        if (!args.get(1).isEmpty() && args.get(1).get(0) instanceof FunctionRef fr) keyFn = fr.name;
        Map<String,List<Object>> groups = new HashMap<>();
        for (Object item : seq) {
            String key = toString(seq(item));
            if (!keyFn.isEmpty()) key = toString(callUserFunction(ctx.functions.get(keyFn), List.of(seq(item)), ctx));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String,List<Object>> e : groups.entrySet()) {
            Map<String,List<Object>> g = new HashMap<>(); g.put("key", seq(e.getKey())); g.put("items", e.getValue()); out.add(g);
        }
        return out;
    }
    private static List<Object> fnSeq(List<List<Object>> args, Context _ctx) { List<Object> out = new ArrayList<>(); for (List<Object> s : args) out.addAll(s); return out; }
    private static List<Object> fnPosition(List<List<Object>> _args, Context ctx) { if (ctx.position == null) return new ArrayList<>(); return seq(ctx.position.doubleValue()); }
    private static List<Object> fnApply(List<List<Object>> args, Context ctx) {
        if (args.isEmpty()) return new ArrayList<>(); List<Object> seq = args.get(0); String ruleset = (args.size()>1 && !args.get(1).isEmpty()) ? toString(args.get(1)) : "main";
        List<RuleDef> rules = ctx.rules.getOrDefault(ruleset, new ArrayList<>()); List<Object> out = new ArrayList<>();
        for (Object item : seq) {
            boolean matched = false;
            for (RuleDef rule : rules) {
                MatchResult mr = matchPattern(rule.pattern, item);
                if (mr.matched) {
                    matched = true; Context n = cloneCtx(ctx); n.contextItem = item; n.variables = copyVars(ctx.variables); n.variables.putAll(mr.bindings); out.addAll(evalExpr(rule.body, n)); break;
                }
            }
            if (!matched) throw new XFormException("XFDY0001: no matching rule");
        }
        return out;
    }
    private static List<Object> fnSum(List<List<Object>> args, Context _ctx) {
        if (args.isEmpty()) return seq(0.0); double total = 0.0; for (Object item : args.get(0)) total += toNumber(seq(item)); return seq(total);
    }

    private static List<Object> firstOrEmpty(List<List<Object>> args) { return args.isEmpty() ? new ArrayList<>() : args.get(0); }
    private static Map<String, List<Object>> copyVars(Map<String, List<Object>> src) { return new HashMap<>(src); }
    private static Context cloneCtx(Context c) { Context n = new Context(); n.contextItem = c.contextItem; n.variables = c.variables; n.functions = c.functions; n.rules = c.rules; n.position = c.position; n.last = c.last; return n; }
    private static List<Object> seq(Object... items) { return new ArrayList<>(Arrays.asList(items)); }
    private static List<Object> seq(Object item) { List<Object> l = new ArrayList<>(); l.add(item); return l; }

    public static String serializeItem(Object item) { return (item instanceof Node n) ? serialize(n) : toString(seq(item)); }
}
