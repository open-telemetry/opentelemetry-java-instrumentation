/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Fork(3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CacheBenchmark {

  private static final Cache<Object, Object> weakCache = Cache.weak();
  private static final Cache<Object, Object> boundedLargeCache = Cache.bounded(10);
  private static final Cache<Object, Object> boundedSmallCache = Cache.bounded(1);

  private String key;
  private String key2;

  @SuppressWarnings("StringOperationCanBeSimplified")
  @Setup
  public void setUp() {
    key = new String(Thread.currentThread().getName());
    key2 = new String(Thread.currentThread().getName()) + "2";
  }

  @Benchmark
  @Threads(1)
  public void threads01_weakConcurrentMap(Blackhole blackhole) {
    weakCache.put(key, "foo");
    blackhole.consume(weakCache.get(key));
    weakCache.put(key2, "foo");
    blackhole.consume(weakCache.get(key2));
    weakCache.remove(key);
    weakCache.remove(key2);
    blackhole.consume(weakCache.get(key));
    blackhole.consume(weakCache.get(key2));
  }

  @Benchmark
  @Threads(5)
  public void threads05_weakConcurrentMap(Blackhole blackhole) {
    weakCache.put(key, "foo");
    blackhole.consume(weakCache.get(key));
    weakCache.put(key2, "foo");
    blackhole.consume(weakCache.get(key2));
    weakCache.remove(key);
    weakCache.remove(key2);
    blackhole.consume(weakCache.get(key));
    blackhole.consume(weakCache.get(key2));
  }

  @Benchmark
  @Threads(10)
  public void threads10_weakConcurrentMap(Blackhole blackhole) {
    weakCache.put(key, "foo");
    blackhole.consume(weakCache.get(key));
    weakCache.put(key2, "foo");
    blackhole.consume(weakCache.get(key2));
    weakCache.remove(key);
    weakCache.remove(key2);
    blackhole.consume(weakCache.get(key));
    blackhole.consume(weakCache.get(key2));
  }

  @Benchmark
  @Threads(1)
  public void threads01_boundedLarge(Blackhole blackhole) {
    boundedLargeCache.put(key, "foo");
    blackhole.consume(boundedLargeCache.get(key));
    boundedLargeCache.put(key2, "foo");
    blackhole.consume(boundedLargeCache.get(key2));
    boundedLargeCache.remove(key);
    boundedLargeCache.remove(key2);
    blackhole.consume(boundedLargeCache.get(key));
    blackhole.consume(boundedLargeCache.get(key2));
  }

  @Benchmark
  @Threads(5)
  public void threads05_boundedLarge(Blackhole blackhole) {
    boundedLargeCache.put(key, "foo");
    blackhole.consume(boundedLargeCache.get(key));
    boundedLargeCache.put(key2, "foo");
    blackhole.consume(boundedLargeCache.get(key2));
    boundedLargeCache.remove(key);
    boundedLargeCache.remove(key2);
    blackhole.consume(boundedLargeCache.get(key));
    blackhole.consume(boundedLargeCache.get(key2));
  }

  @Benchmark
  @Threads(10)
  public void threads10_boundedLarge(Blackhole blackhole) {
    boundedLargeCache.put(key, "foo");
    blackhole.consume(boundedLargeCache.get(key));
    boundedLargeCache.put(key2, "foo");
    blackhole.consume(boundedLargeCache.get(key2));
    boundedLargeCache.remove(key);
    boundedLargeCache.remove(key2);
    blackhole.consume(boundedLargeCache.get(key));
    blackhole.consume(boundedLargeCache.get(key2));
  }

  @Benchmark
  @Threads(1)
  public void threads01_boundedSmall(Blackhole blackhole) {
    boundedSmallCache.put(key, "foo");
    blackhole.consume(boundedSmallCache.get(key));
    boundedSmallCache.put(key2, "foo");
    blackhole.consume(boundedSmallCache.get(key2));
    boundedSmallCache.remove(key);
    boundedSmallCache.remove(key2);
    blackhole.consume(boundedSmallCache.get(key));
    blackhole.consume(boundedSmallCache.get(key2));
  }

  @Benchmark
  @Threads(5)
  public void threads05_boundedSmall(Blackhole blackhole) {
    boundedSmallCache.put(key, "foo");
    blackhole.consume(boundedSmallCache.get(key));
    boundedSmallCache.put(key2, "foo");
    blackhole.consume(boundedSmallCache.get(key2));
    boundedSmallCache.remove(key);
    boundedSmallCache.remove(key2);
    blackhole.consume(boundedSmallCache.get(key));
    blackhole.consume(boundedSmallCache.get(key2));
  }

  @Benchmark
  @Threads(10)
  public void threads10_boundedSmall(Blackhole blackhole) {
    boundedSmallCache.put(key, "foo");
    blackhole.consume(boundedSmallCache.get(key));
    boundedSmallCache.put(key2, "foo");
    blackhole.consume(boundedSmallCache.get(key2));
    boundedSmallCache.remove(key);
    boundedSmallCache.remove(key2);
    blackhole.consume(boundedSmallCache.get(key));
    blackhole.consume(boundedSmallCache.get(key2));
  }
}
