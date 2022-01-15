/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.results;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class CsvPersister implements ResultsPersister {

  // The fields as they are output, in order, but spread across agents
  private static final List<FieldSpec> FIELDS =
      Arrays.asList(
          FieldSpec.of("startupDurationMs", r -> r.startupDurationMs),
          FieldSpec.of("minHeapUsed", r -> r.heapUsed.min),
          FieldSpec.of("maxHeapUsed", r -> r.heapUsed.max),
          FieldSpec.of("totalAllocatedMB", r -> r.getTotalAllocatedMB()),
          FieldSpec.of("totalGCTime", r -> r.totalGCTime),
          FieldSpec.of("maxThreadContextSwitchRate", r -> r.maxThreadContextSwitchRate),
          FieldSpec.of("iterationAvg", r -> r.iterationAvg),
          FieldSpec.of("iterationP95", r -> r.iterationP95),
          FieldSpec.of("requestAvg", r -> r.requestAvg),
          FieldSpec.of("requestP95", r -> r.requestP95),
          FieldSpec.of("netReadAvg", r -> r.averageNetworkRead),
          FieldSpec.of("netWriteAvg", r -> r.averageNetworkWrite),
          FieldSpec.of("peakThreadCount", r -> r.peakThreadCount),
          FieldSpec.of("averageCpuUser", r -> r.averageJvmUserCpu),
          FieldSpec.of("maxCpuUser", r -> r.maxJvmUserCpu),
          FieldSpec.of("averageMachineCpuTotal", r -> r.averageMachineCpuTotal),
          FieldSpec.of("runDurationMs", r -> r.runDurationMs),
          FieldSpec.of("gcPauseMs", r -> NANOSECONDS.toMillis(r.totalGcPauseNanos)));

  private final Path resultsFile;

  public CsvPersister(Path resultsFile) {
    this.resultsFile = resultsFile;
  }

  @Override
  public void write(List<AppPerfResults> results) {

    ensureFileCreated(results);

    StringBuilder sb = new StringBuilder().append(System.currentTimeMillis() / 1000);
    // Don't be confused by the loop -- This generates a single long csv line.
    // Each result is for a given agent run, and we want all the fields for all agents on the same
    // line so that we can create a columnar structure that allows us to more easily compare agent
    // to agent for a given run.
    for (FieldSpec field : FIELDS) {
      for (AppPerfResults result : results) {
        sb.append(",").append(field.getter.apply(result));
      }
    }
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

    List<String> agents = results.stream().map(r -> r.agent.getName()).collect(Collectors.toList());
    for (FieldSpec field : FIELDS) {
      for (String agent : agents) {
        sb.append(",").append(agent).append(':').append(field.name);
      }
    }

    sb.append("\n");
    return sb.toString();
  }

  static class FieldSpec {
    private final String name;
    private final Function<AppPerfResults, Object> getter;

    public FieldSpec(String name, Function<AppPerfResults, Object> getter) {
      this.name = name;
      this.getter = getter;
    }

    static FieldSpec of(String name, Function<AppPerfResults, Object> getter) {
      return new FieldSpec(name, getter);
    }
  }
}
