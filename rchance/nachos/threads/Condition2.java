package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		conditionQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		conditionQueue.addFirst(KThread.currentThread());

		conditionLock.release();

/*		if(KThread.currentThread() != conditionQueue.poll()) {
			Machine.interrupt().disable();
			KThread.sleep();
			Machine.interrupt().enable();
		}
*/		
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

/*		KThread threadToWake;

		if (!waitQueue.isEmpty()) {
			threadToWake = waitQueue.nextThread();
			Machine.interrupt().disable();
			threadToWake.ready();
			Machine.interrupt().enable();
		}
*/	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	}


	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;
	
		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for(int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();
					cv.sleep();
				}
				lock.release();
			}
		}

		public InterlockTest() {
			lock = new Lock();
			cv = new Condition2(lock);
		
			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");
		
			ping.fork();
			pong.fork();

			ping.join();
		}
	}

	public static void selfTest() {
		new InterlockTest();
	}

	private Lock conditionLock;

	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
	private LinkedList<KThread> conditionQueue;


}
