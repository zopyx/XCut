"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.Parser = void 0;
const ast = __importStar(require("./ast"));
const lexer_1 = require("./lexer");
class Parser {
    constructor(text) {
        this.text = text;
        this.lexer = new lexer_1.Lexer(text);
    }
    parseModule() {
        const functions = {};
        const rules = {};
        const varsDecl = {};
        const namespaces = {};
        const imports = [];
        let tok = this.lexer.peek();
        if (tok.kind === "KW" && tok.value === "xform") {
            this.lexer.next();
            this.lexer.expect("KW", "version");
            const version = this.lexer.expect("STRING").value;
            if (version !== "2.0") {
                throw new Error("XFST0005: unsupported version");
            }
            this.lexer.expect("PUNCT", ";");
        }
        while (true) {
            tok = this.lexer.peek();
            if (tok.kind === "KW" && tok.value === "ns") {
                this.parseNs(namespaces);
                continue;
            }
            if (tok.kind === "KW" && tok.value === "import") {
                this.parseImport(imports);
                continue;
            }
            if (tok.kind === "KW" && tok.value === "var") {
                const [name, value] = this.parseVar();
                varsDecl[name] = value;
                continue;
            }
            if (tok.kind === "KW" && tok.value === "def") {
                this.parseDef(functions);
                continue;
            }
            if (tok.kind === "KW" && tok.value === "rule") {
                this.parseRule(rules);
                continue;
            }
            break;
        }
        let expr = null;
        if (this.lexer.peek().kind !== "EOF") {
            expr = this.parseExpr();
            if (this.lexer.peek().kind !== "EOF") {
                throw new Error(`Unexpected token at ${this.lexer.peek().pos}`);
            }
        }
        return new ast.Module({
            functions,
            rules,
            vars: varsDecl,
            namespaces,
            imports,
            expr,
        });
    }
    parseNs(namespaces) {
        this.lexer.expect("KW", "ns");
        const prefix = this.lexer.expect("STRING").value;
        this.lexer.expect("OP", "=");
        const uri = this.lexer.expect("STRING").value;
        this.lexer.expect("PUNCT", ";");
        namespaces[prefix] = uri;
    }
    parseImport(imports) {
        this.lexer.expect("KW", "import");
        const iri = this.lexer.expect("STRING").value;
        let alias = null;
        if (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "as") {
            this.lexer.next();
            alias = this.lexer.expect("IDENT").value;
        }
        this.lexer.expect("PUNCT", ";");
        imports.push([iri, alias]);
    }
    parseVar() {
        this.lexer.expect("KW", "var");
        const name = this.lexer.expect("IDENT").value;
        this.lexer.expect("OP", ":=");
        const value = this.parseExpr();
        this.lexer.expect("PUNCT", ";");
        return [name, value];
    }
    parseDef(functions) {
        this.lexer.expect("KW", "def");
        const name = this.parseQName();
        this.lexer.expect("PUNCT", "(");
        const params = [];
        if (!(this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ")")) {
            params.push(this.parseParam());
            while (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ",") {
                this.lexer.next();
                params.push(this.parseParam());
            }
        }
        this.lexer.expect("PUNCT", ")");
        this.lexer.expect("OP", ":=");
        const body = this.parseExpr();
        this.lexer.expect("PUNCT", ";");
        functions[name] = new ast.FunctionDef(params, body);
    }
    parseParam() {
        const name = this.lexer.expect("IDENT").value;
        let typeRef = null;
        let defaultExpr = null;
        if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ":") {
            this.lexer.next();
            typeRef = this.parseTypeRef();
        }
        if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === ":=") {
            this.lexer.next();
            defaultExpr = this.parseExpr();
        }
        return new ast.Param(name, typeRef, defaultExpr);
    }
    parseTypeRef() {
        const tok = this.lexer.peek();
        if (tok.kind === "IDENT" && ["string", "number", "boolean", "null", "map"].includes(tok.value)) {
            return this.lexer.next().value;
        }
        return this.parseQName();
    }
    parseRule(rules) {
        this.lexer.expect("KW", "rule");
        const name = this.parseQName();
        this.lexer.expect("KW", "match");
        const pattern = this.parsePattern();
        this.lexer.expect("OP", ":=");
        const body = this.parseExpr();
        this.lexer.expect("PUNCT", ";");
        if (!rules[name]) {
            rules[name] = [];
        }
        rules[name].push(new ast.RuleDef(pattern, body));
    }
    parseExpr() {
        const tok = this.lexer.peek();
        if (tok.kind === "KW" && tok.value === "if")
            return this.parseIf();
        if (tok.kind === "KW" && tok.value === "let")
            return this.parseLet();
        if (tok.kind === "KW" && tok.value === "for")
            return this.parseFor();
        if (tok.kind === "KW" && tok.value === "match")
            return this.parseMatch();
        return this.parseOr();
    }
    parseIf() {
        this.lexer.expect("KW", "if");
        const cond = this.parseExpr();
        this.lexer.expect("KW", "then");
        const thenExpr = this.parseExpr();
        this.lexer.expect("KW", "else");
        const elseExpr = this.parseExpr();
        return new ast.IfExpr(cond, thenExpr, elseExpr);
    }
    parseLet() {
        this.lexer.expect("KW", "let");
        const name = this.lexer.expect("IDENT").value;
        this.lexer.expect("OP", ":=");
        const value = this.parseExpr();
        this.lexer.expect("KW", "in");
        const body = this.parseExpr();
        return new ast.LetExpr(name, value, body);
    }
    parseFor() {
        this.lexer.expect("KW", "for");
        const name = this.lexer.expect("IDENT").value;
        this.lexer.expect("KW", "in");
        const seq = this.parseExpr();
        let where = null;
        if (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "where") {
            this.lexer.next();
            where = this.parseExpr();
        }
        this.lexer.expect("KW", "return");
        const body = this.parseExpr();
        return new ast.ForExpr(name, seq, where, body);
    }
    parseMatch() {
        this.lexer.expect("KW", "match");
        const target = this.parseExpr();
        this.lexer.expect("PUNCT", ":");
        const cases = [];
        let defaultExpr = null;
        while (true) {
            const tok = this.lexer.peek();
            if (tok.kind === "KW" && tok.value === "case") {
                this.lexer.next();
                const pattern = this.parsePattern();
                this.lexer.expect("OP", "=");
                this.lexer.expect("OP", ">");
                const expr = this.parseExpr();
                this.lexer.expect("PUNCT", ";");
                cases.push([pattern, expr]);
                continue;
            }
            if (tok.kind === "KW" && tok.value === "default") {
                this.lexer.next();
                this.lexer.expect("OP", "=");
                this.lexer.expect("OP", ">");
                defaultExpr = this.parseExpr();
                this.lexer.expect("PUNCT", ";");
                break;
            }
            break;
        }
        return new ast.MatchExpr(target, cases, defaultExpr);
    }
    parseOr() {
        let expr = this.parseAnd();
        while (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "or") {
            this.lexer.next();
            const right = this.parseAnd();
            expr = new ast.BinaryOp("or", expr, right);
        }
        return expr;
    }
    parseAnd() {
        let expr = this.parseEq();
        while (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "and") {
            this.lexer.next();
            const right = this.parseEq();
            expr = new ast.BinaryOp("and", expr, right);
        }
        return expr;
    }
    parseEq() {
        let expr = this.parseRel();
        while (this.lexer.peek().kind === "OP" && ["=", "!="].includes(this.lexer.peek().value)) {
            const op = this.lexer.next().value;
            const right = this.parseRel();
            expr = new ast.BinaryOp(op, expr, right);
        }
        return expr;
    }
    parseRel() {
        let expr = this.parseAdd();
        while (this.lexer.peek().kind === "OP" && ["<", "<=", ">", ">="].includes(this.lexer.peek().value)) {
            const op = this.lexer.next().value;
            const right = this.parseAdd();
            expr = new ast.BinaryOp(op, expr, right);
        }
        return expr;
    }
    parseAdd() {
        let expr = this.parseMul();
        while (this.lexer.peek().kind === "OP" && ["+", "-"].includes(this.lexer.peek().value)) {
            const op = this.lexer.next().value;
            const right = this.parseMul();
            expr = new ast.BinaryOp(op, expr, right);
        }
        return expr;
    }
    parseMul() {
        let expr = this.parseUnary();
        while (true) {
            const tok = this.lexer.peek();
            if (tok.kind === "OP" && tok.value === "*") {
                this.lexer.next();
                const right = this.parseUnary();
                expr = new ast.BinaryOp("*", expr, right);
                continue;
            }
            if (tok.kind === "KW" && ["div", "mod"].includes(tok.value)) {
                const op = this.lexer.next().value;
                const right = this.parseUnary();
                expr = new ast.BinaryOp(op, expr, right);
                continue;
            }
            break;
        }
        return expr;
    }
    parseUnary() {
        const tok = this.lexer.peek();
        if (tok.kind === "OP" && tok.value === "-") {
            this.lexer.next();
            return new ast.UnaryOp("-", this.parseUnary());
        }
        if (tok.kind === "KW" && tok.value === "not") {
            this.lexer.next();
            return new ast.UnaryOp("not", this.parseUnary());
        }
        return this.parsePrimary();
    }
    parsePrimary() {
        const tok = this.lexer.peek();
        if (tok.kind === "NUMBER") {
            this.lexer.next();
            return new ast.Literal(parseFloat(tok.value));
        }
        if (tok.kind === "STRING") {
            this.lexer.next();
            return new ast.Literal(tok.value);
        }
        if (tok.kind === "PUNCT" && tok.value === "(") {
            this.lexer.next();
            const expr = this.parseExpr();
            this.lexer.expect("PUNCT", ")");
            return expr;
        }
        if (tok.kind === "IDENT" && tok.value === "text") {
            const savedPos = this.lexer.pos;
            const savedTok = this.lexer.buffer;
            this.lexer.next();
            if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
                this.lexer.next();
                const expr = this.parseExpr();
                this.lexer.expect("PUNCT", "}");
                return new ast.TextConstructor(expr);
            }
            this.lexer.pos = savedPos;
            this.lexer.buffer = savedTok;
        }
        if (tok.kind === "OP" && tok.value === "<") {
            return this.parseConstructor();
        }
        if (tok.kind === "DOT" || tok.kind === "SLASH") {
            return this.parsePath();
        }
        if (tok.kind === "IDENT") {
            const name = this.lexer.next().value;
            if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "(") {
                return this.parseFuncCall(name);
            }
            if (this.pathContinues()) {
                return this.parsePath(new ast.PathStart("var", name));
            }
            return new ast.VarRef(name);
        }
        throw new Error(`Unexpected token at ${tok.pos}`);
    }
    parseFuncCall(name) {
        this.lexer.expect("PUNCT", "(");
        const args = [];
        if (!(this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ")")) {
            args.push(this.parseExpr());
            while (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ",") {
                this.lexer.next();
                args.push(this.parseExpr());
            }
        }
        this.lexer.expect("PUNCT", ")");
        return new ast.FuncCall(name, args);
    }
    pathContinues() {
        const tok = this.lexer.peek();
        return tok.kind === "SLASH" || tok.kind === "DOT" || tok.kind === "AT";
    }
    parsePath(start) {
        let actualStart = start;
        if (!actualStart) {
            const tok = this.lexer.next();
            if (tok.kind === "DOT") {
                actualStart = tok.value === ".//" ? new ast.PathStart("desc") : new ast.PathStart("context");
            }
            else if (tok.kind === "SLASH") {
                actualStart = tok.value === "//" ? new ast.PathStart("desc_root") : new ast.PathStart("root");
            }
            else {
                throw new Error(`Invalid path start at ${tok.pos}`);
            }
        }
        const steps = [];
        if (["root", "context", "var"].includes(actualStart.kind)) {
            const tok = this.lexer.peek();
            if (tok.kind === "AT") {
                this.lexer.next();
                const test = new ast.StepTest("name", this.parseQName());
                steps.push(new ast.PathStep("attr", test, []));
            }
            else if (tok.kind === "OP" && tok.value === "*") {
                const test = this.parseStepTest();
                const preds = this.parsePredicates();
                steps.push(new ast.PathStep("child", test, preds));
            }
            else if (tok.kind === "IDENT") {
                const test = this.parseStepTest();
                const preds = this.parsePredicates();
                steps.push(new ast.PathStep("child", test, preds));
            }
        }
        if (["desc", "desc_root"].includes(actualStart.kind)) {
            const tok = this.lexer.peek();
            if (tok.kind === "IDENT" || tok.kind === "OP") {
                const test = this.parseStepTest();
                const preds = this.parsePredicates();
                steps.push(new ast.PathStep("desc_or_self", test, preds));
            }
        }
        while (true) {
            const tok = this.lexer.peek();
            if (tok.kind === "SLASH") {
                let axis = tok.value === "/" ? "child" : "desc";
                this.lexer.next();
                let test;
                let preds = [];
                if (this.lexer.peek().kind === "AT") {
                    this.lexer.next();
                    test = new ast.StepTest("name", this.parseQName());
                    axis = "attr";
                }
                else {
                    test = this.parseStepTest();
                    preds = this.parsePredicates();
                }
                steps.push(new ast.PathStep(axis, test, preds));
                continue;
            }
            if (tok.kind === "DOT") {
                if (tok.value === ".") {
                    this.lexer.next();
                    if (this.lexer.peek().kind === "AT") {
                        this.lexer.next();
                        const test = new ast.StepTest("name", this.parseQName());
                        steps.push(new ast.PathStep("attr", test, []));
                    }
                    else {
                        steps.push(new ast.PathStep("self", new ast.StepTest("node"), []));
                    }
                    continue;
                }
                if (tok.value === "..") {
                    this.lexer.next();
                    steps.push(new ast.PathStep("parent", new ast.StepTest("node"), []));
                    continue;
                }
            }
            if (tok.kind === "AT") {
                this.lexer.next();
                const test = new ast.StepTest("name", this.parseQName());
                steps.push(new ast.PathStep("attr", test, []));
                continue;
            }
            break;
        }
        return new ast.PathExpr(actualStart, steps);
    }
    parseStepTest() {
        const tok = this.lexer.peek();
        if (tok.kind === "OP" && tok.value === "*") {
            this.lexer.next();
            return new ast.StepTest("wildcard");
        }
        if (tok.kind === "IDENT") {
            if (["text", "node", "comment", "pi"].includes(tok.value)) {
                this.lexer.next();
                this.lexer.expect("PUNCT", "(");
                this.lexer.expect("PUNCT", ")");
                return new ast.StepTest(tok.value);
            }
            const name = this.parseQName();
            return new ast.StepTest("name", name);
        }
        throw new Error(`Invalid step test at ${tok.pos}`);
    }
    parsePredicates() {
        const preds = [];
        while (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "[") {
            this.lexer.next();
            preds.push(this.parseExpr());
            this.lexer.expect("PUNCT", "]");
        }
        return preds;
    }
    parseQName() {
        return this.lexer.expect("IDENT").value;
    }
    parsePattern() {
        const tok = this.lexer.peek();
        if (tok.kind === "AT") {
            this.lexer.next();
            const name = this.parseQName();
            return new ast.AttributePattern(name);
        }
        if (tok.kind === "IDENT" && ["node", "text", "comment"].includes(tok.value)) {
            this.lexer.next();
            this.lexer.expect("PUNCT", "(");
            this.lexer.expect("PUNCT", ")");
            return new ast.TypedPattern(tok.value);
        }
        if (tok.kind === "IDENT" && tok.value === "_") {
            this.lexer.next();
            return new ast.WildcardPattern();
        }
        if (tok.kind === "OP" && tok.value === "<") {
            this.lexer.next();
            const name = this.parseQName();
            this.lexer.expect("OP", ">");
            let varName = null;
            let child = null;
            if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
                this.lexer.next();
                varName = this.lexer.expect("IDENT").value;
                this.lexer.expect("PUNCT", "}");
            }
            else if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === "<") {
                child = this.parsePattern();
            }
            else {
                throw new Error("Invalid element pattern content");
            }
            this.lexer.expect("OP", "<");
            this.lexer.expect("SLASH", "/");
            const end = this.parseQName();
            if (end !== name) {
                throw new Error("Mismatched pattern end tag");
            }
            this.lexer.expect("OP", ">");
            return new ast.ElementPattern(name, varName, child);
        }
        throw new Error(`Invalid pattern at ${tok.pos}`);
    }
    parseConstructor() {
        this.lexer.expect("OP", "<");
        const name = this.parseQName();
        const attrs = [];
        while (true) {
            const tok = this.lexer.peek();
            if (tok.kind === "OP" && tok.value === ">") {
                this.lexer.next();
                break;
            }
            if (tok.kind === "SLASH" && tok.value === "/") {
                this.lexer.next();
                this.lexer.expect("OP", ">");
                return new ast.Constructor(name, attrs, []);
            }
            const attrName = this.parseQName();
            this.lexer.expect("OP", "=");
            this.lexer.expect("PUNCT", "{");
            const expr = this.parseExpr();
            this.lexer.expect("PUNCT", "}");
            attrs.push([attrName, expr]);
        }
        const contents = [];
        this.lexer.clearBuffer();
        while (true) {
            if (this.lexer.pos >= this.text.length) {
                throw new Error("Unterminated constructor");
            }
            if (this.text.startsWith("</", this.lexer.pos)) {
                const [endName, newPos] = this.readEndTag();
                if (endName !== name) {
                    throw new Error("Mismatched end tag");
                }
                this.lexer.pos = newPos;
                this.lexer.clearBuffer();
                break;
            }
            if (this.text.startsWith("text{", this.lexer.pos)) {
                this.lexer.pos += 4;
                this.lexer.clearBuffer();
                this.lexer.expect("PUNCT", "{");
                const expr = this.parseExpr();
                this.lexer.expect("PUNCT", "}");
                contents.push(new ast.TextConstructor(expr));
                continue;
            }
            const ch = this.text[this.lexer.pos];
            if (ch === "<") {
                this.lexer.clearBuffer();
                contents.push(this.parseConstructor());
                continue;
            }
            if (ch === "{") {
                this.lexer.pos += 1;
                this.lexer.clearBuffer();
                const expr = this.parseExpr();
                this.lexer.expect("PUNCT", "}");
                contents.push(new ast.Interp(expr));
                continue;
            }
            const text = this.parseCharData();
            if (text && text.trim()) {
                contents.push(new ast.Text(text));
            }
        }
        return new ast.Constructor(name, attrs, contents);
    }
    parseCharData() {
        const out = [];
        while (true) {
            if (this.lexer.pos >= this.text.length)
                break;
            const ch = this.text[this.lexer.pos];
            if (ch === "<" || ch === "{")
                break;
            out.push(ch);
            this.lexer.pos += 1;
        }
        return out.join("");
    }
    readEndTag() {
        let pos = this.lexer.pos;
        if (!this.text.startsWith("</", pos)) {
            throw new Error("Expected end tag");
        }
        pos += 2;
        const start = pos;
        while (pos < this.text.length && /[A-Za-z0-9_:-]/.test(this.text[pos])) {
            pos += 1;
        }
        const name = this.text.slice(start, pos);
        while (pos < this.text.length && /\s/.test(this.text[pos])) {
            pos += 1;
        }
        if (pos >= this.text.length || this.text[pos] !== ">") {
            throw new Error("Unterminated end tag");
        }
        return [name, pos + 1];
    }
}
exports.Parser = Parser;
