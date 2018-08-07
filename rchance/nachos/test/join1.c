#include "syscall.h"

int main(int argc, char* argv[]) {
	char* prog = "exit1.coff";
	int pid, r, status = 0;

	printf("execing %s...\n", prog);
	pid = exec (prog,0,0);
	if(pid > 0) {
		printf("...passed\n");
	} else {
		printf("...failed (pid = %d)\n", pid);
		exit(-1);
	}

	printf("joining %d...\n", pid);
	r = join(pid, &status);
	if(r>0) {
		printf("...passed (status from child = %d)\n", status);
	} else if(r == 0) {
		printf("...child exited with unhandled exception\n");
		exit(-1);
	} else {
		printf("...failed (r = %d)\n", r);
		exit(-1);
	}

	return 0;
}
