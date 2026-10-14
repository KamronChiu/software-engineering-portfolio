CC = gcc
CFLAGS = -g -Wall -Wextra

SRC = src/boot.c src/proc.c src/disk.c src/cli.c
OBJ = $(SRC:.c=.o)

all: neumini

neumini: $(OBJ)
	$(CC) $(CFLAGS) -o neumini $(OBJ)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f neumini $(OBJ)