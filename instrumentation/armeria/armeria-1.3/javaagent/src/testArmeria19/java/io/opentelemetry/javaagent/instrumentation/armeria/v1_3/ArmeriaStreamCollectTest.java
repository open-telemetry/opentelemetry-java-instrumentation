/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests context propagation through the {@code collect()} API path in Armeria 1.9.x.
 *
 * <p>{@code StreamMessage.collect()} was introduced in Armeria 1.9.0. In versions 1.9.0 through
 * 1.9.2, {@code DefaultStreamMessage.collect()} creates a {@code SubscriptionImpl} with a {@code
 * NoopSubscriber} (bypassing {@code SubscriberWrapper}) and a non-null {@code CompletableFuture} as
 * constructor argument 4. The {@code WrapCompletableFutureAdvice} wraps that future to propagate
 * context, this is the only context propagation path for the collect flow, since {@code
 * SubscriberWrapper} skips {@code NoopSubscriber}.
 *
 * <p>Starting in Armeria 1.10.0, the {@code SubscriptionImpl} constructor was refactored to remove
 * the {@code CompletableFuture} parameter at position 4, so the {@code WrapCompletableFutureAdvice}
 * matcher no longer matches and the advice is effectively a no-op. Context propagation for {@code
 * collect()} in 1.10.0+ is handled through other mechanisms in the Armeria codebase itself.
 */
class ArmeriaStreamCollectTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static ServerExtension server =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service(
              "/stream",
              (ctx, req) ->
                  HttpResponse.of(
                      ResponseHeaders.of(HttpStatus.OK),
                      HttpData.ofUtf8("hello"),
                      HttpData.ofUtf8("world")));
        }
      };

  @Test
  void collectPropagatesContext() throws Exception {
    AtomicReference<SpanContext> collectContext = new AtomicReference<>();
    CompletableFuture<List<HttpObject>> collectFuture = new CompletableFuture<>();

    testing.runWithSpan(
        "parent",
        () -> {
          HttpResponse response = WebClient.builder(server.httpUri()).build().get("/stream");
          // collect() in Armeria 1.9.x uses a SubscriptionImpl with NoopSubscriber + a
          // CompletableFuture as arg 4. The CompletableFutureWrapper is the only mechanism
          // propagating context here since SubscriberWrapper skips NoopSubscriber.
          response
              .collect()
              .thenAccept(
                  objects -> {
                    collectContext.set(Span.current().getSpanContext());
                    collectFuture.complete(objects);
                  })
              .exceptionally(
                  t -> {
                    collectFuture.completeExceptionally(t);
                    return null;
                  });
        });

    List<HttpObject> collected = collectFuture.get(10, SECONDS);
    assertThat(collected).isNotEmpty();

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span -> span.hasName("GET").hasParent(trace.getSpan(0)),
              span -> span.hasName("GET /stream").hasParent(trace.getSpan(1)));

          SpanContext parentSpanContext = trace.getSpan(0).getSpanContext();

          // The collect() callback should have context propagated via CompletableFutureWrapper.
          // Without the wrapper (or with a broken wrapper), context would be lost because
          // NoopSubscriber is used and SubscriberWrapper skips it.
          assertThat(collectContext.get()).isNotNull();
          assertThat(collectContext.get().getTraceId()).isEqualTo(parentSpanContext.getTraceId());
        });
  }
}
