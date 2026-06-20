use std::collections::HashSet;

use crate::ast::*;
use crate::lexer::{Lexer, TK};

lazy_static::lazy_static! {
    static ref RESERVED_FUNCTION_NAMES: HashSet<&'static str> = {
        let mut s = HashSet::new();
        s.insert("string");
        s.insert("number");
        s.insert("boolean");
        s.insert("typeOf");
        s.insert("name");
        s.insert("attr");
        s.insert("text");
        s.insert("children");
        s.insert("elements");
        s.insert("attributes");
        s.insert("copy");
        s.insert("count");
        s.insert("empty");
        s.insert("distinct");
        s.insert("sort");
        s.insert("concat");
        s.insert("seq");
        s.insert("head");
        s.insert("tail");
        s.insert("last");
        s.insert("index");
        s.insert("lookup");
        s.insert("groupBy");
        s.insert("sum");
        s.insert("position");
        s.insert("apply");
        s.insert("contains");
        s.insert("startsWith");
        s.insert("endsWith");
        s.insert("substring");
        s.insert("stringLength");
        s.insert("upperCase");
        s.insert("lowerCase");
        s.insert("normalizeSpace");
        s.insert("replace");
        s.insert("matches");
        s.insert("keys");
        s.insert("mapSize");
        s
    };
}

pub struct Parser {
    pub lexer: Lexer,
}

impl Parser {
    pub fn new(text: &str) -> Self {
        Parser { lexer: Lexer::new(text) }
    }

    pub fn parse_module(&mut self) -> Result<Module, String> {
        let mut functions = std::collections::HashMap::new();
        let mut rules: std::collections::HashMap<String, Vec<RuleDef>> =
            std::collections::HashMap::new();
        let mut vars = std::collections::HashMap::new();
        let mut namespaces = std::collections::HashMap::new();
        let mut imports = Vec::new();

        let mut version = "2.0".to_string();
        // Optional prolog
        if self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "xform" {
            self.lexer.next();
            self.lexer.expect(TK::Kw, Some("version"))?;
            version = self.lexer.expect(TK::Str, None)?.value;
            if version != "2.0" && version != "2.1" && version != "2.2" {
                return Err("XFST0005: unsupported version".into());
            }
            self.lexer.expect(TK::Punct, Some(";"))?;
        }

        loop {
            let pk = self.lexer.peek().kind.clone();
            let pv = self.lexer.peek().value.clone();
            if pk == TK::Kw && pv == "ns" {
                self.parse_ns(&mut namespaces)?;
            } else if pk == TK::Kw && pv == "import" {
                self.parse_import(&mut imports)?;
            } else if pk == TK::Kw && pv == "var" {
                let (name, expr) = self.parse_var()?;
                vars.insert(name, expr);
            } else if pk == TK::Kw && pv == "def" {
                let (name, fd) = self.parse_def()?;
                functions.insert(name, fd);
            } else if pk == TK::Kw && pv == "rule" {
                let (name, rd) = self.parse_rule()?;
                rules.entry(name).or_default().push(rd);
            } else {
                break;
            }
        }

        let expr = if self.lexer.peek().kind != TK::Eof {
            Some(self.parse_expr()?)
        } else {
            None
        };

        Ok(Module { functions, rules, vars, namespaces, imports, expr, version })
    }

    fn parse_ns(
        &mut self,
        ns: &mut std::collections::HashMap<String, String>,
    ) -> Result<(), String> {
        self.lexer.expect(TK::Kw, Some("ns"))?;
        let prefix = self.lexer.expect(TK::Str, None)?.value;
        self.lexer.expect(TK::Op, Some("="))?;
        let uri = self.lexer.expect(TK::Str, None)?.value;
        self.lexer.expect(TK::Punct, Some(";"))?;
        ns.insert(prefix, uri);
        Ok(())
    }

    fn parse_import(
        &mut self,
        imports: &mut Vec<(String, Option<String>)>,
    ) -> Result<(), String> {
        self.lexer.expect(TK::Kw, Some("import"))?;
        let iri = self.lexer.expect(TK::Str, None)?.value;
        let alias = if self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "as" {
            self.lexer.next();
            Some(self._expect_identifier()?)
        } else {
            None
        };
        self.lexer.expect(TK::Punct, Some(";"))?;
        imports.push((iri, alias));
        Ok(())
    }

    fn parse_var(&mut self) -> Result<(String, Expr), String> {
        self.lexer.expect(TK::Kw, Some("var"))?;
        let name = self._expect_identifier()?;
        self.lexer.expect(TK::Op, Some(":="))?;
        let expr = self.parse_expr()?;
        self.lexer.expect(TK::Punct, Some(";"))?;
        Ok((name, expr))
    }

    fn parse_def(&mut self) -> Result<(String, FunctionDef), String> {
        self.lexer.expect(TK::Kw, Some("def"))?;
        let name = self.parse_qname()?;
        if RESERVED_FUNCTION_NAMES.contains(name.as_str()) {
            return Err(format!("XFST0006: reserved function name '{}'", name));
        }
        self.lexer.expect(TK::Punct, Some("("))?;
        let params = if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == ")" {
            vec![]
        } else {
            let mut ps = vec![self.parse_param()?];
            while self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "," {
                self.lexer.next();
                ps.push(self.parse_param()?);
            }
            ps
        };
        self.lexer.expect(TK::Punct, Some(")"))?;
        self.lexer.expect(TK::Op, Some(":="))?;
        let body = self.parse_expr()?;
        self.lexer.expect(TK::Punct, Some(";"))?;
        Ok((name, FunctionDef { params, body }))
    }

    fn parse_param(&mut self) -> Result<Param, String> {
        let name = self._expect_identifier()?;
        let type_ref = if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == ":" {
            self.lexer.next();
            Some(self.parse_type_ref()?)
        } else {
            None
        };
        let default = if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == ":=" {
            self.lexer.next();
            Some(self.parse_expr()?)
        } else {
            None
        };
        Ok(Param { name, type_ref, default })
    }

    fn parse_type_ref(&mut self) -> Result<String, String> {
        let tok = self.lexer.peek();
        if (tok.kind == TK::Ident || tok.kind == TK::Kw)
            && ["string", "number", "boolean", "null", "map"].contains(&tok.value.as_str())
        {
            return Ok(self.lexer.next().value);
        }
        self.parse_qname()
    }

    fn parse_rule(&mut self) -> Result<(String, RuleDef), String> {
        self.lexer.expect(TK::Kw, Some("rule"))?;
        let name = self.parse_qname()?;
        self.lexer.expect(TK::Kw, Some("match"))?;
        let pattern = self.parse_pattern()?;
        self.lexer.expect(TK::Op, Some(":="))?;
        let body = self.parse_expr()?;
        self.lexer.expect(TK::Punct, Some(";"))?;
        Ok((name, RuleDef { pattern, body }))
    }

    pub fn parse_expr(&mut self) -> Result<Expr, String> {
        let pk = self.lexer.peek().kind.clone();
        let pv = self.lexer.peek().value.clone();
        if pk == TK::Kw && pv == "if" {
            return self.parse_if();
        }
        if pk == TK::Kw && pv == "let" {
            return self.parse_let();
        }
        if pk == TK::Kw && pv == "for" {
            return self.parse_for();
        }
        if pk == TK::Kw && pv == "match" {
            return self.parse_match();
        }
        self.parse_or()
    }

    fn parse_if(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Kw, Some("if"))?;
        let cond = self.parse_expr()?;
        self.lexer.expect(TK::Kw, Some("then"))?;
        let then_expr = self.parse_expr()?;
        self.lexer.expect(TK::Kw, Some("else"))?;
        let else_expr = self.parse_expr()?;
        Ok(Expr::IfExpr(Box::new(IfExpr { cond, then_expr, else_expr })))
    }

    fn parse_let(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Kw, Some("let"))?;
        let name = self._expect_identifier()?;
        self.lexer.expect(TK::Op, Some(":="))?;
        let value = self.parse_expr()?;
        self.lexer.expect(TK::Kw, Some("in"))?;
        let body = self.parse_expr()?;
        Ok(Expr::LetExpr(Box::new(LetExpr { name, value, body })))
    }

    fn parse_for(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Kw, Some("for"))?;
        let name = self._expect_identifier()?;
        self.lexer.expect(TK::Kw, Some("in"))?;
        let seq = self.parse_expr()?;
        let where_clause =
            if self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "where" {
                self.lexer.next();
                Some(self.parse_expr()?)
            } else {
                None
            };
        self.lexer.expect(TK::Kw, Some("return"))?;
        let body = self.parse_expr()?;
        Ok(Expr::ForExpr(Box::new(ForExpr { name, seq, where_clause, body })))
    }

    fn parse_match(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Kw, Some("match"))?;
        let target = self.parse_expr()?;
        self.lexer.expect(TK::Punct, Some(":"))?;
        let mut cases = Vec::new();
        let mut default = None;
        loop {
            let pk = self.lexer.peek().kind.clone();
            let pv = self.lexer.peek().value.clone();
            if pk == TK::Kw && pv == "case" {
                self.lexer.next();
                let pat = self.parse_pattern()?;
                let guard =
                    if self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "where" {
                        self.lexer.next();
                        Some(self.parse_expr()?)
                    } else {
                        None
                    };
                // "=>" is two tokens: "=" then ">"
                self.lexer.expect(TK::Op, Some("="))?;
                self.lexer.expect(TK::Op, Some(">"))?;
                let expr = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some(";"))?;
                cases.push(MatchCase { pattern: pat, guard, body: expr });
            } else if pk == TK::Kw && pv == "default" {
                self.lexer.next();
                self.lexer.expect(TK::Op, Some("="))?;
                self.lexer.expect(TK::Op, Some(">"))?;
                default = Some(self.parse_expr()?);
                self.lexer.expect(TK::Punct, Some(";"))?;
                break;
            } else {
                break;
            }
        }
        Ok(Expr::MatchExpr(Box::new(MatchExpr { target, cases, default })))
    }

    fn parse_or(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_and()?;
        while self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "or" {
            self.lexer.next();
            let right = self.parse_and()?;
            expr = Expr::BinaryOp { op: "or".into(), left: Box::new(expr), right: Box::new(right) };
        }
        Ok(expr)
    }

    fn parse_and(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_eq()?;
        while self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "and" {
            self.lexer.next();
            let right = self.parse_eq()?;
            expr = Expr::BinaryOp {
                op: "and".into(),
                left: Box::new(expr),
                right: Box::new(right),
            };
        }
        Ok(expr)
    }

    fn parse_eq(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_rel()?;
        while self.lexer.peek().kind == TK::Op
            && (self.lexer.peek().value == "=" || self.lexer.peek().value == "!=")
        {
            if self.lexer.peek().value == "=" && self.next_token_is_arrow_tail() {
                break;
            }
            let op = self.lexer.next().value;
            let right = self.parse_rel()?;
            expr = Expr::BinaryOp { op, left: Box::new(expr), right: Box::new(right) };
        }
        Ok(expr)
    }

    fn next_token_is_arrow_tail(&mut self) -> bool {
        let saved_pos = self.lexer.pos;
        let saved_buf = self.lexer.buf.clone();
        self.lexer.next();
        let is_arrow = self.lexer.peek().kind == TK::Op && self.lexer.peek().value == ">";
        self.lexer.pos = saved_pos;
        self.lexer.buf = saved_buf;
        is_arrow
    }

    fn parse_rel(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_add()?;
        while self.lexer.peek().kind == TK::Op
            && ["<", "<=", ">", ">="].contains(&self.lexer.peek().value.as_str())
        {
            let op = self.lexer.next().value;
            let right = self.parse_add()?;
            expr = Expr::BinaryOp { op, left: Box::new(expr), right: Box::new(right) };
        }
        Ok(expr)
    }

    fn parse_add(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_mul()?;
        while self.lexer.peek().kind == TK::Op
            && (self.lexer.peek().value == "+" || self.lexer.peek().value == "-")
        {
            let op = self.lexer.next().value;
            let right = self.parse_mul()?;
            expr = Expr::BinaryOp { op, left: Box::new(expr), right: Box::new(right) };
        }
        Ok(expr)
    }

    fn parse_mul(&mut self) -> Result<Expr, String> {
        let mut expr = self.parse_unary()?;
        loop {
            let pk = self.lexer.peek().kind.clone();
            let pv = self.lexer.peek().value.clone();
            if pk == TK::Op && pv == "*" {
                self.lexer.next();
                let right = self.parse_unary()?;
                expr =
                    Expr::BinaryOp { op: "*".into(), left: Box::new(expr), right: Box::new(right) };
            } else if pk == TK::Kw && (pv == "div" || pv == "mod") {
                let op = self.lexer.next().value;
                let right = self.parse_unary()?;
                expr = Expr::BinaryOp { op, left: Box::new(expr), right: Box::new(right) };
            } else {
                break;
            }
        }
        Ok(expr)
    }

    fn parse_unary(&mut self) -> Result<Expr, String> {
        if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == "-" {
            self.lexer.next();
            let e = self.parse_unary()?;
            return Ok(Expr::UnaryOp { op: "-".into(), expr: Box::new(e) });
        }
        if self.lexer.peek().kind == TK::Kw && self.lexer.peek().value == "not" {
            self.lexer.next();
            let e = self.parse_unary()?;
            return Ok(Expr::UnaryOp { op: "not".into(), expr: Box::new(e) });
        }
        self.parse_primary()
    }

    fn parse_primary(&mut self) -> Result<Expr, String> {
        let pk = self.lexer.peek().kind.clone();
        let pv = self.lexer.peek().value.clone();

        if pk == TK::Num {
            let v = self.lexer.next().value;
            let n: f64 = v.parse().map_err(|e| format!("Bad number: {}", e))?;
            return Ok(Expr::Literal(LiteralValue::Num(n)));
        }
        if pk == TK::Str {
            let v = self.lexer.next().value;
            return Ok(Expr::Literal(LiteralValue::Str(v)));
        }
        if pk == TK::Kw && ["true", "false", "null"].contains(&pv.as_str()) {
            self.lexer.next();
            let val = match pv.as_str() {
                "true" => LiteralValue::Bool(true),
                "false" => LiteralValue::Bool(false),
                "null" => LiteralValue::Null,
                _ => unreachable!(),
            };
            return Ok(Expr::Literal(val));
        }
        if pk == TK::Punct && pv == "(" {
            self.lexer.next();
            let e = self.parse_expr()?;
            self.lexer.expect(TK::Punct, Some(")"))?;
            return Ok(e);
        }
        if pk == TK::Kw && pv == "apply" {
            return self.parse_apply();
        }
        // text{...} constructor vs text(...) function call
        if pk == TK::Kw && pv == "text" {
            let saved_pos = self.lexer.pos;
            let saved_buf = self.lexer.buf.clone();
            self.lexer.next(); // consume "text"
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "{" {
                self.lexer.next(); // consume "{"
                let e = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                return Ok(Expr::TextConstructor(Box::new(e)));
            }
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "(" {
                return self.parse_func_call("text".to_string());
            }
            // Not text{...} or text(...), restore
            self.lexer.pos = saved_pos;
            self.lexer.buf = saved_buf;
        }
        if pk == TK::Kw && pv == "comment" {
            let saved_pos = self.lexer.pos;
            let saved_buf = self.lexer.buf.clone();
            self.lexer.next(); // consume "comment"
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "{" {
                self.lexer.next(); // consume "{"
                let e = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                return Ok(Expr::CommentConstructor(Box::new(e)));
            }
            self.lexer.pos = saved_pos;
            self.lexer.buf = saved_buf;
        }
        if pk == TK::Kw && pv == "pi" {
            let saved_pos = self.lexer.pos;
            let saved_buf = self.lexer.buf.clone();
            self.lexer.next(); // consume "pi"
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "{" {
                self.lexer.next(); // consume "{"
                let target = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some(","))?;
                let value = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                return Ok(Expr::PIConstructor(Box::new(PIConstructor { target, value })));
            }
            self.lexer.pos = saved_pos;
            self.lexer.buf = saved_buf;
        }
        // Element constructor
        if pk == TK::Op && pv == "<" {
            return self.parse_constructor();
        }
        // Path starting with . or / or @
        if pk == TK::Dot || pk == TK::Slash || pk == TK::At {
            return self.parse_path(None);
        }
        // string/number/boolean/map as function calls
        if pk == TK::Kw && ["string", "number", "boolean", "map"].contains(&pv.as_str()) {
            let saved_pos = self.lexer.pos;
            let saved_buf = self.lexer.buf.clone();
            self.lexer.next();
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "(" {
                return self.parse_func_call(pv);
            }
            self.lexer.pos = saved_pos;
            self.lexer.buf = saved_buf;
        }
        // Identifier: variable, function call, or path start
        if pk == TK::Ident {
            let name = self.lexer.next().value;
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "(" {
                return self.parse_func_call(name);
            }
            if self.path_continues() {
                let start = PathStart { kind: PathStartKind::Var, name: Some(name) };
                return self.parse_path(Some(start));
            }
            return Ok(Expr::VarRef(name));
        }
        Err(format!("Unexpected token {:?} {:?} at {}", pk, pv, self.lexer.peek().pos))
    }

    fn parse_apply(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Kw, Some("apply"))?;
        self.lexer.expect(TK::Punct, Some("("))?;
        let expr = self.parse_expr()?;
        let mut ruleset = None;
        if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "," {
            self.lexer.next();
            ruleset = Some(self.parse_qname()?);
        }
        self.lexer.expect(TK::Punct, Some(")"))?;
        Ok(Expr::ApplyExpr(Box::new(ApplyExpr { expr, ruleset })))
    }

    fn parse_func_call(&mut self, name: String) -> Result<Expr, String> {
        self.lexer.expect(TK::Punct, Some("("))?;
        let mut args = Vec::new();
        let mut named_args = Vec::new();
        if !(self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == ")") {
            let (arg_name, arg_expr) = self.parse_argument()?;
            if let Some(n) = arg_name {
                named_args.push((n, arg_expr));
            } else {
                args.push(arg_expr);
            }
            while self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "," {
                self.lexer.next();
                let (arg_name2, arg_expr2) = self.parse_argument()?;
                if let Some(n) = arg_name2 {
                    named_args.push((n, arg_expr2));
                } else {
                    if !named_args.is_empty() {
                        return Err("XFST0001: positional argument after named argument".into());
                    }
                    args.push(arg_expr2);
                }
            }
        }
        self.lexer.expect(TK::Punct, Some(")"))?;
        Ok(Expr::FuncCall(Box::new(FuncCall { name, args, named_args })))
    }

    fn parse_argument(&mut self) -> Result<(Option<String>, Expr), String> {
        // Look ahead for named argument: ident := expr
        if self.lexer.peek().kind == TK::Ident {
            let saved_pos = self.lexer.pos;
            let saved_buf = self.lexer.buf.clone();
            let name = self.lexer.next().value;
            if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == ":=" {
                self.lexer.next();
                let expr = self.parse_expr()?;
                return Ok((Some(name), expr));
            }
            self.lexer.pos = saved_pos;
            self.lexer.buf = saved_buf;
        }
        Ok((None, self.parse_expr()?))
    }

    fn path_continues(&mut self) -> bool {
        let pk = self.lexer.peek().kind.clone();
        pk == TK::Slash || pk == TK::Dot || pk == TK::At
    }

    fn parse_path(&mut self, start: Option<PathStart>) -> Result<Expr, String> {
        let start = if let Some(s) = start {
            s
        } else {
            let tok = self.lexer.next();
            match (tok.kind, tok.value.as_str()) {
                (TK::Dot, ".//") => PathStart { kind: PathStartKind::Desc, name: None },
                (TK::Dot, _) => PathStart { kind: PathStartKind::Context, name: None },
                (TK::Slash, "//") => PathStart { kind: PathStartKind::DescRoot, name: None },
                (TK::Slash, _) => PathStart { kind: PathStartKind::Root, name: None },
                (TK::At, _) => {
                    // Bare @name is shorthand for ./@name
                    let name = self.parse_qname()?;
                    let steps = vec![PathStep {
                        axis: PathAxis::Attr,
                        test: StepTest::named(&name),
                        predicates: vec![],
                    }];
                    return Ok(Expr::PathExpr(Box::new(PathExpr {
                        start: PathStart { kind: PathStartKind::Context, name: None },
                        steps,
                    })));
                }
                (_, _) => return Err(format!("Invalid path start at {}", tok.pos)),
            }
        };

        let mut steps = Vec::new();

        // For .// or // starts, the immediate name is a desc-or-self step
        if start.kind == PathStartKind::Desc || start.kind == PathStartKind::DescRoot {
            let pk = self.lexer.peek().kind.clone();
            if pk == TK::Ident || (pk == TK::Op && self.lexer.peek().value == "*") {
                let test = self.parse_step_test()?;
                let preds = self.parse_predicates()?;
                steps.push(PathStep { axis: PathAxis::DescOrSelf, test, predicates: preds });
            }
        }

        // For / starts, the immediate name is a child step
        if start.kind == PathStartKind::Root {
            let pk = self.lexer.peek().kind.clone();
            if pk == TK::At {
                self.lexer.next();
                let name = self.parse_qname()?;
                steps.push(PathStep {
                    axis: PathAxis::Attr,
                    test: StepTest::named(&name),
                    predicates: vec![],
                });
            } else if pk == TK::Ident || (pk == TK::Op && self.lexer.peek().value == "*") {
                let test = self.parse_step_test()?;
                let preds = self.parse_predicates()?;
                steps.push(PathStep { axis: PathAxis::Child, test, predicates: preds });
            }
        }

        loop {
            let pk = self.lexer.peek().kind.clone();
            let pv = self.lexer.peek().value.clone();

            if pk == TK::Slash {
                let axis = if pv == "/" { PathAxis::Child } else { PathAxis::Desc };
                self.lexer.next();
                if self.lexer.peek().kind == TK::At {
                    self.lexer.next();
                    let name = self.parse_qname()?;
                    steps.push(PathStep {
                        axis: PathAxis::Attr,
                        test: StepTest::named(&name),
                        predicates: vec![],
                    });
                } else {
                    let test = self.parse_step_test()?;
                    let preds = self.parse_predicates()?;
                    steps.push(PathStep { axis, test, predicates: preds });
                }
                continue;
            }
            if pk == TK::Dot {
                if pv == "." {
                    self.lexer.next();
                    if self.lexer.peek().kind == TK::At {
                        self.lexer.next();
                        let name = self.parse_qname()?;
                        steps.push(PathStep {
                            axis: PathAxis::Attr,
                            test: StepTest::named(&name),
                            predicates: vec![],
                        });
                    } else {
                        steps.push(PathStep {
                            axis: PathAxis::SelfAxis,
                            test: StepTest::node(),
                            predicates: vec![],
                        });
                    }
                    continue;
                }
                if pv == ".." {
                    self.lexer.next();
                    steps.push(PathStep {
                        axis: PathAxis::Parent,
                        test: StepTest::node(),
                        predicates: vec![],
                    });
                    continue;
                }
            }
            if pk == TK::At {
                self.lexer.next();
                let name = self.parse_qname()?;
                steps.push(PathStep {
                    axis: PathAxis::Attr,
                    test: StepTest::named(&name),
                    predicates: vec![],
                });
                continue;
            }
            break;
        }

        Ok(Expr::PathExpr(Box::new(PathExpr { start, steps })))
    }

    fn parse_step_test(&mut self) -> Result<StepTest, String> {
        let pk = self.lexer.peek().kind.clone();
        let pv = self.lexer.peek().value.clone();
        if pk == TK::Op && pv == "*" {
            self.lexer.next();
            return Ok(StepTest::wildcard());
        }
        if pk == TK::Ident || pk == TK::Kw {
            if ["text", "node", "element", "comment", "pi", "document"].contains(&pv.as_str()) {
                self.lexer.next();
                self.lexer.expect(TK::Punct, Some("("))?;
                self.lexer.expect(TK::Punct, Some(")"))?;
                return Ok(match pv.as_str() {
                    "text" => StepTest::text(),
                    "node" => StepTest::node(),
                    "element" => StepTest::element(),
                    "comment" => StepTest { kind: StepTestKind::Comment, name: None },
                    "pi" => StepTest { kind: StepTestKind::Pi, name: None },
                    "document" => StepTest { kind: StepTestKind::Document, name: None },
                    _ => unreachable!(),
                });
            }
            let name = self.parse_qname()?;
            return Ok(StepTest::named(&name));
        }
        Err(format!("Invalid step test at {}", self.lexer.peek().pos))
    }

    fn parse_predicates(&mut self) -> Result<Vec<Expr>, String> {
        let mut preds = Vec::new();
        while self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "[" {
            self.lexer.next();
            preds.push(self.parse_expr()?);
            self.lexer.expect(TK::Punct, Some("]"))?;
        }
        Ok(preds)
    }

    fn _expect_identifier(&mut self) -> Result<String, String> {
        let tok = self.lexer.next();
        if tok.kind == TK::Ident {
            return Ok(tok.value);
        }
        if tok.kind == TK::Kw {
            return Err(format!("XFST0006: reserved word '{}' used as identifier", tok.value));
        }
        Err(format!("Expected identifier at {}", tok.pos))
    }

    fn parse_qname(&mut self) -> Result<String, String> {
        self._expect_identifier()
    }

    fn parse_pattern(&mut self) -> Result<Pattern, String> {
        let pk = self.lexer.peek().kind.clone();
        let pv = self.lexer.peek().value.clone();

        if pk == TK::At {
            self.lexer.next();
            let name = self.parse_qname()?;
            let mut value = None;
            if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == "=" {
                // Look ahead: if next token is ">", this is "=>" not an attr value
                let saved_pos = self.lexer.pos;
                let saved_buf = self.lexer.buf.clone();
                self.lexer.next();
                if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == ">" {
                    self.lexer.pos = saved_pos;
                    self.lexer.buf = saved_buf;
                } else {
                    value = Some(self.parse_pattern_literal()?);
                }
            }
            return Ok(Pattern::Attribute(AttributePattern { name, value }));
        }
        if pk == TK::Ident || pk == TK::Kw {
            if ["node", "element", "text", "comment", "pi", "document"].contains(&pv.as_str()) {
                self.lexer.next();
                self.lexer.expect(TK::Punct, Some("("))?;
                self.lexer.expect(TK::Punct, Some(")"))?;
                return Ok(Pattern::Typed(pv));
            }
            if pv == "_" {
                self.lexer.next();
                return Ok(Pattern::Wildcard);
            }
        }
        if pk == TK::Str {
            let v = self.lexer.next().value;
            return Ok(Pattern::Literal(v));
        }
        if pk == TK::Op && pv == "<" {
            self.lexer.next();
            let name = self.parse_qname()?;
            let mut attrs: Vec<(String, Option<LiteralValue>)> = Vec::new();
            while self.lexer.peek().kind == TK::At {
                self.lexer.next();
                let attr_name = self.parse_qname()?;
                let mut attr_value = None;
                if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == "=" {
                    self.lexer.next();
                    attr_value = Some(self.parse_pattern_literal()?);
                }
                attrs.push((attr_name, attr_value));
            }
            self.lexer.expect(TK::Op, Some(">"))?;
            let mut var: Option<String> = None;
            let mut children: Vec<Pattern> = Vec::new();
            if self.lexer.peek().kind == TK::Punct && self.lexer.peek().value == "{" {
                self.lexer.next();
                var = Some(self._expect_identifier()?);
                self.lexer.expect(TK::Punct, Some("}"))?;
            } else if self.lexer.peek().kind == TK::Op && self.lexer.peek().value == "<" {
                // Could be end tag or child patterns
                while !(self.lexer.peek().kind == TK::Op && self.lexer.peek().value == "<"
                    && self.lexer.pos < self.lexer.chars.len()
                    && self.lexer.chars[self.lexer.pos] == '/')
                {
                    children.push(self.parse_pattern()?);
                }
            }
            self.lexer.expect(TK::Op, Some("<"))?;
            self.lexer.expect(TK::Slash, Some("/"))?;
            let end = self.parse_qname()?;
            if end != name {
                return Err(format!("Mismatched pattern end tag: expected {}, got {}", name, end));
            }
            self.lexer.expect(TK::Op, Some(">"))?;
            return Ok(Pattern::Element(ElementPattern { name, var, attrs, children }));
        }
        Err(format!("Invalid pattern at {}", self.lexer.peek().pos))
    }

    fn parse_pattern_literal(&mut self) -> Result<LiteralValue, String> {
        let kind = self.lexer.peek().kind.clone();
        let value = self.lexer.peek().value.clone();
        if kind == TK::Str {
            self.lexer.next();
            return Ok(LiteralValue::Str(value));
        }
        if kind == TK::Num {
            self.lexer.next();
            let n: f64 = value.parse().map_err(|e| format!("Bad number in pattern: {}", e))?;
            return Ok(LiteralValue::Num(n));
        }
        if kind == TK::Kw && ["true", "false", "null"].contains(&value.as_str()) {
            self.lexer.next();
            return match value.as_str() {
                "true" => Ok(LiteralValue::Bool(true)),
                "false" => Ok(LiteralValue::Bool(false)),
                "null" => Ok(LiteralValue::Null),
                _ => unreachable!(),
            };
        }
        Err("Invalid literal in pattern".into())
    }

    fn parse_constructor(&mut self) -> Result<Expr, String> {
        self.lexer.expect(TK::Op, Some("<"))?;
        let name = self.parse_qname()?;

        let mut attrs = Vec::new();
        loop {
            let pk = self.lexer.peek().kind.clone();
            let pv = self.lexer.peek().value.clone();
            if pk == TK::Op && pv == ">" {
                self.lexer.next();
                break;
            }
            if pk == TK::Slash && pv == "/" {
                self.lexer.next();
                self.lexer.expect(TK::Op, Some(">"))?;
                return Ok(Expr::Constructor(Box::new(Constructor {
                    name,
                    attrs,
                    contents: vec![],
                })));
            }
            let aname = self.parse_qname()?;
            self.lexer.expect(TK::Op, Some("="))?;
            self.lexer.expect(TK::Punct, Some("{"))?;
            let aexpr = self.parse_expr()?;
            self.lexer.expect(TK::Punct, Some("}"))?;
            attrs.push((aname, aexpr));
        }

        // Parse content by inspecting raw chars
        let mut contents = Vec::new();
        self.lexer.buf = None;
        loop {
            let pos = self.lexer.pos;
            if pos >= self.lexer.chars.len() {
                return Err("Unterminated constructor".into());
            }
            // End tag?
            if pos + 1 < self.lexer.chars.len()
                && self.lexer.chars[pos] == '<'
                && self.lexer.chars[pos + 1] == '/'
            {
                let (end_name, new_pos) = self.read_end_tag()?;
                if end_name != name {
                    return Err(format!(
                        "Mismatched end tag: expected {}, got {}",
                        name, end_name
                    ));
                }
                self.lexer.pos = new_pos;
                self.lexer.buf = None;
                break;
            }
            // text{ constructor
            if self.starts_with_at("text{") {
                self.lexer.pos += 4; // "text"
                self.lexer.buf = None;
                self.lexer.expect(TK::Punct, Some("{"))?;
                let e = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                contents.push(Expr::TextConstructor(Box::new(e)));
                continue;
            }
            // comment{ constructor
            if self.starts_with_at("comment{") {
                self.lexer.pos += 7; // "comment"
                self.lexer.buf = None;
                self.lexer.expect(TK::Punct, Some("{"))?;
                let e = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                contents.push(Expr::CommentConstructor(Box::new(e)));
                continue;
            }
            // pi{ constructor
            if self.starts_with_at("pi{") {
                self.lexer.pos += 2; // "pi"
                self.lexer.buf = None;
                self.lexer.expect(TK::Punct, Some("{"))?;
                let target = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some(","))?;
                let value = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                contents.push(Expr::PIConstructor(Box::new(PIConstructor { target, value })));
                continue;
            }
            let ch = self.lexer.chars[self.lexer.pos];
            if ch == '<' {
                self.lexer.buf = None;
                let c = self.parse_constructor()?;
                contents.push(c);
                continue;
            }
            if ch == '{' {
                self.lexer.pos += 1;
                self.lexer.buf = None;
                let e = self.parse_expr()?;
                self.lexer.expect(TK::Punct, Some("}"))?;
                contents.push(Expr::Interp(Box::new(e)));
                continue;
            }
            let cd = self.parse_chardata();
            if !cd.trim().is_empty() {
                contents.push(Expr::CharData(cd));
            }
        }

        Ok(Expr::Constructor(Box::new(Constructor { name, attrs, contents })))
    }

    fn starts_with_at(&self, s: &str) -> bool {
        let pos = self.lexer.pos;
        let sc: Vec<char> = s.chars().collect();
        if pos + sc.len() > self.lexer.chars.len() {
            return false;
        }
        self.lexer.chars[pos..pos + sc.len()] == sc[..]
    }

    fn parse_chardata(&mut self) -> String {
        let mut out = String::new();
        while self.lexer.pos < self.lexer.chars.len() {
            let ch = self.lexer.chars[self.lexer.pos];
            if ch == '<' || ch == '{' {
                break;
            }
            out.push(ch);
            self.lexer.pos += 1;
        }
        out
    }

    fn read_end_tag(&self) -> Result<(String, usize), String> {
        let mut pos = self.lexer.pos;
        if pos + 1 >= self.lexer.chars.len()
            || self.lexer.chars[pos] != '<'
            || self.lexer.chars[pos + 1] != '/'
        {
            return Err("Expected end tag".into());
        }
        pos += 2;
        let start = pos;
        while pos < self.lexer.chars.len()
            && (self.lexer.chars[pos].is_alphanumeric()
                || self.lexer.chars[pos] == '_'
                || self.lexer.chars[pos] == ':'
                || self.lexer.chars[pos] == '-')
        {
            pos += 1;
        }
        let end_name: String = self.lexer.chars[start..pos].iter().collect();
        while pos < self.lexer.chars.len() && self.lexer.chars[pos].is_whitespace() {
            pos += 1;
        }
        if pos >= self.lexer.chars.len() || self.lexer.chars[pos] != '>' {
            return Err("Unterminated end tag".into());
        }
        Ok((end_name, pos + 1))
    }
}
