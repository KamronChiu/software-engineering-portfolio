// src/proc.h
#ifndef PROC_H
#define PROC_H

/* Process management public API for NeuMiniOS
 *
 * Notes:
 *  - Keep function names stable (other modules like cli.c call these).
 *  - If you change the maximum number of concurrent processes, update PROC_MAX_PROCS
 *    here so user-visible helpers can use the same value.
 */

#include <sys/types.h>
#include "disk.h"

#ifndef PROC_MAX_PROCS
/* Public symbol for the maximum number of process slots (tweak if needed) */
#define PROC_MAX_PROCS 5
#endif

/* Initialize process subsystem (install SIGCHLD handler etc). */
int proc_init(void);

/* Launch a file from the in-memory disk image:
 *  - extracts file to /tmp, forks and execs it, and tracks it in process table.
 *  - returns 0 on success (launched and tracked), or negative error code.
 */
int proc_add_run(DiskImage *d, const char *filename);

/* Print process list and recent history to stdout. */
void proc_list(void);

/* Stop a NeuMiniOS process by its neumini-assigned PID:
 *  - returns 0 on success (SIGTERM sent), -1 not found, -2 already finished, -3 kill failed.
 */
int proc_stop_by_neu(int neumini_pid);

/* Non-blocking reap: call periodically in main loop to collect exited children. */
void proc_reap_nonblocking(void);

/* Cleanup all process resources (kill running, remove tmp files, free memory). */
void proc_cleanup(void);

/* Utility / monitoring helpers */
int get_total_process_count(void);    /* number of tracked slots in use */
int get_running_process_count(void);  /* number of processes currently running */

/* Find internal slot index by neumini pid (returns slot index or -1).
 * This is a small helper exposed for debugging/advanced usage; prefer proc_stop_by_neu
 * from user-facing code. */
int proc_find_slot_by_neu(int neumini_pid);

#endif /* PROC_H */