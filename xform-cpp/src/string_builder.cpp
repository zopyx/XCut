#include "string_builder.hpp"
#include <stdlib.h>
#include <string.h>

#define INITIAL_CAPACITY 16

StringBuilder* sb_new(void) {
    StringBuilder *sb = malloc(sizeof(StringBuilder));
    if (!sb) return NULL;
    sb->data = malloc(INITIAL_CAPACITY);
    if (!sb->data) {
        free(sb);
        return NULL;
    }
    sb->len = 0;
    sb->capacity = INITIAL_CAPACITY;
    sb->data[0] = '\0';
    return sb;
}

void sb_free(StringBuilder *sb) {
    if (sb) {
        free(sb->data);
        free(sb);
    }
}

void sb_append(StringBuilder *sb, char c) {
    if (sb->len + 1 >= sb->capacity) {
        size_t new_cap = sb->capacity * 2;
        char *new_data = realloc(sb->data, new_cap);
        if (!new_data) return;
        sb->data = new_data;
        sb->capacity = new_cap;
    }
    sb->data[sb->len++] = c;
    sb->data[sb->len] = '\0';
}

void sb_append_str(StringBuilder *sb, const char *str) {
    if (!str) return;
    sb_append_n(sb, str, strlen(str));
}

void sb_append_n(StringBuilder *sb, const char *str, size_t n) {
    if (!str || n == 0) return;
    
    if (sb->len + n + 1 > sb->capacity) {
        size_t new_cap = sb->capacity * 2;
        while (new_cap < sb->len + n + 1) new_cap *= 2;
        char *new_data = realloc(sb->data, new_cap);
        if (!new_data) return;
        sb->data = new_data;
        sb->capacity = new_cap;
    }
    memcpy(sb->data + sb->len, str, n);
    sb->len += n;
    sb->data[sb->len] = '\0';
}

char* sb_to_string(StringBuilder *sb) {
    if (!sb) return NULL;
    char *result = sb->data;
    sb->data = NULL;
    sb->len = 0;
    sb->capacity = 0;
    return result;
}

char* sb_copy_string(const StringBuilder *sb) {
    if (!sb || !sb->data) return NULL;
    return strdup(sb->data);
}

void sb_clear(StringBuilder *sb) {
    if (sb) {
        sb->len = 0;
        if (sb->data) sb->data[0] = '\0';
    }
}
