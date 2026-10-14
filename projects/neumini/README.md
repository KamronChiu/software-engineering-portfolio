# Operating Systems

**Project:** neumini 

**Member:** Kamron, Lillian, Naz  

---

## What is this
neumini is a small educational in-memory “mini OS” / bootloader and CLI environment implemented in C. It provides an in-memory disk image (simple filesystem), a boot-import of host binaries from host_bin/, a CLI (neu>) implementing basic file operations and a tiny process manager that extracts binaries to /tmp/ and fork()/exec() them on the host.

Use it to demonstrate basic OS concepts (filesystem layout, process creation, reaping, simple shell behaviors) in a compact, easy-to-inspect codebase.


---

## Key features
1. In-memory disk image with directories and files (disk.c / disk.h)
2. boot_import_dir() to import host files from host_bin/ into the disk image at startup
	1. CLI shell support: navigation: cd, ls/list. File ops: create, view, delete, copy, rename, mkdir -p
	2. process ops: run <file>, plist (process list), stop <neumini_pid>
	3. history and line-editing (arrow keys)
3. Simple process manager:
	1. extracts image file to a safe /tmp/neumini_* path
	2. fork() + execl() to run extracted binary
	3. reaps child processes non-blocking; keeps a recent history list

---

## Quick start For MAC
1. Mount / transfer project into the VM (one-time)
```bash
multipass mount "/Users/<you>/path/to/neumini" neumini:/home/ubuntu/neumini
```
2. Transfer (one-off copy):
```bash
multipass transfer -r "/Users/<you>/path/to/neumini" neumini:/home/ubuntu/
```

3. Build and run neumini inside the VM
```bash
multipass exec neumini -- bash
cd /home/ubuntu/neumini/neumini        # path that contains Makefile and src/
make clean && make
./neumini
```

4. Rebuild host example programs used by the mini-OS
```bash
cd /home/ubuntu/neumini/neumini/host_bin
gcc -std=c11 -O2 -Wall -o helloworld helloworld.c
gcc -std=c11 -O2 -Wall -o sleepprog sleepprog.c
# then restart neumini so it re-imports these files
cd ..
./neumini
```

---
## If you are a windows user

1.
The incoming file is a compressed package from the Mac system. When compressing files, the Mac system automatically generates redundant files. Therefore, the first step is to delete the folder named __MACOSX in neumini.

2.
```bash
sudo apt update
sudo apt install dos2unix
```

3.
```bash
find src/ -name "*.c" -o -name "*.h" | xargs dos2unix
dos2unix host_bin/*.c
```

4.
```bash
cat helloworld.c
gcc -std=c11 -O2 -Wall -o helloworld helloworld.c
gcc -std=c11 -O2 -Wall -o sleepprog sleepprog.c
ls -l helloworld sleepprog
```

5.
```bash
ls -l Makefile
make clean
make
./neumini
```
## run in demo
```
Kamron:
neu> create my.txt "hello from neumini"
neu> view my.txt
neu> copy my.txt copy.txt
neu> rename copy.txt copy2.txt
neu> delete my.txt
neu> mkdir a
neu> mkdir a/b
neu> delete a
neu> mkdir -p a/b/c
neu> cd a/b
neu> list
Lillian:
neu> run helloworld
neu> run sleepprog
neu> run sleepprog
neu> run sleepprog
neu> plist
neu> stop <neumini_pid>
Naz:
neu> history
neu> help
neu> exit
```

---
