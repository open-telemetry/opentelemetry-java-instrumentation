/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.benchmark;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.opentelemetry.context.internal.shaded.WeakConcurrentMap;
import java.util.Map;
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
public class WeakMapBenchmark {

  private static final WeakConcurrentMap<String, String> weakConcurrentMap =
      new WeakConcurrentMap<>(true, true);

  private static final WeakConcurrentMap<String, String> weakConcurrentMapInline =
      new WeakConcurrentMap.WithInlinedExpunction<>();

  private static final Cache<String, String> caffeineCache =
      Caffeine.newBuilder().weakKeys().build();
  private static final Map<String, String> caffeineMap = caffeineCache.asMap();

  private String key;

  @Setup
  public void setUp() {
    key = new String(Thread.currentThread().getName());
  }

  @Benchmark
  @Threads(1)
  public void threads01_weakConcurrentMap(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMap.put(key, "foo"));
    blackhole.consume(weakConcurrentMap.get(key));
    blackhole.consume(weakConcurrentMap.remove(key));
  }

  @Benchmark
  @Threads(5)
  public void threads05_weakConcurrentMap(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMap.put(key, "foo"));
    blackhole.consume(weakConcurrentMap.get(key));
    blackhole.consume(weakConcurrentMap.remove(key));
  }

  @Benchmark
  @Threads(10)
  public void threads10_weakConcurrentMap(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMap.put(key, "foo"));
    blackhole.consume(weakConcurrentMap.get(key));
    blackhole.consume(weakConcurrentMap.remove(key));
  }

  @Benchmark
  @Threads(1)
  public void threads01_weakConcurrentMap_inline(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMapInline.put(key, "foo"));
    blackhole.consume(weakConcurrentMapInline.get(key));
    blackhole.consume(weakConcurrentMapInline.remove(key));
  }

  @Benchmark
  @Threads(5)
  public void threads05_weakConcurrentMap_inline(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMapInline.put(key, "foo"));
    blackhole.consume(weakConcurrentMapInline.get(key));
    blackhole.consume(weakConcurrentMapInline.remove(key));
  }

  @Benchmark
  @Threads(10)
  public void threads10_weakConcurrentMap_inline(Blackhole blackhole) {
    blackhole.consume(weakConcurrentMapInline.put(key, "foo"));
    blackhole.consume(weakConcurrentMapInline.get(key));
    blackhole.consume(weakConcurrentMapInline.remove(key));
  }

  @Benchmark
  @Threads(1)
  public void threads01_caffeine(Blackhole blackhole) {
    blackhole.consume(caffeineMap.put(key, "foo"));
    blackhole.consume(caffeineMap.get(key));
    blackhole.consume(caffeineMap.remove(key));
  }

  @Benchmark
  @Threads(5)
  public void threads05_caffeine(Blackhole blackhole) {
    blackhole.consume(caffeineMap.put(key, "foo"));
    blackhole.consume(caffeineMap.get(key));
    blackhole.consume(caffeineMap.remove(key));
  }

  @Benchmark
  @Threads(10)
  public void threads10_caffeine(Blackhole blackhole) {
    blackhole.consume(caffeineMap.put(key, "foo"));
    blackhole.consume(caffeineMap.get(key));
    blackhole.consume(caffeineMap.remove(key));
  }
}
