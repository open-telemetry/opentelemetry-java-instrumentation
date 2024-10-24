/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public class SuppressNestedClientHelper {

  public static Scope disallowNestedClientSpanSync(com.azure.core.util.Context azContext) {
    Context parentContext = currentContext();
    boolean hasAzureClientSpan = azContext.getData("client-method-call-flag").isPresent();
    if (doesNotHaveClientSpan(parentContext) && hasAzureClientSpan) {
      return disallowNestedClientSpan(parentContext).makeCurrent();
    }
    return null;
  }

  public static <T> Mono<T> disallowNestedClientSpanMono(
      Mono<T> delegate, com.azure.core.util.Context azContext) {
    return new Mono<T>() {
      @Override
      public void subscribe(CoreSubscriber<? super T> coreSubscriber) {
        Context parentContext = currentContext();

        boolean hasAzureClientSpan = azContext.getData("client-method-call-flag").isPresent();
        if (doesNotHaveClientSpan(parentContext) && hasAzureClientSpan) {
          try (Scope ignored = disallowNestedClientSpan(parentContext).makeCurrent()) {
            delegate.subscribe(coreSubscriber);
          }
        } else {
          delegate.subscribe(coreSubscriber);
        }
      }
    };
  }

  private static boolean doesNotHaveClientSpan(Context parentContext) {
    return SpanKey.KIND_CLIENT.fromContextOrNull(parentContext) == null
        && SpanKey.HTTP_CLIENT.fromContextOrNull(parentContext) == null;
  }

  private static Context disallowNestedClientSpan(Context parentContext) {
    Span span = Span.getInvalid();
    return SpanKey.HTTP_CLIENT.storeInContext(
        SpanKey.KIND_CLIENT.storeInContext(parentContext, span), span);
  }

  private SuppressNestedClientHelper() {}
}
