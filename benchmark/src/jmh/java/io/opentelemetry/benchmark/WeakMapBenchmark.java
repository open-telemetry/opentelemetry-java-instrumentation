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

@Fork(3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class WeakMapBenchmark {

  private static final WeakConcurrentMap<String, String> weakConcurrentMap = new WeakConcurrentMap<>(true, true);

  private static final Cache<String, String> caffeineCache = Caffeine.newBuilder()
      .weakKeys()
      .build();
  private static final Map<String, String> caffeineMap = caffeineCache.asMap();

  private String key;

  @Setup
  public void setUp() {
    key = new String(Thread.currentThread().getName());
  }

  @Benchmark
  @Threads(1)
  public void weakConcurrentMap_oneThread() {
    weakConcurrentMap.put(key, "foo");
    weakConcurrentMap.get(key);
    weakConcurrentMap.remove(key);
  }

  @Benchmark
  @Threads(1)
  public void caffeineMap_oneThread() {
    caffeineMap.put(key, "foo");
    caffeineMap.get(key);
    caffeineMap.remove(key);
  }

  @Benchmark
  @Threads(5)
  public void weakConcurrentMap_fiveThreads() {
    weakConcurrentMap.put(key, "foo");
    weakConcurrentMap.get(key);
    weakConcurrentMap.remove(key);
  }

  @Benchmark
  @Threads(5)
  public void caffeineMap_fiveThreads() {
    caffeineMap.put(key, "foo");
    caffeineMap.get(key);
    caffeineMap.remove(key);
  }

  @Benchmark
  @Threads(10)
  public void weakConcurrentMap_tenThreads() {
    weakConcurrentMap.put(key, "foo");
    weakConcurrentMap.get(key);
    weakConcurrentMap.remove(key);
  }

  @Benchmark
  @Threads(10)
  public void caffeineMap_tenThreads() {
    caffeineMap.put(key, "foo");
    caffeineMap.get(key);
    caffeineMap.remove(key);
  }
}
