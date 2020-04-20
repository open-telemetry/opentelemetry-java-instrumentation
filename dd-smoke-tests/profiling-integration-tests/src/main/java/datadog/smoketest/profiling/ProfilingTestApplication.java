package datadog.smoketest.profiling;

import datadog.trace.api.Trace;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ProfilingTestApplication {
  private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

  public static void main(final String[] args) throws InterruptedException {
    long exitDelay = -1;
    if (args.length > 0) {
      exitDelay = TimeUnit.SECONDS.toMillis(Long.parseLong(args[0]));
    }
    final long startTime = System.currentTimeMillis();
    while (true) {
      tracedMethod();
      if (exitDelay > 0 && exitDelay + startTime < System.currentTimeMillis()) {
        break;
      }
    }
    System.out.println("Exiting (" + exitDelay + ")");
  }

  @Trace
  private static void tracedMethod() throws InterruptedException {
    System.out.println("Tracing");
    tracedBusyMethod();
    try {
      throw new IllegalStateException("test");
    } catch (final IllegalStateException ignored) {
    }
    Thread.sleep(50);
  }

  @Trace
  private static void tracedBusyMethod() {
    long startTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
    Random random = new Random();
    long accumulator = 0L;
    while (true) {
      accumulator += random.nextInt(113);
      if (THREAD_MX_BEAN.getCurrentThreadCpuTime() - startTime > 10_000_000L) {
        // looking for at least 10ms CPU time
        break;
      }
    }
    System.out.println("accumulated: " + accumulator);
  }
}
