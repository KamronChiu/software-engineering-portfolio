CC = gcc
CFLAGS = -std=c11 -Wall -Wextra -O2
SRC = src/disk.c src/proc.c src/boot.c src/cli.c
OUT = neumini

all: $(OUT)

$(OUT): $(SRC)
	$(CC) $(CFLAGS) -o $(OUT) $(SRC)

clean:
	rm -f $(OUT)
