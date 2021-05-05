/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StopWatchImpl implements StopWatch {
  public static final ThreadLocal<Boolean> enabled =
      new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
          return Boolean.TRUE;
        }
      };
  private static final Map<String, Counter> map = new ConcurrentHashMap<>();

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                Map<Counter, String> counterToName = new HashMap<>();
                map.forEach((key, value) -> counterToName.put(value, key));

                List<Counter> counters = new ArrayList<>(map.values());
                counters.sort((c1, c2) -> Long.compare(c2.time, c1.time));

                for (Counter counter : counters) {
                  long nanos = counter.time;
                  long millis = nanos / 1_000_000;
                  System.err.println(
                      counterToName.get(counter)
                          + " count="
                          + counter.count
                          + ", time="
                          + millis
                          + "ms");
                }
              }
            });
  }

  private static final StopWatch NOP =
      new StopWatch() {
        @Override
        public void stop() {}

        @Override
        public void close() {}
      };

  private final String name;
  private final long start;

  public static StopWatch create(String name) {
    if (enabled.get() != Boolean.TRUE) {
      return NOP;
    }
    return new StopWatchImpl(name);
  }

  private StopWatchImpl(String name) {
    this.name = name;
    this.start = System.nanoTime();
  }

  @Override
  public void stop() {
    long end = System.nanoTime();
    Counter counter = map.get(name);
    if (counter == null) {
      counter = new Counter();
      Counter old = map.putIfAbsent(name, counter);
      if (old != null) {
        counter = old;
      }
    }
    counter.addInvocation(end - start);
  }

  @Override
  public void close() {
    stop();
  }

  private static class Counter {
    long count;
    long time;

    synchronized void addInvocation(long delta) {
      count++;
      time += delta;
    }
  }
}
