/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.repro.lettuce;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * HTTP load generator sending {@code GET /get/key:<n>} requests until the duration has elapsed.
 *
 * <p>By default it runs closed-loop: {@code concurrency} workers each send one request at a time.
 * With {@code --rate=<n>} it runs open-loop instead: requests are started at a fixed rate
 * regardless of response times, with at most {@code concurrency} in flight; requests that cannot
 * start because that limit is reached are counted as skipped rather than queued. Requests in flight
 * at the deadline are allowed to finish (bounded by the request timeout), so that commands stuck
 * until the Redis command timeout are still observed.
 */
final class LoadGenerator {

  private final URI baseUri;
  private final int concurrency;
  private final int rate;
  private final Duration duration;
  private final Duration requestTimeout;
  private final int keyCount;

  private final LongAdder ok = new LongAdder();
  private final LongAdder failed = new LongAdder();
  private final LongAdder skipped = new LongAdder();
  private final Map<String, LongAdder> failures = new ConcurrentHashMap<>();
  private long[] latencies = new long[1 << 16];
  private int latencyCount;

  private LoadGenerator(
      URI baseUri,
      int concurrency,
      int rate,
      Duration duration,
      Duration requestTimeout,
      int keyCount) {
    this.baseUri = baseUri;
    this.concurrency = concurrency;
    this.rate = rate;
    this.duration = duration;
    this.requestTimeout = requestTimeout;
    this.keyCount = keyCount;
  }

  static void main(String[] args) throws Exception {
    Map<String, String> options = new TreeMap<>();
    for (String arg : args) {
      int index = arg.indexOf('=');
      if (!arg.startsWith("--") || index < 0) {
        throw new IllegalArgumentException("expected --name=value, got " + arg);
      }
      options.put(arg.substring(2, index), arg.substring(index + 1));
    }
    LoadGenerator generator =
        new LoadGenerator(
            URI.create(options.getOrDefault("url", "http://127.0.0.1:8080")),
            Integer.parseInt(options.getOrDefault("concurrency", "256")),
            Integer.parseInt(options.getOrDefault("rate", "0")),
            Duration.ofSeconds(Long.parseLong(options.getOrDefault("duration-seconds", "120"))),
            Duration.ofSeconds(
                Long.parseLong(options.getOrDefault("request-timeout-seconds", "90"))),
            Integer.parseInt(options.getOrDefault("keys", "10000")));
    generator.run();
  }

  private void run() throws InterruptedException {
    System.out.printf(
        "load: url=%s concurrency=%d rate=%s duration=%ss requestTimeout=%ss keys=%d%n",
        baseUri,
        concurrency,
        rate > 0 ? rate + "/s" : "closed-loop",
        duration.toSeconds(),
        requestTimeout.toSeconds(),
        keyCount);

    long start = System.nanoTime();
    long deadline = start + duration.toNanos();
    try (HttpClient client =
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor()) {
      Thread driver =
          Thread.ofPlatform()
              .name("load-driver")
              .start(
                  () -> {
                    try {
                      if (rate > 0) {
                        openLoop(client, requests, start, deadline);
                      } else {
                        for (int i = 0; i < concurrency; i++) {
                          requests.execute(() -> closedLoopWorker(client, deadline));
                        }
                      }
                    } finally {
                      requests.shutdown();
                    }
                  });
      long lastOk = 0;
      long lastFailed = 0;
      while (!requests.awaitTermination(10, TimeUnit.SECONDS)) {
        long currentOk = ok.sum();
        long currentFailed = failed.sum();
        System.out.printf(
            "progress: t=%ds ok=%d (+%d) failed=%d (+%d) skipped=%d%n",
            TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start),
            currentOk,
            currentOk - lastOk,
            currentFailed,
            currentFailed - lastFailed,
            skipped.sum());
        lastOk = currentOk;
        lastFailed = currentFailed;
      }
      driver.join();
    }
    printSummary(System.nanoTime() - start);
  }

  private void closedLoopWorker(HttpClient client, long deadline) {
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      send(client);
    }
  }

  private void openLoop(HttpClient client, ExecutorService requests, long start, long deadline) {
    Semaphore inFlight = new Semaphore(concurrency);
    long interval = TimeUnit.SECONDS.toNanos(1) / rate;
    for (long next = start; next < deadline; next += interval) {
      long wait = next - System.nanoTime();
      if (wait > 0) {
        LockSupport.parkNanos(wait);
      }
      if (!inFlight.tryAcquire()) {
        skipped.increment();
        continue;
      }
      requests.execute(
          () -> {
            try {
              send(client);
            } finally {
              inFlight.release();
            }
          });
    }
  }

  private void send(HttpClient client) {
    HttpRequest request =
        HttpRequest.newBuilder(
                baseUri.resolve("/get/key:" + ThreadLocalRandom.current().nextInt(keyCount)))
            .timeout(requestTimeout)
            .GET()
            .build();
    long begin = System.nanoTime();
    try {
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() == 200) {
        ok.increment();
      } else {
        recordFailure("http." + response.statusCode());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    } catch (Exception e) {
      recordFailure(e.getClass().getName());
    }
    recordLatency(System.nanoTime() - begin);
  }

  private synchronized void recordLatency(long nanos) {
    if (latencyCount == latencies.length) {
      latencies = Arrays.copyOf(latencies, latencyCount * 2);
    }
    latencies[latencyCount++] = nanos;
  }

  private void recordFailure(String type) {
    failed.increment();
    failures.computeIfAbsent(type, unused -> new LongAdder()).increment();
  }

  private void printSummary(long elapsedNanos) {
    long[] all;
    synchronized (this) {
      all = Arrays.copyOf(latencies, latencyCount);
    }
    Arrays.sort(all);
    double seconds = elapsedNanos / 1e9;
    System.out.printf("result.elapsed_seconds=%.1f%n", seconds);
    System.out.printf("result.requests=%d%n", all.length);
    System.out.printf("result.ok=%d%n", ok.sum());
    System.out.printf("result.failed=%d%n", failed.sum());
    System.out.printf("result.skipped=%d%n", skipped.sum());
    System.out.printf("result.throughput_rps=%.0f%n", all.length / seconds);
    for (double percentile : new double[] {50, 90, 99, 99.9}) {
      System.out.printf(
          "result.latency_p%s_ms=%.2f%n", percentile, percentileMillis(all, percentile));
    }
    System.out.printf(
        "result.latency_max_ms=%.2f%n", all.length == 0 ? 0 : all[all.length - 1] / 1e6);
    long overOneSecond = Arrays.stream(all).filter(l -> l >= TimeUnit.SECONDS.toNanos(1)).count();
    System.out.printf("result.latency_over_1s=%d%n", overOneSecond);
    new TreeMap<>(failures)
        .forEach((type, count) -> System.out.printf("result.failure.%s=%d%n", type, count.sum()));
  }

  private static double percentileMillis(long[] sorted, double percentile) {
    if (sorted.length == 0) {
      return 0;
    }
    int index = (int) Math.ceil(percentile / 100 * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))] / 1e6;
  }
}
