// src/disk.h
#ifndef DISK_H
#define DISK_H

/* Disk image / in-memory filesystem header
 *
 * Public API for a small in-memory file system used by NeuMiniOS.
 *
 * Notes:
 *  - Paths may be absolute (start with '/') or relative to current_dir.
 *  - Component '.' and '..' are supported.
 *  - Many functions return DISK_OK (0) on success or a negative DISK_ERR_* constant on error.
 *  - Functions that return allocated memory (e.g. disk_get_cwd_path) document who must free.
 */

#include <stddef.h>
#include <time.h>
#include <limits.h>

#ifndef MAX_NAME_LEN
#define MAX_NAME_LEN 128
#endif

/* Standard return codes (used by disk API) */
#define DISK_OK                    0   /* success */
#define DISK_ERR_GENERIC          -1   /* generic error */
#define DISK_ERR_NOTFOUND         -2   /* path/node not found */
#define DISK_ERR_EXISTS           -3   /* name already exists */
#define DISK_ERR_DIR_NOT_EMPTY    -4   /* directory not empty (cannot delete) */
#define DISK_ERR_NO_SPACE         -5   /* not enough space on disk image */
#define DISK_ERR_IO               -6   /* I/O error reading host file */
#define DISK_ERR_NAME_TOOLONG     -7   /* path component too long */
#define DISK_ERR_NOT_A_DIR        -8   /* expected a directory but found file */
#define DISK_ERR_INVALID_NAME     -9   /* name contains invalid characters or is '.'/'..' */

/* File type: either a regular file or a directory */
typedef enum {
    FILETYPE_FILE = 0,
    FILETYPE_DIR  = 1
} FileType;

/* FileNode: node in tree; directories have children (first_child -> next_sibling list) */
typedef struct FileNode {
    char name[MAX_NAME_LEN];        /* filename (no slashes) or directory name */
    FileType type;
    size_t offset;                  /* valid for FILETYPE_FILE: byte offset in disk image */
    size_t size;                    /* valid for FILETYPE_FILE: size in bytes */
    time_t ctime;                   /* creation time */
    struct FileNode *parent;        /* parent directory (NULL for root) */
    struct FileNode *first_child;   /* first child (for directories) */
    struct FileNode *next_sibling;  /* sibling in parent's child list */
} FileNode;

/* DiskImage: in-memory disk image and root/cwd pointers */
typedef struct {
    unsigned char *image;    /* allocated disk image bytes (capacity bytes) */
    size_t capacity;         /* total capacity in bytes */
    size_t used;             /* bytes currently used in image */
    FileNode *root;          /* root directory node ("/") */
    FileNode *current_dir;   /* current working directory pointer */
} DiskImage;


/* Initialize and free disk image
 *  disk_init: allocate disk image buffer and create root directory.
 *     returns DISK_OK (0) on success, DISK_ERR_GENERIC (-1) on allocation failure.
 *  disk_free_all: free all memory associated with disk (nodes + image).
 */
int disk_init(DiskImage *d, size_t capacity);
void disk_free_all(DiskImage *d);


/* Add file content:
 *  disk_add_buf(d, name, buf, size)
 *    - name may be a simple name (created in current_dir) or a path (e.g. "sub/hello.txt" or "/dir/hello")
 *    - buf may be NULL for zero-length files
 *    Return codes: DISK_OK, DISK_ERR_GENERIC, DISK_ERR_EXISTS, DISK_ERR_NO_SPACE, DISK_ERR_NAME_TOOLONG, DISK_ERR_INVALID_NAME
 */
int disk_add_buf(DiskImage *d, const char *name, const unsigned char *buf, size_t size);

/* Add a host file (read from hostpath) into the disk image under 'name' */
int disk_add_hostfile(DiskImage *d, const char *name, const char *hostpath);
/* possible returns: DISK_OK, DISK_ERR_IO, DISK_ERR_NO_SPACE, DISK_ERR_GENERIC, etc. */


/* Find a node by path (absolute or relative).
 *  Returns pointer to FileNode or NULL if not found.
 *  Caller should NOT free the returned node.
 */
FileNode* disk_find(DiskImage *d, const char *path);


/* Delete node (file or directory).
 *  For files: compacts image (moves subsequent bytes left) and updates offsets.
 *  For directories: only empty directories are removed (no recursive rm).
 *  Return codes:
 *    DISK_OK, DISK_ERR_GENERIC, DISK_ERR_NOTFOUND, DISK_ERR_DIR_NOT_EMPTY
 */
int disk_delete(DiskImage *d, const char *path);


/* Copy a file:
 *  disk_copy(d, src_path, dst_name)
 *    - src_path: path to existing source file
 *    - dst_name: destination name (may be a name or a path under current_dir)
 *  Return codes: DISK_OK, DISK_ERR_NOTFOUND, DISK_ERR_EXISTS, DISK_ERR_NO_SPACE, DISK_ERR_GENERIC
 */
int disk_copy(DiskImage *d, const char *src_path, const char *dst_name);


/* Rename a node within its parent:
 *  disk_rename(d, old_path, new_name)
 *    - new_name is a simple name (no '/')
 *  Return codes: DISK_OK, DISK_ERR_NOTFOUND, DISK_ERR_EXISTS, DISK_ERR_GENERIC
 */
int disk_rename(DiskImage *d, const char *old_path, const char *new_name);


/* List files under current_dir (or path if you implement extension).
 *  Output is printed to stdout by implementation.
 */
void disk_list(DiskImage *d);


/* View file contents or directory listing:
 *  disk_view(d, path)
 *    - if path is directory -> prints directory children
 *    - if path is file -> prints contents (non-printable bytes shown as \xNN)
 */
void disk_view(DiskImage *d, const char *path);


/* Directory operations:
 *  disk_mkdir(d, path)   -- create single directory (parent must exist)
 *  disk_mkdir_p(d, path) -- create parents as needed (mkdir -p)
 *  disk_cd(d, path)      -- change current working directory (supports ., .., absolute and relative paths)
 *
 *  Return codes:
 *    DISK_OK, DISK_ERR_NOTFOUND, DISK_ERR_EXISTS, DISK_ERR_GENERIC, DISK_ERR_NAME_TOOLONG, DISK_ERR_NOT_A_DIR, ...
 */
int disk_mkdir(DiskImage *d, const char *path);
int disk_mkdir_p(DiskImage *d, const char *path);
int disk_cd(DiskImage *d, const char *path);

/* Utility: get an allocated string with current working directory absolute path.
 *  Returns malloc'd string on success (caller must free()), or NULL on error.
 */
char *disk_get_cwd_path(DiskImage *d);

/* Optional: stat-like helper (prints to stdout or returns struct in future)
 *  Return codes: DISK_OK, DISK_ERR_NOTFOUND, DISK_ERR_GENERIC
 */
int disk_stat(DiskImage *d, const char *path);

#endif /* DISK_H */