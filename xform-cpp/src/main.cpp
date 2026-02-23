#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "parser.hpp"
#include "eval.hpp"
#include "xmlmodel.hpp"

static char* read_file(const char* path) {
    FILE* f = std::fopen(path, "rb");
    if (!f) {
        std::fprintf(stderr, "Error opening %s\n", path);
        return nullptr;
    }

    std::fseek(f, 0, SEEK_END);
    long len = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);

    char* buf = static_cast<char*>(std::malloc(static_cast<size_t>(len) + 1));
    if (!buf) {
        std::fclose(f);
        return nullptr;
    }

    std::fread(buf, 1, static_cast<size_t>(len), f);
    buf[len] = '\0';
    std::fclose(f);

    return buf;
}

int main(int argc, char* argv[]) {
    if (argc != 3) {
        std::fprintf(stderr, "Usage: %s <input.xml> <transform.xform>\n", argv[0]);
        return 1;
    }

    const char* xml_path = argv[1];
    const char* xform_path = argv[2];

    char* xml_text = read_file(xml_path);
    if (!xml_text) {
        std::fprintf(stderr, "Error reading %s\n", xml_path);
        return 1;
    }

    char* xform_text = read_file(xform_path);
    if (!xform_text) {
        std::fprintf(stderr, "Error reading %s\n", xform_path);
        std::free(xml_text);
        return 1;
    }

    XmlNode* doc = parse_xml(xml_text);
    std::free(xml_text);

    if (!doc) {
        std::fprintf(stderr, "XML parse error\n");
        std::free(xform_text);
        return 1;
    }

    Parser* p = parser_new(xform_text);
    Module* mod = parser_parse_module(p);

    if (!mod) {
        std::fprintf(stderr, "XForm parse error: %s\n", parser_error());
        parser_free(p);
        std::free(xform_text);
        node_unref(doc);
        return 1;
    }
    parser_free(p);
    std::free(xform_text);

    Seq* result = eval_module(mod, doc);
    char* output = serialize_items(result);
    std::printf("%s", output);

    std::free(output);
    seq_free(result);
    module_free(mod);
    node_unref(doc);

    return 0;
}
