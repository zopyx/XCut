package com.zopyx.xform

class Parser(private val text: String) {
    private val lexer = Lexer(text)

    private val softKeywords = setOf(
        "true", "false", "null",
        "string", "number", "boolean", "map",
        "apply", "text", "comment", "pi", "document"
    )

    private val reservedFunctionNames = setOf(
        "string", "number", "boolean", "typeOf",
        "name", "attr", "text", "children", "elements", "attributes", "copy",
        "count", "empty", "distinct", "sort", "concat", "seq",
        "head", "tail", "last", "index", "lookup", "groupBy",
        "sum", "position", "apply",
        "contains", "startsWith", "endsWith", "substring",
        "stringLength", "upperCase", "lowerCase", "normalizeSpace", "replace", "matches",
        "keys", "mapSize"
    )

    fun parseModule(): Module {
        val module = Module()

        // Optional version declaration
        var tok = lexer.peek()
        if (tok.kind == TokenKind.KW && tok.value == "xform") {
            lexer.next()
            lexer.expect(TokenKind.KW, "version")
            val version = lexer.expect(TokenKind.STRING).value
            if (version != "2.0" && version != "2.1") {
                throw XFormException("XFST0005: unsupported version")
            }
            lexer.expect(TokenKind.PUNCT, ";")
        }

        // Parse declarations
        while (true) {
            tok = lexer.peek()
            when {
                tok.kind == TokenKind.KW && tok.value == "ns" -> parseNs(module.namespaces)
                tok.kind == TokenKind.KW && tok.value == "import" -> parseImport(module.imports)
                tok.kind == TokenKind.KW && tok.value == "var" -> {
                    val (name, expr) = parseVar()
                    module.vars[name] = expr
                }
                tok.kind == TokenKind.KW && tok.value == "def" -> parseDef(module.functions)
                tok.kind == TokenKind.KW && tok.value == "rule" -> parseRule(module.rules)
                else -> break
            }
        }

        // Parse main expression
        if (lexer.peek().kind != TokenKind.EOF) {
            module.expr = parseExpr()
            if (lexer.peek().kind != TokenKind.EOF) {
                throw XFormException("unexpected token at ${lexer.peek().pos}")
            }
        }

        return module
    }

    private fun parseNs(namespaces: MutableMap<String, String>) {
        lexer.expect(TokenKind.KW, "ns")
        val prefix = lexer.expect(TokenKind.STRING).value
        lexer.expect(TokenKind.OP, "=")
        val uri = lexer.expect(TokenKind.STRING).value
        lexer.expect(TokenKind.PUNCT, ";")
        namespaces[prefix] = uri
    }

    private fun parseImport(imports: MutableList<ImportDecl>) {
        lexer.expect(TokenKind.KW, "import")
        val iri = lexer.expect(TokenKind.STRING).value
        val alias = if (lexer.peek().kind == TokenKind.KW && lexer.peek().value == "as") {
            lexer.next()
            expectIdentifier()
        } else null
        lexer.expect(TokenKind.PUNCT, ";")
        imports.add(ImportDecl(iri, alias))
    }

    private fun parseVar(): Pair<String, Expr> {
        lexer.expect(TokenKind.KW, "var")
        val name = expectIdentifier()
        lexer.expect(TokenKind.OP, ":=")
        val value = parseExpr()
        lexer.expect(TokenKind.PUNCT, ";")
        return name to value
    }

    private fun parseDef(functions: MutableMap<String, FunctionDef>) {
        lexer.expect(TokenKind.KW, "def")
        val name = parseQName()
        if (name in reservedFunctionNames) {
            throw XFormException("XFST0006: reserved function name '$name'")
        }
        lexer.expect(TokenKind.PUNCT, "(")
        val params = mutableListOf<Param>()
        if (!(lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ")")) {
            params.add(parseParam())
            while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ",") {
                lexer.next()
                params.add(parseParam())
            }
        }
        lexer.expect(TokenKind.PUNCT, ")")
        lexer.expect(TokenKind.OP, ":=")
        val body = parseExpr()
        lexer.expect(TokenKind.PUNCT, ";")
        functions[name] = FunctionDef(params, body)
    }

    private fun parseParam(): Param {
        val name = expectIdentifier()
        val typeRef = if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ":") {
            lexer.next()
            parseTypeRef()
        } else null
        val default = if (lexer.peek().kind == TokenKind.OP && lexer.peek().value == ":=") {
            lexer.next()
            parseExpr()
        } else null
        return Param(name, typeRef, default)
    }

    private fun parseTypeRef(): String {
        val tok = lexer.peek()
        if (tok.kind == TokenKind.IDENT && tok.value in setOf("string", "number", "boolean", "null", "map")) {
            return lexer.next().value
        }
        if (tok.kind == TokenKind.KW && isSoftKeyword(tok.value)) {
            return lexer.next().value
        }
        return parseQName()
    }

    private fun parseRule(rules: MutableMap<String, MutableList<RuleDef>>) {
        lexer.expect(TokenKind.KW, "rule")
        val name = parseQName()
        lexer.expect(TokenKind.KW, "match")
        val pattern = parsePattern()
        lexer.expect(TokenKind.OP, ":=")
        val body = parseExpr()
        lexer.expect(TokenKind.PUNCT, ";")
        rules.getOrPut(name) { mutableListOf() }.add(RuleDef(pattern, body))
    }

    fun parseExpr(): Expr {
        return when (lexer.peek().value) {
            "if" -> parseIf()
            "let" -> parseLet()
            "for" -> parseFor()
            "match" -> parseMatch()
            else -> parseOr()
        }
    }

    private fun parseIf(): Expr {
        lexer.expect(TokenKind.KW, "if")
        val cond = parseExpr()
        lexer.expect(TokenKind.KW, "then")
        val thenExpr = parseExpr()
        lexer.expect(TokenKind.KW, "else")
        val elseExpr = parseExpr()
        return IfExpr(cond, thenExpr, elseExpr)
    }

    private fun parseLet(): Expr {
        lexer.expect(TokenKind.KW, "let")
        val name = expectIdentifier()
        lexer.expect(TokenKind.OP, ":=")
        val value = parseExpr()
        lexer.expect(TokenKind.KW, "in")
        val body = parseExpr()
        return LetExpr(name, value, body)
    }

    private fun parseFor(): Expr {
        lexer.expect(TokenKind.KW, "for")
        val name = expectIdentifier()
        lexer.expect(TokenKind.KW, "in")
        val seq = parseExpr()
        val where = if (lexer.peek().kind == TokenKind.KW && lexer.peek().value == "where") {
            lexer.next()
            parseExpr()
        } else null
        lexer.expect(TokenKind.KW, "return")
        val body = parseExpr()
        return ForExpr(name, seq, where, body)
    }

    private fun parseMatch(): Expr {
        lexer.expect(TokenKind.KW, "match")
        val target = parseExpr()
        lexer.expect(TokenKind.PUNCT, ":")
        val cases = mutableListOf<MatchCase>()
        var default: Expr? = null
        while (true) {
            when {
                lexer.peek().kind == TokenKind.KW && lexer.peek().value == "case" -> {
                    lexer.next()
                    val pattern = parsePattern()
                    lexer.expect(TokenKind.OP, "=")
                    lexer.expect(TokenKind.OP, ">")
                    val expr = parseExpr()
                    lexer.expect(TokenKind.PUNCT, ";")
                    cases.add(MatchCase(pattern, expr))
                }
                lexer.peek().kind == TokenKind.KW && lexer.peek().value == "default" -> {
                    lexer.next()
                    lexer.expect(TokenKind.OP, "=")
                    lexer.expect(TokenKind.OP, ">")
                    default = parseExpr()
                    lexer.expect(TokenKind.PUNCT, ";")
                    break
                }
                else -> break
            }
        }
        return MatchExpr(target, cases, default)
    }

    private fun parseOr(): Expr {
        var expr = parseAnd()
        while (lexer.peek().kind == TokenKind.KW && lexer.peek().value == "or") {
            lexer.next()
            val right = parseAnd()
            expr = BinaryOp("or", expr, right)
        }
        return expr
    }

    private fun parseAnd(): Expr {
        var expr = parseEq()
        while (lexer.peek().kind == TokenKind.KW && lexer.peek().value == "and") {
            lexer.next()
            val right = parseEq()
            expr = BinaryOp("and", expr, right)
        }
        return expr
    }

    private fun parseEq(): Expr {
        var expr = parseRel()
        while (lexer.peek().kind == TokenKind.OP && lexer.peek().value in setOf("=", "!=")) {
            val op = lexer.next().value
            val right = parseRel()
            expr = BinaryOp(op, expr, right)
        }
        return expr
    }

    private fun parseRel(): Expr {
        var expr = parseAdd()
        while (lexer.peek().kind == TokenKind.OP && lexer.peek().value in setOf("<", "<=", ">", ">=")) {
            val op = lexer.next().value
            val right = parseAdd()
            expr = BinaryOp(op, expr, right)
        }
        return expr
    }

    private fun parseAdd(): Expr {
        var expr = parseMul()
        while (lexer.peek().kind == TokenKind.OP && lexer.peek().value in setOf("+", "-")) {
            val op = lexer.next().value
            val right = parseMul()
            expr = BinaryOp(op, expr, right)
        }
        return expr
    }

    private fun parseMul(): Expr {
        var expr = parseUnary()
        while (true) {
            when {
                lexer.peek().kind == TokenKind.OP && lexer.peek().value == "*" -> {
                    lexer.next()
                    val right = parseUnary()
                    expr = BinaryOp("*", expr, right)
                }
                lexer.peek().kind == TokenKind.KW && lexer.peek().value in setOf("div", "mod") -> {
                    val op = lexer.next().value
                    val right = parseUnary()
                    expr = BinaryOp(op, expr, right)
                }
                else -> break
            }
        }
        return expr
    }

    private fun parseUnary(): Expr {
        return when {
            lexer.peek().kind == TokenKind.OP && lexer.peek().value == "-" -> {
                lexer.next()
                UnaryOp("-", parseUnary())
            }
            lexer.peek().kind == TokenKind.KW && lexer.peek().value == "not" -> {
                lexer.next()
                UnaryOp("not", parseUnary())
            }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Expr {
        val tok = lexer.peek()
        return when {
            tok.kind == TokenKind.NUMBER -> {
                lexer.next()
                Literal(tok.value.toDouble())
            }
            tok.kind == TokenKind.STRING -> {
                lexer.next()
                Literal(tok.value)
            }
            tok.kind == TokenKind.PUNCT && tok.value == "(" -> {
                lexer.next()
                val expr = parseExpr()
                lexer.expect(TokenKind.PUNCT, ")")
                expr
            }
            (tok.kind == TokenKind.KW && tok.value == "apply") -> {
                lexer.next()
                lexer.expect(TokenKind.PUNCT, "(")
                val expr = parseExpr()
                val ruleset = if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ",") {
                    lexer.next()
                    expectIdentifier()
                } else null
                lexer.expect(TokenKind.PUNCT, ")")
                ApplyExpr(expr, ruleset)
            }
            (tok.kind == TokenKind.KW && tok.value == "comment") -> {
                lexer.next()
                lexer.expect(TokenKind.PUNCT, "{")
                val expr = parseExpr()
                lexer.expect(TokenKind.PUNCT, "}")
                CommentConstructor(expr)
            }
            (tok.kind == TokenKind.KW && tok.value == "pi") -> {
                lexer.next()
                lexer.expect(TokenKind.PUNCT, "{")
                val target = parseExpr()
                lexer.expect(TokenKind.PUNCT, ",")
                val value = parseExpr()
                lexer.expect(TokenKind.PUNCT, "}")
                PIConstructor(target, value)
            }
            (tok.kind == TokenKind.IDENT && tok.value == "text") || (tok.kind == TokenKind.KW && tok.value == "text") -> {
                val savedPos = lexer.pos
                val savedBuf = lexer.buffer
                lexer.next()
                if (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "{") {
                    lexer.next()
                    val expr = parseExpr()
                    lexer.expect(TokenKind.PUNCT, "}")
                    TextConstructor(expr)
                } else {
                    lexer.pos = savedPos
                    lexer.buffer = savedBuf
                    val name = lexer.next().value
                    when {
                        lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "(" -> parseFuncCall(name)
                        pathContinues() -> parsePath(PathStart("var", name))
                        else -> VarRef(name)
                    }
                }
            }
            tok.kind == TokenKind.OP && tok.value == "<" -> parseConstructor()
            tok.kind == TokenKind.DOT || tok.kind == TokenKind.SLASH -> parsePath(null)
            tok.kind == TokenKind.AT -> parsePath(null)
            tok.kind == TokenKind.IDENT -> {
                val name = lexer.next().value
                when {
                    lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "(" -> parseFuncCall(name)
                    pathContinues() -> parsePath(PathStart("var", name))
                    else -> VarRef(name)
                }
            }
            tok.kind == TokenKind.KW && isSoftKeyword(tok.value) -> {
                val name = lexer.next().value
                when {
                    lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "(" -> parseFuncCall(name)
                    pathContinues() -> parsePath(PathStart("var", name))
                    else -> VarRef(name)
                }
            }
            else -> throw XFormException("unexpected token at ${tok.pos}")
        }
    }

    private fun parseFuncCall(name: String): Expr {
        lexer.expect(TokenKind.PUNCT, "(")
        val args = mutableListOf<Expr>()
        val namedArgs = mutableListOf<NamedArg>()
        var seenNamed = false
        if (!(lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ")")) {
            val argExpr = parseExpr()
            if (lexer.peek().kind == TokenKind.OP && lexer.peek().value == ":=") {
                // This must be a named argument like name := expr
                // But we parsed argExpr as an expression; if it's a VarRef, we can use it as the name
                val argName = when (argExpr) {
                    is VarRef -> argExpr.name
                    else -> throw XFormException("XFST0001: expected identifier for named argument")
                }
                lexer.next()
                val value = parseExpr()
                namedArgs.add(NamedArg(argName, value))
                seenNamed = true
            } else {
                args.add(argExpr)
            }
            while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == ",") {
                lexer.next()
                val nextExpr = parseExpr()
                if (lexer.peek().kind == TokenKind.OP && lexer.peek().value == ":=") {
                    val argName = when (nextExpr) {
                        is VarRef -> nextExpr.name
                        else -> throw XFormException("XFST0001: expected identifier for named argument")
                    }
                    lexer.next()
                    val value = parseExpr()
                    namedArgs.add(NamedArg(argName, value))
                    seenNamed = true
                } else {
                    if (seenNamed) {
                        throw XFormException("XFST0001: positional argument after named argument")
                    }
                    args.add(nextExpr)
                }
            }
        }
        lexer.expect(TokenKind.PUNCT, ")")
        return FuncCall(name, args, namedArgs)
    }

    private fun pathContinues(): Boolean {
        val tok = lexer.peek()
        return tok.kind == TokenKind.SLASH || tok.kind == TokenKind.DOT || tok.kind == TokenKind.AT
    }

    private fun parsePath(start: PathStart?): Expr {
        val actualStart = start ?: run {
            val tok = lexer.next()
            when (tok.kind) {
                TokenKind.DOT -> when (tok.value) {
                    ".//" -> PathStart("desc")
                    else -> PathStart("context")
                }
                TokenKind.SLASH -> when (tok.value) {
                    "//" -> PathStart("desc_root")
                    else -> PathStart("root")
                }
                TokenKind.AT -> PathStart("attr")
                else -> throw XFormException("invalid path start at ${tok.pos}")
            }
        }

        val steps = mutableListOf<PathStep>()

        // Initial step for root/context/var/attr
        if (actualStart.kind in setOf("root", "context", "var", "attr")) {
            when {
                actualStart.kind == "attr" -> {
                    if (lexer.peek().kind == TokenKind.IDENT || (lexer.peek().kind == TokenKind.KW && isSoftKeyword(lexer.peek().value))) {
                        val test = StepTest("name", parseQName())
                        steps.add(PathStep("attr", test))
                    } else {
                        steps.add(PathStep("attr", StepTest("wildcard")))
                    }
                }
                lexer.peek().kind == TokenKind.AT -> {
                    lexer.next()
                    val test = StepTest("name", parseQName())
                    steps.add(PathStep("attr", test))
                }
                lexer.peek().kind == TokenKind.OP && lexer.peek().value == "*" -> {
                    val test = parseStepTest()
                    val preds = parsePredicates()
                    steps.add(PathStep("child", test, preds))
                }
                lexer.peek().kind == TokenKind.IDENT || (lexer.peek().kind == TokenKind.KW && isSoftKeyword(lexer.peek().value)) -> {
                    val test = parseStepTest()
                    val preds = parsePredicates()
                    steps.add(PathStep("child", test, preds))
                }
            }
        }

        // Initial step for desc
        if (actualStart.kind in setOf("desc", "desc_root")) {
            if (lexer.peek().kind == TokenKind.IDENT || lexer.peek().kind == TokenKind.OP ||
                (lexer.peek().kind == TokenKind.KW && isSoftKeyword(lexer.peek().value))) {
                val test = parseStepTest()
                val preds = parsePredicates()
                steps.add(PathStep("desc_or_self", test, preds))
            }
        }

        // More steps
        while (true) {
            when {
                lexer.peek().kind == TokenKind.SLASH -> {
                    val isDesc = lexer.peek().value == "//"
                    lexer.next()
                    // Check if this is an attribute step: / @name
                    val (axis, test, preds) = if (lexer.peek().kind == TokenKind.AT) {
                        lexer.next()
                        Triple("attr", StepTest("name", parseQName()), emptyList<Expr>())
                    } else {
                        val t = parseStepTest()
                        val p = parsePredicates()
                        Triple(if (isDesc) "desc" else "child", t, p)
                    }
                    steps.add(PathStep(axis, test, preds))
                }
                lexer.peek().kind == TokenKind.DOT && lexer.peek().value == "." -> {
                    lexer.next()
                    if (lexer.peek().kind == TokenKind.AT) {
                        lexer.next()
                        steps.add(PathStep("attr", StepTest("name", parseQName())))
                    } else {
                        steps.add(PathStep("self", StepTest("node")))
                    }
                }
                lexer.peek().kind == TokenKind.DOT && lexer.peek().value == ".." -> {
                    lexer.next()
                    steps.add(PathStep("parent", StepTest("node")))
                }
                lexer.peek().kind == TokenKind.AT -> {
                    lexer.next()
                    steps.add(PathStep("attr", StepTest("name", parseQName())))
                }
                else -> break
            }
        }

        return PathExpr(actualStart, steps)
    }

    private fun parseStepTest(): StepTest {
        return when {
            lexer.peek().kind == TokenKind.OP && lexer.peek().value == "*" -> {
                lexer.next()
                StepTest("wildcard")
            }
            lexer.peek().kind == TokenKind.IDENT || (lexer.peek().kind == TokenKind.KW && isSoftKeyword(lexer.peek().value)) -> {
                val name = lexer.peek().value
                if (name in setOf("text", "node", "element", "comment", "pi", "document")) {
                    lexer.next()
                    lexer.expect(TokenKind.PUNCT, "(")
                    lexer.expect(TokenKind.PUNCT, ")")
                    StepTest(name)
                } else {
                    StepTest("name", parseQName())
                }
            }
            else -> throw XFormException("invalid step test at ${lexer.peek().pos}")
        }
    }

    private fun parsePredicates(): List<Expr> {
        val preds = mutableListOf<Expr>()
        while (lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "[") {
            lexer.next()
            preds.add(parseExpr())
            lexer.expect(TokenKind.PUNCT, "]")
        }
        return preds
    }

    private fun parseQName(): String {
        val tok = lexer.peek()
        if (tok.kind == TokenKind.IDENT) {
            return lexer.next().value
        }
        if (tok.kind == TokenKind.KW && isSoftKeyword(tok.value)) {
            return lexer.next().value
        }
        throw XFormException("XFST0006: expected identifier, got ${tok.kind} ${tok.value} at ${tok.pos}")
    }

    private fun expectIdentifier(): String {
        val tok = lexer.peek()
        if (tok.kind == TokenKind.IDENT) {
            return lexer.next().value
        }
        if (tok.kind == TokenKind.KW && isSoftKeyword(tok.value)) {
            return lexer.next().value
        }
        throw XFormException("XFST0006: expected identifier, got ${tok.kind} ${tok.value} at ${tok.pos}")
    }

    private fun isSoftKeyword(name: String): Boolean = name in softKeywords

    private fun parsePattern(): Pattern {
        return when {
            lexer.peek().kind == TokenKind.AT -> {
                lexer.next()
                val name = parseQName()
                if (lexer.peek().kind == TokenKind.OP && lexer.peek().value == "=") {
                    lexer.next()
                    val value = lexer.expect(TokenKind.STRING)
                    AttributePattern(name, Literal(value.value))
                } else {
                    AttributePattern(name)
                }
            }
            lexer.peek().kind == TokenKind.IDENT && lexer.peek().value in setOf("node", "element", "text", "comment", "pi", "document") -> {
                val kind = lexer.next().value
                lexer.expect(TokenKind.PUNCT, "(")
                lexer.expect(TokenKind.PUNCT, ")")
                TypedPattern(kind)
            }
            lexer.peek().kind == TokenKind.IDENT && lexer.peek().value == "_" -> {
                lexer.next()
                WildcardPattern
            }
            lexer.peek().kind == TokenKind.OP && lexer.peek().value == "<" -> {
                lexer.next()
                val name = parseQName()
                lexer.expect(TokenKind.OP, ">")
                val (variable, child, children) = when {
                    lexer.peek().kind == TokenKind.PUNCT && lexer.peek().value == "{" -> {
                        lexer.next()
                        val v = expectIdentifier()
                        lexer.expect(TokenKind.PUNCT, "}")
                        Triple(v, null, emptyList<Pattern>())
                    }
                    lexer.peek().kind == TokenKind.OP && lexer.peek().value == "<" -> {
                        val chs = mutableListOf<Pattern>()
                        while (lexer.peek().kind == TokenKind.OP && lexer.peek().value == "<") {
                            chs.add(parsePattern())
                        }
                        Triple(null, null, chs.toList())
                    }
                    else -> Triple(null, null, emptyList<Pattern>())
                }
                lexer.expect(TokenKind.OP, "<")
                lexer.expect(TokenKind.SLASH, "/")
                val endName = parseQName()
                if (endName != name) {
                    throw XFormException("mismatched pattern end tag")
                }
                lexer.expect(TokenKind.OP, ">")
                ElementPattern(name, variable, child, children)
            }
            else -> throw XFormException("invalid pattern at ${lexer.peek().pos}")
        }
    }

    private fun parseConstructor(): Expr {
        lexer.expect(TokenKind.OP, "<")
        val name = parseQName()
        val attrs = mutableListOf<AttrConstructor>()

        while (true) {
            when {
                lexer.peek().kind == TokenKind.OP && lexer.peek().value == ">" -> {
                    lexer.next()
                    break
                }
                lexer.peek().kind == TokenKind.SLASH && lexer.peek().value == "/" -> {
                    lexer.next()
                    lexer.expect(TokenKind.OP, ">")
                    return Constructor(name, attrs, emptyList())
                }
                else -> {
                    val attrName = parseQName()
                    lexer.expect(TokenKind.OP, "=")
                    lexer.expect(TokenKind.PUNCT, "{")
                    val expr = parseExpr()
                    lexer.expect(TokenKind.PUNCT, "}")
                    attrs.add(AttrConstructor(attrName, expr))
                }
            }
        }

        val contents = mutableListOf<Expr>()
        lexer.clearBuffer()
        while (true) {
            if (lexer.pos >= text.length) {
                throw XFormException("unterminated constructor")
            }
            if (text.substring(lexer.pos).startsWith("</")) {
                val (endName, newPos) = readEndTag()
                if (endName != name) {
                    throw XFormException("mismatched end tag")
                }
                lexer.pos = newPos
                lexer.clearBuffer()
                break
            }
            if (text.substring(lexer.pos).startsWith("text{")) {
                lexer.pos += 4
                lexer.clearBuffer()
                lexer.expect(TokenKind.PUNCT, "{")
                val expr = parseExpr()
                lexer.expect(TokenKind.PUNCT, "}")
                contents.add(TextConstructor(expr))
                continue
            }
            if (text.substring(lexer.pos).startsWith("comment{")) {
                lexer.pos += 7
                lexer.clearBuffer()
                lexer.expect(TokenKind.PUNCT, "{")
                val expr = parseExpr()
                lexer.expect(TokenKind.PUNCT, "}")
                contents.add(CommentConstructor(expr))
                continue
            }
            if (text.substring(lexer.pos).startsWith("pi{")) {
                lexer.pos += 2
                lexer.clearBuffer()
                lexer.expect(TokenKind.PUNCT, "{")
                val target = parseExpr()
                lexer.expect(TokenKind.PUNCT, ",")
                val value = parseExpr()
                lexer.expect(TokenKind.PUNCT, "}")
                contents.add(PIConstructor(target, value))
                continue
            }
            when (text[lexer.pos]) {
                '<' -> {
                    lexer.clearBuffer()
                    contents.add(parseConstructor())
                }
                '{' -> {
                    lexer.pos++
                    lexer.clearBuffer()
                    val expr = parseExpr()
                    lexer.expect(TokenKind.PUNCT, "}")
                    contents.add(Interp(expr))
                }
                else -> {
                    val raw = parseCharData()
                    if (raw.isNotEmpty() && stripSpace(raw).isNotEmpty()) {
                        contents.add(Text(raw))
                    }
                }
            }
        }

        return Constructor(name, attrs, contents)
    }

    private fun parseCharData(): String {
        val start = lexer.pos
        while (lexer.pos < text.length && text[lexer.pos] != '<' && text[lexer.pos] != '{') {
            lexer.pos++
        }
        return text.substring(start, lexer.pos)
    }

    private fun readEndTag(): Pair<String, Int> {
        var pos = lexer.pos
        require(text.substring(pos, minOf(pos + 2, text.length)) == "</") { "expected end tag" }
        pos += 2
        val start = pos
        while (pos < text.length) {
            val c = text[pos]
            if (!c.isLetterOrDigit() && c != '_' && c != ':' && c != '-') break
            pos++
        }
        val name = text.substring(start, pos)
        while (pos < text.length && text[pos].isWhitespace()) pos++
        require(pos < text.length && text[pos] == '>') { "unterminated end tag" }
        return name to (pos + 1)
    }

    private fun stripSpace(s: String): String = s.filter { !it.isWhitespace() }
}
