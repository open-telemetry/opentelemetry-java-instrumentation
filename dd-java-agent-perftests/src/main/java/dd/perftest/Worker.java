package dd.perftest;

public class Worker {

  /** Simulate work for the give number of milliseconds. */
  public static void doWork(long workTimeMS) {
    final long doneTimestamp = System.currentTimeMillis() + workTimeMS;
    while (System.currentTimeMillis() < doneTimestamp) {
      // busy-wait to simulate work
    }
  }
}
