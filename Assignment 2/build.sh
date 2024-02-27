# gcc -Wall -Werror -Wextra -Wpedantic -Wstrict-prototypes -std=gnu11 -o manager manager.c
#!/bin/sh
GCC="gcc -Wall -Werror -Wextra -Wpedantic -Wstrict-prototypes -std=gnu11 -o "
GPP="g++ -Wall -Werror -Wextra -Wpedantic -std=c++17 -o "
BIN="./bin/"


mkdir ${BIN}
clear

rm -f ${BIN}manager
rm -f ${BIN}prog

$GCC ${BIN}manager manager.c
$GPP ${BIN}prog prog.cpp
