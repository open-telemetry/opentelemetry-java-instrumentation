/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

import java.time.Duration

@RestController
class TestController {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test")

  @GetMapping("/foo")
  Mono<FooModel> getFooModel() {
    return Mono.just(new FooModel(0L, "DEFAULT"))
  }

  @GetMapping("/foo/{id}")
  Mono<FooModel> getFooModel(@PathVariable("id") long id) {
    return Mono.just(new FooModel(id, "pass"))
  }

  @GetMapping("/foo/{id}/{name}")
  Mono<FooModel> getFooModel(@PathVariable("id") long id, @PathVariable("name") String name) {
    return Mono.just(new FooModel(id, name))
  }

  @GetMapping("/foo-delayed")
  Mono<FooModel> getFooDelayed() {
    return Mono.just(new FooModel(3L, "delayed")).delayElement(Duration.ofMillis(100))
  }

  @GetMapping("/foo-failfast/{id}")
  Mono<FooModel> getFooFailFast(@PathVariable("id") long id) {
    throw new IllegalStateException("bad things happen")
  }

  @GetMapping("/foo-failmono/{id}")
  Mono<FooModel> getFooFailMono(@PathVariable("id") long id) {
    return Mono.error(new IllegalStateException("bad things happen"))
  }

  @GetMapping("/foo-traced-method/{id}")
  Mono<FooModel> getTracedMethod(@PathVariable("id") long id) {
    return Mono.just(tracedMethod(id))
  }

  @GetMapping("/foo-mono-from-callable/{id}")
  Mono<FooModel> getMonoFromCallable(@PathVariable("id") long id) {
    return Mono.fromCallable { return tracedMethod(id) }
  }

  @GetMapping("/foo-delayed-mono/{id}")
  Mono<FooModel> getFooDelayedMono(@PathVariable("id") long id) {
    return Mono.just(id).delayElement(Duration.ofMillis(100)).map { i -> tracedMethod(i) }
  }

  private FooModel tracedMethod(long id) {
    tracer.spanBuilder("tracedMethod").startSpan().end()
    return new FooModel(id, "tracedMethod")
  }
}
