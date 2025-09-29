package io.opentelemetry.javaagent.bootstrap.executors;

import java.time.Duration;

// needed for lambdas
public class PendingTaskRunnable implements Runnable, WrappedRunnable {

  public static Runnable measurePendingTime(Runnable task) {
    return new PendingTaskRunnable(task);
  }

  private final Long startObservation;
  private final Runnable delegate;

  public PendingTaskRunnable(Runnable delegate) {
    this.delegate = delegate;
    this.startObservation = System.nanoTime();
  }

  @Override
  public void run() {
    Duration queueWaitDuration = Duration.ofNanos(System.nanoTime() - startObservation);
    System.out.println("This class name: " + this.getClass().getName());
//    PendingTaskMetrics.recordTime(startObservation);
    delegate.run();
  }

  @Override
  public Runnable getDelegate() {
    return delegate;
  }
}
