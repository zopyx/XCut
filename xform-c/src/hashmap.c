#include "hashmap.h"
#include <stdlib.h>
#include <string.h>

#define INITIAL_CAPACITY 16
#define LOAD_FACTOR 0.75

typedef struct Entry {
    char *key;
    void *value;
    struct Entry *next;
} Entry;

struct HashMap {
    Entry **buckets;
    size_t capacity;
    size_t size;
};

static unsigned long hash(const char *str) {
    unsigned long hash = 5381;
    int c;
    while ((c = *str++))
        hash = ((hash << 5) + hash) + c;
    return hash;
}

HashMap* hm_new(void) {
    HashMap *hm = malloc(sizeof(HashMap));
    if (!hm) return NULL;
    hm->buckets = calloc(INITIAL_CAPACITY, sizeof(Entry*));
    if (!hm->buckets) {
        free(hm);
        return NULL;
    }
    hm->capacity = INITIAL_CAPACITY;
    hm->size = 0;
    return hm;
}

void hm_free(HashMap *hm) {
    if (!hm) return;
    if (!hm->buckets) {
        free(hm);
        return;
    }
    for (size_t i = 0; i < hm->capacity; i++) {
        Entry *e = hm->buckets[i];
        while (e) {
            Entry *next = e->next;
            free(e->key);
            free(e);
            e = next;
        }
    }
    free(hm->buckets);
    free(hm);
}

void hm_free_with_values(HashMap *hm, void (*free_fn)(void*)) {
    if (!hm) return;
    for (size_t i = 0; i < hm->capacity; i++) {
        Entry *e = hm->buckets[i];
        while (e) {
            Entry *next = e->next;
            free(e->key);
            if (free_fn) free_fn(e->value);
            free(e);
            e = next;
        }
    }
    free(hm->buckets);
    free(hm);
}

void* hm_get(HashMap *hm, const char *key) {
    if (!hm || !key) return NULL;
    unsigned long h = hash(key) % hm->capacity;
    Entry *e = hm->buckets[h];
    while (e) {
        if (strcmp(e->key, key) == 0)
            return e->value;
        e = e->next;
    }
    return NULL;
}

bool hm_set(HashMap *hm, const char *key, void *value) {
    if (!hm || !key) return false;
    unsigned long h = hash(key) % hm->capacity;
    Entry *e = hm->buckets[h];
    while (e) {
        if (strcmp(e->key, key) == 0) {
            e->value = value;
            return true;
        }
        e = e->next;
    }
    
    /* Insert new entry */
    e = malloc(sizeof(Entry));
    if (!e) return false;
    e->key = strdup(key);
    if (!e->key) {
        free(e);
        return false;
    }
    e->value = value;
    e->next = hm->buckets[h];
    hm->buckets[h] = e;
    hm->size++;
    return true;
}

bool hm_contains(HashMap *hm, const char *key) {
    return hm_get(hm, key) != NULL;
}

size_t hm_size(HashMap *hm) {
    return hm ? hm->size : 0;
}

HMEntry* hm_entries(HashMap *hm, size_t *count) {
    if (!hm || !count) return NULL;
    *count = hm->size;
    if (hm->size == 0) return NULL;
    
    HMEntry *entries = malloc(hm->size * sizeof(HMEntry));
    if (!entries) return NULL;
    
    size_t idx = 0;
    for (size_t i = 0; i < hm->capacity; i++) {
        Entry *e = hm->buckets[i];
        while (e) {
            entries[idx].key = e->key;
            entries[idx].value = e->value;
            idx++;
            e = e->next;
        }
    }
    return entries;
}
