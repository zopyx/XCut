package xform

import (
	"fmt"
	"math"
	"sort"
	"strconv"
)

type Context struct {
	ContextItem any
	Variables   map[string][]any
	Functions   map[string]FunctionDef
	Rules       map[string][]RuleDef
	Position    *int
	Last        *int
}

func EvalModule(module *Module, doc *Node) []any {
	functions := map[string]FunctionDef{}
	for k, v := range module.Functions {
		functions[k] = v
	}
	rules := map[string][]RuleDef{}
	for k, v := range module.Rules {
		rules[k] = v
	}
	variables := map[string][]any{}
	ctx := Context{ContextItem: doc, Variables: variables, Functions: functions, Rules: rules}
	for name, expr := range module.Vars {
		variables[name] = EvalExpr(expr, ctx)
	}
	if module.Expr == nil {
		return []any{}
	}
	return EvalExpr(module.Expr, ctx)
}

func EvalExpr(expr Expr, ctx Context) []any {
	switch e := expr.(type) {
	case Literal:
		return []any{e.Value}
	case VarRef:
		if v, ok := ctx.Variables[e.Name]; ok {
			return v
		}
		if _, ok := ctx.Functions[e.Name]; ok {
			return []any{FunctionRef{Name: e.Name}}
		}
		if node, ok := ctx.ContextItem.(*Node); ok {
			out := []any{}
			for _, child := range node.Children {
				if child.Kind == "element" && child.Name == e.Name {
					out = append(out, child)
				}
			}
			return out
		}
		return []any{}
	case IfExpr:
		cond := ToBoolean(EvalExpr(e.Cond, ctx))
		if cond {
			return EvalExpr(e.ThenExpr, ctx)
		}
		return EvalExpr(e.ElseExpr, ctx)
	case LetExpr:
		value := EvalExpr(e.Value, ctx)
		newVars := copyVars(ctx.Variables)
		newVars[e.Name] = value
		newCtx := Context{ContextItem: ctx.ContextItem, Variables: newVars, Functions: ctx.Functions, Rules: ctx.Rules, Position: ctx.Position, Last: ctx.Last}
		return EvalExpr(e.Body, newCtx)
	case ForExpr:
		seq := EvalExpr(e.Seq, ctx)
		out := []any{}
		total := len(seq)
		for idx, item := range seq {
			newVars := copyVars(ctx.Variables)
			newVars[e.Name] = []any{item}
			pos := idx + 1
			last := total
			newCtx := Context{ContextItem: item, Variables: newVars, Functions: ctx.Functions, Rules: ctx.Rules, Position: &pos, Last: &last}
			if e.Where != nil {
				if !ToBoolean(EvalExpr(e.Where, newCtx)) {
					continue
				}
			}
			out = append(out, EvalExpr(e.Body, newCtx)...)
		}
		return out
	case MatchExpr:
		targetSeq := EvalExpr(e.Target, ctx)
		out := []any{}
		for _, target := range targetSeq {
			matchedAny := false
			for _, c := range e.Cases {
				matched, bindings := MatchPattern(c.Pattern, target)
				if matched {
					matchedAny = true
					newVars := copyVars(ctx.Variables)
					for k, v := range bindings {
						newVars[k] = v
					}
					newCtx := Context{ContextItem: target, Variables: newVars, Functions: ctx.Functions, Rules: ctx.Rules, Position: ctx.Position, Last: ctx.Last}
					out = append(out, EvalExpr(c.Expr, newCtx)...)
					break
				}
			}
			if !matchedAny {
				if e.Default == nil {
					panic(fmt.Errorf("XFDY0001: no matching case"))
				}
				newCtx := Context{ContextItem: target, Variables: copyVars(ctx.Variables), Functions: ctx.Functions, Rules: ctx.Rules, Position: ctx.Position, Last: ctx.Last}
				out = append(out, EvalExpr(e.Default, newCtx)...)
			}
		}
		return out
	case FuncCall:
		args := [][]any{}
		for _, a := range e.Args {
			args = append(args, EvalExpr(a, ctx))
		}
		return CallFunction(e.Name, args, ctx)
	case UnaryOp:
		val := EvalExpr(e.Expr, ctx)
		if e.Op == "-" {
			return []any{-ToNumber(val)}
		}
		if e.Op == "not" {
			return []any{!ToBoolean(val)}
		}
	case BinaryOp:
		if e.Op == "and" {
			left := EvalExpr(e.Left, ctx)
			if !ToBoolean(left) {
				return []any{false}
			}
			right := EvalExpr(e.Right, ctx)
			return []any{ToBoolean(right)}
		}
		if e.Op == "or" {
			left := EvalExpr(e.Left, ctx)
			if ToBoolean(left) {
				return []any{true}
			}
			right := EvalExpr(e.Right, ctx)
			return []any{ToBoolean(right)}
		}
		left := EvalExpr(e.Left, ctx)
		right := EvalExpr(e.Right, ctx)
		return []any{EvalBinary(e.Op, left, right)}
	case PathExpr:
		return EvalPath(e, ctx)
	case Constructor:
		return []any{EvalConstructor(e, ctx)}
	case TextConstructor:
		return []any{&Node{Kind: "text", Value: ToString(EvalExpr(e.Expr, ctx)), Attrs: map[string]string{}}}
	case Text:
		return []any{e.Value}
	case Interp:
		return EvalExpr(e.Expr, ctx)
	}
	panic(fmt.Errorf("unknown expr"))
}

func EvalBinary(op string, left []any, right []any) any {
	if op == "and" {
		return ToBoolean(left) && ToBoolean(right)
	}
	if op == "or" {
		return ToBoolean(left) || ToBoolean(right)
	}
	if op == "=" {
		return ValueEqual(left, right)
	}
	if op == "!=" {
		return !ValueEqual(left, right)
	}
	lnum := ToNumber(left)
	rnum := ToNumber(right)
	switch op {
	case "+":
		return lnum + rnum
	case "-":
		return lnum - rnum
	case "*":
		return lnum * rnum
	case "div":
		return lnum / rnum
	case "mod":
		return math.Mod(lnum, rnum)
	case "<":
		return lnum < rnum
	case "<=":
		return lnum <= rnum
	case ">":
		return lnum > rnum
	case ">=":
		return lnum >= rnum
	}
	panic(fmt.Errorf("unknown operator %s", op))
}

func EvalPath(expr PathExpr, ctx Context) []any {
	steps := expr.Steps
	base := []any{}
	switch expr.Start.Kind {
	case "context":
		if ctx.ContextItem != nil {
			base = []any{ctx.ContextItem}
		}
	case "root":
		base = rootOf(ctx.ContextItem)
	case "desc":
		if ctx.ContextItem != nil {
			base = []any{ctx.ContextItem}
		}
	case "desc_root":
		base = rootOf(ctx.ContextItem)
	case "var":
		if expr.Start.Name != nil {
			if v, ok := ctx.Variables[*expr.Start.Name]; ok {
				base = v
			} else {
				if ctx.ContextItem != nil {
					base = []any{ctx.ContextItem}
					steps = append([]PathStep{{Axis: "child", Test: StepTest{Kind: "name", Name: expr.Start.Name}, Predicates: []Expr{}}}, steps...)
				}
			}
		}
	}
	current := base
	for _, step := range steps {
		current = ApplyStep(current, step, ctx)
	}
	return current
}

func rootOf(item any) []any {
	if node, ok := item.(*Node); ok {
		cur := node
		for cur.Parent != nil {
			cur = cur.Parent
		}
		return []any{cur}
	}
	return []any{}
}

func ApplyStep(items []any, step PathStep, ctx Context) []any {
	out := []any{}
	for _, item := range items {
		node, ok := item.(*Node)
		if !ok {
			continue
		}
		candidates := []*Node{}
		switch step.Axis {
		case "self":
			candidates = []*Node{node}
		case "parent":
			if node.Parent != nil {
				candidates = []*Node{node.Parent}
			}
		case "desc_or_self":
			candidates = append(candidates, node)
			candidates = append(candidates, IterDescendants(node)...)
		case "desc":
			candidates = append(candidates, IterDescendants(node)...)
		case "attr":
			if node.Kind == "element" {
				if step.Test.Kind == "name" && step.Test.Name != nil {
					name := *step.Test.Name
					if val, ok := node.Attrs[name]; ok {
						candidates = []*Node{{Kind: "attribute", Name: name, Value: val, Attrs: map[string]string{}}}
					}
				} else if step.Test.Kind == "wildcard" {
					for k, v := range node.Attrs {
						candidates = append(candidates, &Node{Kind: "attribute", Name: k, Value: v, Attrs: map[string]string{}})
					}
				}
			}
		case "child":
			candidates = append(candidates, node.Children...)
		}

		filtered := []*Node{}
		for _, c := range candidates {
			if matchesStepTest(step.Test, c) {
				filtered = append(filtered, c)
			}
		}
		for _, pred := range step.Predicates {
			predOut := []*Node{}
			for i, child := range filtered {
				pos := i + 1
				last := len(filtered)
				predCtx := Context{ContextItem: child, Variables: ctx.Variables, Functions: ctx.Functions, Rules: ctx.Rules, Position: &pos, Last: &last}
				if ToBoolean(EvalExpr(pred, predCtx)) {
					predOut = append(predOut, child)
				}
			}
			filtered = predOut
		}
		for _, c := range filtered {
			out = append(out, c)
		}
	}
	return out
}

func matchesStepTest(test StepTest, node *Node) bool {
	switch test.Kind {
	case "wildcard":
		return node.Kind == "element"
	case "text":
		return node.Kind == "text"
	case "node":
		return true
	case "comment":
		return node.Kind == "comment"
	case "pi":
		return node.Kind == "pi"
	case "name":
		if test.Name == nil {
			return false
		}
		return node.Name == *test.Name
	}
	return false
}

func EvalConstructor(expr Constructor, ctx Context) *Node {
	order := make([]string, 0, len(expr.Attrs))
	node := &Node{Kind: "element", Name: expr.Name, Attrs: map[string]string{}, AttrOrder: order}
	for _, attr := range expr.Attrs {
		val := EvalExpr(attr.Expr, ctx)
		node.Attrs[attr.Name] = ToString(val)
		node.AttrOrder = append(node.AttrOrder, attr.Name)
	}
	children := []*Node{}
	for _, content := range expr.Contents {
		switch c := content.(type) {
		case Text:
			children = append(children, &Node{Kind: "text", Value: c.Value, Attrs: map[string]string{}})
		default:
			seq := EvalExpr(content, ctx)
			for _, item := range seq {
				if n, ok := item.(*Node); ok {
					child := DeepCopy(n, true)
					children = append(children, child)
				} else {
					children = append(children, &Node{Kind: "text", Value: ToString([]any{item}), Attrs: map[string]string{}})
				}
			}
		}
	}
	for _, c := range children {
		c.Parent = node
	}
	node.Children = children
	return node
}

type FunctionRef struct{ Name string }

func CallFunction(name string, args [][]any, ctx Context) []any {
	if fn, ok := ctx.Functions[name]; ok {
		return callUserFunction(fn, args, ctx)
	}

	builtin, ok := builtins[name]
	if !ok {
		panic(fmt.Errorf("XFST0003: unknown function %s", name))
	}
	return builtin(args, ctx)
}

func callUserFunction(fn FunctionDef, args [][]any, ctx Context) []any {
	params := fn.Params
	if len(args) > len(params) {
		panic(fmt.Errorf("XFDY0002: wrong arity"))
	}
	newVars := copyVars(ctx.Variables)
	for i, v := range args {
		newVars[params[i].Name] = v
	}
	if len(args) < len(params) {
		for i := len(args); i < len(params); i++ {
			param := params[i]
			if param.Default == nil {
				panic(fmt.Errorf("XFDY0002: wrong arity"))
			}
			newVars[param.Name] = EvalExpr(param.Default, ctx)
		}
	}
	newCtx := Context{ContextItem: ctx.ContextItem, Variables: newVars, Functions: ctx.Functions, Rules: ctx.Rules, Position: ctx.Position, Last: ctx.Last}
	return EvalExpr(fn.Body, newCtx)
}

func ToBoolean(seq []any) bool {
	if len(seq) == 0 {
		return false
	}
	for _, item := range seq {
		if _, ok := item.(*Node); ok {
			return true
		}
	}
	for _, item := range seq {
		switch v := item.(type) {
		case bool:
			if v {
				return true
			}
		case int:
			if v != 0 {
				return true
			}
		case float64:
			if v != 0.0 {
				return true
			}
		case string:
			if v != "" {
				return true
			}
		default:
			if item != nil {
				return true
			}
		}
	}
	return false
}

func ToString(seq []any) string {
	if len(seq) == 0 {
		return ""
	}
	item := seq[0]
	if node, ok := item.(*Node); ok {
		return node.StringValue()
	}
	if item == nil {
		return ""
	}
	switch v := item.(type) {
	case bool:
		if v {
			return "true"
		}
		return "false"
	case float64:
		if v == float64(int64(v)) {
			return fmt.Sprintf("%d", int64(v))
		}
		return fmt.Sprintf("%v", v)
	case int:
		return fmt.Sprintf("%d", v)
	default:
		return fmt.Sprintf("%v", v)
	}
}

func ToNumber(seq []any) float64 {
	if len(seq) == 0 {
		return 0.0
	}
	item := seq[0]
	if node, ok := item.(*Node); ok {
		item = node.StringValue()
	}
	switch v := item.(type) {
	case bool:
		if v {
			return 1.0
		}
		return 0.0
	case int:
		return float64(v)
	case float64:
		return v
	case string:
		f, err := strconv.ParseFloat(v, 64)
		if err != nil {
			panic(fmt.Errorf("XFDY0002: number conversion"))
		}
		return f
	default:
		panic(fmt.Errorf("XFDY0002: number conversion"))
	}
}

func ValueEqual(left []any, right []any) bool {
	return ToString(left) == ToString(right)
}

func MatchPattern(pattern Pattern, item any) (bool, map[string][]any) {
	switch p := pattern.(type) {
	case WildcardPattern:
		return true, map[string][]any{}
	case AttributePattern:
		if node, ok := item.(*Node); ok && node.Kind == "attribute" && node.Name == p.Name {
			return true, map[string][]any{}
		}
		return false, map[string][]any{}
	case TypedPattern:
		if item == nil {
			return false, map[string][]any{}
		}
		node, ok := item.(*Node)
		if p.Kind == "node" {
			return ok, map[string][]any{}
		}
		if p.Kind == "text" {
			return ok && node.Kind == "text", map[string][]any{}
		}
		if p.Kind == "comment" {
			return ok && node.Kind == "comment", map[string][]any{}
		}
		return false, map[string][]any{}
	case ElementPattern:
		if node, ok := item.(*Node); ok && node.Kind == "element" && node.Name == p.Name {
			bindings := map[string][]any{}
			if p.Var != nil {
				children := []any{}
				for _, c := range node.Children {
					children = append(children, c)
				}
				bindings[*p.Var] = children
				return true, bindings
			}
			if p.Child != nil {
				for _, child := range node.Children {
					matched, childBindings := MatchPattern(p.Child, child)
					if matched {
						for k, v := range childBindings {
							bindings[k] = v
						}
						return true, bindings
					}
				}
				return false, map[string][]any{}
			}
			return true, map[string][]any{}
		}
		return false, map[string][]any{}
	}
	return false, map[string][]any{}
}

// Builtins

type builtinFn func(args [][]any, ctx Context) []any

func fnString(args [][]any, _ Context) []any  { return []any{ToString(firstOrEmpty(args))} }
func fnNumber(args [][]any, _ Context) []any  { return []any{ToNumber(firstOrEmpty(args))} }
func fnBoolean(args [][]any, _ Context) []any { return []any{ToBoolean(firstOrEmpty(args))} }

func fnTypeOf(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{"null"}
	}
	item := args[0][0]
	if _, ok := item.(*Node); ok {
		return []any{"node"}
	}
	if _, ok := item.(map[string][]any); ok {
		return []any{"map"}
	}
	switch item.(type) {
	case bool:
		return []any{"boolean"}
	case int, float64:
		return []any{"number"}
	case nil:
		return []any{"null"}
	default:
		return []any{"string"}
	}
}

func fnName(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{""}
	}
	if node, ok := args[0][0].(*Node); ok {
		return []any{node.Name}
	}
	return []any{""}
}

func fnAttr(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{""}
	}
	node, ok := args[0][0].(*Node)
	if !ok || node.Kind != "element" {
		return []any{""}
	}
	if len(args) < 2 {
		return []any{""}
	}
	key := ToString(args[1])
	return []any{node.Attrs[key]}
}

func fnText(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{""}
	}
	node, ok := args[0][0].(*Node)
	if ok {
		deep := true
		if len(args) > 1 {
			deep = ToBoolean(args[1])
		}
		if deep {
			return []any{node.StringValue()}
		}
		if node.Kind == "element" || node.Kind == "document" {
			direct := ""
			for _, c := range node.Children {
				if c.Kind == "text" {
					direct += c.Value
				}
			}
			return []any{direct}
		}
		return []any{node.StringValue()}
	}
	return []any{ToString(args[0])}
}

func fnChildren(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{}
	}
	if node, ok := args[0][0].(*Node); ok {
		out := []any{}
		for _, c := range node.Children {
			out = append(out, c)
		}
		return out
	}
	return []any{}
}

func fnElements(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{}
	}
	node, ok := args[0][0].(*Node)
	if !ok || (node.Kind != "element" && node.Kind != "document") {
		return []any{}
	}
	nameTest := ""
	if len(args) > 1 {
		nameTest = ToString(args[1])
	}
	out := []any{}
	for _, c := range node.Children {
		if c.Kind == "element" {
			if nameTest == "" || c.Name == nameTest {
				out = append(out, c)
			}
		}
	}
	return out
}

func fnCopy(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{}
	}
	node, ok := args[0][0].(*Node)
	if !ok {
		return []any{}
	}
	recurse := true
	if len(args) > 1 {
		recurse = ToBoolean(args[1])
	}
	return []any{DeepCopy(node, recurse)}
}

func fnCount(args [][]any, _ Context) []any {
	if len(args) == 0 {
		return []any{float64(0)}
	}
	return []any{float64(len(args[0]))}
}

func fnEmpty(args [][]any, _ Context) []any {
	if len(args) == 0 {
		return []any{true}
	}
	return []any{len(args[0]) == 0}
}

func fnDistinct(args [][]any, _ Context) []any {
	if len(args) == 0 {
		return []any{}
	}
	seen := map[string]bool{}
	out := []any{}
	for _, item := range args[0] {
		key := ToString([]any{item})
		if seen[key] {
			continue
		}
		seen[key] = true
		out = append(out, item)
	}
	return out
}

func fnSort(args [][]any, ctx Context) []any {
	if len(args) == 0 {
		return []any{}
	}
	seq := args[0]
	keyFn := ""
	if len(args) > 1 && len(args[1]) > 0 {
		if ref, ok := args[1][0].(FunctionRef); ok {
			keyFn = ref.Name
		}
	}
	out := append([]any{}, seq...)
	sort.Slice(out, func(i, j int) bool {
		if keyFn != "" {
			fn := ctx.Functions[keyFn]
			ki := ToString(callUserFunction(fn, [][]any{{out[i]}}, ctx))
			kj := ToString(callUserFunction(fn, [][]any{{out[j]}}, ctx))
			return ki < kj
		}
		return ToString([]any{out[i]}) < ToString([]any{out[j]})
	})
	return out
}

func fnConcat(args [][]any, _ Context) []any {
	out := []any{}
	for _, seq := range args {
		out = append(out, seq...)
	}
	return out
}

func fnHead(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{}
	}
	return []any{args[0][0]}
}

func fnTail(args [][]any, _ Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		return []any{}
	}
	return append([]any{}, args[0][1:]...)
}

func fnLast(args [][]any, ctx Context) []any {
	if len(args) == 0 || len(args[0]) == 0 {
		if ctx.Last == nil {
			return []any{}
		}
		return []any{float64(*ctx.Last)}
	}
	seq := args[0]
	if len(seq) == 0 {
		return []any{}
	}
	return []any{seq[len(seq)-1]}
}

func fnIndex(args [][]any, ctx Context) []any {
	if len(args) == 0 {
		return []any{}
	}
	seq := args[0]
	keyFn := ""
	if len(args) > 1 && len(args[1]) > 0 {
		if ref, ok := args[1][0].(FunctionRef); ok {
			keyFn = ref.Name
		}
	}
	index := map[string][]any{}
	for _, item := range seq {
		key := ToString([]any{item})
		if keyFn != "" {
			fn := ctx.Functions[keyFn]
			key = ToString(callUserFunction(fn, [][]any{{item}}, ctx))
		}
		index[key] = append(index[key], item)
	}
	return []any{index}
}

func fnLookup(args [][]any, _ Context) []any {
	if len(args) < 2 {
		return []any{}
	}
	if len(args[0]) == 0 {
		return []any{}
	}
	mapping, ok := args[0][0].(map[string][]any)
	if !ok {
		return []any{}
	}
	key := ToString(args[1])
	return mapping[key]
}

func fnGroupBy(args [][]any, ctx Context) []any {
	if len(args) < 2 {
		return []any{}
	}
	seq := args[0]
	keyFn := ""
	if len(args[1]) > 0 {
		if ref, ok := args[1][0].(FunctionRef); ok {
			keyFn = ref.Name
		}
	}
	groups := map[string][]any{}
	for _, item := range seq {
		key := ToString([]any{item})
		if keyFn != "" {
			fn := ctx.Functions[keyFn]
			key = ToString(callUserFunction(fn, [][]any{{item}}, ctx))
		}
		groups[key] = append(groups[key], item)
	}
	out := []any{}
	for k, v := range groups {
		out = append(out, map[string][]any{"key": []any{k}, "items": v})
	}
	return out
}

func fnSeq(args [][]any, _ Context) []any {
	out := []any{}
	for _, seq := range args {
		out = append(out, seq...)
	}
	return out
}

func fnPosition(_ [][]any, ctx Context) []any {
	if ctx.Position == nil {
		return []any{}
	}
	return []any{float64(*ctx.Position)}
}

func fnApply(args [][]any, ctx Context) []any {
	if len(args) == 0 {
		return []any{}
	}
	seq := args[0]
	ruleset := "main"
	if len(args) > 1 && len(args[1]) > 0 {
		ruleset = ToString(args[1])
	}
	rules := ctx.Rules[ruleset]
	out := []any{}
	for _, item := range seq {
		matched := false
		for _, rule := range rules {
			ok, bindings := MatchPattern(rule.Pattern, item)
			if ok {
				matched = true
				newVars := copyVars(ctx.Variables)
				for k, v := range bindings {
					newVars[k] = v
				}
				newCtx := Context{ContextItem: item, Variables: newVars, Functions: ctx.Functions, Rules: ctx.Rules, Position: ctx.Position, Last: ctx.Last}
				out = append(out, EvalExpr(rule.Body, newCtx)...)
				break
			}
		}
		if !matched {
			panic(fmt.Errorf("XFDY0001: no matching rule"))
		}
	}
	return out
}

func fnSum(args [][]any, _ Context) []any {
	if len(args) == 0 {
		return []any{0.0}
	}
	total := 0.0
	for _, item := range args[0] {
		total += ToNumber([]any{item})
	}
	return []any{total}
}

var builtins map[string]builtinFn

func init() {
	builtins = map[string]builtinFn{
		"string":   fnString,
		"number":   fnNumber,
		"boolean":  fnBoolean,
		"typeOf":   fnTypeOf,
		"name":     fnName,
		"attr":     fnAttr,
		"text":     fnText,
		"children": fnChildren,
		"elements": fnElements,
		"copy":     fnCopy,
		"count":    fnCount,
		"empty":    fnEmpty,
		"distinct": fnDistinct,
		"sort":     fnSort,
		"concat":   fnConcat,
		"index":    fnIndex,
		"lookup":   fnLookup,
		"groupBy":  fnGroupBy,
		"seq":      fnSeq,
		"sum":      fnSum,
		"head":     fnHead,
		"tail":     fnTail,
		"last":     fnLast,
		"position": fnPosition,
		"apply":    fnApply,
	}
}

func firstOrEmpty(args [][]any) []any {
	if len(args) == 0 {
		return []any{}
	}
	return args[0]
}

func copyVars(src map[string][]any) map[string][]any {
	out := map[string][]any{}
	for k, v := range src {
		out[k] = v
	}
	return out
}

func SerializeItem(item any) string {
	if node, ok := item.(*Node); ok {
		return Serialize(node)
	}
	return ToString([]any{item})
}
