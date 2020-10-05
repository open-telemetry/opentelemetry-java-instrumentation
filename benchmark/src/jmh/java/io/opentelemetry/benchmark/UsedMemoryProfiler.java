/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

public class UsedMemoryProfiler implements InternalProfiler {
  private long totalHeapBefore;
  private long usedHeapBefore;

  @Override
  public String getDescription() {
    return "Used memory heap profiler";
  }

  @Override
  public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
    System.gc();
    System.runFinalization();

    totalHeapBefore = Runtime.getRuntime().totalMemory();
    usedHeapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }

  @Override
  public Collection<? extends Result> afterIteration(
      BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {

    long totalHeap = Runtime.getRuntime().totalMemory();
    long usedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    Collection<ScalarResult> results = new ArrayList<>();
    results.add(
        new ScalarResult("heap.total.before", totalHeapBefore, "bytes", AggregationPolicy.AVG));
    results.add(
        new ScalarResult("heap.used.before", usedHeapBefore, "bytes", AggregationPolicy.AVG));
    results.add(new ScalarResult("heap.total.after", totalHeap, "bytes", AggregationPolicy.AVG));
    results.add(new ScalarResult("heap.used.after", usedHeap, "bytes", AggregationPolicy.AVG));

    return results;
  }
}
