"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Node = void 0;
exports.parseXml = parseXml;
exports.deepCopy = deepCopy;
exports.iterDescendants = iterDescendants;
exports.serialize = serialize;
const xmldom_1 = require("@xmldom/xmldom");
class Node {
    constructor(opts) {
        var _a, _b, _c, _d, _e;
        this.kind = opts.kind;
        this.name = (_a = opts.name) !== null && _a !== void 0 ? _a : null;
        this.value = (_b = opts.value) !== null && _b !== void 0 ? _b : null;
        this.children = (_c = opts.children) !== null && _c !== void 0 ? _c : [];
        this.attrs = (_d = opts.attrs) !== null && _d !== void 0 ? _d : {};
        this.parent = (_e = opts.parent) !== null && _e !== void 0 ? _e : null;
    }
    stringValue() {
        var _a, _b;
        if (this.kind === "text") {
            return (_a = this.value) !== null && _a !== void 0 ? _a : "";
        }
        if (this.kind === "attribute") {
            return (_b = this.value) !== null && _b !== void 0 ? _b : "";
        }
        if (this.kind === "element" || this.kind === "document") {
            return this.children.map((c) => c.stringValue()).join("");
        }
        return "";
    }
}
exports.Node = Node;
function parseXml(text) {
    const parser = new xmldom_1.DOMParser({ errorHandler: { warning: () => { }, error: () => { }, fatalError: () => { } } });
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
function buildElement(el) {
    const attrs = {};
    if (el.attributes) {
        for (let i = 0; i < el.attributes.length; i += 1) {
            const a = el.attributes.item(i);
            if (a) {
                attrs[a.name] = a.value;
            }
        }
    }
    const node = new Node({ kind: "element", name: el.tagName, attrs });
    const children = [];
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
function buildDomNode(node) {
    var _a, _b, _c;
    switch (node.nodeType) {
        case 1: // ELEMENT_NODE
            return buildElement(node);
        case 3: // TEXT_NODE
            return new Node({ kind: "text", value: (_a = node.nodeValue) !== null && _a !== void 0 ? _a : "" });
        case 8: // COMMENT_NODE
            return new Node({ kind: "comment", value: (_b = node.nodeValue) !== null && _b !== void 0 ? _b : "" });
        case 7: // PROCESSING_INSTRUCTION_NODE
            return new Node({ kind: "pi", value: (_c = node.nodeValue) !== null && _c !== void 0 ? _c : "" });
        default:
            return null;
    }
}
function deepCopy(node, recurse = true) {
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
function* iterDescendants(node) {
    for (const child of node.children) {
        yield child;
        yield* iterDescendants(child);
    }
}
function serialize(item) {
    var _a, _b;
    if (item.kind === "document") {
        return item.children.map((c) => serialize(c)).join("");
    }
    if (item.kind === "text") {
        return escapeText((_a = item.value) !== null && _a !== void 0 ? _a : "");
    }
    if (item.kind === "attribute") {
        return escapeAttr((_b = item.value) !== null && _b !== void 0 ? _b : "");
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
function escapeText(text) {
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
function escapeAttr(text) {
    return escapeText(text).replace(/\"/g, "&quot;");
}
