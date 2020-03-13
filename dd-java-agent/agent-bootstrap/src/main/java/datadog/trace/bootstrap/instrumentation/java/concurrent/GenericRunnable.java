package datadog.trace.bootstrap.instrumentation.java.concurrent;

/** Used by ThreadPoolExecutorInstrumentation to check executor support */
public class GenericRunnable implements Runnable {

  @Override
  public void run() {}
}
