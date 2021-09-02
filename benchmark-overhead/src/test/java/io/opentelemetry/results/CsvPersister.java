/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

class CsvPersister implements ResultsPersister {

  private final Path resultsFile;

  public CsvPersister(Path resultsFile) {this.resultsFile = resultsFile;}

  @Override
  public void write(List<AppPerfResults> results) {

    ensureFileCreated(results);

    StringBuilder sb = new StringBuilder().append(System.currentTimeMillis() / 1000);
    // Don't be confused by the loop -- This generates a single long csv line.
    // Each result is for a given agent run, and we want all the fields for all agents on the same
    // line so that we can create a columnar structure that allows us to more easily compare agent
    // to agent for a given run.
    doSorted(results, result -> {
      sb.append(",").append(result.startupDurationMs);
      sb.append(",").append(result.heapUsed.min);
      sb.append(",").append(result.heapUsed.max);
      sb.append(",").append(result.getTotalAllocatedMB());
      sb.append(",").append(result.totalGCTime);
      sb.append(",").append(result.maxThreadContextSwitchRate);
      sb.append(",").append(result.iterationAvg);
      sb.append(",").append(result.iterationP95);
      sb.append(",").append(result.requestAvg);
      sb.append(",").append(result.requestP95);
      sb.append(",").append(result.averageNetworkRead);
      sb.append(",").append(result.averageNetworkWrite);
      sb.append(",").append(result.peakThreadCount);
      sb.append(",").append(result.averageJvmUserCpu);
      sb.append(",").append(result.maxJvmUserCpu);
    });
    sb.append("\n");
    try {
      Files.writeString(resultsFile, sb.toString(), StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new RuntimeException("Error writing csv content", e);
    }
  }

  private void ensureFileCreated(List<AppPerfResults> results) {
    if (Files.exists(resultsFile)) {
      return;
    }
    try {
      String headerLine = createHeaderLine(results);
      Files.writeString(resultsFile, headerLine);
    } catch (IOException e) {
      throw new RuntimeException("Error creating csv output stub", e);
    }
  }

  private String createHeaderLine(List<AppPerfResults> results) {
    StringBuilder sb = new StringBuilder("timestamp");
    // Don't be confused by the loop -- This generates a single long csv line.
    // Each result is for a given agent run, and we want all the fields for all agents on the same
    // line so that we can create a columnar structure that allows us to more easily compare agent
    // to agent for a given run.
    doSorted(results, result -> {
      String agent = result.getAgentName();
      sb.append(",").append(agent).append(":startupTimeMs");
      sb.append(",").append(agent).append(":minHeapUsed");
      sb.append(",").append(agent).append(":maxHeapUsed");
      sb.append(",").append(agent).append(":totalAllocatedMB");
      sb.append(",").append(agent).append(":totalGCTime");
      sb.append(",").append(agent).append(":maxThreadContextSwitchRate");
      sb.append(",").append(agent).append(":iterationAvg");
      sb.append(",").append(agent).append(":iterationP95");
      sb.append(",").append(agent).append(":requestAvg");
      sb.append(",").append(agent).append(":requestP95");
      sb.append(",").append(agent).append(":netReadAvg");
      sb.append(",").append(agent).append(":netWriteAvg");
      sb.append(",").append(agent).append(":peakThreadCount");
      sb.append(",").append(agent).append(":averageCpuUser");
      sb.append(",").append(agent).append(":maxCpuUser");
    });
    sb.append("\n");
    return sb.toString();
  }

  private void doSorted(List<AppPerfResults> results, Consumer<AppPerfResults> consumer) {
    results.stream()
        .sorted(Comparator.comparing(AppPerfResults::getAgentName))
        .forEach(consumer);
  }
}
