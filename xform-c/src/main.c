#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"
#include "eval.h"
#include "xmlmodel.h"

static char* read_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "Error opening %s\n", path);
        return NULL;
    }
    
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    char *buf = malloc(len + 1);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    
    fread(buf, 1, len, f);
    buf[len] = '\0';
    fclose(f);
    
    return buf;
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        fprintf(stderr, "Usage: %s <input.xml> <transform.xform>\n", argv[0]);
        return 1;
    }
    
    const char *xml_path = argv[1];
    const char *xform_path = argv[2];
    
    char *xml_text = read_file(xml_path);
    if (!xml_text) {
        fprintf(stderr, "Error reading %s\n", xml_path);
        return 1;
    }
    
    char *xform_text = read_file(xform_path);
    if (!xform_text) {
        fprintf(stderr, "Error reading %s\n", xform_path);
        free(xml_text);
        return 1;
    }
    
    XmlNode *doc = parse_xml(xml_text);
    free(xml_text);
    
    if (!doc) {
        fprintf(stderr, "XML parse error\n");
        free(xform_text);
        return 1;
    }
    
    Parser *p = parser_new(xform_text);
    
    Module *mod = parser_parse_module(p);
    
    if (!mod) {
        fprintf(stderr, "XForm parse error: %s\n", parser_error());
        parser_free(p);
        free(xform_text);
        node_unref(doc);
        return 1;
    }
    parser_free(p);
    free(xform_text);
    
    Seq *result = eval_module(mod, doc);
    if (!result) {
        fprintf(stderr, "Evaluation error\n");
        module_free(mod);
        node_unref(doc);
        return 1;
    }
    
    char *output = serialize_items(result);
    printf("%s", output);
    
    free(output);
    seq_free(result);
    module_free(mod);
    node_unref(doc);
    
    return 0;
}
