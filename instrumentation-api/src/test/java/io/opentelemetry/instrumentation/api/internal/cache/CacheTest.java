/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CacheTest {
  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Nested
  @SuppressWarnings("ClassCanBeStatic")
  class StrongKeys {
    @Test
    void bounded() {
      Cache<String, String> cache = Cache.bounded(1);

      assertThat(cache.computeIfAbsent("bear", unused -> "roar")).isEqualTo("roar");
      cache.remove("bear");

      MapBackedCache<?, ?> mapBackedCache = ((MapBackedCache<?, ?>) cache);
      assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
      assertThat(mapBackedCache.size()).isEqualTo(1);

      assertThat(cache.computeIfAbsent("cat", unused -> "bark")).isEqualTo("meow");
      assertThat(mapBackedCache.size()).isEqualTo(1);

      cache.put("dog", "bark");
      assertThat(cache.get("dog")).isEqualTo("bark");
      assertThat(mapBackedCache.size()).isEqualTo(1);
      assertThat(cache.computeIfAbsent("cat", unused -> "purr")).isEqualTo("purr");
    }
  }

  @Nested
  @SuppressWarnings("ClassCanBeStatic")
  class WeakKeys {
    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    void unbounded() {
      Cache<String, String> cache = Cache.weak();

      assertThat(cache.computeIfAbsent("bear", unused -> "roar")).isEqualTo("roar");
      cache.remove("bear");

      WeakLockFreeCache<?, ?> weakLockFreeCache = ((WeakLockFreeCache<?, ?>) cache);
      String cat = new String("cat");
      String dog = new String("dog");
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");
      assertThat(weakLockFreeCache.size()).isEqualTo(1);

      assertThat(cache.computeIfAbsent(cat, unused -> "bark")).isEqualTo("meow");
      assertThat(weakLockFreeCache.size()).isEqualTo(1);

      cache.put(dog, "bark");
      assertThat(cache.get(dog)).isEqualTo("bark");
      assertThat(cache.get(cat)).isEqualTo("meow");
      assertThat(cache.get(new String("dog"))).isNull();
      assertThat(weakLockFreeCache.size()).isEqualTo(2);
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");

      cat = null;
      System.gc();
      // Wait for GC to be reflected.
      await().untilAsserted(() -> assertThat(weakLockFreeCache.size()).isEqualTo(1));
      assertThat(cache.computeIfAbsent(dog, unused -> "bark")).isEqualTo("bark");
      dog = null;
      System.gc();
      // Wait for GC to be reflected.
      await().untilAsserted(() -> assertThat(weakLockFreeCache.size()).isEqualTo(0));
    }

    // regression test for
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/18232
    @Test
    void computeIfAbsentDeadlock() throws InterruptedException {
      Cache<Object, String> cache1 = Cache.weak();
      Cache<String, String> cache2 = Cache.weak();

      class State {
        Object key = new Object();
        final WeakReference<Object> keyRef = new WeakReference<>(key);
      }

      // add a bunch of entries to cache1, later we'll let GC clear them so that there would be
      // stale elements to expunge from the cache
      List<State> state = new ArrayList<>();
      for (int i = 0; i < 100_000; i++) {
        State s = new State();
        state.add(s);
        cache1.put(s.key, "value");
      }

      // To truly tigger a deadlock we'd need to call computeIfAbsent on both caches and inside
      // computeIfAbsent call some other operation on the cache to trigger inline eviction so that
      // it removes elements from the other cache.
      // Since that is too complicated we use a latch to simulate a long-running computation that
      // locks the map (or a part of it) and see if we can get another thread to block on it.
      CountDownLatch wait = new CountDownLatch(1);
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch completed = new CountDownLatch(1);
      Thread t1 =
          new Thread(
              () ->
                  cache1.computeIfAbsent(
                      "test",
                      unused -> {
                        started.countDown();
                        try {
                          if (!wait.await(20, SECONDS)) {
                            throw new IllegalStateException("wait timed out");
                          }
                          return "value";
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                      }));
      cleanup.deferCleanup(t1::interrupt);
      t1.start();

      if (!started.await(10, SECONDS)) {
        throw new IllegalStateException("wait timed out");
      }
      Thread t2 =
          new Thread(
              () ->
                  cache2.computeIfAbsent(
                      "key",
                      unused -> {
                        // first clean keys so they could ge GCd
                        for (State s : state) {
                          s.key = null;
                        }
                        // trigger GC and wait for the key to be cleared
                        for (State s : state) {
                          try {
                            GcUtils.awaitGc(s.keyRef, Duration.ofSeconds(10));
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          } catch (TimeoutException e) {
                            throw new IllegalStateException(e);
                          }
                        }
                        // now trigger inline eviction of the cleared keys in cache1
                        // note that currently inline eviction is disabled when inside
                        // computeIfAbsent call to
                        // trigger the deadlock modify AbstractWeakConcurrentMap.expungeStaleEntries
                        // method
                        cache2.get("test");
                        completed.countDown();
                        return "value";
                      }));
      cleanup.deferCleanup(t2::interrupt);
      t2.start();
      // this wait will time out when t2 is blocked by t1
      if (!completed.await(10, SECONDS)) {
        throw new IllegalStateException("wait timed out");
      }

      wait.countDown();
      t1.join(Duration.ofSeconds(10).toMillis());
    }
  }
}
