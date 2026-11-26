/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1.ApacheHttpAsyncClientInstrumentation.DelegatingRequestProducer;
import io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1.ApacheHttpAsyncClientInstrumentation.WrappedFutureCallback;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

class ApacheHttpAsyncClientStateTest {

  @Test
  void consumesSpanBeforeReentrantResponseGetter() throws Exception {
    HttpContext httpContext = mock(HttpContext.class);
    WrappedFutureCallback<Object> callback = startRequest(httpContext);
    AtomicBoolean firstRead = new AtomicBoolean(true);
    when(httpContext.getAttribute(HttpCoreContext.HTTP_RESPONSE))
        .thenAnswer(invocation -> {
          if (firstRead.getAndSet(false)) {
            callback.cancelled();
          }
          return null;
        });

    callback.completed(new Object());

    verify(httpContext, times(1)).getAttribute(HttpCoreContext.HTTP_RESPONSE);
  }

  @Test
  void claimsSpanOnceWhenCompletionRacesCancellation() throws Exception {
    HttpContext httpContext = mock(HttpContext.class);
    WrappedFutureCallback<Object> callback = startRequest(httpContext);
    CountDownLatch responseRead = new CountDownLatch(1);
    CountDownLatch resumeCompletion = new CountDownLatch(1);
    AtomicBoolean firstRead = new AtomicBoolean(true);
    when(httpContext.getAttribute(HttpCoreContext.HTTP_RESPONSE))
        .thenAnswer(invocation -> {
          if (firstRead.getAndSet(false)) {
            responseRead.countDown();
            assertThat(resumeCompletion.await(10, SECONDS)).isTrue();
          }
          return null;
        });
    CompletableFuture<Void> completion =
        CompletableFuture.runAsync(() -> callback.completed(new Object()));

    try {
      assertThat(responseRead.await(10, SECONDS)).isTrue();
      callback.cancelled();
    } finally {
      resumeCompletion.countDown();
      completion.get(10, SECONDS);
    }

    verify(httpContext, times(1)).getAttribute(HttpCoreContext.HTTP_RESPONSE);
  }

  private static WrappedFutureCallback<Object> startRequest(HttpContext httpContext)
      throws Exception {
    WrappedFutureCallback<Object> callback =
        new WrappedFutureCallback<>(Context.root(), httpContext, null);
    HttpAsyncRequestProducer delegate = mock(HttpAsyncRequestProducer.class);
    when(delegate.getTarget()).thenReturn(new HttpHost("localhost"));
    when(delegate.generateRequest()).thenReturn(new BasicHttpRequest("GET", "/test"));
    new DelegatingRequestProducer(Context.root(), delegate, callback).generateRequest();
    return callback;
  }
}
