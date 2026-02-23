#ifndef XMLMODEL_H
#define XMLMODEL_H

#include <stddef.h>
#include <stdbool.h>

typedef enum {
    NODE_DOCUMENT,
    NODE_ELEMENT,
    NODE_ATTRIBUTE,
    NODE_TEXT,
    NODE_COMMENT,
    NODE_PI
} NodeKind;

typedef struct XmlNode XmlNode;

struct XmlNode {
    NodeKind kind;
    char *name;     /* NULL for text, comment, document */
    char *value;    /* For text, attribute, comment, PI */
    /* Attributes as ordered pairs */
    struct {
        char *name;
        char *value;
    } *attrs;
    size_t attr_count;
    XmlNode **children;
    size_t child_count;
    /* Reference counting for memory management */
    int ref_count;
};

/* Reference counting */
XmlNode* node_ref(XmlNode *node);
void node_unref(XmlNode *node);

/* Node creation */
XmlNode* node_new_document(void);
XmlNode* node_new_element(const char *name);
XmlNode* node_new_text(const char *value);
XmlNode* node_new_attribute(const char *name, const char *value);

/* String value of node */
char* node_string_value(XmlNode *node);

/* Deep copy */
XmlNode* node_deep_copy(XmlNode *node);

/* Descendant iteration - returns array of referenced nodes (caller unrefs) */
XmlNode** node_descendants(XmlNode *node, size_t *count);

/* XML parsing */
XmlNode* parse_xml(const char *text);

/* Serialization */
char* serialize_xml(XmlNode *node);
char* escape_text(const char *s);
char* escape_attr(const char *s);

/* Helper for adding children */
void node_add_child(XmlNode *parent, XmlNode *child);
void node_add_attr(XmlNode *elem, const char *name, const char *value);

#endif
