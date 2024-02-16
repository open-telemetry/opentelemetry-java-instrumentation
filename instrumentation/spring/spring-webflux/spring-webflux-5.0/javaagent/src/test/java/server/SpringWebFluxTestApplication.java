/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@SpringBootApplication
@ComponentScan(basePackages = {"server"})
public class SpringWebFluxTestApplication {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");
  private static final CountDownLatch slowRequestLatch = new CountDownLatch(1);

  @Bean
  RouterFunction<ServerResponse> echoRouterFunction(EchoHandler echoHandler) {
    return route(POST("/echo"), new EchoHandlerFunction(echoHandler));
  }

  @Bean
  RouterFunction<ServerResponse> greetRouterFunction(GreetingHandler greetingHandler) {
    return route(GET("/greet"), request -> greetingHandler.defaultGreet())
        .andRoute(GET("/greet/{name}"), greetingHandler::customGreet)
        .andRoute(GET("/greet/{name}/{word}"), greetingHandler::customGreetWithWord)
        .andRoute(GET("/double-greet"), request -> greetingHandler.doubleGreet())
        .andRoute(
            GET("/greet-delayed"),
            request -> greetingHandler.defaultGreet().delayElement(Duration.ofMillis(100)))
        .andRoute(
            GET("/greet-failfast/{id}"),
            request -> {
              throw new IllegalStateException("bad things happen");
            })
        .andRoute(
            GET("/greet-failmono/{id}"),
            request -> Mono.error(new IllegalStateException("bad things happen")))
        .andRoute(
            GET("/greet-traced-method/{id}"),
            request ->
                greetingHandler.intResponse(
                    Mono.just(tracedMethod(Integer.parseInt(request.pathVariable("id"))))))
        .andRoute(
            GET("/greet-mono-from-callable/{id}"),
            request ->
                greetingHandler.intResponse(
                    Mono.fromCallable(
                        () -> tracedMethod(Integer.parseInt(request.pathVariable("id"))))))
        .andRoute(
            GET("/greet-delayed-mono/{id}"),
            request ->
                greetingHandler.intResponse(
                    Mono.just(Integer.parseInt(request.pathVariable("id")))
                        .delayElement(Duration.ofMillis(100))
                        .map(SpringWebFluxTestApplication::tracedMethod)))
        .andRoute(
            GET("/slow"),
            request -> {
              try {
                slowRequestLatch.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                throw new IllegalStateException(e);
              }
              return Mono.delay(Duration.ofMillis(100))
                  .then(ServerResponse.ok().body(BodyInserters.fromObject("ok")));
            });
  }

  public static void resumeSlowRequest() {
    slowRequestLatch.countDown();
  }

  @Component
  public static class GreetingHandler {
    public static final String DEFAULT_RESPONSE = "HELLO";

    Mono<ServerResponse> defaultGreet() {
      return ServerResponse.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(BodyInserters.fromObject(DEFAULT_RESPONSE));
    }

    Mono<ServerResponse> doubleGreet() {
      return ServerResponse.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(BodyInserters.fromObject(DEFAULT_RESPONSE + DEFAULT_RESPONSE));
    }

    Mono<ServerResponse> customGreet(ServerRequest request) {
      return ServerResponse.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(BodyInserters.fromObject(DEFAULT_RESPONSE + " " + request.pathVariable("name")));
    }

    Mono<ServerResponse> customGreetWithWord(ServerRequest request) {
      return ServerResponse.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(
              BodyInserters.fromObject(
                  DEFAULT_RESPONSE
                      + " "
                      + request.pathVariable("name")
                      + " "
                      + request.pathVariable("word")));
    }

    Mono<ServerResponse> intResponse(Mono<FooModel> mono) {
      return ServerResponse.ok()
          .contentType(MediaType.TEXT_PLAIN)
          .body(
              BodyInserters.fromPublisher(
                  mono.map(i -> DEFAULT_RESPONSE + " " + i.getId()), String.class));
    }
  }

  private static FooModel tracedMethod(long id) {
    tracer.spanBuilder("tracedMethod").startSpan().end();
    return new FooModel(id, "tracedMethod");
  }
}
