package io.opentelemetry.loadgenerator;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;

import com.google.common.util.concurrent.RateLimiter;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    mixinStandardHelpOptions = true,
    description = "Generates traces and spans at a specified rate")
public class LoadGenerator implements Callable<Integer> {
  @Option(names = "--rate", required = true, description = "rate, per second, to generate traces")
  private int rate;

  @Option(
      names = "--threads",
      defaultValue = "6",
      description = "Number of trace-generating threads (default: ${DEFAULT-VALUE})")
  private int threads;

  @Option(
      names = "--width",
      defaultValue = "2",
      description = "Number of spans directly below the root (default: ${DEFAULT-VALUE})")
  private int width;

  @Option(
      names = "--depth",
      defaultValue = "3",
      description = "Total spans deep per trace, including parent (default: ${DEFAULT-VALUE})")
  private int depth;

  @Option(
      names = "--warmup",
      defaultValue = "5",
      description = "Time, in seconds, to ramp up to target rate (default: ${DEFAULT-VALUE})")
  private int warmupPeriod;

  @Option(
      names = "--print-interval",
      defaultValue = "20",
      description = "Interval, in seconds, to print statistics (default: ${DEFAULT-VALUE})")
  private int printInterval;

  private RateLimiter rateLimiter;
  private final AtomicLong tracesSent = new AtomicLong();

  @Override
  public Integer call() throws Exception {
    rateLimiter = RateLimiter.create(rate, warmupPeriod, TimeUnit.SECONDS);

    long intervalStart = System.currentTimeMillis();
    long tracesAtLastReport = 0;

    for (int i = 0; i < threads; i++) {
      final Thread workerThread = new Thread(new Worker(), "Worker-" + i);
      workerThread.setDaemon(true);
      workerThread.start();
    }

    while (true) {
      Thread.sleep(printInterval * 1000);

      final long currentTracesSent = tracesSent.get();
      final long intervalEnd = System.currentTimeMillis();

      final double currentRate =
          (currentTracesSent - tracesAtLastReport) / ((intervalEnd - intervalStart) / 1000d);

      System.out.println(
          "Total Traces Sent: " + currentTracesSent + ", Rate this interval: " + currentRate);
      intervalStart = System.currentTimeMillis();
      tracesAtLastReport = currentTracesSent;
    }
  }

  public static void main(final String[] args) {
    final int exitCode = new CommandLine(new LoadGenerator()).execute(args);
    System.exit(exitCode);
  }

  private class Worker implements Runnable {

    @Override
    public void run() {

      while (true) {
        rateLimiter.acquire();
        final AgentSpan parent = startSpan("parentSpan");

        try (final AgentScope scope = activateSpan(parent, true)) {
          for (int i = 0; i < width; i++) {
            final AgentSpan widthSpan = startSpan("span-" + i);
            try (final AgentScope widthScope = activateSpan(widthSpan, true)) {
              for (int j = 0; j < depth - 2; j++) {
                final AgentSpan depthSpan = startSpan("span-" + i + "-" + j);
                try (final AgentScope depthScope = activateSpan(depthSpan, true)) {
                  // do nothing.  Maybe sleep? but that will mean we need more threads to keep the
                  // effective rate
                } finally {
                  depthSpan.finish();
                }
              }
            } finally {
              widthSpan.finish();
            }
          }
        } finally {
          parent.finish();
        }

        tracesSent.getAndIncrement();
      }
    }
  }
}
