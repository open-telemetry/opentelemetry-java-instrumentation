package datadog.smoketest.profiling;

import datadog.trace.api.Trace;
import java.util.concurrent.TimeUnit;

public class ProfilingTestApplication {

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
    Thread.sleep(100);
  }
}
