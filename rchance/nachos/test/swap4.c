int bigbufnum = 16*1024 / sizeof(int);
int bigbuf[16*1024/sizeof(int)];

void init_buf() {
	int i;
	for (i = 0; i<bigbufnum; i++) {
		bigbuf[i] = i;
	}
}

void validate_buf() {
	int i;
	for(i = 0; i < bigbufnum; i++) {
		if(bigbuf[i] != i) {
			int s = i*1000*1000;
			s += bigbuf[i];
			exit(s);
		}
	}
}

int main(int argc, char* argv[]) {
	init_buf();
	validate_buf();
	validate_buf();
	validate_buf();
	exit(-1000);
}
