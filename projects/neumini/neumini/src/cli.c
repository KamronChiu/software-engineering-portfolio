// src/cli.c
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <termios.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>

#include "disk.h"
#include "proc.h"
#include "boot.h"

#define DISK_CAP (8 * 1024 * 1024)
#define INPUT_BUF_SIZE 1024
#define MAX_ARGS 12
#define HISTORY_MAX 100
/* safe write wrapper to avoid 'ignoring return value' warnings */
#define SAFE_WRITE(fd, buf, len) do { ssize_t _r = write((fd),(buf),(len)); (void)_r; } while (0)

/* History linked list */
typedef struct HistNode {
    char *cmd;
    struct HistNode *prev;
    struct HistNode *next;
} HistNode;

static HistNode *hist_head = NULL;
static HistNode *hist_tail = NULL;
static int hist_count = 0;

static void history_add(const char *cmd) {
    if (!cmd || !*cmd) return;
    if (hist_tail && hist_tail->cmd && strcmp(hist_tail->cmd, cmd) == 0) return;
    HistNode *n = malloc(sizeof(HistNode));
    if (!n) return;
    n->cmd = strdup(cmd);
    if (!n->cmd) { free(n); return; } /* on strdup failure */
    n->prev = hist_tail;
    n->next = NULL;
    if (hist_tail) hist_tail->next = n;
    hist_tail = n;
    if (!hist_head) hist_head = n;
    hist_count++;
    if (hist_count > HISTORY_MAX) {
        HistNode *old = hist_head;
        hist_head = old->next;
        if (hist_head) hist_head->prev = NULL;
        free(old->cmd);
        free(old);
        hist_count--;
    }
}

static void history_free_all(void) {
    HistNode *p = hist_head;
    while (p) {
        HistNode *n = p->next;
        free(p->cmd);
        free(p);
        p = n;
    }
    hist_head = hist_tail = NULL;
    hist_count = 0;
}

/* Print history: newest first (like teammate implementation) */
static void history_show(void) {
    if (!hist_tail) {
        printf("(no history)\n");
        return;
    }
    printf("--- command history (most recent first) ---\n");
    int idx = 0;
    HistNode *p = hist_tail;
    while (p && idx < HISTORY_MAX) {
        printf("%3d: %s\n", idx + 1, p->cmd);
        p = p->prev;
        idx++;
    }
    printf("------------------------------------------\n");
}

/* Terminal raw mode helpers */
static struct termios orig_termios;
static int raw_enabled = 0;

static void disable_raw_mode(void) {
    if (!raw_enabled) return;
    tcsetattr(STDIN_FILENO, TCSAFLUSH, &orig_termios);
    raw_enabled = 0;
}

static void enable_raw_mode(void) {
    if (raw_enabled) return;
    if (tcgetattr(STDIN_FILENO, &orig_termios) == -1) return;
    struct termios raw = orig_termios;
    raw.c_lflag &= ~(ICANON | ECHO);
    raw.c_cc[VMIN] = 1;
    raw.c_cc[VTIME] = 0;
    tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw);
    raw_enabled = 1;
}

/* Ensure terminal is restored on signals */
static void restore_and_exit(int signo) {
    (void)signo;
    disable_raw_mode();
    /* re-raise default to get usual termination if desired */
    signal(signo, SIG_DFL);
    raise(signo);
}

/* install signal handlers and atexit restore */
static void install_terminal_handlers(void) {
    atexit(disable_raw_mode);
    signal(SIGINT, restore_and_exit);
    signal(SIGTERM, restore_and_exit);
    signal(SIGHUP, restore_and_exit);
}

static void clear_line_and_prompt(const char *prompt) {
    const char *clear_seq = "\x1b[2K\r";
    SAFE_WRITE(STDOUT_FILENO, clear_seq, strlen(clear_seq));
    SAFE_WRITE(STDOUT_FILENO, prompt, strlen(prompt));
}

/* Read input with history navigation */
static int read_input_with_history(const char *prompt, char *buf, size_t bufsz) {
    if (!buf || bufsz == 0) return -1;
    buf[0] = '\0';
    size_t len = 0;
    HistNode *nav = NULL;

    enable_raw_mode();
    SAFE_WRITE(STDOUT_FILENO, prompt, strlen(prompt));

    while (1) {
        char c;
        ssize_t n = read(STDIN_FILENO, &c, 1);
        if (n == 0) { /* EOF on stdin */
            disable_raw_mode();
            return -1;
        }
        if (n < 0) {
            if (errno == EINTR) continue;
            disable_raw_mode();
            return -1;
        }

        if (c == '\r' || c == '\n') {
            SAFE_WRITE(STDOUT_FILENO, "\n", 1);
            break;
        }

        if (c == 127 || c == 8) { // backspace
            if (len > 0) {
                const char *bs = "\b \b";
                SAFE_WRITE(STDOUT_FILENO, bs, 3);
                len--;
                buf[len] = '\0';
            }
            continue;
        }

        if (c == 27) { // ESC seq
            char seq[2];
            if (read(STDIN_FILENO, &seq[0], 1) != 1) continue;
            if (read(STDIN_FILENO, &seq[1], 1) != 1) continue;
            if (seq[0] == '[') {
                if (seq[1] == 'A') { // up
                    if (!nav) nav = hist_tail;
                    else if (nav->prev) nav = nav->prev;
                    if (nav) {
                        clear_line_and_prompt(prompt);
                        size_t hlen = strlen(nav->cmd);
                        if (hlen >= bufsz) hlen = bufsz - 1;
                        strncpy(buf, nav->cmd, bufsz-1);
                        buf[hlen] = '\0';
                        len = hlen;
                        SAFE_WRITE(STDOUT_FILENO, buf, len);
                    }
                } else if (seq[1] == 'B') { // down
                    if (nav) {
                        if (nav->next) nav = nav->next;
                        else nav = NULL;
                    }
                    clear_line_and_prompt(prompt);
                    if (nav) {
                        size_t hlen = strlen(nav->cmd);
                        if (hlen >= bufsz) hlen = bufsz - 1;
                        strncpy(buf, nav->cmd, bufsz-1);
                        buf[hlen] = '\0';
                        len = hlen;
                        SAFE_WRITE(STDOUT_FILENO, buf, len);
                    } else {
                        buf[0] = '\0';
                        len = 0;
                    }
                }
            }
            continue;
        }

        if ((unsigned char)c >= 32 && (unsigned char)c <= 126) {
            if (len < bufsz - 1) {
                buf[len++] = c;
                buf[len] = '\0';
                SAFE_WRITE(STDOUT_FILENO, &c, 1);
            }
        }
    }

    disable_raw_mode();
    return (int)len;
}

/* Robust parsing */
static int split_args_safe(char *line, char *argv[], int max_args) {
    if (!line) return -1;
    int argc = 0;
    char *p = line;

    while (*p) {
        while (*p == ' ' || *p == '\t') p++;
        if (*p == '\0') break;

        if (argc >= max_args) return -3;

        if (*p == '"') {
            p++;
            argv[argc++] = p;
            char *q = strchr(p, '"');
            if (!q) return -2;
            *q = '\0';
            p = q + 1;
        } else {
            argv[argc++] = p;
            while (*p && *p != ' ' && *p != '\t') p++;
            if (*p) { *p = '\0'; p++; }
        }
    }
    return argc;
}

/* Print full help (expanded / user-friendly) */
static void print_help_long(void) {
    puts("\n========== NeuMiniOS Command Help ==========");
    puts(" Basic commands:");
    puts("  help                 - Display this help text");
    puts("  exit | quit          - Quit NeuMiniOS");
    puts("  clear                - Clear the terminal screen");
    puts("  history              - Show recent commands (most recent first)");
    puts("");
    puts(" File operations:");
    puts("  list                 - List files in current directory");
    puts("  create <name> \"<text>\" - Create a file and write content into it");
    puts("  view <file>          - View file contents");
    puts("  delete <file>        - Delete file (empty dir only for directories)");
    puts("  copy <src> <dst>     - Copy file within disk image");
    puts("  rename <old> <new>   - Rename a file or directory");
    puts("  cd <directory>       - Change current directory (supports .. and absolute paths)");
    puts("  mkdir [-p] <path>    - Make directory. Use -p to create intermediate directories");
    puts("");
    puts(" Process management:");
    puts("  run <file>           - Extract and run executable from disk image");
    puts("  plist                - List tracked NeuMiniOS processes");
    puts("  stop <neumini_pid|host_pid> - Stop a process either by neumini pid or host pid");
    puts("");
    puts(" Notes:");
    puts("  - Files and executables are stored inside the in-memory Disk Image.");
    puts("  - To add host binaries at boot, put them in host_bin/ (rebuilt for target)");
    puts("  - Use quoted content for create, e.g. create my.txt \"hello world\"");
    puts("============================================\n");
}

/* Main CLI */
int main(int argc, char *argv[]) {
    const char *host_dir = "./host_bin";
    if (argc >= 2) host_dir = argv[1];

    install_terminal_handlers();

    DiskImage disk;
    if (disk_init(&disk, DISK_CAP) != 0) {
        fprintf(stderr, "Error: failed to allocate disk image\n");
        return 1;
    }

    printf("NeuBoot starting... allocating disk %zu bytes\n", disk.capacity);
    if (boot_import_dir(&disk, host_dir) != 0) {
        fprintf(stderr, "NeuBoot failed to import files from %s\n", host_dir);
        disk_free_all(&disk);
        return 1;
    }
    printf("NeuBoot finished. Disk used: %zu bytes\n", disk.used);

    proc_init();

    char line[INPUT_BUF_SIZE];
    char *argvs[MAX_ARGS];
    const char *prompt = "neu> ";

    while (1) {
        proc_reap_nonblocking();

        memset(line, 0, sizeof(line));
        int llen = read_input_with_history(prompt, line, sizeof(line));
        if (llen < 0) break; /* EOF or fatal -> exit shell */

        char *tmp = line;
        while (*tmp == ' ' || *tmp == '\t') tmp++;
        if (*tmp != '\0') history_add(tmp);

        int ac = split_args_safe(line, argvs, MAX_ARGS);
        if (ac == -2) {
            printf("Parse error: unterminated quote.\n");
            continue;
        }
        if (ac == -3) {
            printf("Error: too many arguments (max %d)\n", MAX_ARGS);
            continue;
        }
        if (ac < 0) {
            printf("Parse error.\n");
            continue;
        }
        if (ac == 0) continue;

        char *cmd = argvs[0];

        /* ------------------- added 'history' command ------------------- */
        if (strcmp(cmd, "history") == 0 || strcmp(cmd, "h") == 0) {
            history_show();
            continue;
        }

        if (strcmp(cmd, "list") == 0) {
            disk_list(&disk);

        } else if (strcmp(cmd, "view") == 0) {
            if (ac < 2) {
                printf("Usage: view <file>\n");
            } else {
                FileNode *n = disk_find(&disk, argvs[1]);
                if (!n) {
                    printf("Error: view failed: file '%s' not found\n", argvs[1]);
                } else {
                    disk_view(&disk, argvs[1]);
                }
            }

        } else if (strcmp(cmd, "create") == 0) {
            if (ac < 3) {
                printf("Usage: create <name> \"<text>\"\n");
            } else {
                int r = disk_add_buf(&disk, argvs[1], (unsigned char*)argvs[2], strlen(argvs[2]));
                if (r == 0) printf("Created '%s' (%zu bytes)\n", argvs[1], strlen(argvs[2]));
                else if (r == DISK_ERR_EXISTS) printf("Error: create failed: name exists\n");
                else if (r == DISK_ERR_NO_SPACE) printf("Error: create failed: insufficient disk capacity\n");
                else printf("Error: create failed: internal error (%d)\n", r);
            }

        } else if (strcmp(cmd, "delete") == 0) {
            if (ac < 2) {
                printf("Usage: delete <file>\n");
            } else {
                FileNode *n = disk_find(&disk, argvs[1]);
                if (!n) {
                    printf("Error: delete failed: file '%s' not found\n", argvs[1]);
                } else {
                    int r = disk_delete(&disk, argvs[1]);
                    if (r == 0) {
                        printf("Deleted '%s'\n", argvs[1]);
                    } else if (r == DISK_ERR_DIR_NOT_EMPTY || r == -3 || r == -4) {
                        /* some implementations used -3 or -4 for "dir not empty" */
                        printf("Error: delete failed: directory '%s' is not empty\n", argvs[1]);
                    } else {
                        printf("Error: delete failed for '%s' (code %d)\n", argvs[1], r);
                    }
                }
            }

        } else if (strcmp(cmd, "copy") == 0) {
            if (ac < 3) {
                printf("Usage: copy <src> <dst>\n");
            } else {
                FileNode *srcn = disk_find(&disk, argvs[1]);
                if (!srcn) {
                    printf("Error: copy failed: source '%s' not found\n", argvs[1]);
                } else if (disk_find(&disk, argvs[2])) {
                    printf("Error: copy failed: destination '%s' already exists\n", argvs[2]);
                } else {
                    int r = disk_copy(&disk, argvs[1], argvs[2]);
                    if (r == 0) printf("Copied '%s' -> '%s'\n", argvs[1], argvs[2]);
                    else if (r == DISK_ERR_NO_SPACE) printf("Error: copy failed: insufficient disk capacity\n");
                    else printf("Error: copy failed for '%s' (%d)\n", argvs[1], r);
                }
            }

        } else if (strcmp(cmd, "rename") == 0) {
            if (ac < 3) {
                printf("Usage: rename <old> <new>\n");
            } else {
                if (!disk_find(&disk, argvs[1])) {
                    printf("Error: rename failed: '%s' not found\n", argvs[1]);
                } else if (disk_find(&disk, argvs[2])) {
                    printf("Error: rename failed: target '%s' already exists\n", argvs[2]);
                } else {
                    int r = disk_rename(&disk, argvs[1], argvs[2]);
                    if (r == 0) printf("Renamed '%s' -> '%s'\n", argvs[1], argvs[2]);
                    else printf("Error: rename failed for '%s' (%d)\n", argvs[1], r);
                }
            }

        } else if (strcmp(cmd, "run") == 0) {
            if (ac < 2) {
                printf("Usage: run <file>\n");
            } else {
                int r = proc_add_run(&disk, argvs[1]);
                if (r == -2) printf("Error: run failed: file '%s' not found\n", argvs[1]);
                else if (r == -3) printf("Error: run failed: too many concurrent processes\n");
                else if (r != 0 && r != 1) printf("Error: run failed (%d)\n", r);
            }

        } else if (strcmp(cmd, "plist") == 0) {
            proc_list();

        } else if (strcmp(cmd, "stop") == 0) {
            if (ac < 2) {
                printf("Usage: stop <neumini_pid|host_pid>\n");
            } else {
                int npid = atoi(argvs[1]);
                int r = proc_stop_by_neu(npid);
                if (r == 0) {
                    printf("Sent stop to neumini_pid %d\n", npid);
                    proc_reap_nonblocking(); /* immediate update */
                } else if (r == -2) {
                    printf("Error: stop failed: process already finished\n");
                } else if (r == -1) {
                    pid_t host = (pid_t)atoi(argvs[1]);
                    if (host <= 0) {
                        printf("Error: stop failed: invalid pid\n");
                    } else {
                        if (kill(host, SIGTERM) == 0) {
                            printf("Sent SIGTERM to host pid %d\n", (int)host);
                        } else {
                            printf("Error: stop failed: kill host pid %d: %s\n", (int)host, strerror(errno));
                        }
                    }
                } else {
                    printf("Error: stop failed (%d)\n", r);
                }
            }

        } else if (strcmp(cmd, "cd") == 0) {
            if (ac < 2) {
                printf("Usage: cd <directory>\n");
            } else {
                int r = disk_cd(&disk, argvs[1]);
                if (r == DISK_ERR_NOTFOUND) {
                    printf("Error: cd failed: '%s' not found\n", argvs[1]);
                } else if (r == DISK_ERR_GENERIC) {
                    printf("Error: cd failed: not a directory or other error (%d)\n", r);
                } else if (r != 0) {
                    printf("Error: cd failed (%d)\n", r);
                } else {
                    printf("cd: changed directory to %s\n", argvs[1]);
                }
            }
        } else if (strcmp(cmd, "mkdir") == 0) {
            if (ac < 2) {
                printf("Usage: mkdir [-p] <path>\n");
            } else if (ac >= 2 && strcmp(argvs[1], "-p") == 0) {
                if (ac < 3) {
                    printf("Usage: mkdir -p <path>\n");
                } else {
                    /* Quick pre-check: if any existing path component is a file, report it */
                    char parentpath[PATH_MAX];
                    strncpy(parentpath, argvs[2], sizeof(parentpath)-1);
                    parentpath[sizeof(parentpath)-1] = '\0';
                    /* remove trailing slash */
                    size_t L = strlen(parentpath);
                    while (L > 1 && parentpath[L-1] == '/') { parentpath[L-1] = '\0'; L--; }
                    /* find parent of final component */
                    char *last = strrchr(parentpath, '/');
                    if (last) {
                        *last = '\0';
                        FileNode *maybe_parent = disk_find(&disk, parentpath[0] ? parentpath : "/");
                        if (maybe_parent && maybe_parent->type == FILETYPE_FILE) {
                            printf("mkdir: failed: a path component '%s' exists and is a file\n", parentpath);
                            *last = '/';
                            continue;
                        }
                        *last = '/';
                    } else {
                        FileNode *maybe = disk_find(&disk, parentpath);
                        if (maybe && maybe->type == FILETYPE_FILE) {
                            printf("mkdir: failed: a path component '%s' exists and is a file\n", parentpath);
                            continue;
                        }
                    }

                    int r = disk_mkdir_p(&disk, argvs[2]);
                    if (r == 0) printf("mkdir: created directory %s\n", argvs[2]);
                    else if (r == DISK_ERR_NOT_A_DIR || r == DISK_ERR_NOTFOUND) {
                        printf("mkdir: failed: a path component exists but is a file (cannot create directory inside a file)\n");
                    } else if (r == DISK_ERR_NAME_TOOLONG || r == DISK_ERR_NAME_TOOLONG) {
                        printf("mkdir: failed: name too long or invalid\n");
                    } else {
                        printf("mkdir: failed (%d)\n", r);
                    }
                }
            } else {
                /* simple single-level mkdir (no -p) */
                int r = disk_mkdir(&disk, argvs[1]);
                if (r == 0) printf("mkdir: created directory %s\n", argvs[1]);
                else if (r == DISK_ERR_EXISTS) printf("mkdir: failed: already exists\n");
                else if (r == DISK_ERR_NOT_A_DIR || r == DISK_ERR_GENERIC) printf("mkdir: failed: a path component exists but is a file\n");
                else printf("mkdir: failed (%d)\n", r);
            }
        } else if (strcmp(cmd, "help") == 0) {
            print_help_long();

        } else if (strcmp(cmd, "exit") == 0 || strcmp(cmd, "quit") == 0) {
            break;

        } else {
            int rc = system(line);
            if (rc == -1) {
                printf("Error: host command failed to execute\n");
            }
        }
    }

    disk_free_all(&disk);
    proc_cleanup();
    history_free_all();

    printf("NeuMiniOS shutting down.\n");
    return 0;
}