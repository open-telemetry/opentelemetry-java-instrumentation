/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.agents.Agent;
import io.opentelemetry.config.TestConfig;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

public class ConsoleResultsPersister implements ResultsPersister {

  @Override
  public void write(Map<Agent, AppPerfResults> results) {
    TestConfig config = results.values().stream().findFirst().get().getConfig();
    System.out.println("----------------------------------------------------------");
    System.out.printf(" %s : %s\n", config.getName(), config.getDescription());
    System.out.printf(" %d users, %d iterations\n", config.getConcurrentConnections(), config.getTotalIterations());
    System.out.println("----------------------------------------------------------");

    display(results, "Agent", appPerfResults -> appPerfResults.getAgent().getName());
    display(results, "Startup time (ms)", res -> String.valueOf(res.getStartupDurationMs()));
    display(results, "Total allocated MB", res -> format(res.getTotalAllocatedMB()));
    display(results, "Heap (min)", res -> String.valueOf(res.getHeapUsed().min));
    display(results, "Heap (max)", res -> String.valueOf(res.getHeapUsed().max));
    display(results, "Thread switch rate",
        res -> String.valueOf(res.getMaxThreadContextSwitchRate()));
    display(results, "GC time", res -> String.valueOf(res.getTotalGCTime()));
    display(results, "Req. mean", res -> format(res.getRequestAvg()));
    display(results, "Req. p95", res -> format(res.getRequestP95()));
    display(results, "Iter. mean", res -> format(res.getIterationAvg()));
    display(results, "Iter. p95", res -> format(res.getIterationP95()));
    display(results, "Peak threads", res -> String.valueOf(res.getPeakThreadCount()));
  }

  private void display(Map<Agent, AppPerfResults> results, String pref,
      Function<AppPerfResults, String> vs) {
    System.out.printf("%-20s: ", pref);
    results.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getKey().getName()))
        .forEach(entry -> {
          Agent agent = entry.getKey();
          System.out.printf("%17s", vs.apply(results.get(agent)));
      });
    System.out.println();
  }

  private String format(double d) {
    return String.format("%.2f", d);
  }

}
