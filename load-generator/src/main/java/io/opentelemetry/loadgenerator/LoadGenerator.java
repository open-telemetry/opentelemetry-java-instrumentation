/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.loadgenerator;

import com.google.common.util.concurrent.RateLimiter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

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
      Thread workerThread = new Thread(new Worker(), "Worker-" + i);
      workerThread.setDaemon(true);
      workerThread.start();
    }

    while (true) {
      Thread.sleep(printInterval * 1000);

      long currentTracesSent = tracesSent.get();
      long intervalEnd = System.currentTimeMillis();

      double currentRate =
          (currentTracesSent - tracesAtLastReport) / ((intervalEnd - intervalStart) / 1000d);

      System.out.println(
          "Total Traces Sent: " + currentTracesSent + ", Rate this interval: " + currentRate);
      intervalStart = System.currentTimeMillis();
      tracesAtLastReport = currentTracesSent;
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new LoadGenerator()).execute(args);
    System.exit(exitCode);
  }

  private class Worker implements Runnable {

    @Override
    public void run() {

      while (true) {
        rateLimiter.acquire();
        Span parent = tracer.spanBuilder("parentSpan").startSpan();

        try (Scope scope = parent.makeCurrent()) {
          for (int i = 0; i < width; i++) {
            Span widthSpan = tracer.spanBuilder("span-" + i).startSpan();
            try (Scope widthScope = widthSpan.makeCurrent()) {
              for (int j = 0; j < depth - 2; j++) {
                Span depthSpan = tracer.spanBuilder("span-" + i + "-" + j).startSpan();
                try (Scope depthScope = depthSpan.makeCurrent()) {
                  // do nothing.  Maybe sleep? but that will mean we need more threads to keep the
                  // effective rate
                } finally {
                  depthSpan.end();
                }
              }
            } finally {
              widthSpan.end();
            }
          }
        } finally {
          parent.end();
        }

        tracesSent.getAndIncrement();
      }
    }
  }
}
