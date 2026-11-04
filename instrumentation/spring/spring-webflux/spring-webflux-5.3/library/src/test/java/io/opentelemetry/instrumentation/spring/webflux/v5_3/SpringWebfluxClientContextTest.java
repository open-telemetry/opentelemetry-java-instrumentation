/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

class SpringWebfluxClientContextTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void emptyExchangePreservesExistingSpanLifecycle() {
    List<SpanContext> exchanges = new ArrayList<>();
    ExchangeFunction client =
        filteredExchange(
            request -> {
              exchanges.add(Span.current().getSpanContext());
              return Mono.empty();
            });

    ClientResponse response =
        testing.runWithSpan("parent", () -> exchange(client, "/empty").block());

    assertThat(response).isNull();
    assertThat(exchanges)
        .singleElement()
        .satisfies(context -> assertThat(context.isValid()).isTrue());
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void callbacksUseApplicationContextAndAllowIndependentRequests(boolean hasParent, boolean fails) {
    Context parent = hasParent ? testing.runWithSpan("parent", Context::current) : Context.root();
    SpanContext parentSpan = Span.fromContext(parent).getSpanContext();
    List<SpanContext> callbacks = new ArrayList<>();
    List<SpanContext> exchanges = new ArrayList<>();
    RuntimeException failure = new IllegalStateException("exchange failed");
    ClientResponse response = ClientResponse.create(HttpStatus.OK).build();
    WebClient independent = client(request -> Mono.just(response));
    WebClient client =
        client(
            request -> {
              exchanges.add(Span.current().getSpanContext());
              return Mono.defer(
                  () -> {
                    exchanges.add(Span.current().getSpanContext());
                    return fails ? Mono.error(failure) : Mono.just(response);
                  });
            });

    try (Scope ignored = parent.makeCurrent()) {
      client
          .get()
          .uri("http://localhost/outer")
          .exchangeToMono(Mono::just)
          .doOnSubscribe(
              subscription -> {
                callbacks.add(Span.current().getSpanContext());
                independent
                    .get()
                    .uri("http://localhost/independent")
                    .exchangeToMono(Mono::just)
                    .block();
              })
          .doOnNext(value -> callbacks.add(Span.current().getSpanContext()))
          .doOnError(error -> callbacks.add(Span.current().getSpanContext()))
          .doOnSuccess(value -> callbacks.add(Span.current().getSpanContext()))
          .onErrorResume(error -> error == failure ? Mono.empty() : Mono.error(error))
          .block();
    }

    assertThat(callbacks).containsOnly(parentSpan).hasSize(fails ? 2 : 3);
    assertThat(exchanges).hasSize(2);
    assertThat(exchanges.get(0).isValid()).isTrue();
    assertThat(exchanges.get(0)).isNotEqualTo(parentSpan);
    assertThat(exchanges.get(1)).isEqualTo(exchanges.get(0));
    assertIndependentTraces(hasParent);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delayedRequestAndCancelUseClientContext(boolean hasParent) {
    Context parent = hasParent ? testing.runWithSpan("parent", Context::current) : Context.root();
    List<SpanContext> exchanges = new ArrayList<>();
    List<SpanContext> requests = new ArrayList<>();
    List<SpanContext> cancellations = new ArrayList<>();
    List<SpanContext> callbacks = new ArrayList<>();
    ExchangeFunction client =
        filteredExchange(
            request -> {
              exchanges.add(Span.current().getSpanContext());
              return Mono.<ClientResponse>never()
                  .doOnRequest(n -> requests.add(Span.current().getSpanContext()))
                  .doOnCancel(() -> cancellations.add(Span.current().getSpanContext()));
            });
    BaseSubscriber<ClientResponse> subscriber =
        new BaseSubscriber<ClientResponse>() {
          @Override
          protected void hookOnSubscribe(Subscription subscription) {
            callbacks.add(Span.current().getSpanContext());
          }
        };

    try (Scope ignored = parent.makeCurrent()) {
      // exchangeToMono requests upstream eagerly, so exercise delayed demand at the filter
      // boundary.
      exchange(client, "/cancelled").subscribe(subscriber);
    }
    subscriber.request(1);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    subscriber.cancel();

    assertThat(callbacks).containsExactly(Span.fromContext(parent).getSpanContext());
    assertThat(exchanges).hasSize(1);
    assertThat(exchanges.get(0).isValid()).isTrue();
    assertThat(exchanges.get(0)).isNotEqualTo(Span.fromContext(parent).getSpanContext());
    assertThat(requests).containsExactly(exchanges.get(0));
    assertThat(cancellations).containsExactly(exchanges.get(0));
    if (hasParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("GET").hasKind(CLIENT).hasNoParent()));
    }
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  private static void assertIndependentTraces(boolean hasParent) {
    if (hasParent) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0)),
                  span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("GET").hasKind(CLIENT).hasNoParent()),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("GET").hasKind(CLIENT).hasNoParent()));
    }
  }

  private static Mono<ClientResponse> exchange(ExchangeFunction client, String path) {
    return client.exchange(
        ClientRequest.create(HttpMethod.GET, URI.create("http://localhost" + path)).build());
  }

  private static ExchangeFunction filteredExchange(ExchangeFunction exchange) {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.create(testing.getOpenTelemetry());
    List<ExchangeFilterFunction> filters = new ArrayList<>();
    telemetry.addFilter(filters);
    return filters.get(0).apply(exchange);
  }

  private static WebClient client(ExchangeFunction exchange) {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.create(testing.getOpenTelemetry());
    return WebClient.builder().exchangeFunction(exchange).filters(telemetry::addFilter).build();
  }
}
