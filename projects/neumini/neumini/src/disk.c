/* src/disk.c - in-memory disk image and simple filesystem */
#define _POSIX_C_SOURCE 200809L
#include "disk.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <limits.h>

/* --- Helper types / forward declarations --- */
static FileNode* create_node(const char *name, FileType type);
static void append_child(FileNode *parent, FileNode *child);
static void free_node_recursive(FileNode *n);
typedef void (*node_func)(FileNode*, void*);
static void walk_nodes(FileNode *n, node_func fn, void *ctx);
static FileNode* resolve_parent_and_name(DiskImage *d, const char *path, char out_name[MAX_NAME_LEN]);
static void adjust_offsets_after(DiskImage *d, size_t threshold, size_t delta);
static void remove_node_from_parent(FileNode *node);
static void node_path(FileNode *n, char *out, size_t outlen);

/* --- Implementations --- */

static FileNode* create_node(const char *name, FileType type) {
    FileNode *n = calloc(1, sizeof(FileNode));
    if (!n) return NULL;
    if (name) {
        /* ensure null-terminated and avoid overflow */
        snprintf(n->name, sizeof(n->name), "%s", name);
    } else {
        n->name[0] = '\0';
    }
    n->type = type;
    n->offset = 0;
    n->size = 0;
    n->ctime = time(NULL);
    n->parent = NULL;
    n->first_child = NULL;
    n->next_sibling = NULL;
    return n;
}

/* append child to parent's child list (at end) */
static void append_child(FileNode *parent, FileNode *child) {
    if (!parent || !child) return;
    child->parent = parent;
    child->next_sibling = NULL;
    if (!parent->first_child) {
        parent->first_child = child;
    } else {
        FileNode *p = parent->first_child;
        while (p->next_sibling) p = p->next_sibling;
        p->next_sibling = child;
    }
}

/* recursive free of subtree */
static void free_node_recursive(FileNode *n) {
    if (!n) return;
    FileNode *c = n->first_child;
    while (c) {
        FileNode *nx = c->next_sibling;
        free_node_recursive(c);
        c = nx;
    }
    free(n);
}

/* preorder walk */
static void walk_nodes(FileNode *n, node_func fn, void *ctx) {
    if (!n || !fn) return;
    fn(n, ctx);
    FileNode *c = n->first_child;
    while (c) {
        walk_nodes(c, fn, ctx);
        c = c->next_sibling;
    }
}

/* Initialize disk image with single root directory */
int disk_init(DiskImage *d, size_t capacity) {
    if (!d) return -1;
    d->image = malloc(capacity);
    if (!d->image) return -1;
    d->capacity = capacity;
    d->used = 0;
    d->root = create_node("/", FILETYPE_DIR);
    if (!d->root) { free(d->image); d->image = NULL; return -1; }
    d->current_dir = d->root;
    return 0;
}

void disk_free_all(DiskImage *d) {
    if (!d) return;
    if (d->root) {
        free_node_recursive(d->root);
        d->root = NULL;
        d->current_dir = NULL;
    }
    if (d->image) {
        free(d->image);
        d->image = NULL;
    }
    d->capacity = 0;
    d->used = 0;
}

/* Utility: build path string for node (e.g., /, /dir/file) */
static void node_path(FileNode *n, char *out, size_t outlen) {
    if (!n || !out || outlen == 0) return;
    /* collect names up to a reasonable depth */
    char stack[64][MAX_NAME_LEN];
    int depth = 0;
    FileNode *cur = n;
    while (cur && cur->parent && depth < (int) (sizeof(stack) / sizeof(stack[0]))) {
        /* use snprintf to avoid truncation warnings */
        snprintf(stack[depth], MAX_NAME_LEN, "%s", cur->name);
        depth++;
        cur = cur->parent;
    }
    /* cur is root */
    size_t pos = 0;
    if (depth == 0) {
        if (outlen > 1) { out[0] = '/'; out[1] = '\0'; }
        else if (outlen == 1) out[0] = '\0';
        return;
    }
    /* build from root down */
    for (int i = depth - 1; i >= 0; --i) {
        size_t need = strlen(stack[i]) + 2; /* '/' + name + '\0' maybe */
        if (pos + need >= outlen) break;
        out[pos++] = '/';
        size_t l = strlen(stack[i]);
        memcpy(out + pos, stack[i], l);
        pos += l;
    }
    out[pos] = '\0';
}

/* improved disk_find: support absolute/relative, '.' and '..', ignore repeated slashes */
FileNode* disk_find(DiskImage *d, const char *path) {
    if (!d || !path) return NULL;
    /* empty path -> current dir */
    if (path[0] == '\0') return d->current_dir;
    const char *p = path;
    FileNode *cur = NULL;
    if (p[0] == '/') {
        cur = d->root;
        /* skip leading slashes */
        while (*p == '/') p++;
    } else {
        cur = d->current_dir;
    }
    char comp[MAX_NAME_LEN];
    while (*p) {
        /* read next component safely */
        size_t ci = 0;
        while (*p && *p != '/') {
            if (ci < sizeof(comp) - 1) comp[ci++] = *p;
            else return NULL; /* component too long */
            p++;
        }
        comp[ci] = '\0';
        /* skip empty component (from repeated slashes) */
        if (ci == 0) {
            if (*p == '/') { while (*p == '/') p++; continue; }
            else break;
        }
        if (strcmp(comp, ".") == 0) {
            /* no-op */
        } else if (strcmp(comp, "..") == 0) {
            if (cur->parent) cur = cur->parent;
            else cur = d->root;
        } else {
            FileNode *child = cur->first_child;
            while (child) {
                if (strcmp(child->name, comp) == 0) break;
                child = child->next_sibling;
            }
            if (!child) return NULL;
            cur = child;
        }
        while (*p == '/') p++;
    }
    return cur;
}

/* resolve_parent_and_name:
   - out_name is filled with the final component (basename)
   - returns parent FileNode* or NULL on error */
static FileNode* resolve_parent_and_name(DiskImage *d, const char *path, char out_name[MAX_NAME_LEN]) {
    if (!d || !path || !out_name) return NULL;
    char tmp[PATH_MAX];
    if (strlen(path) >= sizeof(tmp)) return NULL;
    strncpy(tmp, path, sizeof(tmp)-1);
    tmp[sizeof(tmp)-1] = '\0';
    /* strip trailing slashes (but keep single leading slash) */
    size_t L = strlen(tmp);
    while (L > 1 && tmp[L-1] == '/') { tmp[L-1] = '\0'; L--; }
    char *last = strrchr(tmp, '/');
    if (!last) {
        /* no slash -> parent is current_dir */
        strncpy(out_name, tmp, MAX_NAME_LEN-1);
        out_name[MAX_NAME_LEN-1] = '\0';
        return d->current_dir;
    } else {
        if (last == tmp) {
            /* path like "/name" -> parent is root */
            strncpy(out_name, last + 1, MAX_NAME_LEN-1);
            out_name[MAX_NAME_LEN-1] = '\0';
            return d->root;
        } else {
            size_t plen = last - tmp;
            if (plen >= sizeof(tmp)) return NULL;
            char parentpath[PATH_MAX];
            memcpy(parentpath, tmp, plen);
            parentpath[plen] = '\0';
            strncpy(out_name, last + 1, MAX_NAME_LEN-1);
            out_name[MAX_NAME_LEN-1] = '\0';
            FileNode *parent = disk_find(d, parentpath);
            return parent;
        }
    }
}

/* disk_add_buf: add a file with given name (name may be path) */
int disk_add_buf(DiskImage *d, const char *name, const unsigned char *buf, size_t size) {
    if (!d || !name) return -1;
    char base[MAX_NAME_LEN];
    FileNode *parent = resolve_parent_and_name(d, name, base);
    if (!parent) return -1;
    if (parent->type != FILETYPE_DIR) return -1;
    if (base[0] == '\0') return -1;
    /* exists? */
    FileNode *ch = parent->first_child;
    while (ch) {
        if (strcmp(ch->name, base) == 0) return -3; /* exists */
        ch = ch->next_sibling;
    }
    if (size > 0 && d->used + size > d->capacity) return -4; /* no space */
    FileNode *node = create_node(base, FILETYPE_FILE);
    if (!node) return -1;
    node->offset = d->used;
    node->size = size;
    append_child(parent, node);
    if (size > 0 && buf) {
        memcpy(d->image + d->used, buf, size);
        d->used += size;
    }
    return 0;
}

/* disk_add_hostfile: read host file and add under target name */
int disk_add_hostfile(DiskImage *d, const char *name, const char *hostpath) {
    if (!d || !name || !hostpath) return -1;
    FILE *f = fopen(hostpath, "rb");
    if (!f) return -5;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return -5; }
    long sz = ftell(f);
    if (sz < 0) { fclose(f); return -5; }
    rewind(f);
    unsigned char *buf = NULL;
    if (sz > 0) {
        buf = malloc((size_t)sz);
        if (!buf) { fclose(f); return -1; }
        size_t r = fread(buf, 1, (size_t)sz, f);
        fclose(f);
        if (r != (size_t)sz) { free(buf); return -5; }
    } else {
        fclose(f);
    }
    int res = disk_add_buf(d, name, buf, (size_t)sz);
    if (buf) free(buf);
    return res;
}

/* adjust offsets helper */
typedef struct {
    size_t threshold;
    size_t delta;
} AdjustCtx;
static void adjust_offsets_fn(FileNode *n, void *ctx) {
    AdjustCtx *a = (AdjustCtx *)ctx;
    if (!n || !a) return;
    if (n->type == FILETYPE_FILE && n->offset > a->threshold) {
        n->offset -= a->delta;
    }
}
static void adjust_offsets_after(DiskImage *d, size_t threshold, size_t delta) {
    if (!d) return;
    AdjustCtx ctx;
    ctx.threshold = threshold;
    ctx.delta = delta;
    walk_nodes(d->root, adjust_offsets_fn, &ctx);
}

/* remove node from its parent's child list (does not free node) */
static void remove_node_from_parent(FileNode *node) {
    if (!node || !node->parent) return;
    FileNode *parent = node->parent;
    FileNode *p = parent->first_child;
    FileNode *prev = NULL;
    while (p) {
        if (p == node) {
            if (prev) prev->next_sibling = p->next_sibling;
            else parent->first_child = p->next_sibling;
            break;
        }
        prev = p;
        p = p->next_sibling;
    }
    node->parent = NULL;
    node->next_sibling = NULL;
}

/* disk_delete:  file or empty directory. For files, compact the image. */
int disk_delete(DiskImage *d, const char *path) {
    if (!d || !path) return -1;
    FileNode *node = disk_find(d, path);
    if (!node) return -2; /* not found */
    if (node == d->root) return -1; /* cannot delete root */
    FileNode *parent = node->parent;
    if (!parent) return -1;
    if (node->type == FILETYPE_DIR) {
        if (node->first_child) return -3; /* dir not empty */
        remove_node_from_parent(node);
        free(node);
        return 0;
    } else {
        size_t off = node->offset;
        size_t sz = node->size;
        remove_node_from_parent(node);
        free(node);
        if (off > d->used || off + sz > d->used) return -1;
        if (off + sz == d->used) {
            d->used = off;
            return 0;
        } else {
            size_t tail_bytes = d->used - (off + sz);
            if (tail_bytes > 0) {
                memmove(d->image + off, d->image + off + sz, tail_bytes);
            }
            d->used -= sz;
            adjust_offsets_after(d, off, sz);
            return 0;
        }
    }
}

/* disk_copy: copy src_file to dst_name */
int disk_copy(DiskImage *d, const char *src_path, const char *dst_name) {
    if (!d || !src_path || !dst_name) return -1;
    FileNode *src = disk_find(d, src_path);
    if (!src) return -2;
    if (src->type != FILETYPE_FILE) return -1;
    char base[MAX_NAME_LEN];
    FileNode *parent = resolve_parent_and_name(d, dst_name, base);
    if (!parent) return -1;
    if (parent->type != FILETYPE_DIR) return -1;
    if (base[0] == '\0') return -1;
    FileNode *ch = parent->first_child;
    while (ch) {
        if (strcmp(ch->name, base) == 0) return -3; /* exists */
        ch = ch->next_sibling;
    }
    if (src->size > 0 && d->used + src->size > d->capacity) return -4;
    FileNode *node = create_node(base, FILETYPE_FILE);
    if (!node) return -1;
    node->offset = d->used;
    node->size = src->size;
    append_child(parent, node);
    if (src->size > 0) {
        memcpy(d->image + d->used, d->image + src->offset, src->size);
        d->used += src->size;
    }
    return 0;
}

/* disk_rename */
int disk_rename(DiskImage *d, const char *old_path, const char *new_name) {
    if (!d || !old_path || !new_name) return -1;
    FileNode *n = disk_find(d, old_path);
    if (!n) return -2;
    FileNode *parent = n->parent;
    if (!parent) return -1;
    FileNode *ch = parent->first_child;
    while (ch) {
        if (ch != n && strcmp(ch->name, new_name) == 0) return -3;
        ch = ch->next_sibling;
    }
    snprintf(n->name, MAX_NAME_LEN, "%s", new_name);
    return 0;
}

/* disk_list */
void disk_list(DiskImage *d) {
    if (!d || !d->current_dir) { printf("(no files)\n"); return; }
    char pathbuf[PATH_MAX];
    node_path(d->current_dir, pathbuf, sizeof(pathbuf));
    printf("Files in %s (disk used %zu / %zu bytes):\n", pathbuf, d->used, d->capacity);
    FileNode *c = d->current_dir->first_child;
    if (!c) { printf("  (empty)\n"); return; }
    while (c) {
        char timestr[64] = "-";
        if (c->ctime) {
            struct tm tm;
            localtime_r(&c->ctime, &tm);
            strftime(timestr, sizeof(timestr), "%Y-%m-%d %H:%M:%S", &tm);
        }
        if (c->type == FILETYPE_DIR) {
            printf("  [DIR]  %s\t\t%s\n", c->name, timestr);
        } else {
            printf("  [FILE] %s\t%zu bytes\t%s\n", c->name, c->size, timestr);
        }
        c = c->next_sibling;
    }
}

/* disk_view */
void disk_view(DiskImage *d, const char *path) {
    if (!d || !path) { fprintf(stderr, "view: invalid args\n"); return; }
    FileNode *n = disk_find(d, path);
    if (!n) { printf("view: file not found\n"); return; }
    if (n->type == FILETYPE_DIR) {
        printf("Directory %s:\n", n->name);
        FileNode *c = n->first_child;
        while (c) {
            printf("  %s\n", c->name);
            c = c->next_sibling;
        }
        return;
    }
    if (n->offset > d->used || n->offset + n->size > d->used) {
        fprintf(stderr, "view: file metadata corrupted or out-of-range (offset %zu size %zu, disk used %zu)\n",
                n->offset, n->size, d->used);
        return;
    }
    unsigned char *p = d->image + n->offset;
    printf("---- %s (%zu bytes) ----\n", n->name, n->size);
    for (size_t i = 0; i < n->size; ++i) {
        unsigned char b = p[i];
        if (b >= 32 && b <= 126) putchar(b);
        else printf("\\x%02x", b);
    }
    putchar('\n');
}

/* disk_mkdir (single dir, parent must exist) */
int disk_mkdir(DiskImage *d, const char *path) {
    if (!d || !path || path[0] == '\0') return -1;
    char name[MAX_NAME_LEN];
    FileNode *parent = resolve_parent_and_name(d, path, name);
    if (!parent) return -1;
    if (parent->type != FILETYPE_DIR) return -1;
    FileNode *ch = parent->first_child;
    while (ch) {
        if (strcmp(ch->name, name) == 0) return -3; /* exists */
        ch = ch->next_sibling;
    }
    FileNode *n = create_node(name, FILETYPE_DIR);
    if (!n) return -1;
    append_child(parent, n);
    return 0;
}

/* disk_mkdir_p: like mkdir -p, create intermediates as needed */
int disk_mkdir_p(DiskImage *d, const char *path) {
    if (!d || !path || path[0] == '\0') return -1;
    char tmp[PATH_MAX];
    if (strlen(path) >= sizeof(tmp)) return -7;
    strncpy(tmp, path, sizeof(tmp)-1);
    tmp[sizeof(tmp)-1] = '\0';
    size_t L = strlen(tmp);
    while (L > 1 && tmp[L-1] == '/') { tmp[L-1] = '\0'; L--; }
    FileNode *cur;
    char *p = tmp;
    if (*p == '/') { cur = d->root; while (*p == '/') p++; }
    else cur = d->current_dir;
    char comp[MAX_NAME_LEN];
    while (*p) {
        size_t ci = 0;
        while (*p && *p != '/') {
            if (ci < sizeof(comp) - 1) comp[ci++] = *p;
            else return -7;
            p++;
        }
        comp[ci] = '\0';
        if (ci == 0) { if (*p == '/') { while (*p == '/') p++; continue; } else break; }
        if (strcmp(comp, ".") == 0) { }
        else if (strcmp(comp, "..") == 0) {
            if (cur->parent) cur = cur->parent; else cur = d->root;
        } else {
            FileNode *child = cur->first_child;
            while (child) {
                if (strcmp(child->name, comp) == 0) break;
                child = child->next_sibling;
            }
            if (!child) {
                FileNode *n = create_node(comp, FILETYPE_DIR);
                if (!n) return -1;
                append_child(cur, n);
                cur = n;
            } else {
                if (child->type != FILETYPE_DIR) return -2;
                cur = child;
            }
        }
        while (*p == '/') p++;
    }
    return 0;
}

/* disk_cd */
int disk_cd(DiskImage *d, const char *path) {
    if (!d || !path) return -1;
    if (strcmp(path, "/") == 0) { d->current_dir = d->root; return 0; }
    if (path[0] == '\0') return 0;
    FileNode *n = disk_find(d, path);
    if (!n) return -2;
    if (n->type != FILETYPE_DIR) return -3;
    d->current_dir = n;
    return 0;
}