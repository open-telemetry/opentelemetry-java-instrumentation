/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package server

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

import java.time.Duration

@RestController
class TestController {

  private static final Tracer TRACER = OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto")

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
    throw new RuntimeException("bad things happen")
  }

  @GetMapping("/foo-failmono/{id}")
  Mono<FooModel> getFooFailMono(@PathVariable("id") long id) {
    return Mono.error(new RuntimeException("bad things happen"))
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
    TRACER.spanBuilder("tracedMethod").startSpan().end()
    return new FooModel(id, "tracedMethod")
  }
}
