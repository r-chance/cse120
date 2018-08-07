package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;
/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
			freePages.add(i);
		}

		lock = new Lock();
		executeLock = new Lock();
		joinLock = new Lock();

		pinnedPages = new LinkedList<Integer>();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}


	/**
 	 * Returns physical page number from top of list of free pages
 	 */
	public static int getFreePage() {
		// If there are free pages
		if(getNumFreePages() > 0) {
			return freePages.poll();
		}
		// If there are no more free pages
		return -1;
		
	}

	/**
 	 * Adds physical page back to list of free pages when released by 
 	 * a process
 	 */
	public static void returnPage(int page) {
		freePages.add(page);
	}

	/**
 	 * Returns the number of free physical pages
 	 */
	public static int getNumFreePages() {
		return freePages.size();
	}

	public static int issuePID() {
		numProcesses++;
		return pid++;
	}

	public static int exitProcess() {
		return --numProcesses;
	}

	public static int getNumProcesses() {
		return numProcesses++;
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	private static LinkedList<Integer> freePages = new LinkedList<Integer>();
	// lock for synchronization of memory
	public static Lock lock;

	// lock for executing a new process
	public static Lock executeLock;

	// lock for joining processes
	public static Lock joinLock;

	private static int pid = 0;

	private static int numProcesses = 0;

	protected static LinkedList<Integer> pinnedPages;
}
