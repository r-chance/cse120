package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.String;
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
	
		for(int i = 0; i < 16; i++) {
			openFiles[i] = null;
		}
		openFiles[0] = UserKernel.console.openForReading();
		openFiles[1] = UserKernel.console.openForWriting();
		
		childProcess = new ArrayList<Integer>();
		childExitStatus = new HashMap<Integer,Integer>();

		pid = UserKernel.issuePID();

		joinCondition = new Condition(UserKernel.joinLock);
		waitForPage = new Condition(VMKernel.noFreePageLock);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];	

		int bytesRead = readVirtualMemory(vaddr, bytes);
		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0){
				return new String(bytes, 0, length);
}
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		int vpn = Processor.pageFromAddress(vaddr);

		byte[] memory = Machine.processor().getMemory();

		int numRead = 0;

		int ppage = -1;
		while(length > 0) {
			if(!pageTable[vpn].valid) {
				Machine.processor().writeRegister(Processor.regBadVAddr,vaddr);
				handleException(Processor.exceptionPageFault);
			}
			ppage = new Integer(pageTable[Machine.processor().pageFromAddress(vaddr)].ppn);
			UserKernel.pinnedPages.add(ppage);
			int pageOffset = Machine.processor().offsetFromAddress(vaddr);
			int paddr = Machine.processor().makeAddress(ppage,pageOffset);
			if (paddr < 0 || paddr >= memory.length)
				return numRead;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(memory, paddr, data, offset, amount);
			vaddr += amount;
			numRead += amount;
			offset += amount;
			length -= amount;
			vpn = Processor.pageFromAddress(vaddr);
			if(ppage != -1) {
				UserKernel.pinnedPages.remove(Integer.valueOf(ppage));

				if(VMKernel.waitingForPage == true)
					waitForPage.wake();
			}
		}
		return numRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		int vpn = Processor.pageFromAddress(vaddr);
		byte[] memory = Machine.processor().getMemory();
		int numWritten = 0;

		// Mark pagetable entry as dirty
//		pageTable[vpn].dirty = true;

		int ppage = -1;

		while(length > 0) {
			if(!pageTable[vpn].valid) {
				Machine.processor().writeRegister(Processor.regBadVAddr,vaddr);
				handleException(Processor.exceptionPageFault);
			}
			ppage = pageTable[Machine.processor().pageFromAddress(vaddr)].ppn;
			
			UserKernel.pinnedPages.add(ppage);
			
			int pageOffset = Machine.processor().offsetFromAddress(vaddr);
			int paddr = Machine.processor().makeAddress(ppage,pageOffset);	
			if (paddr < 0 || paddr >= memory.length)
				return numWritten;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(data, offset, memory, paddr, amount);
			length -= amount;
			numWritten += amount;
			offset += amount;
			vaddr += amount;
			vpn = Processor.pageFromAddress(vaddr);
			if(ppage != -1) {
				UserKernel.pinnedPages.remove(Integer.valueOf(ppage));

				if(VMKernel.waitingForPage == true)
					waitForPage.wake();
			}
		}
		return numWritten;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		UserKernel.lock.acquire();

		if(numPages > UserKernel.getNumFreePages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.lock.release();
			return false;
		}

		// allocate page table with number of pages used by this process
		pageTable = new TranslationEntry[numPages];

		for(int i = 0; i < numPages; i++) {
			int ppn = UserKernel.getFreePage();
			pageTable[i] = new TranslationEntry(i,ppn,true,false,false,false);
		}

		UserKernel.lock.release();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int ppn = pageTable[vpn].ppn;
				boolean readOnly = section.isReadOnly();
				
				if(readOnly)
					pageTable[vpn].readOnly = true;
				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i,ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// For each section, release allocated pages back to kernel
		UserKernel.lock.acquire();
		for(int i = 0; i < pageTable.length; i++) {
			int ppn = pageTable[i].ppn;
			UserKernel.returnPage(ppn);
		}
		UserKernel.lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		if(this.getPID() != 0) return -1;

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		unloadSections();
		UserKernel.lock.acquire();
		if(parentProcess != null) {
			if(unhandledException) 
				parentProcess.childExitStatus.put(this.getPID(),null);
			else
				parentProcess.childExitStatus.put(this.getPID(),status);
			if(parentProcess.joinedProcess == this.getPID())
				parentProcess.joinCondition.wake();		
		}

		coff.close();

		if(UserKernel.exitProcess() == 0) {

			UserKernel.lock.release();
			if(this.getPID() == 0) handleHalt();
			else Kernel.kernel.terminate();
		}
		else {
			UserKernel.lock.release();
			KThread.finish(); 		
		}
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
		case syscallJoin:
			return handleJoin(a0,a1);
		case syscallOpen:
			return handleOpen(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);		
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			unloadSections();
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			unhandledException = true;
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			this.handleExit(0);
			Lib.assertNotReached("Unexpected exception");
		}
	}


	private int handleExec(int vaddress, int argc, int argv) {

		String fileName = readVirtualMemoryString(vaddress,256);
		if(fileName == null || argc < 0) {
			return -1;
		}

		byte[] argAddrs = new byte[4*argc];
		int numRead = readVirtualMemory(argv,argAddrs);
		if(numRead < 4*argc) return -1;

		String[] args = new String[argc];
		for(int i = 0; i < argc; i++) {
			int address = 0;
			for(int j = 0; j < 4; j++) {
				address = (address << Byte.SIZE) | argAddrs[i*4+(3-j)];
			}
			args[i] = readVirtualMemoryString(address,256);
			if(args[i] == null) return -1;
		}
		
		UserProcess child = newUserProcess();
		child.setParent(this);

		UserKernel.executeLock.acquire();
		boolean executed = child.execute(fileName,args);
		UserKernel.executeLock.release();

		if(!executed) return -1;

		childProcess.add(child.getPID());

		return child.getPID();
	}


	private int handleJoin(int processID, int statusPtr) {

		if(!childProcess.contains(processID) || processID < 0) return -1;

		if(childExitStatus.containsKey(processID)) return 1;

		UserKernel.joinLock.acquire();
		joinedProcess = processID;
		if(!childExitStatus.containsKey(processID)) {

			joinCondition.sleep();
		}
		childProcess.remove(processID);
		UserKernel.joinLock.release();
		Integer status;
		if((status = childExitStatus.get(processID)) == null) return 0;

		byte[] arr = new byte[4];
		

		for(int i = 0; i < 4; i++) {
		
			arr[i] = (byte)((status >> 8*i)&0xff);
		}

		writeVirtualMemory(statusPtr,arr);

		return 1;
	}


	private int handleCreate(int vaddress) {
		String fd = readVirtualMemoryString(vaddress,256);
		if(fd != null) {
			for(int i = 0; i < 16; i++) {
				if(openFiles[i] == null) {
					openFiles[i] = ThreadedKernel.fileSystem.open(fd,true);
					if(openFiles[i] != null) return i;
				}
			}
		}
		return -1;
	}

	private int handleOpen(int vaddress) {
		String fd = readVirtualMemoryString(vaddress,256);
		if(fd != null) {
			for(int i = 0; i < 16; i++) {
				if(openFiles[i] == null) {
					openFiles[i] = ThreadedKernel.fileSystem.open(fd,false);
					if(openFiles[i] != null) return i;
				}
			}
		}	
		return -1;
	}


	private int handleRead(int fd, int destAddr, int numBytes) {
		if(fd < 0 || fd >= 16 || numBytes < 0 || openFiles[fd] == null) return -1;
		if(destAddr+numBytes > pageSize*numPages) return -1;

		int vaddress = destAddr;
		int total = 0;
		int transferSize;
		int numWritten = 0;		
		while(numBytes > 0) {
			if(numBytes < pageSize)
				transferSize = numBytes;
			else 
				transferSize = pageSize;
			openFiles[fd].read(readBuffer,0,transferSize);
			while(numWritten != transferSize) {
				numWritten += writeVirtualMemory(vaddress+total,readBuffer,numWritten,transferSize-numWritten);
			}
			numWritten = 0;
			numBytes -= transferSize;
			total += transferSize;
		}
		return total;
	}

	private int handleWrite(int fd, int vaddress, int count) {
		int total = 0;
		int transferSize;
		int numRead = 0;
		int numWritten = 0;
		if(fd < 0 || fd >= 16 || count < 0 || openFiles[fd] == null) return -1;

		if(vaddress+count > pageSize*numPages) return -1;

		while(count > 0) {
		
			if(count < pageSize) transferSize = count;
			else transferSize = pageSize;
			// Read from vmem to buffer
			while(numRead != transferSize) {
				numRead += readVirtualMemory(vaddress+total,writeBuffer,numRead,transferSize);
			}
			numRead = 0;

			// Write from buffer to file
			while(numWritten != transferSize) {
				numWritten += openFiles[fd].write(writeBuffer,numWritten,transferSize);
			}
			total += numWritten;
			count -= numWritten;
			numWritten = 0;
		}

		return total;
	}	


	private int handleClose(int fd) {
		if(fd < 0 || fd > 16) return -1;
		if(openFiles[fd] != null) {
				
			openFiles[fd].close();
			openFiles[fd] = null;
			return 0;
		}
		return -1;
	}


	private int handleUnlink(int vaddress) {
		for(int i = 0; i < 16; i++) {
			if(openFiles[i] != null && readVirtualMemoryString(vaddress,256) != null) {
				if(openFiles[i].getName().equals(readVirtualMemoryString(vaddress,256))) {
					if(ThreadedKernel.fileSystem.remove(openFiles[i].getName())) {
					openFiles[i] = null;
					return 0;
					}
				}
			}
		}
		return -1;
	}


	public void setParent(UserProcess parent) {
	
		this.parentProcess = parent;
	}

	public UserProcess getParent() {
		
		return this.parentProcess;
	}

	public int getPID() {
		return pid;
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] openFiles = new OpenFile[16];

	private byte[] readBuffer = new byte[pageSize];

	private byte[] writeBuffer = new byte[pageSize];

	private UserProcess parentProcess = null;

	private ArrayList<Integer> childProcess;// values are pid

	private HashMap<Integer,Integer> childExitStatus;// values are exit status

	protected int joinedProcess = 0;// nothing can join to pid=0

	private int pid;

	public Condition joinCondition;

	public Condition waitForPage;
	
	private boolean unhandledException = false;
}
