package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	HashMap<KThread,Long> waitTime = new HashMap<KThread,Long>();

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		for(Map.Entry<KThread,Long> e : waitTime.entrySet()) {
			KThread threadToCheck = e.getKey();
			Long id = e.getValue();

			if(id.longValue() < Machine.timer().getTime()) {
				threadToCheck.ready();// add checked thread to ready queue
				waitTime.remove(threadToCheck);// remove from wait list
			}
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {

                if(x <= 0) return;// trivial case

		waitTime.put(KThread.currentThread(),new Long(Machine.timer().getTime() + x));
		Machine.interrupt().disable();// Disable interrupts for context switch atomicity
		KThread.sleep();
		Machine.interrupt().enable();// Renable interrupts
	}
	
	// Testing
	public static void alarmTest1() {
		int durations[] = {0, 1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d: durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void selfTest() {
		alarmTest1();
	}
}
