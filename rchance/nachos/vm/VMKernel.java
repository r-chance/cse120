package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapfile = ThreadedKernel.fileSystem.open("kernelswapfile", true);
		swapPagesFilled = new boolean[16];
		invPageTablePID = new int[Machine.processor().getNumPhysPages()];
		invPageTableTE = new TranslationEntry[Machine.processor().getNumPhysPages()];
		swapMapTE = new HashMap<Integer,LinkedList<TranslationEntry>>();

		noFreePageLock = new Lock();
		swapfileWriteLock = new Lock();
		swapfileReadLock = new Lock();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		ThreadedKernel.fileSystem.remove("kernelswapfile");
		super.terminate();
	}

	/**
 	 * Evicts a physical page and returns the page number
 	 */
	public static int evictPage() {
		boolean pageEvicted = false;

		// value to determine if all pages have been checked for eviction
		int startingPPN = IPTIterator;

		int finalPPN;

		// find a page to evict
		do {
			// evict a page if it isn't pinned
			if(!pinnedPages.contains(IPTIterator)) {
				// evict page w/o swap if not dirty
				if(!invPageTableTE[IPTIterator].dirty) {
					// set valid bit to false, indicating page no longer in memory
					invPageTableTE[IPTIterator].valid = false;
					// return ppn that was evicted
					finalPPN = IPTIterator;
					advanceIPT();

					return finalPPN;
				}
				// evict page w/ swap otherwise
				else {

					// find open page in swapfile
					int i;
					for(i = 0; i < swapPagesFilled.length; i++) {

						if(swapPagesFilled[i] == false) {
							break;
						}
					}
					// ensure swap page array is large enough
					if(swapPagesFilled.length == i) {

						boolean[] temp = new boolean[2*swapPagesFilled.length];
						System.arraycopy(swapPagesFilled,0,temp,0,swapPagesFilled.length);
						swapPagesFilled = temp;
					}

					swapPagesFilled[i] = true;
					swapOut(invPageTableTE[IPTIterator].ppn,i);
					invPageTableTE[IPTIterator].valid = false;
					pageEvicted = true;
					invPageTableTE[IPTIterator].ppn = i;					

					// add TE to map with PIDs
					if(!swapMapTE.containsKey(invPageTablePID[IPTIterator])) {
						swapMapTE.put(invPageTablePID[IPTIterator],new LinkedList<TranslationEntry>());
					}
					swapMapTE.get(invPageTablePID[IPTIterator]).add(invPageTableTE[IPTIterator]);

					finalPPN = IPTIterator;
					advanceIPT();
					return finalPPN;
				}
			}
			// If page is pinned, advance iterator
			else {
				advanceIPT();
			}
		} while(!pageEvicted && (startingPPN != IPTIterator));
			
		// return -1 if no page can be freed		
		return -1;
	}

	public static void swapOut(int ppn, int index) {

		int pageSize = Machine.processor().pageSize;

		swapfileWriteLock.acquire();
		byte[] memoryPage = new byte[pageSize];
		System.arraycopy(Machine.processor().getMemory(),ppn*pageSize,memoryPage,0,pageSize);
		
		swapfile.write(index*pageSize,memoryPage,0,pageSize);
		invPageTableTE[IPTIterator].ppn = index;
		swapfileWriteLock.release();
	
	}

	public static void swapIn(int PID, int vpn, int ppn) {
		int pageSize = Machine.processor().pageSize;

		swapfileReadLock.acquire();
		byte[] memoryPage = new byte[pageSize];
		
		// search through IPT for swap page index
		LinkedList temp = swapMapTE.get(PID);
		TranslationEntry tempTE = null;
		for(int i = 0; i < temp.size(); i++) {
			tempTE = (TranslationEntry)temp.get(i);
			if(tempTE.vpn == vpn) 
				break;
		}
		if(tempTE != null) {
			swapfile.read(tempTE.ppn*pageSize,memoryPage,0,pageSize);
			System.arraycopy(memoryPage,0,Machine.processor().getMemory(),ppn*pageSize,pageSize);
		}
		swapfileReadLock.release();

	}

	private static void advanceIPT() {
		IPTIterator += 1;
		if(IPTIterator == Machine.processor().getNumPhysPages())
			IPTIterator = 0;
	}

	private boolean pagePinned(int ppn) {
		
		if(super.pinnedPages.contains(ppn)) {
			return true;
		}
		else return false;
	}

	public static int getPage() {
		// Attempt to get a free page
		int freePage = getFreePage();
		// If a free page was retrieved
		if(freePage != -1) {
			return freePage;
		}
		else {
			freePage = evictPage();
		}
		return freePage;
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static int[] invPageTablePID;
	public static TranslationEntry[] invPageTableTE;

	public static int IPTIterator = 0;

	private static OpenFile swapfile;	

	private static boolean[] swapPagesFilled;
	public static HashMap<Integer,LinkedList<TranslationEntry>> swapMapTE; // mapping of PID to translation entry in swap file

	public static Lock noFreePageLock;

	public static Lock swapfileWriteLock;

	public static Lock swapfileReadLock;
	
	public static boolean waitingForPage = false;

}
