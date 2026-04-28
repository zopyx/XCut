#include "xmlmodel.hpp"
#include "string_builder.hpp"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <libxml/parser.h>
#include <libxml/tree.h>

XmlNode* node_ref(XmlNode *node) {
    if (node) {
        node->ref_count++;
    }
    return node;
}

void node_unref(XmlNode *node) {
    if (!node) return;
    node->ref_count--;
    if (node->ref_count > 0) return;
    
    free(node->name);
    free(node->value);
    
    for (size_t i = 0; i < node->attr_count; i++) {
        free(node->attrs[i].name);
        free(node->attrs[i].value);
    }
    free(node->attrs);
    
    for (size_t i = 0; i < node->child_count; i++) {
        node_unref(node->children[i]);
    }
    free(node->children);
    
    free(node);
}

XmlNode* node_new_document(void) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_DOCUMENT;
    n->ref_count = 1;
    return n;
}

XmlNode* node_new_element(const char *name) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_ELEMENT;
    n->name = strdup(name);
    n->ref_count = 1;
    return n;
}

XmlNode* node_new_text(const char *value) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_TEXT;
    n->value = strdup(value);
    n->ref_count = 1;
    return n;
}

XmlNode* node_new_attribute(const char *name, const char *value) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_ATTRIBUTE;
    n->name = strdup(name);
    n->value = strdup(value);
    n->ref_count = 1;
    return n;
}

XmlNode* node_new_comment(const char *value) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_COMMENT;
    n->value = strdup(value);
    n->ref_count = 1;
    return n;
}

XmlNode* node_new_pi(const char *name, const char *value) {
    XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
    n->kind = NODE_PI;
    n->name = strdup(name);
    n->value = strdup(value);
    n->ref_count = 1;
    return n;
}

void node_add_child(XmlNode *parent, XmlNode *child) {
    if (!parent || !child) return;
    parent->children = (XmlNode**)realloc(parent->children, 
        (parent->child_count + 1) * sizeof(XmlNode*));
    parent->children[parent->child_count++] = node_ref(child);
}

void node_add_attr(XmlNode *elem, const char *name, const char *value) {
    if (!elem) return;
    elem->attrs = (decltype(elem->attrs))realloc(elem->attrs, 
        (elem->attr_count + 1) * sizeof(*elem->attrs));
    elem->attrs[elem->attr_count].name = strdup(name);
    elem->attrs[elem->attr_count].value = strdup(value);
    elem->attr_count++;
}

char* node_string_value(XmlNode *node) {
    if (!node) return strdup("");
    
    switch (node->kind) {
        case NODE_TEXT:
        case NODE_ATTRIBUTE:
            return strdup(node->value ? node->value : "");
        
        case NODE_ELEMENT:
        case NODE_DOCUMENT: {
            StringBuilder *sb = sb_new();
            for (size_t i = 0; i < node->child_count; i++) {
                char *sv = node_string_value(node->children[i]);
                sb_append_str(sb, sv);
                free(sv);
            }
            return sb_to_string(sb);
        }
        
        default:
            return strdup("");
    }
}

XmlNode* node_deep_copy(XmlNode *node) {
    if (!node) return NULL;
    
    XmlNode *copy = (XmlNode*)calloc(1, sizeof(XmlNode));
    copy->kind = node->kind;
    copy->name = node->name ? strdup(node->name) : NULL;
    copy->value = node->value ? strdup(node->value) : NULL;
    copy->ref_count = 1;
    
    /* Copy attributes */
    copy->attrs = (decltype(copy->attrs))malloc(node->attr_count * sizeof(*copy->attrs));
    copy->attr_count = node->attr_count;
    for (size_t i = 0; i < node->attr_count; i++) {
        copy->attrs[i].name = strdup(node->attrs[i].name);
        copy->attrs[i].value = strdup(node->attrs[i].value);
    }
    
    /* Copy children */
    copy->children = (XmlNode**)malloc(node->child_count * sizeof(XmlNode*));
    copy->child_count = node->child_count;
    for (size_t i = 0; i < node->child_count; i++) {
        copy->children[i] = node_deep_copy(node->children[i]);
    }
    
    return copy;
}

XmlNode** node_descendants(XmlNode *node, size_t *count) {
    *count = 0;
    XmlNode **result = NULL;
    
    for (size_t i = 0; i < node->child_count; i++) {
        XmlNode *child = node->children[i];
        result = (XmlNode**)realloc(result, (*count + 1) * sizeof(XmlNode*));
        result[(*count)++] = node_ref(child);
        
        size_t subcount;
        XmlNode **sub = node_descendants(child, &subcount);
        if (subcount > 0) {
            result = (XmlNode**)realloc(result, (*count + subcount) * sizeof(XmlNode*));
            for (size_t j = 0; j < subcount; j++) {
                result[(*count)++] = sub[j];
            }
            free(sub);
        }
    }
    
    return result;
}

/* Convert libxml2 node to our XmlNode */
static XmlNode* convert_node(xmlNode *node) {
    if (!node) return NULL;
    
    switch (node->type) {
        case XML_ELEMENT_NODE: {
            XmlNode *n = node_new_element((const char*)node->name);
            
            /* Attributes */
            for (xmlAttr *attr = node->properties; attr; attr = attr->next) {
                xmlChar *value = xmlNodeGetContent((xmlNode*)attr);
                node_add_attr(n, (const char*)attr->name, (const char*)value);
                xmlFree(value);
            }
            
            /* Sort attributes for determinism */
            for (size_t i = 0; i < n->attr_count; i++) {
                for (size_t j = i + 1; j < n->attr_count; j++) {
                    if (strcmp(n->attrs[i].name, n->attrs[j].name) > 0) {
                        char *tname = n->attrs[i].name;
                        char *tval = n->attrs[i].value;
                        n->attrs[i].name = n->attrs[j].name;
                        n->attrs[i].value = n->attrs[j].value;
                        n->attrs[j].name = tname;
                        n->attrs[j].value = tval;
                    }
                }
            }
            
            /* Children */
            for (xmlNode *child = node->children; child; child = child->next) {
                XmlNode *c = convert_node(child);
                if (c) {
                    node_add_child(n, c);
                    node_unref(c);
                }
            }
            
            return n;
        }
        
        case XML_TEXT_NODE:
        case XML_CDATA_SECTION_NODE: {
            char *content = (char*)xmlNodeGetContent(node);
            XmlNode *n = node_new_text(content ? content : "");
            xmlFree(content);
            return n;
        }
        
        case XML_COMMENT_NODE: {
            XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
            n->kind = NODE_COMMENT;
            n->value = (char*)xmlNodeGetContent(node);
            if (!n->value) n->value = strdup("");
            n->ref_count = 1;
            return n;
        }
        
        case XML_PI_NODE: {
            XmlNode *n = (XmlNode*)calloc(1, sizeof(XmlNode));
            n->kind = NODE_PI;
            n->name = strdup((const char*)node->name);
            n->value = (char*)xmlNodeGetContent(node);
            if (!n->value) n->value = strdup("");
            n->ref_count = 1;
            return n;
        }
        
        default:
            return NULL;
    }
}

XmlNode* parse_xml(const char *text) {
    LIBXML_TEST_VERSION;
    
    xmlDoc *doc = xmlReadMemory(text, strlen(text), NULL, NULL,
        XML_PARSE_NOENT | XML_PARSE_NONET);
    
    if (!doc) {
        return NULL;
    }
    
    XmlNode *root = node_new_document();
    xmlNode *root_element = xmlDocGetRootElement(doc);
    
    if (root_element) {
        /* Process all top-level nodes (including comments, PIs) */
        for (xmlNode *node = doc->children; node; node = node->next) {
            XmlNode *c = convert_node(node);
            if (c) {
                node_add_child(root, c);
                node_unref(c);
            }
        }
    }
    
    xmlFreeDoc(doc);
    return root;
}

char* escape_text(const char *s) {
    if (!s) return strdup("");
    StringBuilder *sb = sb_new();
    while (*s) {
        switch (*s) {
            case '&': sb_append_str(sb, "&amp;"); break;
            case '<': sb_append_str(sb, "&lt;"); break;
            case '>': sb_append_str(sb, "&gt;"); break;
            default: sb_append(sb, *s); break;
        }
        s++;
    }
    return sb_to_string(sb);
}

char* escape_attr(const char *s) {
    if (!s) return strdup("");
    StringBuilder *sb = sb_new();
    while (*s) {
        switch (*s) {
            case '&': sb_append_str(sb, "&amp;"); break;
            case '<': sb_append_str(sb, "&lt;"); break;
            case '>': sb_append_str(sb, "&gt;"); break;
            case '"': sb_append_str(sb, "&quot;"); break;
            default: sb_append(sb, *s); break;
        }
        s++;
    }
    return sb_to_string(sb);
}

static void serialize_node(XmlNode *node, StringBuilder *sb);

static void serialize_element(XmlNode *node, StringBuilder *sb) {
    sb_append(sb, '<');
    sb_append_str(sb, node->name);
    
    for (size_t i = 0; i < node->attr_count; i++) {
        sb_append(sb, ' ');
        sb_append_str(sb, node->attrs[i].name);
        sb_append_str(sb, "=\"");
        char *escaped = escape_attr(node->attrs[i].value);
        sb_append_str(sb, escaped);
        free(escaped);
        sb_append(sb, '"');
    }
    
    if (node->child_count == 0) {
        sb_append_str(sb, "/>");
        return;
    }
    
    sb_append(sb, '>');
    
    for (size_t i = 0; i < node->child_count; i++) {
        serialize_node(node->children[i], sb);
    }
    
    sb_append_str(sb, "</");
    sb_append_str(sb, node->name);
    sb_append(sb, '>');
}

static void serialize_node(XmlNode *node, StringBuilder *sb) {
    switch (node->kind) {
        case NODE_DOCUMENT:
            for (size_t i = 0; i < node->child_count; i++) {
                serialize_node(node->children[i], sb);
            }
            break;
        
        case NODE_ELEMENT:
            serialize_element(node, sb);
            break;
        
        case NODE_TEXT: {
            char *escaped = escape_text(node->value);
            sb_append_str(sb, escaped);
            free(escaped);
            break;
        }
        
        case NODE_COMMENT:
            sb_append_str(sb, "<!--");
            sb_append_str(sb, node->value ? node->value : "");
            sb_append_str(sb, "-->");
            break;
        
        case NODE_PI:
            sb_append_str(sb, "<?");
            sb_append_str(sb, node->name ? node->name : "");
            sb_append(sb, ' ');
            sb_append_str(sb, node->value ? node->value : "");
            sb_append_str(sb, "?>");
            break;
        
        case NODE_ATTRIBUTE:
            /* Attribute as standalone result - serialize its value */
            sb_append_str(sb, node->value ? node->value : "");
            break;
    }
}

char* serialize_xml(XmlNode *node) {
    if (!node) return strdup("");
    StringBuilder *sb = sb_new();
    serialize_node(node, sb);
    return sb_to_string(sb);
}
