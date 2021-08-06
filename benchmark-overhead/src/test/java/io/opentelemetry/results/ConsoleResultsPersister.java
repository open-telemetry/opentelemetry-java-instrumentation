/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ConsoleResultsPersister implements ResultsPersister {

  @Override
  public void write(List<AppPerfResults> results) {
    TestConfig config = results.stream().findFirst().get().config;
    System.out.println("----------------------------------------------------------");
    System.out.printf(" %s : %s\n", config.getName(), config.getDescription());
    System.out.printf(" %d users, %d iterations\n", config.getConcurrentConnections(), config.getTotalIterations());
    System.out.println("----------------------------------------------------------");

    display(results, "Agent", appPerfResults -> appPerfResults.agent.getName());
    display(results, "Startup time (ms)", res -> String.valueOf(res.startupDurationMs));
    display(results, "Total allocated MB", res -> format(res.getTotalAllocatedMB()));
    display(results, "Heap (min)", res -> String.valueOf(res.heapUsed.min));
    display(results, "Heap (max)", res -> String.valueOf(res.heapUsed.max));
    display(results, "Thread switch rate",
        res -> String.valueOf(res.maxThreadContextSwitchRate));
    display(results, "GC time", res -> String.valueOf(res.totalGCTime));
    display(results, "Req. mean", res -> format(res.requestAvg));
    display(results, "Req. p95", res -> format(res.requestP95));
    display(results, "Iter. mean", res -> format(res.iterationAvg));
    display(results, "Iter. p95", res -> format(res.iterationP95));
    display(results, "Peak threads", res -> String.valueOf(res.peakThreadCount));
  }

  private void display(List<AppPerfResults> results, String pref,
      Function<AppPerfResults, String> vs) {
    System.out.printf("%-20s: ", pref);
    results.stream()
        .sorted(Comparator.comparing(AppPerfResults::getAgentName))
        .forEach(result -> {
          System.out.printf("%17s", vs.apply(result));
      });
    System.out.println();
  }

  private String format(double d) {
    return String.format("%.2f", d);
  }

}
