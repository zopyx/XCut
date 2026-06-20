import * as ast from "./ast";
import { Lexer, Token } from "./lexer";

const SOFT_KEYWORDS = new Set([
  "true", "false", "null", "string", "number", "boolean", "map",
  "apply", "text", "comment", "pi",
]);

const RESERVED_FUNCTION_NAMES = new Set([
  "string", "number", "boolean", "typeOf", "name", "attr", "text",
  "children", "elements", "attributes", "copy", "count", "empty",
  "distinct", "sort", "concat", "seq", "head", "tail", "last",
  "index", "lookup", "groupBy", "sum", "position", "apply",
  "contains", "startsWith", "endsWith", "substring", "stringLength",
  "upperCase", "lowerCase", "normalizeSpace", "replace", "matches",
  "keys", "mapSize",
]);

export class Parser {
  private text: string;
  private lexer: Lexer;

  constructor(text: string) {
    this.text = text;
    this.lexer = new Lexer(text);
  }

  parseModule(): ast.Module {
    const functions: Record<string, ast.FunctionDef> = {};
    const rules: Record<string, ast.RuleDef[]> = {};
    const varsDecl: Record<string, ast.Expr> = {};
    const namespaces: Record<string, string> = {};
    const imports: Array<[string, string | null]> = [];

    let tok = this.lexer.peek();
    if (tok.kind === "KW" && tok.value === "xform") {
      this.lexer.next();
      this.lexer.expect("KW", "version");
      const version = this.lexer.expect("STRING").value;
      if (version !== "2.0" && version !== "2.1") {
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

    let expr: ast.Expr | null = null;
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

  private parseNs(namespaces: Record<string, string>): void {
    this.lexer.expect("KW", "ns");
    const prefix = this.lexer.expect("STRING").value;
    this.lexer.expect("OP", "=");
    const uri = this.lexer.expect("STRING").value;
    this.lexer.expect("PUNCT", ";");
    namespaces[prefix] = uri;
  }

  private parseImport(imports: Array<[string, string | null]>): void {
    this.lexer.expect("KW", "import");
    const iri = this.lexer.expect("STRING").value;
    let alias: string | null = null;
    if (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "as") {
      this.lexer.next();
      alias = this.expectIdentifier();
    }
    this.lexer.expect("PUNCT", ";");
    imports.push([iri, alias]);
  }

  private parseVar(): [string, ast.Expr] {
    this.lexer.expect("KW", "var");
    const name = this.expectIdentifier();
    this.lexer.expect("OP", ":=");
    const value = this.parseExpr();
    this.lexer.expect("PUNCT", ";");
    return [name, value];
  }

  private parseDef(functions: Record<string, ast.FunctionDef>): void {
    this.lexer.expect("KW", "def");
    const name = this.parseQName();
    if (RESERVED_FUNCTION_NAMES.has(name)) {
      throw new Error(`XFST0006: reserved function name '${name}'`);
    }
    this.lexer.expect("PUNCT", "(");
    const params: ast.Param[] = [];
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

  private parseParam(): ast.Param {
    const name = this.expectIdentifier();
    let typeRef: string | null = null;
    let defaultExpr: ast.Expr | null = null;
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

  private parseTypeRef(): string {
    const tok = this.lexer.peek();
    if (tok.kind === "IDENT" && ["string", "number", "boolean", "null", "map"].includes(tok.value)) {
      return this.lexer.next().value;
    }
    return this.parseQName();
  }

  private parseRule(rules: Record<string, ast.RuleDef[]>): void {
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

  parseExpr(): ast.Expr {
    const tok = this.lexer.peek();
    if (tok.kind === "KW" && tok.value === "if") return this.parseIf();
    if (tok.kind === "KW" && tok.value === "let") return this.parseLet();
    if (tok.kind === "KW" && tok.value === "for") return this.parseFor();
    if (tok.kind === "KW" && tok.value === "match") return this.parseMatch();
    return this.parseOr();
  }

  private parseIf(): ast.Expr {
    this.lexer.expect("KW", "if");
    const cond = this.parseExpr();
    this.lexer.expect("KW", "then");
    const thenExpr = this.parseExpr();
    this.lexer.expect("KW", "else");
    const elseExpr = this.parseExpr();
    return new ast.IfExpr(cond, thenExpr, elseExpr);
  }

  private parseLet(): ast.Expr {
    this.lexer.expect("KW", "let");
    const name = this.expectIdentifier();
    this.lexer.expect("OP", ":=");
    const value = this.parseExpr();
    this.lexer.expect("KW", "in");
    const body = this.parseExpr();
    return new ast.LetExpr(name, value, body);
  }

  private parseFor(): ast.Expr {
    this.lexer.expect("KW", "for");
    const name = this.expectIdentifier();
    this.lexer.expect("KW", "in");
    const seq = this.parseExpr();
    let where: ast.Expr | null = null;
    if (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "where") {
      this.lexer.next();
      where = this.parseExpr();
    }
    this.lexer.expect("KW", "return");
    const body = this.parseExpr();
    return new ast.ForExpr(name, seq, where, body);
  }

  private parseMatch(): ast.Expr {
    this.lexer.expect("KW", "match");
    const target = this.parseExpr();
    this.lexer.expect("PUNCT", ":");
    const cases: Array<[ast.Pattern, ast.Expr]> = [];
    let defaultExpr: ast.Expr | null = null;
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

  private parseOr(): ast.Expr {
    let expr = this.parseAnd();
    while (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "or") {
      this.lexer.next();
      const right = this.parseAnd();
      expr = new ast.BinaryOp("or", expr, right);
    }
    return expr;
  }

  private parseAnd(): ast.Expr {
    let expr = this.parseEq();
    while (this.lexer.peek().kind === "KW" && this.lexer.peek().value === "and") {
      this.lexer.next();
      const right = this.parseEq();
      expr = new ast.BinaryOp("and", expr, right);
    }
    return expr;
  }

  private parseEq(): ast.Expr {
    let expr = this.parseRel();
    while (this.lexer.peek().kind === "OP" && ["=", "!="].includes(this.lexer.peek().value)) {
      const op = this.lexer.next().value;
      const right = this.parseRel();
      expr = new ast.BinaryOp(op, expr, right);
    }
    return expr;
  }

  private parseRel(): ast.Expr {
    let expr = this.parseAdd();
    while (this.lexer.peek().kind === "OP" && ["<", "<=", ">", ">="].includes(this.lexer.peek().value)) {
      const op = this.lexer.next().value;
      const right = this.parseAdd();
      expr = new ast.BinaryOp(op, expr, right);
    }
    return expr;
  }

  private parseAdd(): ast.Expr {
    let expr = this.parseMul();
    while (this.lexer.peek().kind === "OP" && ["+", "-"].includes(this.lexer.peek().value)) {
      const op = this.lexer.next().value;
      const right = this.parseMul();
      expr = new ast.BinaryOp(op, expr, right);
    }
    return expr;
  }

  private parseMul(): ast.Expr {
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

  private parseUnary(): ast.Expr {
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

  private parsePrimary(): ast.Expr {
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
    if (tok.kind === "KW" && tok.value === "apply") {
      this.lexer.next();
      this.lexer.expect("PUNCT", "(");
      const expr = this.parseExpr();
      let ruleset: string | null = null;
      if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ",") {
        this.lexer.next();
        ruleset = this.expectIdentifier();
      }
      this.lexer.expect("PUNCT", ")");
      return new ast.ApplyExpr(expr, ruleset);
    }
    if (tok.kind === "KW" && tok.value === "text") {
      const savedPos = this.lexer.pos;
      const savedTok = (this.lexer as any).buffer as Token | null;
      this.lexer.next();
      if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
        this.lexer.next();
        const expr = this.parseExpr();
        this.lexer.expect("PUNCT", "}");
        return new ast.TextConstructor(expr);
      }
      this.lexer.pos = savedPos;
      (this.lexer as any).buffer = savedTok;
    }
    if (tok.kind === "KW" && tok.value === "comment") {
      const savedPos = this.lexer.pos;
      const savedTok = (this.lexer as any).buffer as Token | null;
      this.lexer.next();
      if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
        this.lexer.next();
        const expr = this.parseExpr();
        this.lexer.expect("PUNCT", "}");
        return new ast.CommentConstructor(expr);
      }
      this.lexer.pos = savedPos;
      (this.lexer as any).buffer = savedTok;
    }
    if (tok.kind === "KW" && tok.value === "pi") {
      const savedPos = this.lexer.pos;
      const savedTok = (this.lexer as any).buffer as Token | null;
      this.lexer.next();
      if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
        this.lexer.next();
        const target = this.parseExpr();
        this.lexer.expect("PUNCT", ",");
        const value = this.parseExpr();
        this.lexer.expect("PUNCT", "}");
        return new ast.PIConstructor(target, value);
      }
      this.lexer.pos = savedPos;
      (this.lexer as any).buffer = savedTok;
    }
    if (tok.kind === "OP" && tok.value === "<") {
      return this.parseConstructor();
    }
    if (tok.kind === "DOT" || tok.kind === "SLASH") {
      return this.parsePath();
    }
    if (tok.kind === "AT") {
      return this.parsePath();
    }
    if (tok.kind === "IDENT" || (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value))) {
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

  private parseFuncCall(name: string): ast.FuncCall {
    this.lexer.expect("PUNCT", "(");
    const args: ast.Expr[] = [];
    const namedArgs: Array<[string, ast.Expr]> = [];
    let seenNamed = false;
    if (!(this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ")")) {
      while (true) {
        const argExpr = this.parseExpr();
        if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === ":=") {
          this.lexer.next();
          // Named argument: argExpr should be VarRef
          if (!(argExpr instanceof ast.VarRef)) {
            throw new Error("XFST0001: expected identifier before :=");
          }
          const namedValue = this.parseExpr();
          namedArgs.push([argExpr.name, namedValue]);
          seenNamed = true;
        } else {
          if (seenNamed) {
            throw new Error("XFST0001: positional argument after named argument");
          }
          args.push(argExpr);
        }
        if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === ",") {
          this.lexer.next();
          continue;
        }
        break;
      }
    }
    this.lexer.expect("PUNCT", ")");
    return new ast.FuncCall(name, args, namedArgs);
  }

  private pathContinues(): boolean {
    const tok = this.lexer.peek();
    return tok.kind === "SLASH" || tok.kind === "DOT" || tok.kind === "AT";
  }

  private parsePath(start?: ast.PathStart): ast.PathExpr {
    let actualStart = start;
    if (!actualStart) {
      const tok = this.lexer.next();
      if (tok.kind === "DOT") {
        actualStart = tok.value === ".//" ? new ast.PathStart("desc") : new ast.PathStart("context");
      } else if (tok.kind === "SLASH") {
        actualStart = tok.value === "//" ? new ast.PathStart("desc_root") : new ast.PathStart("root");
      } else if (tok.kind === "AT") {
        actualStart = new ast.PathStart("attr");
      } else {
        throw new Error(`Invalid path start at ${tok.pos}`);
      }
    }

    const steps: ast.PathStep[] = [];
    if (["root", "context", "var"].includes(actualStart.kind)) {
      const tok = this.lexer.peek();
      if (tok.kind === "AT") {
        this.lexer.next();
        const test = new ast.StepTest("name", this.parseQName());
        steps.push(new ast.PathStep("attr", test, []));
      } else if (tok.kind === "OP" && tok.value === "*") {
        const test = this.parseStepTest();
        const preds = this.parsePredicates();
        steps.push(new ast.PathStep("child", test, preds));
      } else if (tok.kind === "IDENT" || (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value))) {
        const test = this.parseStepTest();
        const preds = this.parsePredicates();
        steps.push(new ast.PathStep("child", test, preds));
      }
    }
    if (["desc", "desc_root"].includes(actualStart.kind)) {
      const tok = this.lexer.peek();
      if (tok.kind === "IDENT" || tok.kind === "OP" || (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value))) {
        const test = this.parseStepTest();
        const preds = this.parsePredicates();
        steps.push(new ast.PathStep("desc_or_self", test, preds));
      }
    }
    if (actualStart.kind === "attr") {
      const tok = this.lexer.peek();
      if (tok.kind === "IDENT" || (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value))) {
        const test = new ast.StepTest("name", this.parseQName());
        steps.push(new ast.PathStep("attr", test, []));
      }
    }

    while (true) {
      const tok = this.lexer.peek();
      if (tok.kind === "SLASH") {
        let axis = tok.value === "/" ? "child" : "desc";
        this.lexer.next();
        let test: ast.StepTest;
        let preds: ast.Expr[] = [];
        if (this.lexer.peek().kind === "AT") {
          this.lexer.next();
          test = new ast.StepTest("name", this.parseQName());
          axis = "attr";
        } else {
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
          } else {
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

  private parseStepTest(): ast.StepTest {
    const tok = this.lexer.peek();
    if (tok.kind === "OP" && tok.value === "*") {
      this.lexer.next();
      return new ast.StepTest("wildcard");
    }
    if (tok.kind === "IDENT" || (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value))) {
      if (["text", "node", "element", "comment", "pi", "document"].includes(tok.value)) {
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

  private parsePredicates(): ast.Expr[] {
    const preds: ast.Expr[] = [];
    while (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "[") {
      this.lexer.next();
      preds.push(this.parseExpr());
      this.lexer.expect("PUNCT", "]");
    }
    return preds;
  }

  private parseQName(): string {
    return this.expectIdentifier();
  }

  private parseLiteral(): ast.Literal {
    const tok = this.lexer.peek();
    if (tok.kind === "STRING") {
      this.lexer.next();
      return new ast.Literal(tok.value);
    }
    if (tok.kind === "NUMBER") {
      this.lexer.next();
      return new ast.Literal(parseFloat(tok.value));
    }
    throw new Error(`Expected literal at ${tok.pos}`);
  }

  private parsePattern(): ast.Pattern {
    const tok = this.lexer.peek();
    if (tok.kind === "AT") {
      this.lexer.next();
      const name = this.expectIdentifier();
      if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === "=") {
        this.lexer.next();
        const val = this.parseLiteral();
        return new ast.AttributePattern(name, val);
      }
      return new ast.AttributePattern(name);
    }
    if (tok.kind === "STRING") {
      return new ast.LiteralPattern(this.lexer.next().value);
    }
    if (tok.kind === "IDENT" && ["node", "element", "text", "comment", "pi", "document"].includes(tok.value)) {
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
      const name = this.expectIdentifier();
      const attrs: Array<[string, ast.Literal | null]> = [];
      while (this.lexer.peek().kind === "AT") {
        this.lexer.next();
        const attrName = this.expectIdentifier();
        let attrVal: ast.Literal | null = null;
        if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === "=") {
          this.lexer.next();
          attrVal = this.parseLiteral();
        }
        attrs.push([attrName, attrVal]);
      }
      this.lexer.expect("OP", ">");
      let varName: string | null = null;
      let children: ast.Pattern[] = [];
      if (this.lexer.peek().kind === "PUNCT" && this.lexer.peek().value === "{") {
        this.lexer.next();
        varName = this.expectIdentifier();
        this.lexer.expect("PUNCT", "}");
      } else if (this.lexer.peek().kind === "OP" && this.lexer.peek().value === "<") {
        while (this.lexer.peek().kind === "OP" && this.lexer.peek().value === "<") {
          children.push(this.parsePattern());
        }
      } else {
        throw new Error("Invalid element pattern content");
      }
      this.lexer.expect("OP", "<");
      this.lexer.expect("SLASH", "/");
      const end = this.parseQName();
      if (end !== name) {
        throw new Error("Mismatched pattern end tag");
      }
      this.lexer.expect("OP", ">");
      return new ast.ElementPattern(name, varName, null, attrs, children);
    }
    throw new Error(`Invalid pattern at ${tok.pos}`);
  }

  private parseConstructor(): ast.Constructor {
    this.lexer.expect("OP", "<");
    const name = this.parseQName();
    const attrs: Array<[string, ast.Expr]> = [];
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

    const contents: ast.Expr[] = [];
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
      if (this.text.startsWith("comment{", this.lexer.pos)) {
        this.lexer.pos += 7;
        this.lexer.clearBuffer();
        this.lexer.expect("PUNCT", "{");
        const expr = this.parseExpr();
        this.lexer.expect("PUNCT", "}");
        contents.push(new ast.CommentConstructor(expr));
        continue;
      }
      if (this.text.startsWith("pi{", this.lexer.pos)) {
        this.lexer.pos += 2;
        this.lexer.clearBuffer();
        this.lexer.expect("PUNCT", "{");
        const target = this.parseExpr();
        this.lexer.expect("PUNCT", ",");
        const value = this.parseExpr();
        this.lexer.expect("PUNCT", "}");
        contents.push(new ast.PIConstructor(target, value));
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

  private parseCharData(): string {
    const out: string[] = [];
    while (true) {
      if (this.lexer.pos >= this.text.length) break;
      const ch = this.text[this.lexer.pos];
      if (ch === "<" || ch === "{") break;
      out.push(ch);
      this.lexer.pos += 1;
    }
    return out.join("");
  }

  private readEndTag(): [string, number] {
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

  private expectIdentifier(): string {
    const tok = this.lexer.peek();
    if (tok.kind === "IDENT") {
      return this.lexer.next().value;
    }
    if (tok.kind === "KW" && SOFT_KEYWORDS.has(tok.value)) {
      return this.lexer.next().value;
    }
    throw new Error(`XFST0006: expected identifier, got ${tok.kind} ${tok.value} at ${tok.pos}`);
  }
}
