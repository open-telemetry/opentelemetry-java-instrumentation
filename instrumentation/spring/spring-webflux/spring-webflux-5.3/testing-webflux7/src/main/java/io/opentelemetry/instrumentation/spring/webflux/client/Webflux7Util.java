/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class Webflux7Util {
  static final boolean IS_WEBFLUX_7 = detectWebflux7();

  private static boolean detectWebflux7() {
    try {
      WebClient.RequestBodySpec.class.getMethod("exchange");
      return false;
    } catch (NoSuchMethodException ignored) {
      return true;
    }
  }

  static Mono<ClientResponse> exchangeToMono(WebClient.RequestBodySpec request) {
    return request.exchangeToMono(Mono::just);
  }

  static int doRequest(WebClient.RequestBodySpec request) {
    return doRequest(request, response -> Mono.just(response.statusCode().value()));
  }

  static <T> T doRequest(
      WebClient.RequestBodySpec request, Function<ClientResponse, Mono<T>> handler) {
    return request.exchangeToMono(handler).block();
  }

  static int getStatusCode(ClientResponse response) {
    return response.statusCode().value();
  }

  static void sendRequestWithCallback(
      WebClient.RequestBodySpec request,
      Consumer<Integer> callback,
      Consumer<Throwable> errorCallback) {
    request
        .exchangeToMono(response -> Mono.just(response.statusCode().value()))
        .subscribe(callback, errorCallback);
  }

  private Webflux7Util() {}
}
