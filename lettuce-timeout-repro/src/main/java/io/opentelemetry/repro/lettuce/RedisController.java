/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.repro.lettuce;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
class RedisController {

  private final ReactiveStringRedisTemplate redis;
  private final int keyCount;
  private final String value;
  private final LongAdder successes = new LongAdder();
  private final Map<String, LongAdder> errors = new ConcurrentHashMap<>();
  private volatile boolean ready;

  RedisController(
      ReactiveStringRedisTemplate redis,
      @Value("${repro.keys:10000}") int keyCount,
      @Value("${repro.value-size:128}") int valueSize) {
    this.redis = redis;
    this.keyCount = keyCount;
    this.value = "v".repeat(valueSize);
  }

  // reactive GET, the operation reported to time out
  @GetMapping("/get/{key}")
  Mono<String> get(@PathVariable String key) {
    return redis
        .opsForValue()
        .get(key)
        .defaultIfEmpty("")
        .doOnSuccess(unused -> successes.increment())
        .doOnError(this::recordError);
  }

  // populates the keys read by the load generator; called once by run.sh before the load starts
  @GetMapping("/populate")
  Mono<String> populate() {
    return Flux.range(0, keyCount)
        .flatMap(i -> redis.opsForValue().set("key:" + i, value), 64)
        .then(Mono.fromCallable(this::markReady));
  }

  @GetMapping("/ready")
  ResponseEntity<String> ready() {
    return ready
        ? ResponseEntity.ok("ready")
        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("populating");
  }

  @GetMapping("/stats")
  Map<String, Long> stats() {
    Map<String, Long> result = new TreeMap<>();
    result.put("redis.get.success", successes.sum());
    errors.forEach((type, count) -> result.put("redis.get.error." + type, count.sum()));
    return result;
  }

  private String markReady() {
    ready = true;
    return "populated " + keyCount + " keys";
  }

  private void recordError(Throwable error) {
    StringBuilder type = new StringBuilder(error.getClass().getName());
    for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
      type.append("<-").append(cause.getClass().getName());
    }
    errors.computeIfAbsent(type.toString(), unused -> new LongAdder()).increment();
  }
}
