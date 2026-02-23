#ifndef STRING_BUILDER_H
#define STRING_BUILDER_H

#include <stddef.h>

/* Simple dynamic string builder */
typedef struct {
    char *data;
    size_t len;
    size_t capacity;
} StringBuilder;

StringBuilder* sb_new(void);
void sb_free(StringBuilder *sb);
void sb_append(StringBuilder *sb, char c);
void sb_append_str(StringBuilder *sb, const char *str);
void sb_append_n(StringBuilder *sb, const char *str, size_t n);
char* sb_to_string(StringBuilder *sb);  /* transfers ownership */
char* sb_copy_string(const StringBuilder *sb);
void sb_clear(StringBuilder *sb);

#endif
