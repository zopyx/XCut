use std::collections::HashMap;
use std::rc::Rc;

use crate::ast::*;
use crate::xmlmodel::{
    deep_copy, deep_copy_recurse, iter_descendants, make_attr, make_comment, make_element,
    make_pi, make_text, serialize, XmlNode, NodeKind,
};

pub type Seq = Vec<Item>;
pub type SeqRef = Rc<Seq>;
pub type XMap = HashMap<String, Seq>;

#[derive(Clone, Debug)]
pub enum Item {
    Node(Rc<XmlNode>),
    Str(String),
    Num(f64),
    Bool(bool),
    Null,
    Map(Rc<XMap>),
    FuncRef(String),
}

pub const MAX_RECURSION_DEPTH: usize = 10000;

#[derive(Clone)]
pub struct Context {
    pub context_item: Option<Item>,
    pub root: Rc<XmlNode>,
    pub variables: HashMap<String, SeqRef>,
    pub functions: HashMap<String, FunctionDef>,
    pub rules: HashMap<String, Vec<RuleDef>>,
    pub position: Option<f64>,
    pub last: Option<f64>,
    pub recursion_depth: usize,
    pub version: String,
}

impl Context {
    fn with_item(&self, item: Item) -> Context {
        Context { context_item: Some(item), ..self.clone() }
    }
    fn with_vars(&self, vars: HashMap<String, SeqRef>) -> Context {
        Context { variables: vars, ..self.clone() }
    }
}

pub fn eval_module(module: &Module, doc: Rc<XmlNode>) -> Result<Seq, String> {
    let mut variables: HashMap<String, SeqRef> = HashMap::new();
    let root = doc.clone();
    let mut ctx = Context {
        context_item: Some(Item::Node(doc)),
        root,
        variables: variables.clone(),
        functions: module.functions.clone(),
        rules: module.rules.clone(),
        position: None,
        last: None,
        recursion_depth: 0,
        version: module.version.clone(),
    };
    for (name, expr) in &module.vars {
        let val = eval_expr(expr, &ctx)?;
        let rc = Rc::new(val.clone());
        ctx.variables.insert(name.clone(), rc.clone());
        variables.insert(name.clone(), rc);
    }
    match &module.expr {
        None => Ok(vec![]),
        Some(e) => eval_expr(e, &ctx),
    }
}

pub fn eval_expr(expr: &Expr, ctx: &Context) -> Result<Seq, String> {
    match expr {
        Expr::Literal(lit) => Ok(vec![lit_to_item(lit)]),

        Expr::VarRef(name) => {
            if let Some(val) = ctx.variables.get(name) {
                return Ok((**val).clone());
            }
            if ctx.functions.contains_key(name.as_str()) {
                return Ok(vec![Item::FuncRef(name.clone())]);
            }
            // Fall back to child axis from context
            if let Some(Item::Node(node)) = &ctx.context_item {
                if node.kind == NodeKind::Element || node.kind == NodeKind::Document {
                    let children: Seq = node
                        .children
                        .iter()
                        .filter(|c| {
                            c.kind == NodeKind::Element && c.name.as_deref() == Some(name)
                        })
                        .map(|c| Item::Node(c.clone()))
                        .collect();
                    return Ok(children);
                }
            }
            Ok(vec![])
        }

        Expr::IfExpr(ie) => {
            let cond = eval_expr(&ie.cond, ctx)?;
            if to_boolean(&cond) {
                eval_expr(&ie.then_expr, ctx)
            } else {
                eval_expr(&ie.else_expr, ctx)
            }
        }

        Expr::LetExpr(le) => {
            let val = eval_expr(&le.value, ctx)?;
            let mut vars = ctx.variables.clone();
            vars.insert(le.name.clone(), Rc::new(val));
            eval_expr(&le.body, &ctx.with_vars(vars))
        }

        Expr::ForExpr(fe) => {
            let seq = eval_expr(&fe.seq, ctx)?;
            let total = seq.len();
            let mut out = Vec::new();
            for (idx, item) in seq.into_iter().enumerate() {
                let mut vars = ctx.variables.clone();
                vars.insert(fe.name.clone(), Rc::new(vec![item.clone()]));
                let new_ctx = Context {
                    context_item: Some(item),
                    variables: vars,
                    position: Some((idx + 1) as f64),
                    last: Some(total as f64),
                    ..ctx.clone()
                };
                if let Some(w) = &fe.where_clause {
                    if !to_boolean(&eval_expr(w, &new_ctx)?) {
                        continue;
                    }
                }
                out.extend(eval_expr(&fe.body, &new_ctx)?);
            }
            Ok(out)
        }

        Expr::MatchExpr(me) => {
            let target_seq = eval_expr(&me.target, ctx)?;
            let mut out = Vec::new();
            for target in target_seq {
                let mut matched = false;
                for case in &me.cases {
                    if let Some(bindings) = match_pattern(&case.pattern, &target) {
                        let mut vars = ctx.variables.clone();
                        vars.extend(bindings);
                        let new_ctx = Context {
                            context_item: Some(target.clone()),
                            variables: vars,
                            ..ctx.clone()
                        };
                        if let Some(guard) = &case.guard {
                            if !to_boolean(&eval_expr(guard, &new_ctx)?) {
                                continue;
                            }
                        }
                        matched = true;
                        out.extend(eval_expr(&case.body, &new_ctx)?);
                        break;
                    }
                }
                if !matched {
                    match &me.default {
                        Some(d) => {
                            let new_ctx = ctx.with_item(target);
                            out.extend(eval_expr(d, &new_ctx)?);
                        }
                        None => return Err("XFDY0001: no matching case".into()),
                    }
                }
            }
            Ok(out)
        }

        Expr::FuncCall(fc) => {
            let args: Result<Vec<Seq>, String> =
                fc.args.iter().map(|a| eval_expr(a, ctx)).collect();
            let named: HashMap<String, Seq> = fc.named_args.iter()
                .map(|(n, e)| eval_expr(e, ctx).map(|v| (n.clone(), v)))
                .collect::<Result<_, _>>()?;
            let named_raw: HashMap<String, Expr> = fc.named_args.iter()
                .map(|(n, e)| (n.clone(), e.clone()))
                .collect();
            call_function(&fc.name, args?, ctx, named, named_raw)
        }

        Expr::ApplyExpr(ae) => {
            let seq = eval_expr(&ae.expr, ctx)?;
            let ruleset = ae.ruleset.clone().unwrap_or_else(|| "main".into());
            _do_apply(seq, &ruleset, ctx)
        }

        Expr::UnaryOp { op, expr } => {
            let val = eval_expr(expr, ctx)?;
            match op.as_str() {
                "-" => Ok(vec![Item::Num(-to_number(&val)?)]),
                "not" => Ok(vec![Item::Bool(!to_boolean(&val))]),
                _ => Err(format!("Unknown unary op {}", op)),
            }
        }

        Expr::BinaryOp { op, left, right } => {
            match op.as_str() {
                "and" => {
                    let l = eval_expr(left, ctx)?;
                    if !to_boolean(&l) {
                        return Ok(vec![Item::Bool(false)]);
                    }
                    let r = eval_expr(right, ctx)?;
                    Ok(vec![Item::Bool(to_boolean(&r))])
                }
                "or" => {
                    let l = eval_expr(left, ctx)?;
                    if to_boolean(&l) {
                        return Ok(vec![Item::Bool(true)]);
                    }
                    let r = eval_expr(right, ctx)?;
                    Ok(vec![Item::Bool(to_boolean(&r))])
                }
                _ => {
                    let l = eval_expr(left, ctx)?;
                    let r = eval_expr(right, ctx)?;
                    Ok(vec![eval_binary(op, &l, &r)?])
                }
            }
        }

        Expr::PathExpr(pe) => eval_path(pe, ctx),

        Expr::Constructor(c) => Ok(vec![Item::Node(eval_constructor(c, ctx)?)]),

        Expr::TextConstructor(e) => {
            let val = eval_expr(e, ctx)?;
            Ok(vec![Item::Node(make_text(&to_string(&val)))])
        }

        Expr::CommentConstructor(e) => {
            let val = eval_expr(e, ctx)?;
            Ok(vec![Item::Node(make_comment(&to_string(&val)))])
        }

        Expr::PIConstructor(pi) => {
            let target = to_string(&eval_expr(&pi.target, ctx)?);
            let value = to_string(&eval_expr(&pi.value, ctx)?);
            Ok(vec![Item::Node(make_pi(&target, &value))])
        }

        Expr::CharData(s) => Ok(vec![Item::Str(s.clone())]),

        Expr::Interp(e) => eval_expr(e, ctx),
    }
}

fn lit_to_item(lit: &LiteralValue) -> Item {
    match lit {
        LiteralValue::Str(s) => Item::Str(s.clone()),
        LiteralValue::Num(n) => Item::Num(*n),
        LiteralValue::Bool(b) => Item::Bool(*b),
        LiteralValue::Null => Item::Null,
    }
}

fn eval_binary(op: &str, left: &Seq, right: &Seq) -> Result<Item, String> {
    match op {
        "=" => Ok(Item::Bool(value_equal(left, right))),
        "!=" => Ok(Item::Bool(!value_equal(left, right))),
        "+" => Ok(Item::Num(to_number(left)? + to_number(right)?)),
        "-" => Ok(Item::Num(to_number(left)? - to_number(right)?)),
        "*" => Ok(Item::Num(to_number(left)? * to_number(right)?)),
        "div" => Ok(Item::Num(to_number(left)? / to_number(right)?)),
        "mod" => Ok(Item::Num(to_number(left)? % to_number(right)?)),
        "<" => Ok(Item::Bool(to_number(left)? < to_number(right)?)),
        "<=" => Ok(Item::Bool(to_number(left)? <= to_number(right)?)),
        ">" => Ok(Item::Bool(to_number(left)? > to_number(right)?)),
        ">=" => Ok(Item::Bool(to_number(left)? >= to_number(right)?)),
        _ => Err(format!("Unknown operator {}", op)),
    }
}

fn eval_path(pe: &PathExpr, ctx: &Context) -> Result<Seq, String> {
    let mut extra_steps: Vec<PathStep> = Vec::new();

    let base: Seq = match &pe.start.kind {
        PathStartKind::Context => {
            match &ctx.context_item {
                Some(item) => vec![item.clone()],
                None => vec![],
            }
        }
        PathStartKind::Root => vec![Item::Node(ctx.root.clone())],
        PathStartKind::Desc => {
            match &ctx.context_item {
                Some(item) => vec![item.clone()],
                None => vec![],
            }
        }
        PathStartKind::DescRoot => vec![Item::Node(ctx.root.clone())],
        PathStartKind::Var => {
            let name = pe.start.name.as_deref().unwrap_or("");
            if let Some(val) = ctx.variables.get(name) {
                (**val).clone()
            } else {
                // Treat as child axis from context
                let child_step = PathStep {
                    axis: PathAxis::Child,
                    test: StepTest::named(name),
                    predicates: vec![],
                };
                extra_steps.push(child_step);
                match &ctx.context_item {
                    Some(item) => vec![item.clone()],
                    None => vec![],
                }
            }
        }
    };

    let all_steps: Vec<&PathStep> =
        extra_steps.iter().chain(pe.steps.iter()).collect();

    let mut current = base;
    for step in all_steps {
        current = apply_step(&current, step, ctx)?;
    }
    Ok(current)
}

fn apply_step(items: &Seq, step: &PathStep, ctx: &Context) -> Result<Seq, String> {
    let mut out: Seq = Vec::new();
    for item in items {
        let node = match item {
            Item::Node(n) => n.clone(),
            _ => continue,
        };

        let candidates: Vec<Rc<XmlNode>> = match step.axis {
            PathAxis::SelfAxis => vec![node.clone()],
            PathAxis::Parent => {
                // We don't track parents; skip
                continue;
            }
            PathAxis::DescOrSelf => {
                let mut v = vec![node.clone()];
                v.extend(iter_descendants(&node));
                v
            }
            PathAxis::Desc => iter_descendants(&node),
            PathAxis::Attr => {
                if node.kind == NodeKind::Element {
                    match &step.test.kind {
                        StepTestKind::Name => {
                            let name = step.test.name.as_deref().unwrap_or("");
                            if let Some((_, v)) =
                                node.attrs.iter().find(|(k, _)| k == name)
                            {
                                vec![make_attr(name, v)]
                            } else {
                                vec![]
                            }
                        }
                        StepTestKind::Wildcard => node
                            .attrs
                            .iter()
                            .map(|(k, v)| make_attr(k, v))
                            .collect(),
                        _ => vec![],
                    }
                } else {
                    vec![]
                }
            }
            PathAxis::Child => {
                if node.kind == NodeKind::Element || node.kind == NodeKind::Document {
                    node.children.clone()
                } else {
                    vec![]
                }
            }
        };

        for cand in candidates {
            if matches_test(&cand, &step.test) {
                // Apply predicates
                let item_cand = Item::Node(cand.clone());
                let pred_ctx = ctx.with_item(item_cand.clone());
                let mut ok = true;
                for pred in &step.predicates {
                    if !to_boolean(&eval_expr(pred, &pred_ctx)?) {
                        ok = false;
                        break;
                    }
                }
                if ok {
                    out.push(item_cand);
                }
            }
        }
    }
    Ok(out)
}

fn matches_test(node: &Rc<XmlNode>, test: &StepTest) -> bool {
    match test.kind {
        StepTestKind::Node => true,
        StepTestKind::Wildcard => node.kind == NodeKind::Element || node.kind == NodeKind::Attribute,
        StepTestKind::Element => node.kind == NodeKind::Element,
        StepTestKind::Text => node.kind == NodeKind::Text,
        StepTestKind::Comment => node.kind == NodeKind::Comment,
        StepTestKind::Pi => node.kind == NodeKind::Pi,
        StepTestKind::Document => node.kind == NodeKind::Document,
        StepTestKind::Name => node.name.as_deref() == test.name.as_deref(),
    }
}

fn eval_constructor(c: &Constructor, ctx: &Context) -> Result<Rc<XmlNode>, String> {
    let mut seen_attrs = std::collections::HashSet::new();
    let mut attrs = Vec::new();
    for (aname, aexpr) in &c.attrs {
        if !seen_attrs.insert(aname.clone()) {
            return Err("XFDY0005".into());
        }
        let val = eval_expr(aexpr, ctx)?;
        attrs.push((aname.clone(), to_string(&val)));
    }

    let mut children: Vec<Rc<XmlNode>> = Vec::new();
    for content in &c.contents {
        match content {
            Expr::CharData(s) => {
                if !s.trim().is_empty() {
                    children.push(make_text(s));
                }
            }
            _ => {
                let seq = eval_expr(content, ctx)?;
                for item in seq {
                    match item {
                        Item::Node(n) if n.kind == NodeKind::Attribute => {
                            if ctx.version.as_str() >= "2.2" {
                                return Err("XFDY0005".into());
                            }
                            children.push(make_text(&(n.value.clone().unwrap_or_default())));
                        }
                        Item::Node(n) => children.push(deep_copy(&n)),
                        other => children.push(make_text(&to_string(&[other]))),
                    }
                }
            }
        }
    }

    // Merge adjacent text nodes
    let mut merged: Vec<Rc<XmlNode>> = Vec::new();
    for child in children {
        if child.kind == NodeKind::Text {
            if let Some(last) = merged.last_mut() {
                if last.kind == NodeKind::Text {
                    let new_val = format!(
                        "{}{}",
                        last.value.as_deref().unwrap_or(""),
                        child.value.as_deref().unwrap_or("")
                    );
                    *last = make_text(&new_val);
                    continue;
                }
            }
        }
        merged.push(child);
    }

    Ok(make_element(&c.name, attrs, merged))
}

fn match_pattern(pat: &Pattern, item: &Item) -> Option<HashMap<String, SeqRef>> {
    match pat {
        Pattern::Wildcard => Some(HashMap::new()),
        Pattern::Literal(s) => {
            if let Item::Node(n) = item {
                if n.kind == NodeKind::Text && n.value.as_deref() == Some(s) {
                    return Some(HashMap::new());
                }
            }
            None
        }
        Pattern::Attribute(ap) => {
            if let Item::Node(n) = item {
                if n.kind == NodeKind::Attribute && n.name.as_deref() == Some(&ap.name) {
                    if let Some(ref lit) = ap.value {
                        let expected = match lit {
                            LiteralValue::Str(v) => v.as_str(),
                            _ => return None,
                        };
                        if n.value.as_deref() == Some(expected) {
                            return Some(HashMap::new());
                        }
                        return None;
                    }
                    return Some(HashMap::new());
                }
            }
            None
        }
        Pattern::Typed(kind) => {
            if let Item::Node(n) = item {
                let matches = match kind.as_str() {
                    "node" => true,
                    "element" => n.kind == NodeKind::Element,
                    "text" => n.kind == NodeKind::Text,
                    "comment" => n.kind == NodeKind::Comment,
                    "pi" => n.kind == NodeKind::Pi,
                    "document" => n.kind == NodeKind::Document,
                    _ => false,
                };
                if matches {
                    return Some(HashMap::new());
                }
            }
            None
        }
        Pattern::Element(ep) => {
            if let Item::Node(n) = item {
                if n.kind == NodeKind::Element && n.name.as_deref() == Some(&ep.name) {
                    // Check attribute constraints
                    for (attr_name, attr_value) in &ep.attrs {
                        let found = n.attrs.iter().find(|(k, _)| k == attr_name);
                        if let Some((_, v)) = found {
                            if let Some(ref lit) = attr_value {
                                let expected = match lit {
                                    LiteralValue::Str(s) => s.as_str(),
                                    _ => return None,
                                };
                                if v != expected {
                                    return None;
                                }
                            }
                        } else {
                            return None;
                        }
                    }
                    let mut bindings = HashMap::new();
                    if let Some(var) = &ep.var {
                        let seq: Seq = n.children.iter().map(|c| Item::Node(c.clone())).collect();
                        bindings.insert(var.clone(), Rc::new(seq));
                        return Some(bindings);
                    }
                    if !ep.children.is_empty() {
                        if n.children.len() != ep.children.len() {
                            return None;
                        }
                        for (child_pat, child_node) in ep.children.iter().zip(n.children.iter()) {
                            if let Some(b) = match_pattern(child_pat, &Item::Node(child_node.clone())) {
                                bindings.extend(b);
                            } else {
                                return None;
                            }
                        }
                        return Some(bindings);
                    }
                    return Some(bindings);
                }
            }
            None
        }
    }
}

fn _do_apply(seq: Seq, ruleset: &str, ctx: &Context) -> Result<Seq, String> {
    if ctx.recursion_depth >= MAX_RECURSION_DEPTH {
        return Err("XFDY0099".into());
    }
    if ruleset != "main" && !ctx.rules.contains_key(ruleset) {
        return Err("XFST0007".into());
    }
    let rules = ctx.rules.get(ruleset).cloned().unwrap_or_default();
    let mut out = Vec::new();
    for item in seq {
        let mut matched = false;
        for rule in &rules {
            if let Some(bindings) = match_pattern(&rule.pattern, &item) {
                matched = true;
                let mut vars = ctx.variables.clone();
                vars.extend(bindings);
                let new_ctx = Context {
                    context_item: Some(item.clone()),
                    variables: vars,
                    recursion_depth: ctx.recursion_depth + 1,
                    ..ctx.clone()
                };
                out.extend(eval_expr(&rule.body, &new_ctx)?);
                break;
            }
        }
        if !matched {
            out.extend(_apply_builtin(&item, ruleset, ctx)?);
        }
    }
    Ok(out)
}

fn _apply_builtin(item: &Item, ruleset: &str, ctx: &Context) -> Result<Seq, String> {
    let node = match item {
        Item::Node(n) => n.clone(),
        _ => return Ok(vec![]),
    };
    match node.kind {
        NodeKind::Document => {
            let children: Seq = node.children.iter().map(|c| Item::Node(c.clone())).collect();
            _do_apply(children, ruleset, ctx)
        }
        NodeKind::Element => {
            let children_seq: Seq = node.children.iter().map(|c| Item::Node(c.clone())).collect();
            let applied = _do_apply(children_seq, ruleset, ctx)?;
            let mut new_children: Vec<Rc<XmlNode>> = Vec::new();
            for c in applied {
                if let Item::Node(n) = c {
                    new_children.push(n);
                }
            }
            Ok(vec![Item::Node(Rc::new(XmlNode {
                kind: NodeKind::Element,
                name: node.name.clone(),
                value: None,
                attrs: node.attrs.clone(),
                children: new_children,
            }))])
        }
        NodeKind::Attribute | NodeKind::Text | NodeKind::Comment | NodeKind::Pi => {
            Ok(vec![Item::Node(deep_copy(&node))])
        }
    }
}

// ── Built-in functions ───────────────────────────────────────────────────────

fn call_function(
    name: &str,
    args: Vec<Seq>,
    ctx: &Context,
    named: HashMap<String, Seq>,
    named_raw: HashMap<String, Expr>,
) -> Result<Seq, String> {
    // User-defined function?
    if let Some(fd) = ctx.functions.get(name) {
        if ctx.recursion_depth >= MAX_RECURSION_DEPTH {
            return Err("XFDY0099".into());
        }
        let fd = fd.clone();
        if args.len() > fd.params.len() {
            return Err(format!("XFDY0008: too many arguments for {}", name));
        }
        let mut vars = ctx.variables.clone();
        let mut bound = std::collections::HashSet::new();
        for (i, param) in fd.params.iter().enumerate() {
            if i < args.len() {
                vars.insert(param.name.clone(), Rc::new(args[i].clone()));
                bound.insert(param.name.clone());
            }
        }
        for (param_name, value) in &named {
            if bound.contains(param_name) {
                return Err("XFDY0008: duplicate argument".into());
            }
            let matching = fd.params.iter().find(|p| &p.name == param_name);
            if matching.is_none() {
                return Err("XFDY0008: unknown parameter".into());
            }
            vars.insert(param_name.clone(), Rc::new(value.clone()));
            bound.insert(param_name.clone());
        }
        for param in &fd.params {
            if !bound.contains(&param.name) {
                let new_ctx = Context { variables: vars.clone(), ..ctx.clone() };
                if let Some(def) = &param.default {
                    let val = eval_expr(def, &new_ctx)?;
                    vars.insert(param.name.clone(), Rc::new(val));
                    bound.insert(param.name.clone());
                } else {
                    return Err("XFDY0008: missing required parameter".into());
                }
            }
        }
        let new_ctx = Context {
            variables: vars,
            recursion_depth: ctx.recursion_depth + 1,
            ..ctx.clone()
        };
        return eval_expr(&fd.body, &new_ctx);
    }

    match name {
        "string" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(vec![Item::Str(to_string(&seq))])
        }
        "number" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(vec![Item::Num(to_number_loose(&seq))])
        }
        "boolean" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(vec![Item::Bool(to_boolean(&seq))])
        }
        "typeOf" => {
            let seq = args.into_iter().next().unwrap_or_default();
            let t = match seq.first() {
                None => "null",
                Some(Item::Node(_)) => "node",
                Some(Item::Map(_)) => "map",
                Some(Item::Bool(_)) => "boolean",
                Some(Item::Num(_)) => "number",
                Some(Item::Null) => "null",
                Some(Item::Str(_)) => "string",
                Some(Item::FuncRef(_)) => "function",
            };
            Ok(vec![Item::Str(t.to_string())])
        }
        "name" => {
            let seq = args.into_iter().next().unwrap_or_default();
            match seq.first() {
                Some(Item::Node(n)) => {
                    let s = n.name.clone().unwrap_or_default();
                    Ok(vec![Item::Str(s)])
                }
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![Item::Str(String::new())]),
            }
        }
        "attr" => {
            let mut it = args.into_iter();
            let node_seq = it.next().unwrap_or_default();
            let key_seq = it.next().unwrap_or_default();
            let key = to_string(&key_seq);
            match node_seq.first() {
                Some(Item::Node(n)) if n.kind == NodeKind::Element => {
                    let v = n
                        .attrs
                        .iter()
                        .find(|(k, _)| k == &key)
                        .map(|(_, v)| v.clone())
                        .unwrap_or_default();
                    Ok(vec![Item::Str(v)])
                }
                Some(Item::Node(_)) => Ok(vec![Item::Str(String::new())]),
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![Item::Str(String::new())]),
            }
        }
        "text" => {
            let mut it = args.into_iter();
            let node_seq = it.next().unwrap_or_default();
            let deep_seq = it.next();
            let deep = if let Some(ds) = deep_seq {
                to_boolean(&ds)
            } else if let Some(ds) = named.get("deep") {
                to_boolean(ds)
            } else {
                true
            };
            match node_seq.first() {
                Some(Item::Node(n)) => {
                    let s = if deep {
                        n.string_value()
                    } else {
                        if n.kind == NodeKind::Element || n.kind == NodeKind::Document {
                            n.children
                                .iter()
                                .filter(|c| c.kind == NodeKind::Text)
                                .map(|c| c.value.clone().unwrap_or_default())
                                .collect::<Vec<_>>()
                                .join("")
                        } else {
                            n.string_value()
                        }
                    };
                    Ok(vec![Item::Str(s)])
                }
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![Item::Str(String::new())]),
            }
        }
        "children" => {
            let seq = args.into_iter().next().unwrap_or_default();
            match seq.first() {
                Some(Item::Node(n)) => {
                    Ok(n.children.iter().map(|c| Item::Node(c.clone())).collect())
                }
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![]),
            }
        }
        "elements" => {
            let mut it = args.into_iter();
            let node_seq = it.next().unwrap_or_default();
            let name_seq = it.next();
            match node_seq.first() {
                Some(Item::Node(n))
                    if n.kind == NodeKind::Element || n.kind == NodeKind::Document =>
                {
                    let name_filter = name_seq.as_ref().map(|s| to_string(s));
                    let out: Seq = n
                        .children
                        .iter()
                        .filter(|c| {
                            c.kind == NodeKind::Element
                                && name_filter
                                    .as_ref()
                                    .map_or(true, |nf| nf.is_empty() || c.name.as_deref() == Some(nf))
                        })
                        .map(|c| Item::Node(c.clone()))
                        .collect();
                    Ok(out)
                }
                Some(Item::Node(_)) => Ok(vec![]),
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![]),
            }
        }
        "attributes" => {
            let seq = args.into_iter().next().unwrap_or_default();
            match seq.first() {
                Some(Item::Node(n)) if n.kind == NodeKind::Element => {
                    let out: Seq = n.attrs.iter()
                        .map(|(k, v)| Item::Node(make_attr(k, v)))
                        .collect();
                    Ok(out)
                }
                Some(Item::Node(_)) => Ok(vec![]),
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![]),
            }
        }
        "copy" => {
            let mut it = args.into_iter();
            let node_seq = it.next().unwrap_or_default();
            match node_seq.first() {
                Some(Item::Node(n)) => {
                    let recurse = if let Some(rs) = it.next() {
                        to_boolean(&rs)
                    } else if let Some(rs) = named.get("recurse") {
                        to_boolean(rs)
                    } else {
                        true
                    };
                    Ok(vec![Item::Node(deep_copy_recurse(n, recurse))])
                }
                Some(_) => Err("XFDY0003".into()),
                None => Ok(vec![]),
            }
        }
        "count" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(vec![Item::Num(seq.len() as f64)])
        }
        "empty" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(vec![Item::Bool(seq.is_empty())])
        }
        "distinct" => {
            let seq = args.into_iter().next().unwrap_or_default();
            let mut seen = std::collections::HashSet::new();
            let out: Seq = seq
                .into_iter()
                .filter(|item| seen.insert(to_string(&[item.clone()])))
                .collect();
            Ok(out)
        }
        "sort" => {
            let mut it = args.into_iter();
            let seq = it.next().unwrap_or_default();
            let key_seq = it.next();
            let key_fn = key_seq.as_ref().and_then(|s| match s.first() {
                Some(Item::FuncRef(n)) => Some(n.clone()),
                _ => None,
            });
            let mut keyed: Vec<(String, Item)> = seq
                .iter()
                .map(|item| {
                    let key = if let Some(ref kf) = key_fn {
                        call_function(kf, vec![vec![item.clone()]], ctx, HashMap::new(), HashMap::new())
                            .map(|s| to_string(&s))
                            .unwrap_or_default()
                    } else {
                        to_string(&[item.clone()])
                    };
                    (key, item.clone())
                })
                .collect();
            keyed.sort_by(|a, b| a.0.cmp(&b.0));
            Ok(keyed.into_iter().map(|(_, v)| v).collect())
        }
        "concat" | "seq" => {
            let mut out = Vec::new();
            for seq in args {
                out.extend(seq);
            }
            Ok(out)
        }
        "head" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(seq.into_iter().take(1).collect())
        }
        "tail" => {
            let seq = args.into_iter().next().unwrap_or_default();
            Ok(seq.into_iter().skip(1).collect())
        }
        "last" => {
            let seq = args.into_iter().next().unwrap_or_default();
            if seq.is_empty() {
                if let Some(l) = ctx.last {
                    return Ok(vec![Item::Num(l)]);
                }
                return Err("XFDY0003".into());
            }
            Ok(vec![seq.into_iter().last().unwrap()])
        }
        "position" => {
            match ctx.position {
                Some(p) => Ok(vec![Item::Num(p)]),
                None => Err("XFDY0003".into()),
            }
        }
        "index" => {
            let seq = args.get(0).cloned().unwrap_or_default();
            let key_fn = args.get(1).and_then(|s| match s.first() {
                Some(Item::FuncRef(n)) => Some(n.clone()),
                _ => None,
            });
            let key_expr = named_raw.get("key").cloned();
            let mut map: XMap = HashMap::new();
            for item in seq {
                let key = if let Some(ref kf) = key_fn {
                    to_string(&call_function(kf, vec![vec![item.clone()]], ctx, HashMap::new(), HashMap::new())?)
                } else if let Some(ref ke) = key_expr {
                    let item_ctx = Context {
                        context_item: Some(item.clone()),
                        ..ctx.clone()
                    };
                    to_string(&eval_expr(ke, &item_ctx)?)
                } else {
                    to_string(&[item.clone()])
                };
                map.entry(key).or_default().push(item);
            }
            Ok(vec![Item::Map(Rc::new(map))])
        }
        "lookup" => {
            let mut it = args.into_iter();
            let map_seq = it.next().unwrap_or_default();
            let key_seq = it.next().unwrap_or_default();
            let key = to_string(&key_seq);
            match map_seq.first() {
                Some(Item::Map(m)) => Ok(m.get(&key).cloned().unwrap_or_default()),
                _ => Ok(vec![]),
            }
        }
        "groupBy" => {
            let mut it = args.into_iter();
            let seq = it.next().unwrap_or_default();
            let key_seq = it.next();
            let key_fn = key_seq.as_ref().and_then(|s| match s.first() {
                Some(Item::FuncRef(n)) => Some(n.clone()),
                _ => None,
            });
            let mut order: Vec<String> = Vec::new();
            let mut groups: HashMap<String, Seq> = HashMap::new();
            for item in seq {
                let key = if let Some(ref kf) = key_fn {
                    to_string(&call_function(kf, vec![vec![item.clone()]], ctx, HashMap::new(), HashMap::new())?)
                } else {
                    to_string(&[item.clone()])
                };
                if !groups.contains_key(&key) {
                    order.push(key.clone());
                }
                groups.entry(key).or_default().push(item);
            }
            let out: Seq = order
                .into_iter()
                .map(|k| {
                    let items = groups.remove(&k).unwrap_or_default();
                    let mut m: XMap = HashMap::new();
                    m.insert("key".into(), vec![Item::Str(k)]);
                    m.insert("items".into(), items);
                    Item::Map(Rc::new(m))
                })
                .collect();
            Ok(out)
        }
        "sum" => {
            let seq = args.into_iter().next().unwrap_or_default();
            let mut total = 0.0f64;
            for item in seq {
                total += to_number(&[item])?;
            }
            Ok(vec![Item::Num(total)])
        }
        "apply" => {
            let seq = args.get(0).cloned().unwrap_or_default();
            let ruleset = args.get(1).map(|s| to_string(s)).filter(|s| !s.is_empty()).unwrap_or_else(|| "main".into());
            _do_apply(seq, &ruleset, ctx)
        }
        "contains" => {
            let mut it = args.into_iter();
            let a = to_string(&it.next().unwrap_or_default());
            let b = to_string(&it.next().unwrap_or_default());
            Ok(vec![Item::Bool(a.contains(&b))])
        }
        "startsWith" => {
            let mut it = args.into_iter();
            let a = to_string(&it.next().unwrap_or_default());
            let b = to_string(&it.next().unwrap_or_default());
            Ok(vec![Item::Bool(a.starts_with(&b))])
        }
        "endsWith" => {
            let mut it = args.into_iter();
            let a = to_string(&it.next().unwrap_or_default());
            let b = to_string(&it.next().unwrap_or_default());
            Ok(vec![Item::Bool(a.ends_with(&b))])
        }
        "substring" => {
            let mut it = args.into_iter();
            let s = to_string(&it.next().unwrap_or_default());
            let start_seq = it.next();
            if start_seq.is_none() {
                return Ok(vec![Item::Str("".into())]);
            }
            let start = to_number(&start_seq.unwrap())? as usize;
            let length_seq = it.next();
            let result = if let Some(ls) = length_seq {
                let length = to_number(&ls)? as usize;
                s.chars().skip(start.saturating_sub(1)).take(length).collect::<String>()
            } else {
                s.chars().skip(start.saturating_sub(1)).collect::<String>()
            };
            Ok(vec![Item::Str(result)])
        }
        "normalizeSpace" => {
            let s = to_string(&args.into_iter().next().unwrap_or_default());
            let normalized: String = s.split_whitespace().collect::<Vec<_>>().join(" ");
            Ok(vec![Item::Str(normalized)])
        }
        "replace" => {
            let mut it = args.into_iter();
            let s = to_string(&it.next().unwrap_or_default());
            let pattern = to_string(&it.next().unwrap_or_default());
            let replacement = to_string(&it.next().unwrap_or_default());
            Ok(vec![Item::Str(s.replace(&pattern, &replacement))])
        }
        "stringLength" => {
            let s = to_string(&args.into_iter().next().unwrap_or_default());
            Ok(vec![Item::Num(s.chars().count() as f64)])
        }
        "upperCase" => {
            let s = to_string(&args.into_iter().next().unwrap_or_default());
            Ok(vec![Item::Str(s.to_uppercase())])
        }
        "lowerCase" => {
            let s = to_string(&args.into_iter().next().unwrap_or_default());
            Ok(vec![Item::Str(s.to_lowercase())])
        }
        "matches" => {
            let mut it = args.into_iter();
            let s = to_string(&it.next().unwrap_or_default());
            let pattern = to_string(&it.next().unwrap_or_default());
            Ok(vec![Item::Bool(s.contains(&pattern))])
        }
        "keys" => {
            let seq = args.into_iter().next().unwrap_or_default();
            match seq.first() {
                Some(Item::Map(m)) => {
                    let mut keys: Vec<String> = m.keys().cloned().collect();
                    keys.sort();
                    Ok(keys.into_iter().map(Item::Str).collect())
                }
                _ => Ok(vec![]),
            }
        }
        "mapSize" => {
            let seq = args.into_iter().next().unwrap_or_default();
            match seq.first() {
                Some(Item::Map(m)) => Ok(vec![Item::Num(m.len() as f64)]),
                _ => Ok(vec![Item::Num(0.0)]),
            }
        }
        _ => Err(format!("XFST0003: unknown function {}", name)),
    }
}

// ── Coercions ────────────────────────────────────────────────────────────────

pub fn to_boolean(seq: &[Item]) -> bool {
    if seq.is_empty() {
        return false;
    }
    if seq.iter().any(|i| matches!(i, Item::Node(_))) {
        return true;
    }
    seq.iter().any(|item| match item {
        Item::Bool(b) => *b,
        Item::Num(n) => *n != 0.0,
        Item::Str(s) => !s.is_empty(),
        Item::Null => false,
        Item::Map(_) | Item::FuncRef(_) => true,
        Item::Node(_) => true,
    })
}

pub fn to_string(seq: &[Item]) -> String {
    match seq.first() {
        None => String::new(),
        Some(Item::Node(n)) => n.string_value(),
        Some(Item::Null) => String::new(),
        Some(Item::Bool(b)) => if *b { "true".into() } else { "false".into() },
        Some(Item::Num(n)) => fmt_num(*n),
        Some(Item::Str(s)) => s.clone(),
        Some(Item::Map(_)) => "[map]".into(),
        Some(Item::FuncRef(s)) => s.clone(),
    }
}

pub fn to_number(seq: &[Item]) -> Result<f64, String> {
    match seq.first() {
        None => Ok(0.0),
        Some(Item::Num(n)) => Ok(*n),
        Some(Item::Bool(b)) => Ok(if *b { 1.0 } else { 0.0 }),
        Some(Item::Str(s)) => {
            s.trim().parse::<f64>().map_err(|_| format!("XFDY0002: cannot convert {:?} to number", s))
        }
        Some(Item::Node(n)) => {
            let sv = n.string_value();
            sv.trim().parse::<f64>().map_err(|_| format!("XFDY0002: cannot convert {:?} to number", sv))
        }
        Some(Item::Null) => Ok(0.0),
        _ => Err("XFDY0002: number conversion error".into()),
    }
}

pub fn to_number_loose(seq: &[Item]) -> f64 {
    match seq.first() {
        None => f64::NAN,
        Some(Item::Num(n)) => *n,
        Some(Item::Bool(b)) => if *b { 1.0 } else { 0.0 },
        Some(Item::Str(s)) => s.trim().parse::<f64>().unwrap_or(f64::NAN),
        Some(Item::Node(n)) => n.string_value().trim().parse::<f64>().unwrap_or(f64::NAN),
        Some(Item::Null) => f64::NAN,
        _ => f64::NAN,
    }
}

fn value_equal(left: &[Item], right: &[Item]) -> bool {
    to_string(left) == to_string(right)
}

pub fn fmt_num(n: f64) -> String {
    if n.fract() == 0.0 && n.is_finite() && n.abs() < 1e15 {
        format!("{}", n as i64)
    } else {
        format!("{}", n)
    }
}

pub fn serialize_items(items: &Seq) -> String {
    items
        .iter()
        .map(|item| match item {
            Item::Node(n) => serialize(n),
            Item::Str(s) => s.clone(),
            Item::Num(n) => fmt_num(*n),
            Item::Bool(b) => if *b { "true".into() } else { "false".into() },
            Item::Null => String::new(),
            Item::Map(_) => String::new(),
            Item::FuncRef(_) => String::new(),
        })
        .collect()
}
