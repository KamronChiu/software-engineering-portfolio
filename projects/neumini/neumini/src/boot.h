// src/boot.h
#ifndef BOOT_H
#define BOOT_H

#include "disk.h"

/* Return 0 on success, non-zero on error */
int boot_import_dir(DiskImage *d, const char *hostdir);

#endif // BOOT_H