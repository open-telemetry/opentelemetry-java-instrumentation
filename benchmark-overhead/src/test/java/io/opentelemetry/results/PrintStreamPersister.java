/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.opentelemetry.config.TestConfig;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

class PrintStreamPersister implements ResultsPersister {

  private final PrintStream out;

  public PrintStreamPersister(PrintStream out) {
    this.out = out;
  }

  @Override
  public void write(List<AppPerfResults> results) {
    TestConfig config = results.stream().findFirst().get().config;
    out.println("----------------------------------------------------------");
    out.println(" Run at " + new Date());
    out.printf(" %s : %s\n", config.getName(), config.getDescription());
    out.printf(
        " %d users, %d iterations\n",
        config.getConcurrentConnections(), config.getTotalIterations());
    out.println("----------------------------------------------------------");

    display(results, "Agent", appPerfResults -> appPerfResults.agent.getName());
    display(
        results,
        "Run duration",
        res -> {
          Duration duration = Duration.ofMillis(res.runDurationMs);
          return String.format(
              "%02d:%02d:%02d",
              duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
        });
    display(results, "Avg. CPU (user) %", res -> String.valueOf(res.averageJvmUserCpu));
    display(results, "Max. CPU (user) %", res -> String.valueOf(res.maxJvmUserCpu));
    display(results, "Avg. mch tot cpu %", res -> String.valueOf(res.averageMachineCpuTotal));
    display(results, "Startup time (ms)", res -> String.valueOf(res.startupDurationMs));
    display(results, "Total allocated MB", res -> format(res.getTotalAllocatedMB()));
    display(results, "Min heap used (MB)", res -> format(res.getMinHeapUsedMB()));
    display(results, "Max heap used (MB)", res -> format(res.getMaxHeapUsedMB()));
    display(results, "Thread switch rate", res -> String.valueOf(res.maxThreadContextSwitchRate));
    display(results, "GC time (ms)", res -> String.valueOf(res.totalGCTime));
    display(
        results,
        "GC pause time (ms)",
        res -> String.valueOf(NANOSECONDS.toMillis(res.totalGcPauseNanos)));
    display(results, "Req. mean (ms)", res -> format(res.requestAvg));
    display(results, "Req. p95 (ms)", res -> format(res.requestP95));
    display(results, "Iter. mean (ms)", res -> format(res.iterationAvg));
    display(results, "Iter. p95 (ms)", res -> format(res.iterationP95));
    display(results, "Net read avg (bps)", res -> format(res.averageNetworkRead));
    display(results, "Net write avg (bps)", res -> format(res.averageNetworkWrite));
    display(results, "Peak threads", res -> String.valueOf(res.peakThreadCount));
  }

  private void display(
      List<AppPerfResults> results, String pref, Function<AppPerfResults, String> vs) {
    out.printf("%-20s: ", pref);
    results.forEach(
        result -> {
          out.printf("%17s", vs.apply(result));
        });
    out.println();
  }

  private String format(double d) {
    return String.format("%.2f", d);
  }
}
