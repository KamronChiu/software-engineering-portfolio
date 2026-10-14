// src/boot.c
#define _POSIX_C_SOURCE 200809L
#include "boot.h"
#include "disk.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>
#include <unistd.h>

#define PATH_BUFSZ 4096

/* Helper: print current time string */
static void print_time_now(void) {
    time_t t = time(NULL);
    char buf[64];
    struct tm tm;
    localtime_r(&t, &tm);
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &tm);
    printf("%s", buf);
}

/* Recursive importer:
   host_dir: actual host filesystem path (absolute or relative)
   disk_prefix: path prefix to use inside DiskImage (relative to disk current dir, root at boot)
     - if disk_prefix == NULL or empty -> import into current dir (root)
     - else files will be created as "disk_prefix/name" and directories as "disk_prefix/subdir"
*/
static int boot_import_dir_recursive(DiskImage *d, const char *host_dir, const char *disk_prefix,
                                     size_t *out_count, size_t *out_bytes)
{
    DIR *dp = opendir(host_dir);
    if (!dp) {
        fprintf(stderr, "Error: cannot open host dir '%s': %s\n", host_dir, strerror(errno));
        return -1;
    }

    struct dirent *ent;
    while ((ent = readdir(dp)) != NULL) {
        const char *name = ent->d_name;
        if (strcmp(name, ".") == 0 || strcmp(name, "..") == 0) continue;

        char host_path[PATH_BUFSZ];
        if (snprintf(host_path, sizeof(host_path), "%s/%s", host_dir, name) >= (int)sizeof(host_path)) {
            fprintf(stderr, "Warning: path too long, skipping '%s/%s'\n", host_dir, name);
            continue;
        }

        char disk_path[PATH_BUFSZ];
        if (disk_prefix && disk_prefix[0] != '\0') {
            if (snprintf(disk_path, sizeof(disk_path), "%s/%s", disk_prefix, name) >= (int)sizeof(disk_path)) {
                fprintf(stderr, "Warning: disk path too long, skipping '%s'\n", disk_path);
                continue;
            }
        } else {
            // top-level: use name directly
            strncpy(disk_path, name, sizeof(disk_path)-1);
            disk_path[sizeof(disk_path)-1] = '\0';
        }

        struct stat st;
        if (stat(host_path, &st) != 0) {
            fprintf(stderr, "Warning: cannot stat '%s': %s (skipping)\n", host_path, strerror(errno));
            continue;
        }

        if (S_ISDIR(st.st_mode)) {
            // create directory in disk and recurse
            int r = disk_mkdir(d, disk_path);
            if (r == 0) {
                printf("  imported dir : %s\n", disk_path);
            } else if (r == -3) {
                // already exists
                printf("  dir exists   : %s\n", disk_path);
            } else {
                printf("  dir import failed: %s (code %d)\n", disk_path, r);
            }

            // recurse into child directory
            int rr = boot_import_dir_recursive(d, host_path, disk_path, out_count, out_bytes);
            if (rr != 0) {
                // continue importing others even if recursion had errors
                fprintf(stderr, "Warning: error importing dir '%s'\n", host_path);
            }
        } else if (S_ISREG(st.st_mode)) {
            // regular file -> import contents
            long fsize = (long)st.st_size;
            int r = disk_add_hostfile(d, disk_path, host_path);
            if (r == 0) {
                printf("  imported file: %s (%ld bytes)\n", disk_path, fsize);
                if (out_count) (*out_count)++;
                if (out_bytes) (*out_bytes) += (size_t)fsize;
            } else {
                // map some error codes to readable messages
                if (r == -4) {
                    printf("  import failed : %s (%ld bytes) - insufficient disk capacity\n", disk_path, fsize);
                } else if (r == -5) {
                    printf("  import failed : %s - IO/read error\n", disk_path);
                } else if (r == -3) {
                    printf("  import failed : %s - name exists\n", disk_path);
                } else {
                    printf("  import failed : %s (code %d)\n", disk_path, r);
                }
            }
        } else {
            // skip other types (symlink, device, etc)
            printf("  skipping non-regular: %s\n", host_path);
        }
    }

    if (closedir(dp) != 0) {
        fprintf(stderr, "Warning: closedir failed for '%s': %s\n", host_dir, strerror(errno));
    }

    // After processing this directory, print a brief disk usage snapshot (optional)
    printf("  [snapshot] disk used: %zu / %zu bytes\n", d->used, d->capacity);

    return 0;
}

/* Public API: boot_import_dir
   Returns 0 on success, non-zero on error.
   On success prints an import summary including number of imported files and total bytes,
   and final disk usage summary.
*/
int boot_import_dir(DiskImage *d, const char *hostdir) {
    if (!d || !hostdir) return -1;

    printf("========================================\n");
    printf(" NeuBoot Import - starting\n");
    printf(" Time: ");
    print_time_now();
    printf("\n Source: %s\n", hostdir);
    printf("========================================\n");

    size_t imported_files = 0;
    size_t imported_bytes = 0;

    int rc = boot_import_dir_recursive(d, hostdir, "", &imported_files, &imported_bytes);
    if (rc != 0) {
        fprintf(stderr, "Error: boot_import_dir_recursive returned %d\n", rc);
        // continue to print summary even if errors occurred
    }

    printf("----------------------------------------\n");
    printf(" NeuBoot Import - summary\n");
    printf(" Imported files : %zu\n", imported_files);
    printf(" Imported bytes : %zu\n", imported_bytes);
    printf(" Disk capacity  : %zu bytes\n", d->capacity);
    printf(" Disk used      : %zu bytes\n", d->used);
    printf(" Free space     : %zu bytes\n", (d->capacity > d->used ? d->capacity - d->used : 0));
    printf(" Time finished  : ");
    print_time_now();
    printf("\n");
    printf("========================================\n");

    return (rc == 0 ? 0 : rc);
}