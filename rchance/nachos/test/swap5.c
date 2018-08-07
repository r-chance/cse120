int bigbufnum = 16*1024/sizeof(int);
int bigbuf[16*1024/sizeof(int)];

void write_buf(int base) {
	int i;
	for(i = 0; i < bigbufnum; i++) {
		bigbuf[i] = i +base;
	}
}

void validate_buf (int base) {
	int i;
	for(i = 0; i < bigbufnum;i++) {
		if(bigbuf[i] != (i+base)) {
			int s = i*1000*1000;
			s += bigbuf[i];
			exit(s);
		}
		bigbuf[i]= bigbuf[i];
	}
}

int main(int argc, char* argv[]) {
	write_buf(0);
	validate_buf(0);
	write_buf(100*1000);
	validate_buf(100*1000);
	write_buf(200*1000);
	validate_buf(200*1000);
	exit(-1000);
}
