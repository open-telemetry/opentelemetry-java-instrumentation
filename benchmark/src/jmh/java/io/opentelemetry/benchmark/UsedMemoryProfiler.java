package io.opentelemetry.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import org.openjdk.jmh.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.profile.*;
import org.openjdk.jmh.results.*;

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
        new ScalarResult("·heap.total.before", totalHeapBefore, "bytes", AggregationPolicy.AVG));
    results.add(
        new ScalarResult("·heap.used.before", usedHeapBefore, "bytes", AggregationPolicy.AVG));
    results.add(new ScalarResult("·heap.total.after", totalHeap, "bytes", AggregationPolicy.AVG));
    results.add(new ScalarResult("·heap.used.after", usedHeap, "bytes", AggregationPolicy.AVG));
    results.add(
        new ScalarResult(
            "·heap.total.change", totalHeap - totalHeapBefore, "bytes", AggregationPolicy.AVG));
    results.add(
        new ScalarResult(
            "·heap.used.change", usedHeap - usedHeapBefore, "bytes", AggregationPolicy.AVG));

    return results;
  }
}
