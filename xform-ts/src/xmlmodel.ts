import { DOMParser } from "@xmldom/xmldom";

export type NodeKind = "document" | "element" | "attribute" | "text" | "comment" | "pi";

export class Node {
  kind: NodeKind;
  name: string | null;
  value: string | null;
  children: Node[];
  attrs: Record<string, string>;
  parent: Node | null;

  constructor(opts: {
    kind: NodeKind;
    name?: string | null;
    value?: string | null;
    children?: Node[];
    attrs?: Record<string, string>;
    parent?: Node | null;
  }) {
    this.kind = opts.kind;
    this.name = opts.name ?? null;
    this.value = opts.value ?? null;
    this.children = opts.children ?? [];
    this.attrs = opts.attrs ?? {};
    this.parent = opts.parent ?? null;
  }

  stringValue(): string {
    if (this.kind === "text") {
      return this.value ?? "";
    }
    if (this.kind === "attribute") {
      return this.value ?? "";
    }
    if (this.kind === "element" || this.kind === "document") {
      return this.children.map((c) => c.stringValue()).join("");
    }
    return "";
  }
}

export function parseXml(text: string): Node {
  const parser = new DOMParser({ errorHandler: { warning: () => {}, error: () => {}, fatalError: () => {} } });
  const doc = parser.parseFromString(text, "text/xml");
  const docNode = new Node({ kind: "document" });
  const rootEl = doc.documentElement;
  if (rootEl) {
    const rootNode = buildElement(rootEl);
    rootNode.parent = docNode;
    docNode.children = [rootNode];
  }
  return docNode;
}

function buildElement(el: Element): Node {
  const attrs: Record<string, string> = {};
  if (el.attributes) {
    for (let i = 0; i < el.attributes.length; i += 1) {
      const a = el.attributes.item(i);
      if (a) {
        attrs[a.name] = a.value;
      }
    }
  }
  const node = new Node({ kind: "element", name: el.tagName, attrs });
  const children: Node[] = [];
  for (let i = 0; i < el.childNodes.length; i += 1) {
    const child = el.childNodes.item(i);
    if (!child) {
      continue;
    }
    const built = buildDomNode(child);
    if (built) {
      built.parent = node;
      children.push(built);
    }
  }
  node.children = children;
  return node;
}

function buildDomNode(node: ChildNode): Node | null {
  switch (node.nodeType) {
    case 1: // ELEMENT_NODE
      return buildElement(node as Element);
    case 3: // TEXT_NODE
      return new Node({ kind: "text", value: node.nodeValue ?? "" });
    case 8: // COMMENT_NODE
      return new Node({ kind: "comment", value: node.nodeValue ?? "" });
    case 7: // PROCESSING_INSTRUCTION_NODE
      return new Node({ kind: "pi", value: node.nodeValue ?? "" });
    default:
      return null;
  }
}

export function deepCopy(node: Node, recurse = true): Node {
  const copied = new Node({
    kind: node.kind,
    name: node.name,
    value: node.value,
    attrs: { ...node.attrs },
  });
  if (recurse) {
    copied.children = node.children.map((c) => {
      const child = deepCopy(c, true);
      child.parent = copied;
      return child;
    });
  }
  return copied;
}

export function* iterDescendants(node: Node): Iterable<Node> {
  for (const child of node.children) {
    yield child;
    yield* iterDescendants(child);
  }
}

export function serialize(item: Node): string {
  if (item.kind === "document") {
    return item.children.map((c) => serialize(c)).join("");
  }
  if (item.kind === "text") {
    return escapeText(item.value ?? "");
  }
  if (item.kind === "attribute") {
    return escapeAttr(item.value ?? "");
  }
  if (item.kind === "element") {
    const attrs = Object.entries(item.attrs)
      .map(([name, value]) => ` ${name}="${escapeAttr(value)}"`)
      .join("");
    if (item.children.length === 0) {
      return `<${item.name}${attrs}/>`;
    }
    const inner = item.children.map((c) => serialize(c)).join("");
    return `<${item.name}${attrs}>${inner}</${item.name}>`;
  }
  return "";
}

function escapeText(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function escapeAttr(text: string): string {
  return escapeText(text).replace(/\"/g, "&quot;");
}
