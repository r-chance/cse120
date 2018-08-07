#include "syscall.h"

int main(int argc, char* argv[]) {

	char* prog = "exit1.coff";
	int pid;

	pid = exec(prog,0,0);
	exit(pid);
}
