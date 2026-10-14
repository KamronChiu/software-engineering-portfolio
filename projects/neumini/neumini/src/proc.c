/* src/proc.c - merged improvements: robust tmp-extract, SIGCHLD flag, stats APIs */
#define _POSIX_C_SOURCE 200809L
#include "proc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <errno.h>
#include <signal.h>
#include <limits.h>
#include <fcntl.h>
#include <time.h>   /* for nanosleep and time_t */

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

#define MAX_PROCS 5
#define STAT_FINISHED    0
#define STAT_RUNNING     1
#define STAT_TERMINATING 2

/* signal flag set by SIGCHLD handler -> main loop / reap will handle it */
static volatile sig_atomic_t child_exited_flag = 0;

/* small async-signal-safe handler: only set the flag */
static void sigchld_set_flag(int signo) {
    (void)signo;
    child_exited_flag = 1;
}

/* Array-based process slot (active processes) */
typedef struct {
    int used;                      /* 0 = free, 1 = occupied */
    int neumini_pid;               /* NeuMiniOS PID (unique assigned) */
    pid_t host_pid;                /* host OS pid (forked child) */
    char name[MAX_NAME_LEN];       /* filename */
    int active;                    /* STAT_* */
    char tmp_path[PATH_MAX];       /* extracted temp executable path */
    time_t start_time;
} ProcSlot;

/* History linked-list node (keeps all runs for display) */
typedef struct HistNode {
    int neumini_pid;
    pid_t host_pid;
    char name[MAX_NAME_LEN];
    int active;
    time_t start_time;
    struct HistNode *next;
} HistNode;

/* globals */
static ProcSlot proc_table[MAX_PROCS];
static HistNode *history_head = NULL;
static int next_neu_pid = 1;

/* forward declarations for new helpers */
int get_total_process_count(void);
int get_running_process_count(void);
int proc_find_slot_by_neu(int neumini_pid);

/* init */
int proc_init(void) {
    for (int i = 0; i < MAX_PROCS; ++i) {
        proc_table[i].used = 0;
        proc_table[i].tmp_path[0] = '\0';
        proc_table[i].active = STAT_FINISHED;
    }
    history_head = NULL;
    next_neu_pid = 1;
    child_exited_flag = 0;

    /* install simple SIGCHLD handler that sets a flag; actual reaping done elsewhere */
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sigchld_set_flag;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) != 0) {
        /* non-fatal, just log */
        perror("sigaction(SIGCHLD)");
    }

    return 0;
}

/* helper: find free slot index or -1 */
static int find_free_slot(void) {
    for (int i = 0; i < MAX_PROCS; ++i) {
        if (!proc_table[i].used) return i;
    }
    return -1;
}

/* helper: find slot by neumini_pid */
static int find_slot_by_neu_local(int neumini_pid) {
    for (int i = 0; i < MAX_PROCS; ++i) {
        if (proc_table[i].used && proc_table[i].neumini_pid == neumini_pid) return i;
    }
    return -1;
}

/* exported wrapper (declared in proc.h) */
int proc_find_slot_by_neu(int neumini_pid) {
    return find_slot_by_neu_local(neumini_pid);
}

/* helper: find slot by host pid */
static int find_slot_by_host(pid_t host) {
    for (int i = 0; i < MAX_PROCS; ++i) {
        if (proc_table[i].used && proc_table[i].host_pid == host) return i;
    }
    return -1;
}

/* append history node */
static void history_add(int neu, pid_t host, const char *name, int active) {
    HistNode *n = malloc(sizeof(HistNode));
    if (!n) return;
    n->neumini_pid = neu;
    n->host_pid = host;
    if (name) {
        snprintf(n->name, sizeof(n->name), "%s", name);
    } else {
        n->name[0] = '\0';
    }
    n->active = active;
    n->start_time = time(NULL);
    n->next = history_head;
    history_head = n;
}

/* update history entry by host pid (mark finished) */
static void history_mark_finished_by_host(pid_t host) {
    HistNode *h = history_head;
    while (h) {
        if (h->host_pid == host) { h->active = 0; return; }
        h = h->next;
    }
}

/* helper to extract a FileNode to a tmp file; returns strdup'd path or NULL
   (robust writing loop to avoid short-writes and proper cleanup). */
static char *extract_to_tmp(DiskImage *d, FileNode *f, int neu) {
    if (!d || !f) return NULL;
    char tmpl[PATH_MAX];
    snprintf(tmpl, sizeof(tmpl), "/tmp/neumini_%d_XXXXXX", neu);
    int fd = mkstemp(tmpl);
    if (fd < 0) return NULL;

    if (f->size > 0) {
        size_t total = (size_t)f->size;
        size_t written = 0;
        unsigned char *src = d->image + f->offset;
        while (written < total) {
            ssize_t w = write(fd, src + written, total - written);
            if (w < 0) {
                if (errno == EINTR) continue;
                close(fd);
                unlink(tmpl);
                return NULL;
            }
            written += (size_t)w;
        }
    }
    if (fchmod(fd, 0755) != 0) {
        close(fd);
        unlink(tmpl);
        return NULL;
    }
    close(fd);
    return strdup(tmpl);
}

/* proc_add_run: run file from disk image
   returns:
     0 = launched and tracked
    -2 = file not found
    -3 = too many concurrent processes
    other negative = error
*/
int proc_add_run(DiskImage *d, const char *filename) {
    if (!filename) return -1;
    FileNode *f = disk_find(d, filename);
    if (!f) return -2;

    int slot = find_free_slot();
    if (slot < 0) return -3;

    int neu = next_neu_pid++;
    char *tmp_path = extract_to_tmp(d, f, neu);
    if (!tmp_path) return -4;

    pid_t pid = fork();
    if (pid < 0) {
        free(tmp_path);
        return -6;
    }

    if (pid == 0) {
        /* child */
        execl(tmp_path, tmp_path, (char *)NULL);
        perror("execl");
        _exit(127);
    } else {
        /* parent: populate slot */
        proc_table[slot].used = 1;
        proc_table[slot].neumini_pid = neu;
        proc_table[slot].host_pid = pid;
        snprintf(proc_table[slot].name, sizeof(proc_table[slot].name), "%s", filename);
        proc_table[slot].active = STAT_RUNNING;
        proc_table[slot].start_time = time(NULL);
        snprintf(proc_table[slot].tmp_path, sizeof(proc_table[slot].tmp_path), "%s", tmp_path);

        history_add(neu, pid, filename, 1);

        printf("Launched neumini pid=%d (host pid=%d)\n", neu, (int)pid);

        /* immediate non-blocking reap check in case the child exited right away */
        {
            int status;
            pid_t w = waitpid(pid, &status, WNOHANG);
            if (w == pid) {
                /* child already exited; mark as finished and cleanup tmp */
                proc_table[slot].active = STAT_FINISHED;
                history_mark_finished_by_host(pid);
                if (proc_table[slot].tmp_path[0] != '\0') {
                    unlink(proc_table[slot].tmp_path);
                    proc_table[slot].tmp_path[0] = '\0';
                }
                proc_table[slot].used = 0;
            }
        }

        free(tmp_path);
        return 0;
    }
}

/* return number of slots currently used (total processes tracked) */
int get_total_process_count(void) {
    int cnt = 0;
    for (int i = 0; i < MAX_PROCS; ++i) if (proc_table[i].used) cnt++;
    return cnt;
}

/* return number of processes currently running */
int get_running_process_count(void) {
    int cnt = 0;
    for (int i = 0; i < MAX_PROCS; ++i) if (proc_table[i].used && proc_table[i].active == STAT_RUNNING) cnt++;
    return cnt;
}

/* list: print active slots and history (history newer first) */
void proc_list(void) {
    int total = get_total_process_count();
    int running = get_running_process_count();
    printf("\n=== NeuMiniOS Process List ===\n");
    printf("Processes: running=%d total=%d (max=%d)\n",running, total, MAX_PROCS);
    printf("Memory: %d/%d process slots used\n", total, MAX_PROCS);
    printf("PID\tProgram Name\t\tSystem PID\tStatus\n");
    printf("---\t------------\t\t----------\t------\n");
    for (int i = 0; i < MAX_PROCS; ++i) {
        if (!proc_table[i].used) continue;
        const char *status_str = (proc_table[i].active == STAT_RUNNING) ? "Running" : (proc_table[i].active == STAT_TERMINATING ? "Terminating" : "Finished");
        printf("%d\t%-20s\t%d\t\t%s\n",
               proc_table[i].neumini_pid,
               proc_table[i].name,
               (int)proc_table[i].host_pid,
               status_str);
    }
    if (total == 0) printf("  (no active tracked processes)\n");

    printf("Recent history (most recent first):\n");
    HistNode *h = history_head;
    if (!h) { printf("  (no history)\n"); return; }
    int cnt = 0;
    while (h && cnt < 20) {
        printf("  neumini_pid=%d host_pid=%d name=%s status=%s\n",
               h->neumini_pid, (int)h->host_pid, h->name, h->active ? "running" : "finished");
        h = h->next; cnt++;
    }
}

/* stop: stop by neumini_pid mark terminating + short poll then reap */
int proc_stop_by_neu(int neumini_pid) {
    int idx = find_slot_by_neu_local(neumini_pid);
    if (idx < 0) return -1;               // not found
    if (proc_table[idx].active == STAT_FINISHED) return -2; // already finished

    pid_t host = proc_table[idx].host_pid;
    if (kill(host, SIGTERM) != 0) {
        return -3; // kill failed
    }

    /* show immediate feedback */
    proc_table[idx].active = STAT_TERMINATING;

    /* short poll up to ~200ms - try to detect quick exit */
    for (int tries = 0; tries < 20; ++tries) {
        /* check existence via kill(pid,0) */
        if (kill(host, 0) != 0) {
            if (errno == ESRCH) {
                /* process no longer exists - try reap */
                int status;
                (void)waitpid(host, &status, WNOHANG);
                proc_table[idx].active = STAT_FINISHED;
                history_mark_finished_by_host(host);
                if (proc_table[idx].tmp_path[0] != '\0') {
                    unlink(proc_table[idx].tmp_path);
                    proc_table[idx].tmp_path[0] = '\0';
                }
                proc_table[idx].used = 0;
                return 0;
            }
            /* other errno from kill(...) - continue polling */
        }
        /* still exists - sleep a bit using nanosleep (portable, avoids implicit usleep warnings) */
        struct timespec _ts = {0, 10000000L}; /* 10 ms */
        nanosleep(&_ts, NULL);
    }

    /* not exited within short window: return success (SIGTERM sent),
       status remains TERMINATING until later reap() updates it */
    return 0;
}

/* reap finished children (non-blocking) */
void proc_reap_nonblocking(void) {
    /* if signaled, clear flag and proceed to reap records */
    if (child_exited_flag) child_exited_flag = 0;

    int status;
    pid_t pid;
    while ((pid = waitpid(-1, &status, WNOHANG)) > 0) {
        int idx = find_slot_by_host(pid);
        if (idx >= 0) {
            proc_table[idx].active = STAT_FINISHED;
            history_mark_finished_by_host(pid);
            if (proc_table[idx].tmp_path[0] != '\0') {
                unlink(proc_table[idx].tmp_path);
                proc_table[idx].tmp_path[0] = '\0';
            }
            proc_table[idx].used = 0;
        } else {
            history_mark_finished_by_host(pid);
        }
    }
}

/* cleanup */
void proc_cleanup(void) {
    for (int i = 0; i < MAX_PROCS; ++i) {
        if (proc_table[i].used && proc_table[i].active == STAT_RUNNING) {
            kill(proc_table[i].host_pid, SIGTERM);
            waitpid(proc_table[i].host_pid, NULL, 0);
            if (proc_table[i].tmp_path[0] != '\0') {
                unlink(proc_table[i].tmp_path);
                proc_table[i].tmp_path[0] = '\0';
            }
            proc_table[i].used = 0;
        } else if (proc_table[i].used && proc_table[i].tmp_path[0] != '\0') {
            unlink(proc_table[i].tmp_path);
            proc_table[i].tmp_path[0] = '\0';
            proc_table[i].used = 0;
        }
    }
    HistNode *h = history_head;
    while (h) { HistNode *n = h->next; free(h); h = n; }
    history_head = NULL;
}