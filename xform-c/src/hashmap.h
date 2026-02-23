#ifndef HASHMAP_H
#define HASHMAP_H

#include <stddef.h>
#include <stdbool.h>

/* Simple hash map for string -> void* */
typedef struct HashMap HashMap;

typedef struct {
    const char *key;
    void *value;
} HMEntry;

HashMap* hm_new(void);
void hm_free(HashMap *hm);
void hm_free_with_values(HashMap *hm, void (*free_fn)(void*));
void* hm_get(HashMap *hm, const char *key);
bool hm_set(HashMap *hm, const char *key, void *value);
bool hm_contains(HashMap *hm, const char *key);
size_t hm_size(HashMap *hm);
HMEntry* hm_entries(HashMap *hm, size_t *count);  /* caller frees array, not keys/values */

#endif
