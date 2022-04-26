/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_14;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public class SuppressNestedClientMono<T> extends Mono<T> {

  private final Mono<T> delegate;

  public SuppressNestedClientMono(Mono<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void subscribe(CoreSubscriber<? super T> actual) {
    Context parentContext = currentContext();
    if (SpanKey.HTTP_CLIENT.fromContextOrNull(parentContext) == null) {
      try (Scope ignored =
          SpanKey.HTTP_CLIENT.storeInContext(parentContext, Span.getInvalid()).makeCurrent()) {
        delegate.subscribe(actual);
      }
    } else {
      delegate.subscribe(actual);
    }
  }
}
