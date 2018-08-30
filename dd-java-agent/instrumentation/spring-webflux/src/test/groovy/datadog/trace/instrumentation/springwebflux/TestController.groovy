package datadog.trace.instrumentation.springwebflux

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class TestController {

  @GetMapping("/failfoo/{id}")
  Mono<FooModel> getFooModelFail(@PathVariable("id") long id) {
    return Mono.just(new FooModel((id / 0), "fail"))
  }

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

  @GetMapping(value = "/annotation-foos/{count}")
  Flux<FooModel> getXFooModels(@PathVariable("count") long count) {
    FooModel[] foos = FooModel.createXFooModels(count)
    return Flux.just(foos)
  }
}
