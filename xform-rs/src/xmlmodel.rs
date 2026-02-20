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

/// Extract entity name → value mappings from DOCTYPE internal subset.
/// Only handles simple `<!ENTITY name "value">` or `<!ENTITY name 'value'>` forms.
fn extract_entities(doctype_block: &str) -> Vec<(String, String)> {
    let mut entities = Vec::new();
    let mut s = doctype_block;
    while let Some(pos) = s.find("<!ENTITY") {
        s = &s[pos + 8..];
        // skip whitespace
        let s2 = s.trim_start();
        // read name
        let end = s2.find(|c: char| c.is_whitespace()).unwrap_or(s2.len());
        let name = s2[..end].to_string();
        let rest = s2[end..].trim_start();
        // expect a quote
        if rest.starts_with('"') || rest.starts_with('\'') {
            let quote = rest.chars().next().unwrap();
            let inner = &rest[1..];
            if let Some(close) = inner.find(quote) {
                let value = inner[..close].to_string();
                entities.push((name, value));
            }
        }
        s = s2;
    }
    entities
}

/// Replace `&name;` entity references in XML text using provided mapping.
fn replace_entities(xml: &str, entities: &[(String, String)]) -> String {
    if entities.is_empty() {
        return xml.to_string();
    }
    let mut out = xml.to_string();
    for (name, value) in entities {
        let ref_str = format!("&{};", name);
        out = out.replace(&ref_str, value);
    }
    out
}

/// Remove <!DOCTYPE ...> blocks and extract entities before parsing.
fn preprocess(xml: &str) -> String {
    if !xml.contains("<!DOCTYPE") {
        return xml.to_string();
    }
    let bytes = xml.as_bytes();
    let mut entities: Vec<(String, String)> = Vec::new();
    let mut out_bytes = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if i + 9 <= bytes.len() && &bytes[i..i + 9] == b"<!DOCTYPE" {
            let start_doctype = i;
            i += 9;
            let mut depth = 0usize;
            while i < bytes.len() {
                match bytes[i] {
                    b'[' => { depth += 1; i += 1; }
                    b']' => { if depth > 0 { depth -= 1; } i += 1; }
                    b'>' if depth == 0 => { i += 1; break; }
                    _ => { i += 1; }
                }
            }
            // Extract entity defs from this DOCTYPE block
            let doctype_text = std::str::from_utf8(&bytes[start_doctype..i]).unwrap_or("");
            entities.extend(extract_entities(doctype_text));
        } else {
            out_bytes.push(bytes[i]);
            i += 1;
        }
    }
    let without_doctype = String::from_utf8_lossy(&out_bytes).into_owned();
    replace_entities(&without_doctype, &entities)
}

pub fn parse_xml(text: &str) -> Result<Rc<XmlNode>, String> {
    let clean = preprocess(text);
    let cursor = std::io::Cursor::new(clean.as_bytes().to_vec());

    use xml::reader::{EventReader, XmlEvent, ParserConfig};
    let config = ParserConfig::new()
        .trim_whitespace(false)
        .whitespace_to_characters(true)
        .ignore_comments(false);
    let reader = EventReader::new_with_config(cursor, config);

    // Stack of (node_kind, name, attrs, children)
    let mut stack: Vec<(NodeKind, Option<String>, Vec<(String, String)>, Vec<Rc<XmlNode>>)> =
        vec![(NodeKind::Document, None, vec![], vec![])];

    for event in reader {
        match event.map_err(|e| format!("XML parse error: {}", e))? {
            XmlEvent::StartElement { name, attributes, .. } => {
                let mut attrs: Vec<(String, String)> = attributes
                    .into_iter()
                    .map(|a| (a.name.local_name, a.value))
                    .collect();
                // Sort for determinism (xmltree uses HashMap, we want stable order)
                attrs.sort_by(|a, b| a.0.cmp(&b.0));
                stack.push((NodeKind::Element, Some(name.local_name), attrs, vec![]));
            }
            XmlEvent::EndElement { .. } => {
                let (kind, name, attrs, children) = stack.pop().unwrap();
                let node = Rc::new(XmlNode { kind, name, value: None, attrs, children });
                stack.last_mut().unwrap().3.push(node);
            }
            XmlEvent::Characters(text) | XmlEvent::CData(text) => {
                let node = Rc::new(XmlNode {
                    kind: NodeKind::Text,
                    name: None,
                    value: Some(text),
                    attrs: vec![],
                    children: vec![],
                });
                stack.last_mut().unwrap().3.push(node);
            }
            XmlEvent::Comment(text) => {
                let node = Rc::new(XmlNode {
                    kind: NodeKind::Comment,
                    name: None,
                    value: Some(text),
                    attrs: vec![],
                    children: vec![],
                });
                stack.last_mut().unwrap().3.push(node);
            }
            XmlEvent::ProcessingInstruction { name, data } => {
                let node = Rc::new(XmlNode {
                    kind: NodeKind::Pi,
                    name: Some(name),
                    value: data,
                    attrs: vec![],
                    children: vec![],
                });
                stack.last_mut().unwrap().3.push(node);
            }
            _ => {}
        }
    }

    let (_, _, _, children) = stack.pop().unwrap();
    Ok(Rc::new(XmlNode {
        kind: NodeKind::Document,
        name: None,
        value: None,
        attrs: vec![],
        children,
    }))
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
                format!("<{}{}/>", name, attrs)
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
