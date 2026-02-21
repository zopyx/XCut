package xform

import (
	"encoding/xml"
	"io"
	"sort"
	"strings"
)

type Node struct {
	Kind      string
	Name      string
	Value     string
	Children  []*Node
	Attrs     map[string]string
	AttrOrder []string
	Parent    *Node
}

func (n *Node) StringValue() string {
	switch n.Kind {
	case "text", "attribute":
		return n.Value
	case "element", "document":
		out := ""
		for _, c := range n.Children {
			out += c.StringValue()
		}
		return out
	default:
		return ""
	}
}

func ParseXML(text string) (*Node, error) {
	return ParseXMLBytes([]byte(text))
}

func ParseXMLBytes(data []byte) (*Node, error) {
	text := normalizeXMLBytes(data)
	doc := &Node{Kind: "document", Attrs: map[string]string{}}
	decoder := xml.NewDecoder(strings.NewReader(text))
	var stack []*Node
	for {
		tok, err := decoder.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
		switch t := tok.(type) {
		case xml.StartElement:
			order := make([]string, 0, len(t.Attr))
			n := &Node{Kind: "element", Name: t.Name.Local, Attrs: map[string]string{}}
			for _, a := range t.Attr {
				n.Attrs[a.Name.Local] = a.Value
				order = append(order, a.Name.Local)
			}
			n.AttrOrder = order
			if len(stack) == 0 {
				n.Parent = doc
				doc.Children = append(doc.Children, n)
			} else {
				parent := stack[len(stack)-1]
				n.Parent = parent
				parent.Children = append(parent.Children, n)
			}
			stack = append(stack, n)
		case xml.EndElement:
			if len(stack) > 0 {
				stack = stack[:len(stack)-1]
			}
		case xml.CharData:
			if len(stack) == 0 {
				continue
			}
			txt := string(t)
			n := &Node{Kind: "text", Value: txt, Attrs: map[string]string{}}
			parent := stack[len(stack)-1]
			n.Parent = parent
			parent.Children = append(parent.Children, n)
		case xml.Comment:
			if len(stack) == 0 {
				continue
			}
			n := &Node{Kind: "comment", Value: string(t), Attrs: map[string]string{}}
			parent := stack[len(stack)-1]
			n.Parent = parent
			parent.Children = append(parent.Children, n)
		case xml.ProcInst:
			if len(stack) == 0 {
				continue
			}
			n := &Node{Kind: "pi", Value: string(t.Inst), Attrs: map[string]string{}}
			parent := stack[len(stack)-1]
			n.Parent = parent
			parent.Children = append(parent.Children, n)
		}
	}
	return doc, nil
}

func DeepCopy(node *Node, recurse bool) *Node {
	copied := &Node{Kind: node.Kind, Name: node.Name, Value: node.Value, Attrs: map[string]string{}, AttrOrder: append([]string{}, node.AttrOrder...)}
	for k, v := range node.Attrs {
		copied.Attrs[k] = v
	}
	if recurse {
		for _, c := range node.Children {
			child := DeepCopy(c, true)
			child.Parent = copied
			copied.Children = append(copied.Children, child)
		}
	}
	return copied
}

func IterDescendants(node *Node) []*Node {
	out := []*Node{}
	for _, child := range node.Children {
		out = append(out, child)
		out = append(out, IterDescendants(child)...)
	}
	return out
}

func Serialize(item *Node) string {
	switch item.Kind {
	case "document":
		out := ""
		for _, c := range item.Children {
			out += Serialize(c)
		}
		return out
	case "text":
		return escapeText(item.Value)
	case "attribute":
		return escapeAttr(item.Value)
	case "element":
		attrs := ""
		keys := item.AttrOrder
		if len(keys) == 0 {
			keys = make([]string, 0, len(item.Attrs))
			for k := range item.Attrs {
				keys = append(keys, k)
			}
			sort.Strings(keys)
		}
		for _, k := range keys {
			attrs += " " + k + "=\"" + escapeAttr(item.Attrs[k]) + "\""
		}
		if len(item.Children) == 0 {
			return "<" + item.Name + attrs + "/>"
		}
		inner := ""
		for _, c := range item.Children {
			inner += Serialize(c)
		}
		return "<" + item.Name + attrs + ">" + inner + "</" + item.Name + ">"
	default:
		return ""
	}
}

func escapeText(text string) string {
	replacer := strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;")
	return replacer.Replace(text)
}

func escapeAttr(text string) string {
	replacer := strings.NewReplacer("\"", "&quot;")
	return replacer.Replace(escapeText(text))
}

func normalizeXMLBytes(data []byte) string {
	text := string(data)
	lower := strings.ToLower(text)
	if strings.Contains(lower, "encoding=\"iso-8859-1\"") || strings.Contains(lower, "encoding='iso-8859-1'") {
		runes := make([]rune, 0, len(data))
		for _, b := range data {
			runes = append(runes, rune(b))
		}
		text = string(runes)
		text = strings.ReplaceAll(text, "encoding=\"ISO-8859-1\"", "encoding=\"UTF-8\"")
		text = strings.ReplaceAll(text, "encoding='ISO-8859-1'", "encoding=\"UTF-8\"")
	}
	return replaceNamedEntities(text)
}

func replaceNamedEntities(text string) string {
	replacer := strings.NewReplacer(
		"&mdash;", "—",
		"&hellip;", "…",
		"&nbsp;", "\u00a0",
	)
	return replacer.Replace(text)
}
