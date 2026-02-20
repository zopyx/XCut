use std::collections::HashMap;
use std::io::Cursor;
use std::rc::Rc;

#[derive(Debug, Clone, PartialEq)]
pub enum NodeKind {
    Document,
    Element,
    Attribute,
    Text,
    Comment,
    Pi,
}

#[derive(Debug, Clone)]
pub struct XmlNode {
    pub kind: NodeKind,
    pub name: Option<String>,
    pub value: Option<String>,
    /// Ordered list of (name, value) pairs for attributes
    pub attrs: Vec<(String, String)>,
    pub children: Vec<Rc<XmlNode>>,
}

impl XmlNode {
    pub fn string_value(&self) -> String {
        match self.kind {
            NodeKind::Text | NodeKind::Attribute => self.value.clone().unwrap_or_default(),
            NodeKind::Element | NodeKind::Document => {
                self.children.iter().map(|c| c.string_value()).collect()
            }
            _ => String::new(),
        }
    }
}

/// Remove <!DOCTYPE ...> blocks before parsing so xml-rs does not choke on
/// entity declarations that some parsers cannot handle.
fn strip_doctype(xml: &str) -> String {
    if !xml.contains("<!DOCTYPE") {
        return xml.to_string();
    }
    let bytes = xml.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        // Look for <!DOCTYPE
        if i + 9 <= bytes.len() && &bytes[i..i + 9] == b"<!DOCTYPE" {
            // Skip until matching '>' (handling '[' ... ']' internal subset)
            i += 9;
            let mut depth = 0usize;
            while i < bytes.len() {
                match bytes[i] {
                    b'[' => {
                        depth += 1;
                        i += 1;
                    }
                    b']' => {
                        if depth > 0 {
                            depth -= 1;
                        }
                        i += 1;
                    }
                    b'>' if depth == 0 => {
                        i += 1;
                        break;
                    }
                    _ => {
                        i += 1;
                    }
                }
            }
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub fn parse_xml(text: &str) -> Result<Rc<XmlNode>, String> {
    let clean = strip_doctype(text);
    let cursor = Cursor::new(clean.as_bytes().to_vec());
    let el = xmltree::Element::parse(cursor)
        .map_err(|e| format!("XML parse error: {}", e))?;
    let root = build_element(&el);
    let doc = Rc::new(XmlNode {
        kind: NodeKind::Document,
        name: None,
        value: None,
        attrs: vec![],
        children: vec![root],
    });
    Ok(doc)
}

fn build_element(el: &xmltree::Element) -> Rc<XmlNode> {
    let mut children = Vec::new();
    for child in &el.children {
        match child {
            xmltree::XMLNode::Element(ce) => {
                children.push(build_element(ce));
            }
            xmltree::XMLNode::Text(t) => {
                if !t.is_empty() {
                    children.push(Rc::new(XmlNode {
                        kind: NodeKind::Text,
                        name: None,
                        value: Some(t.clone()),
                        attrs: vec![],
                        children: vec![],
                    }));
                }
            }
            xmltree::XMLNode::CData(t) => {
                children.push(Rc::new(XmlNode {
                    kind: NodeKind::Text,
                    name: None,
                    value: Some(t.clone()),
                    attrs: vec![],
                    children: vec![],
                }));
            }
            xmltree::XMLNode::Comment(t) => {
                children.push(Rc::new(XmlNode {
                    kind: NodeKind::Comment,
                    name: None,
                    value: Some(t.clone()),
                    attrs: vec![],
                    children: vec![],
                }));
            }
            xmltree::XMLNode::ProcessingInstruction(target, data) => {
                children.push(Rc::new(XmlNode {
                    kind: NodeKind::Pi,
                    name: Some(target.clone()),
                    value: data.clone(),
                    attrs: vec![],
                    children: vec![],
                }));
            }
        }
    }

    // Collect attributes in the order xmltree provides them
    let attrs: Vec<(String, String)> = {
        // xmltree stores attributes in a HashMap; try to use attribute_order if available
        // We fall back to sorted order for determinism
        let mut v: Vec<(String, String)> =
            el.attributes.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
        // Sort for determinism (matches how xsltproc typically outputs attributes)
        v.sort_by(|a, b| a.0.cmp(&b.0));
        v
    };

    Rc::new(XmlNode {
        kind: NodeKind::Element,
        name: Some(el.name.clone()),
        value: None,
        attrs,
        children,
    })
}

pub fn deep_copy(node: &Rc<XmlNode>) -> Rc<XmlNode> {
    Rc::new(XmlNode {
        kind: node.kind.clone(),
        name: node.name.clone(),
        value: node.value.clone(),
        attrs: node.attrs.clone(),
        children: node.children.iter().map(deep_copy).collect(),
    })
}

pub fn iter_descendants(node: &Rc<XmlNode>) -> Vec<Rc<XmlNode>> {
    let mut out = Vec::new();
    for child in &node.children {
        out.push(child.clone());
        out.extend(iter_descendants(child));
    }
    out
}

pub fn serialize(node: &Rc<XmlNode>) -> String {
    match node.kind {
        NodeKind::Document => node.children.iter().map(serialize).collect(),
        NodeKind::Text => escape_text(node.value.as_deref().unwrap_or("")),
        NodeKind::Comment => String::new(), // omit comments in output
        NodeKind::Pi => String::new(),
        NodeKind::Attribute => escape_attr(node.value.as_deref().unwrap_or("")),
        NodeKind::Element => {
            let name = node.name.as_deref().unwrap_or("");
            let attrs: String = node
                .attrs
                .iter()
                .map(|(k, v)| format!(" {}=\"{}\"", k, escape_attr(v)))
                .collect();
            if node.children.is_empty() {
                format!("<{}{} />", name, attrs)
                    .replace(" />", "/>")
                    // match Python: no space before />
            } else {
                let inner: String = node.children.iter().map(serialize).collect();
                format!("<{}{}>{}</{}>", name, attrs, inner, name)
            }
        }
    }
}

pub fn escape_text(s: &str) -> String {
    s.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;")
}

pub fn escape_attr(s: &str) -> String {
    escape_text(s).replace('"', "&quot;")
}

/// Build a new element XmlNode (for eval_constructor)
pub fn make_element(
    name: &str,
    attrs: Vec<(String, String)>,
    children: Vec<Rc<XmlNode>>,
) -> Rc<XmlNode> {
    Rc::new(XmlNode {
        kind: NodeKind::Element,
        name: Some(name.to_string()),
        value: None,
        attrs,
        children,
    })
}

pub fn make_text(value: &str) -> Rc<XmlNode> {
    Rc::new(XmlNode {
        kind: NodeKind::Text,
        name: None,
        value: Some(value.to_string()),
        attrs: vec![],
        children: vec![],
    })
}

pub fn make_attr(name: &str, value: &str) -> Rc<XmlNode> {
    Rc::new(XmlNode {
        kind: NodeKind::Attribute,
        name: Some(name.to_string()),
        value: Some(value.to_string()),
        attrs: vec![],
        children: vec![],
    })
}
