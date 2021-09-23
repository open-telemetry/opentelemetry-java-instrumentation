/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class PrintStreamPersister implements ResultsPersister {

  private final PrintStream out;

  public PrintStreamPersister(PrintStream out) {
    this.out = out;
  }

  @Override
  public void write(List<AppPerfResults> results) {
    List<AppPerfResults> sorted =
        results.stream()
            .sorted(Comparator.comparing(AppPerfResults::getAgentName))
            .collect(Collectors.toList());
    TestConfig config = sorted.stream().findFirst().get().config;
    out.println("----------------------------------------------------------");
    out.println(" Run at " + new Date());
    out.printf(" %s : %s\n", config.getName(), config.getDescription());
    out.printf(
        " %d users, %d iterations\n",
        config.getConcurrentConnections(), config.getTotalIterations());
    out.println("----------------------------------------------------------");

    display(sorted, "Agent", appPerfResults -> appPerfResults.agent.getName());
    display(sorted, "Avg. CPU (user)", res -> String.valueOf(res.averageJvmUserCpu));
    display(sorted, "Max. CPU (user)", res -> String.valueOf(res.maxJvmUserCpu));
    display(sorted, "Startup time (ms)", res -> String.valueOf(res.startupDurationMs));
    display(sorted, "Total allocated MB", res -> format(res.getTotalAllocatedMB()));
    display(sorted, "Heap (min)", res -> String.valueOf(res.heapUsed.min));
    display(sorted, "Heap (max)", res -> String.valueOf(res.heapUsed.max));
    display(sorted, "Thread switch rate", res -> String.valueOf(res.maxThreadContextSwitchRate));
    display(sorted, "GC time", res -> String.valueOf(res.totalGCTime));
    display(sorted, "Req. mean", res -> format(res.requestAvg));
    display(sorted, "Req. p95", res -> format(res.requestP95));
    display(sorted, "Iter. mean", res -> format(res.iterationAvg));
    display(sorted, "Iter. p95", res -> format(res.iterationP95));
    display(sorted, "Net read avg (bps)", res -> format(res.averageNetworkRead));
    display(sorted, "Net write avg (bps)", res -> format(res.averageNetworkWrite));
    display(sorted, "Peak threads", res -> String.valueOf(res.peakThreadCount));
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
