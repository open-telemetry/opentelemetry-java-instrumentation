/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;

/**
 * Based on Spring Sleuth's Reactor instrumentation.
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientBeanPostProcessor.java
 */
public final class TraceWebClientSubscriber implements CoreSubscriber<ClientResponse> {

  final CoreSubscriber<? super ClientResponse> actual;

  final reactor.util.context.Context context;

  private final Operation<ClientResponse> operation;

  public TraceWebClientSubscriber(
      CoreSubscriber<? super ClientResponse> actual, Operation<ClientResponse> operation) {
    this.actual = actual;
    this.context = actual.currentContext();
    this.operation = operation;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.actual.onSubscribe(subscription);
  }

  @Override
  public void onNext(ClientResponse response) {
    operation.end(response);
    try (Scope ignored = operation.makeParentCurrent()) {
      actual.onNext(response);
    }
  }

  @Override
  public void onError(Throwable t) {
    operation.endExceptionally(t);
    try (Scope ignored = operation.makeParentCurrent()) {
      actual.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = operation.makeParentCurrent()) {
      actual.onComplete();
    }
  }

  @Override
  public reactor.util.context.Context currentContext() {
    return this.context;
  }
}
