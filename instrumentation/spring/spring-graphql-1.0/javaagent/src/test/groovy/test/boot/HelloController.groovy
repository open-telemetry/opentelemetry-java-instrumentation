package test.boot

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.Callable

@Controller
@SchemaMapping(typeName = "Query")
@SuppressWarnings("unused")
class HelloController {

  @SchemaMapping
  String helloWorldAsValue() {
    return "Hello World!"
  }

  @SchemaMapping
  Mono<String> helloWorldAsMono() {
    return Mono.just("Hello World!")
  }

  @SchemaMapping
  Flux<String> helloWorldAsFlux() {
    return Mono.just("Hello World!").flux()
  }

  @SchemaMapping
  Callable<String> helloWorldAsCallable() {
    return () -> "Hello World!"
  }

}
